package com.moviecat.app.data.remote

import android.util.Log
import com.google.gson.GsonBuilder
import com.moviecat.app.data.model.CategoryParam
import com.moviecat.app.data.model.DnsMode
import com.moviecat.app.data.model.SourceItem
import com.moviecat.app.data.model.SourceKind
import com.moviecat.app.data.model.SourceSettings
import com.moviecat.app.data.model.SpiderMode
import com.moviecat.app.data.repository.SourceDraft
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Collections
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

data class LanControlSnapshot(
    val sources: List<SourceItem>,
    val selectedSourceId: String?,
    val warnings: List<String>,
    val lastError: String?,
    val catalogCount: Int,
    val featuredCount: Int,
    val categories: List<CategoryParam>
)

class LanControlServer(
    private val scope: CoroutineScope,
    private val snapshotProvider: () -> LanControlSnapshot,
    private val onUpsertSource: suspend (SourceDraft) -> Unit,
    private val onDeleteSource: suspend (String) -> Unit,
    private val onSelectSource: suspend (String) -> Unit
) {
    private val tag = "MovieCatLan"
    private val gson = GsonBuilder().setPrettyPrinting().create()

    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    var port: Int = 8090
        private set

    val isRunning: Boolean
        get() = acceptJob?.isActive == true && serverSocket?.isClosed == false

    suspend fun start(port: Int = 8090): List<String> = withContext(Dispatchers.IO) {
        if (isRunning) {
            return@withContext resolveUrls(this@LanControlServer.port)
        }

        this@LanControlServer.port = port
        val socket = ServerSocket(port)
        serverSocket = socket
        acceptJob = scope.launch(Dispatchers.IO) {
            while (!socket.isClosed) {
                runCatching { socket.accept() }
                    .onSuccess { client ->
                        launch(Dispatchers.IO) { handleClient(client) }
                    }
                    .onFailure { error ->
                        if (error !is CancellationException && !socket.isClosed) throw error
                    }
            }
        }
        resolveUrls(port)
    }

    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        serverSocket?.close()
        serverSocket = null
    }

    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        socket.use { client ->
            client.soTimeout = 5000
            val inputStream = client.getInputStream()
            val writer = BufferedWriter(OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8))

            try {
                val requestLine = readAsciiLine(inputStream) ?: return@withContext
                val parts = requestLine.split(" ")
                if (parts.size < 2) {
                    sendText(writer, "400 Bad Request", "Bad Request")
                    return@withContext
                }

                val method = parts[0]
                val fullPath = parts[1]
                val path = fullPath.substringBefore("?")
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = readAsciiLine(inputStream) ?: break
                    if (line.isBlank()) break
                    val separator = line.indexOf(':')
                    if (separator > 0) {
                        headers[line.substring(0, separator).trim().lowercase()] =
                            line.substring(separator + 1).trim()
                    }
                }

                val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                val body = if (contentLength > 0) readBody(inputStream, contentLength) else ""

                when {
                    method == "GET" && path == "/" -> sendHtml(writer, renderIndexPage(snapshotProvider()))
                    method == "GET" && path == "/api/sources" -> sendJson(writer, gson.toJson(snapshotProvider()))
                    method == "POST" && path == "/save" -> {
                        val form = parseForm(body)
                        runBlocking { onUpsertSource(form.toDraft()) }
                        sendRedirect(writer)
                    }
                    method == "POST" && path == "/delete" -> {
                        parseForm(body)["id"]?.let { id -> runBlocking { onDeleteSource(id) } }
                        sendRedirect(writer)
                    }
                    method == "POST" && path == "/select" -> {
                        parseForm(body)["id"]?.let { id -> runBlocking { onSelectSource(id) } }
                        sendRedirect(writer)
                    }
                    method == "GET" && path == "/health" -> sendJson(writer, """{"status":"ok"}""")
                    else -> sendText(writer, "404 Not Found", "Not Found")
                }
            } catch (error: Throwable) {
                Log.e(tag, "LAN control request failed", error)
                runCatching {
                    sendText(writer, "500 Internal Server Error", error.message ?: "Internal Server Error")
                }
            }
        }
    }

    private fun parseForm(encoded: String): Map<String, String> {
        if (encoded.isBlank()) return emptyMap()
        return encoded.split("&")
            .mapNotNull { pair ->
                val index = pair.indexOf('=')
                if (index < 0) return@mapNotNull null
                val key = URLDecoder.decode(pair.substring(0, index), "UTF-8")
                val value = URLDecoder.decode(pair.substring(index + 1), "UTF-8")
                key to value
            }
            .toMap()
    }

    private fun Map<String, String>.toDraft(): SourceDraft {
        val sourceKind = get("kind").orEmpty().takeIf { it.isNotBlank() }?.let {
            runCatching { SourceKind.valueOf(it) }.getOrDefault(SourceKind.DIRECT)
        }
        val dnsMode = runCatching { DnsMode.valueOf(get("dnsMode").orEmpty()) }.getOrDefault(DnsMode.SYSTEM)
        val spiderMode = runCatching { SpiderMode.valueOf(get("spiderMode").orEmpty()) }.getOrDefault(SpiderMode.NONE)

        return SourceDraft(
            id = get("id")?.takeIf { it.isNotBlank() },
            label = get("label").orEmpty(),
            url = get("url").orEmpty(),
            kind = sourceKind,
            isPinned = get("isPinned") == "on",
            settings = SourceSettings(
                dnsMode = dnsMode,
                dohUrl = get("dohUrl"),
                userAgent = get("userAgent"),
                blockVideoAds = get("blockVideoAds") == "on",
                adSkipSeconds = get("adSkipSeconds")?.toIntOrNull() ?: 35,
                requestHeadersText = get("requestHeadersText").orEmpty(),
                categoryParamsText = get("categoryParamsText").orEmpty(),
                homeRecommendationsText = get("homeRecommendationsText").orEmpty(),
                spiderMode = spiderMode,
                spiderClass = get("spiderClass"),
                spiderJarUrl = get("spiderJarUrl"),
                spiderScriptUrl = get("spiderScriptUrl"),
                spiderScriptCode = get("spiderScriptCode"),
                spiderExt = get("spiderExt"),
                apiPath = get("apiPath"),
                detailPath = get("detailPath"),
                searchPath = get("searchPath"),
                extraNotes = get("extraNotes")
            )
        )
    }

    private fun readBody(inputStream: java.io.InputStream, contentLength: Int): String {
        val buffer = ByteArray(contentLength)
        var offset = 0
        while (offset < contentLength) {
            val read = inputStream.read(buffer, offset, contentLength - offset)
            if (read == -1) break
            offset += read
        }
        return String(buffer, 0, offset, StandardCharsets.UTF_8)
    }

    private fun readAsciiLine(inputStream: java.io.InputStream): String? {
        val output = ByteArrayOutputStream()
        while (true) {
            val value = inputStream.read()
            if (value == -1) {
                return if (output.size() == 0) null else output.toString(StandardCharsets.UTF_8.name())
            }
            if (value == '\n'.code) {
                break
            }
            if (value != '\r'.code) {
                output.write(value)
            }
        }
        return output.toString(StandardCharsets.UTF_8.name())
    }

    private fun sendHtml(writer: BufferedWriter, body: String) {
        sendResponse(writer, "200 OK", "text/html; charset=utf-8", body)
    }

    private fun sendJson(writer: BufferedWriter, body: String) {
        sendResponse(writer, "200 OK", "application/json; charset=utf-8", body)
    }

    private fun sendText(writer: BufferedWriter, status: String, body: String) {
        sendResponse(writer, status, "text/plain; charset=utf-8", body)
    }

    private fun sendRedirect(writer: BufferedWriter) {
        writer.write("HTTP/1.1 303 See Other\r\n")
        writer.write("Location: /\r\n")
        writer.write("Content-Length: 0\r\n")
        writer.write("Connection: close\r\n")
        writer.write("\r\n")
        writer.flush()
    }

    private fun sendResponse(writer: BufferedWriter, status: String, contentType: String, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        writer.write("HTTP/1.1 $status\r\n")
        writer.write("Content-Type: $contentType\r\n")
        writer.write("Content-Length: ${bytes.size}\r\n")
        writer.write("Connection: close\r\n")
        writer.write("\r\n")
        writer.write(body)
        writer.flush()
    }

    private fun renderIndexPage(snapshot: LanControlSnapshot): String {
        val sourceCards = snapshot.sources.joinToString(separator = "") { source ->
            val settings = source.settings
            """
            <details class="panel source-card" ${if (source.id == snapshot.selectedSourceId) "open" else ""}>
              <summary>
                <div>
                  <strong>${escapeHtml(source.label)}</strong>
                  <div class="muted">${escapeHtml(source.kind.name)} · ${escapeHtml(source.url)}</div>
                </div>
                <div class="badge">${if (source.id == snapshot.selectedSourceId) "当前使用中" else "点击展开"}</div>
              </summary>
              <form method="post" action="/save" class="source-form">
                <input type="hidden" name="id" value="${escapeHtml(source.id)}" />
                <div class="grid two">
                  ${textField("label", "名称", source.label)}
                  ${textField("url", "源地址", source.url)}
                  ${selectField("kind", "源类型", SourceKind.entries.map { it.name }, source.kind.name)}
                  ${checkboxField("isPinned", "置顶显示", source.isPinned)}
                  ${selectField("dnsMode", "DNS 策略", DnsMode.entries.map { it.name }, settings.dnsMode.name)}
                  ${textField("dohUrl", "自定义 DoH", settings.dohUrl.orEmpty())}
                  ${textField("userAgent", "User-Agent", settings.userAgent.orEmpty())}
                  ${checkboxField("blockVideoAds", "广告拦截", settings.blockVideoAds)}
                  ${textField("adSkipSeconds", "跳过秒数", settings.normalizedAdSkipSeconds().toString())}
                  ${textField("apiPath", "首页 API 覆盖", settings.apiPath.orEmpty())}
                  ${textField("detailPath", "详情 API 模板", settings.detailPath.orEmpty())}
                  ${textField("searchPath", "搜索 API 模板", settings.searchPath.orEmpty())}
                </div>
                <div class="grid two">
                  ${selectField("spiderMode", "Spider 模式", SpiderMode.entries.map { it.name }, settings.spiderMode.name)}
                  ${textField("spiderClass", "Spider 类名", settings.spiderClass.orEmpty())}
                  ${textField("spiderJarUrl", "Jar 地址", settings.spiderJarUrl.orEmpty())}
                  ${textField("spiderScriptUrl", "JS 地址", settings.spiderScriptUrl.orEmpty())}
                </div>
                ${textAreaField("spiderExt", "Spider Ext / 站点扩展", settings.spiderExt.orEmpty(), 3)}
                ${textAreaField("spiderScriptCode", "内嵌 JS 脚本", settings.spiderScriptCode.orEmpty(), 5)}
                ${textAreaField("requestHeadersText", "请求头", settings.requestHeadersText, 4, "支持 JSON 或每行 Header: Value")}
                ${textAreaField("categoryParamsText", "分类参数", settings.categoryParamsText, 4, "每行: 名称|值|附加参数")}
                ${textAreaField("homeRecommendationsText", "首页推荐位", settings.homeRecommendationsText, 4, "每行: 标题|封面|播放地址|说明")}
                ${textAreaField("extraNotes", "备注", settings.extraNotes.orEmpty(), 3)}
                <div class="actions">
                  <button type="submit">保存配置</button>
                </div>
              </form>
              <div class="actions" style="margin-top:12px;">
                <form method="post" action="/select">
                  <input type="hidden" name="id" value="${escapeHtml(source.id)}" />
                  <button type="submit">切换并加载</button>
                </form>
                <form method="post" action="/delete">
                  <input type="hidden" name="id" value="${escapeHtml(source.id)}" />
                  <button class="danger" type="submit">删除</button>
                </form>
              </div>
            </details>
            """.trimIndent()
        }

        val warningBlocks = buildList {
            snapshot.lastError?.let { add("""<div class="notice error">${escapeHtml(it)}</div>""") }
            snapshot.warnings.forEach { add("""<div class="notice warn">${escapeHtml(it)}</div>""") }
        }.joinToString("\n")

        val categoryText = snapshot.categories.joinToString("、") { it.name.ifBlank { it.value } }.ifBlank { "未配置" }

        return """
        <!doctype html>
        <html lang="zh-CN">
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1" />
          <title>MovieCat 管理后台</title>
          <style>
            :root {
              color-scheme: dark;
              --bg: #0f1217;
              --panel: rgba(24,29,36,0.92);
              --panel-2: #222833;
              --text: #eff3f8;
              --muted: #9eb0c3;
              --accent: #f6c85f;
              --accent-2: #89c2d9;
              --danger: #ef476f;
            }
            * { box-sizing: border-box; }
            body {
              margin: 0;
              padding: 24px;
              font-family: "Segoe UI", "PingFang SC", sans-serif;
              background: radial-gradient(circle at top, #1d2430 0%, var(--bg) 58%);
              color: var(--text);
            }
            .wrap { max-width: 1280px; margin: 0 auto; display: grid; gap: 18px; }
            .hero, .panel { background: var(--panel); border: 1px solid rgba(255,255,255,0.08); border-radius: 22px; padding: 20px; box-shadow: 0 12px 40px rgba(0,0,0,0.24); }
            h1, h2, p { margin: 0; }
            .hero p { margin-top: 8px; color: var(--muted); }
            .stats { margin-top: 16px; display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
            .stat { background: var(--panel-2); border-radius: 14px; padding: 12px 14px; }
            .muted { color: var(--muted); font-size: 13px; margin-top: 6px; }
            .notice { padding: 12px 14px; border-radius: 14px; margin-bottom: 10px; }
            .notice.warn { background: rgba(137,194,217,0.14); color: #d6f1ff; }
            .notice.error { background: rgba(239,71,111,0.16); color: #ffdbe4; }
            .grid { display: grid; gap: 12px; }
            .grid.two { grid-template-columns: repeat(2, minmax(0, 1fr)); }
            label.field { display: grid; gap: 8px; color: var(--muted); font-size: 14px; }
            input, textarea, select {
              width: 100%;
              padding: 12px 14px;
              border-radius: 12px;
              border: 1px solid rgba(255,255,255,0.1);
              background: #11161d;
              color: var(--text);
            }
            textarea { resize: vertical; min-height: 88px; }
            .checkbox { display: flex; align-items: center; gap: 8px; margin-top: 28px; }
            .checkbox input { width: auto; }
            button {
              border: 0;
              border-radius: 12px;
              padding: 12px 16px;
              background: var(--accent);
              color: #241800;
              font-weight: 700;
              cursor: pointer;
            }
            button.danger { background: rgba(239,71,111,0.16); color: #ffd2dd; }
            .actions { display: flex; gap: 8px; flex-wrap: wrap; }
            details summary {
              display: flex;
              justify-content: space-between;
              align-items: center;
              cursor: pointer;
              list-style: none;
            }
            details summary::-webkit-details-marker { display:none; }
            .badge {
              padding: 8px 12px;
              border-radius: 999px;
              background: rgba(246,200,95,0.16);
              color: #ffe7a6;
              font-size: 12px;
            }
            .source-form { display: grid; gap: 14px; margin-top: 16px; }
            .source-card + .source-card { margin-top: 16px; }
            code { color: #ffe8a9; }
            @media (max-width: 920px) {
              body { padding: 14px; }
              .stats, .grid.two { grid-template-columns: 1fr; }
            }
          </style>
        </head>
        <body>
          <div class="wrap">
            <section class="hero">
              <h1>MovieCat 局域网管理后台</h1>
              <p>现在这里不只是“加源地址”，而是完整的源配置中心。你可以在手机或电脑上维护 DNS、请求头、分类参数、首页推荐位，以及 Spider 的 Jar / JS / Ext 参数。</p>
              <div class="stats">
                <div class="stat"><div>已保存源</div><strong>${snapshot.sources.size}</strong></div>
                <div class="stat"><div>当前片单</div><strong>${snapshot.catalogCount}</strong></div>
                <div class="stat"><div>推荐位</div><strong>${snapshot.featuredCount}</strong></div>
                <div class="stat"><div>分类参数</div><strong>${escapeHtml(categoryText)}</strong></div>
              </div>
              <div class="muted" style="margin-top:12px;">JSON API: <code>/api/sources</code> · 健康检查: <code>/health</code></div>
            </section>
            <section class="panel">
              <h2>新增源</h2>
              <div class="muted" style="margin-top:8px;">新建后会直接保存到电视端数据库，并立刻尝试加载。</div>
              <form method="post" action="/save" class="source-form" style="margin-top:16px;">
                <div class="grid two">
                  ${textField("label", "名称", "")}
                  ${textField("url", "源地址", "")}
                  ${selectField("kind", "源类型", SourceKind.entries.map { it.name }, SourceKind.DIRECT.name)}
                  ${checkboxField("isPinned", "置顶显示", true)}
                </div>
                <div class="actions"><button type="submit">创建源</button></div>
              </form>
            </section>
            <section class="panel">
              <h2>运行状态</h2>
              <div style="margin-top:14px;">$warningBlocks</div>
            </section>
            $sourceCards
          </div>
        </body>
        </html>
        """.trimIndent()
    }

    private fun textField(name: String, label: String, value: String): String {
        return """
        <label class="field">$label
          <input name="$name" value="${escapeHtml(value)}" />
        </label>
        """.trimIndent()
    }

    private fun textAreaField(name: String, label: String, value: String, rows: Int, hint: String? = null): String {
        return """
        <label class="field">$label
          <textarea name="$name" rows="$rows" placeholder="${escapeHtml(hint.orEmpty())}">${escapeHtml(value)}</textarea>
        </label>
        """.trimIndent()
    }

    private fun selectField(name: String, label: String, options: List<String>, selected: String): String {
        val optionHtml = options.joinToString("") { option ->
            """<option value="$option" ${if (option == selected) "selected" else ""}>$option</option>"""
        }
        return """
        <label class="field">$label
          <select name="$name">$optionHtml</select>
        </label>
        """.trimIndent()
    }

    private fun checkboxField(name: String, label: String, checked: Boolean): String {
        return """
        <label class="checkbox">
          <input type="checkbox" name="$name" ${if (checked) "checked" else ""} />
          <span>$label</span>
        </label>
        """.trimIndent()
    }

    private fun resolveUrls(port: Int): List<String> {
        val interfaces = NetworkInterface.getNetworkInterfaces()?.let { Collections.list(it) }.orEmpty()
        return interfaces
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { network ->
                Collections.list(network.inetAddresses)
                    .filterIsInstance<Inet4Address>()
                    .filter { !it.isLoopbackAddress }
                    .map { "http://${it.hostAddress}:$port" }
            }
            .distinct()
            .sorted()
    }

    private fun escapeHtml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
