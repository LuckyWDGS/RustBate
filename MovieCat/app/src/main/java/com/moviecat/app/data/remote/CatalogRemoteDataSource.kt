package com.moviecat.app.data.remote

import android.content.Context
import android.util.Log
import com.moviecat.app.data.model.CatalogItem
import com.moviecat.app.data.model.CategoryParam
import com.moviecat.app.data.model.DnsMode
import com.moviecat.app.data.model.Episode
import com.moviecat.app.data.model.ParsedPayload
import com.moviecat.app.data.model.SourceItem
import com.moviecat.app.data.model.SourceKind
import com.moviecat.app.data.model.SpiderMode
import com.moviecat.app.util.debugSummary
import com.moviecat.app.util.previewForLog
import java.net.InetAddress
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.util.regex.Pattern
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Request
import okhttp3.dnsoverhttps.DnsOverHttps

class CatalogRemoteDataSource(private val context: Context) {
    private val tag = "MovieCatNet"
    private val browserUserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36"
    private val bootstrapClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val quickJsSpiderEngine = QuickJsSpiderEngine(::loadTextWithSettings)
    private val jarSpiderEngine = CatVodJarSpiderEngine(context, ::downloadBinary)
    private val clientCache = ConcurrentHashMap<String, OkHttpClient>()

    suspend fun fetchPayload(source: SourceItem): ParsedPayload = withContext(Dispatchers.IO) {
        Log.d(tag, "fetchPayload start ${source.debugSummary()}")
        val payload = when {
            source.kind == SourceKind.TVBOX_CONFIG -> {
                val bodyBytes = downloadConfigBytes(source)
                val body = runCatching { bodyBytes.toString(StandardCharsets.UTF_8) }.getOrDefault("")
                val parsed = SourcePayloadParser.parseBytes(bodyBytes)
                if (parsed.catalogItems.isNotEmpty() || parsed.discoveredSources.isNotEmpty()) {
                    parsed
                } else {
                    resolveLandingConfig(source, body) ?: parsed
                }
            }
            source.settings.spiderMode == SpiderMode.QUICKJS -> {
                quickJsSpiderEngine.fetchHome(source)
            }
            source.settings.spiderMode == SpiderMode.CATVOD_JAR || source.kind == SourceKind.SPIDER -> {
                jarSpiderEngine.fetchHome(source)
            }
            else -> {
                SourcePayloadParser.parse(loadTextWithSettings(source.url, source))
            }
        }
        Log.d(tag, "fetchPayload done ${source.label}: ${payload.debugSummary()}")
        payload
    }

    suspend fun resolveDetail(source: SourceItem, item: CatalogItem): CatalogItem = withContext(Dispatchers.IO) {
        Log.d(tag, "resolveDetail start ${source.label}: ${item.debugSummary()}")
        val resolved = when {
            item.playlists.isNotEmpty() -> item
            source.settings.spiderMode == SpiderMode.QUICKJS -> {
                quickJsSpiderEngine.fetchDetail(source, item) ?: item
            }
            source.settings.spiderMode == SpiderMode.CATVOD_JAR || source.kind == SourceKind.SPIDER -> {
                jarSpiderEngine.fetchDetail(source, item) ?: item
            }
            else -> {
                val detailToken = item.detailToken ?: return@withContext item
                val detailUrl = buildDetailUrl(source, detailToken) ?: return@withContext item
                SourcePayloadParser.parseDetailItem(loadTextWithSettings(detailUrl, source)) ?: item
            }
        }
        Log.d(tag, "resolveDetail done ${source.label}: ${resolved.debugSummary()}")
        resolved
    }

    suspend fun resolveEpisode(source: SourceItem, episode: Episode): Episode = withContext(Dispatchers.IO) {
        Log.d(tag, "resolveEpisode start ${source.label}: ${episode.debugSummary()}")
        val resolved = when {
            source.settings.spiderMode == SpiderMode.QUICKJS -> quickJsSpiderEngine.resolveEpisode(source, episode)
            source.settings.spiderMode == SpiderMode.CATVOD_JAR || source.kind == SourceKind.SPIDER -> {
                jarSpiderEngine.resolveEpisode(source, episode)
            }
            else -> episode
        }
        Log.d(tag, "resolveEpisode done ${source.label}: ${resolved.debugSummary()}")
        resolved
    }

    suspend fun searchCatalog(source: SourceItem, query: String): ParsedPayload = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            return@withContext ParsedPayload(warnings = listOf("请输入搜索关键词。"))
        }
        Log.d(tag, "searchCatalog start ${source.debugSummary()} query=${query.previewForLog(80)}")
        val payload = when {
            source.settings.spiderMode == SpiderMode.QUICKJS -> quickJsSpiderEngine.search(source, query)
            source.settings.spiderMode == SpiderMode.CATVOD_JAR || source.kind == SourceKind.SPIDER -> {
                jarSpiderEngine.search(source, query)
            }
            else -> {
                val searchUrl = buildSearchUrl(source, query)
                    ?: return@withContext ParsedPayload(warnings = listOf("当前源没有可用的搜索接口。"))
                SourcePayloadParser.parse(loadTextWithSettings(searchUrl, source))
            }
        }
        Log.d(tag, "searchCatalog done ${source.label}: ${payload.debugSummary()}")
        payload
    }

    suspend fun fetchCategory(
        source: SourceItem,
        category: CategoryParam,
        page: Int = 1
    ): ParsedPayload = withContext(Dispatchers.IO) {
        Log.d(tag, "fetchCategory start ${source.label}: category=${category.name}/${category.value}, page=$page")
        val payload = when {
            source.settings.spiderMode == SpiderMode.QUICKJS -> quickJsSpiderEngine.fetchCategory(source, category, page)
            source.settings.spiderMode == SpiderMode.CATVOD_JAR || source.kind == SourceKind.SPIDER -> {
                jarSpiderEngine.fetchCategory(source, category, page)
            }
            else -> {
                val categoryUrl = buildCategoryUrl(source, category, page)
                    ?: return@withContext ParsedPayload(warnings = listOf("当前源没有可用的分类接口。"))
                SourcePayloadParser.parse(loadTextWithSettings(categoryUrl, source))
            }
        }
        Log.d(tag, "fetchCategory done ${source.label}: ${payload.debugSummary()}")
        payload
    }

    suspend fun loadTextWithSettings(url: String, source: SourceItem): String = withContext(Dispatchers.IO) {
        executeRequest(url, source).use { response ->
            if (!response.isSuccessful) {
                error("请求失败：HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) {
                error("接口返回为空")
            }
            Log.d(tag, "loadTextWithSettings body url=${response.request.url} size=${body.length} preview=${body.previewForLog()}")
            body
        }
    }

    suspend fun downloadBinary(url: String, source: SourceItem): ByteArray = withContext(Dispatchers.IO) {
        executeRequest(url, source).use { response ->
            if (!response.isSuccessful) {
                error("请求失败：HTTP ${response.code}")
            }
            response.body?.bytes() ?: error("二进制资源为空")
        }
    }

    private suspend fun downloadConfigBytes(source: SourceItem): ByteArray = withContext(Dispatchers.IO) {
        val primary = runCatching {
            executeRequest(
                url = source.url,
                source = source,
                overrideUserAgent = source.settings.userAgent ?: "MovieCat/0.2 Android TV",
                forceIdentityEncoding = false
            ).use { response ->
                if (!response.isSuccessful) error("请求失败：HTTP ${response.code}")
                val bytes = response.body?.bytes()?.decodeMaybeGzip(response.header("Content-Encoding")) ?: error("接口返回为空")
                Log.d(tag, "config primary bytes=${bytes.size}, encoding=${response.header("Content-Encoding")}, url=${source.url}")
                bytes
            }
        }.getOrNull()

        val primaryParsed = primary?.let(SourcePayloadParser::parseBytes)
        if (primary != null && primaryParsed != null &&
            (primaryParsed.catalogItems.isNotEmpty() || primaryParsed.discoveredSources.isNotEmpty())
        ) {
            return@withContext primary
        }

        executeRequest(
            url = source.url,
            source = source,
            overrideUserAgent = browserUserAgent,
            forceIdentityEncoding = false
        ).use { response ->
            if (!response.isSuccessful) error("请求失败：HTTP ${response.code}")
            val bytes = response.body?.bytes()?.decodeMaybeGzip(response.header("Content-Encoding")) ?: error("接口返回为空")
            Log.d(tag, "config fallback bytes=${bytes.size}, encoding=${response.header("Content-Encoding")}, url=${source.url}")
            bytes
        }
    }

    private fun buildDetailUrl(source: SourceItem, detailToken: String): String? {
        source.settings.detailPath
            ?.takeIf { it.isNotBlank() }
            ?.let { detailPath ->
                return detailPath
                    .replace("{id}", detailToken)
                    .replace("{base}", source.url.substringBefore("?"))
            }

        return when {
            source.url.contains("ac=detail") -> source.url
            source.url.contains("ac=list") -> source.url
                .replace("ac=list", "ac=detail")
                .plus(if (source.url.contains("ids=")) "" else "&ids=$detailToken")
            source.url.contains("?") -> "${source.url}&ac=detail&ids=$detailToken"
            else -> "${source.url}?ac=detail&ids=$detailToken"
        }
    }

    private fun buildSearchUrl(source: SourceItem, query: String): String? {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        source.settings.searchPath
            ?.takeIf { it.isNotBlank() }
            ?.let { searchPath ->
                return searchPath
                    .replace("{wd}", encoded)
                    .replace("{query}", encoded)
                    .replace("{base}", source.url.substringBefore("?"))
            }

        return when {
            source.url.contains("wd=") -> source.url.replaceAfter("wd=", encoded)
            source.url.contains("ac=list") -> source.url.replace("ac=list", "ac=detail") + "&wd=$encoded"
            source.url.contains("?") -> "${source.url}&wd=$encoded"
            else -> "${source.url}?wd=$encoded"
        }
    }

    private fun buildCategoryUrl(source: SourceItem, category: CategoryParam, page: Int): String? {
        val categoryId = URLEncoder.encode(category.value.ifBlank { category.name }, StandardCharsets.UTF_8.toString())
        source.settings.apiPath
            ?.takeIf { it.contains("{tid}") || it.contains("{id}") }
            ?.let { apiPath ->
                return apiPath
                    .replace("{tid}", categoryId)
                    .replace("{id}", categoryId)
                    .replace("{pg}", page.toString())
                    .replace("{base}", source.url.substringBefore("?"))
            }

        return when {
            source.url.contains("ac=list") -> {
                val cleaned = source.url.substringBefore("&pg=").substringBefore("&t=").substringBefore("&type=")
                "$cleaned&t=$categoryId&pg=$page"
            }
            source.url.contains("?") -> "${source.url}&t=$categoryId&pg=$page"
            else -> "${source.url}?t=$categoryId&pg=$page"
        }
    }

    private fun clientFor(source: SourceItem): OkHttpClient {
        val cacheKey = buildString {
            append(source.settings.dnsMode.name)
            append("|")
            append(source.settings.dohUrl.orEmpty())
            append("|")
            append(source.settings.userAgent.orEmpty())
            append("|")
            append(source.settings.requestHeadersText.hashCode())
        }
        return clientCache.getOrPut(cacheKey) {
            val builder = bootstrapClient.newBuilder()
            when (source.settings.dnsMode) {
                DnsMode.SYSTEM -> Unit
                DnsMode.DOH_CLOUDFLARE -> {
                    val dns = buildDoh("https://cloudflare-dns.com/dns-query")
                    if (dns != null) builder.dns(dns)
                }
                DnsMode.DOH_GOOGLE -> {
                    val dns = buildDoh("https://dns.google/dns-query")
                    if (dns != null) builder.dns(dns)
                }
                DnsMode.CUSTOM_DOH -> {
                    val dns = buildDoh(source.settings.dohUrl)
                    if (dns != null) builder.dns(dns)
                }
            }
            builder.build()
        }
    }

    private fun executeRequest(
        url: String,
        source: SourceItem,
        overrideUserAgent: String? = null,
        forceIdentityEncoding: Boolean = false
    ): Response {
        return try {
            rawExecute(url, source, overrideUserAgent, forceIdentityEncoding)
        } catch (error: Throwable) {
            val fallbackUrl = url.toHttpFallbackUrl()
            if (fallbackUrl != null && error.isSslLikeError()) {
                Log.w(tag, "executeRequest ssl fallback ${source.label}: $url -> $fallbackUrl", error)
                rawExecute(fallbackUrl, source, overrideUserAgent, forceIdentityEncoding)
            } else {
                Log.e(tag, "executeRequest failed ${source.label}: $url", error)
                throw error
            }
        }
    }

    private fun rawExecute(
        url: String,
        source: SourceItem,
        overrideUserAgent: String? = null,
        forceIdentityEncoding: Boolean = false
    ): Response {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", overrideUserAgent ?: source.settings.userAgent ?: "MovieCat/0.2 Android")
            .apply {
                if (forceIdentityEncoding) {
                    header("Accept-Encoding", "identity")
                }
                source.settings.headerMap().forEach { (key, value) -> header(key, value) }
            }
            .build()
        Log.d(
            tag,
            "request ${source.label}: ${request.method} ${request.url} ua=${request.header("User-Agent")} headers=${request.headers.names().joinToString()}"
        )
        return clientFor(source).newCall(request).execute().also { response ->
            Log.d(
                tag,
                "response ${source.label}: code=${response.code} finalUrl=${response.request.url} contentType=${response.body?.contentType()} length=${response.body?.contentLength()}"
            )
        }
    }

    private fun buildDoh(url: String?): okhttp3.Dns? {
        val dohUrl = url?.toHttpUrlOrNull() ?: return null
        return runCatching {
            DnsOverHttps.Builder()
                .client(bootstrapClient)
                .url(dohUrl)
                .bootstrapDnsHosts(
                    InetAddress.getByName("1.1.1.1"),
                    InetAddress.getByName("8.8.8.8")
                )
                .build()
        }.getOrNull()
    }

    private suspend fun resolveLandingConfig(source: SourceItem, body: String): ParsedPayload? {
        val candidates = extractLandingConfigUrls(source.url, body)
        if (candidates.isEmpty()) {
            return null
        }
        val discovered = candidates.mapIndexed { index, url ->
            SourceItem(
                id = "landing-${source.id}-$index",
                label = "${source.label} 候选 ${index + 1}",
                url = url,
                kind = if (url.endsWith(".js", ignoreCase = true) || url.contains("csp_")) {
                    SourceKind.SPIDER
                } else {
                    SourceKind.TVBOX_CONFIG
                },
                isPinned = false,
                settings = source.settings
            )
        }
        val primary = discovered.first()
        Log.d(tag, "resolveLandingConfig hit ${source.label}: primary=${primary.url}, candidates=${candidates.size}")
        val nested = fetchPayload(primary)
        return nested.copy(
            discoveredSources = discovered + nested.discoveredSources,
            warnings = listOf("已从导航页自动识别配置地址：${primary.url}") + nested.warnings
        )
    }

    private fun extractLandingConfigUrls(baseUrl: String, body: String): List<String> {
        val trimmed = body.trim()
        if (!trimmed.startsWith("<")) {
            return emptyList()
        }
        val document = Jsoup.parse(body, baseUrl)
        val clipboardValues = document.select("[data-clipboard-text]")
            .mapNotNull { it.attr("data-clipboard-text").trim().takeIf(String::isNotBlank) }
        val hrefValues = document.select("a[href]")
            .mapNotNull { it.absUrl("href").trim().takeIf(String::isNotBlank) }
        val textValues = document.select(".link-url, code, pre")
            .mapNotNull { it.text().trim().takeIf(String::isNotBlank) }
        val rawMatches = extractRawHttpUrls(body)

        return (clipboardValues + hrefValues + textValues + rawMatches)
            .mapNotNull { normalizeCandidateUrl(baseUrl, it) }
            .filter { candidate ->
                candidate.startsWith("http") && (
                    candidate.contains("/tv") ||
                        candidate.endsWith(".json", ignoreCase = true) ||
                        candidate.endsWith(".js", ignoreCase = true) ||
                        candidate.endsWith(".bmp", ignoreCase = true)
                    )
            }
            .distinct()
            .also { matches ->
                if (matches.isNotEmpty()) {
                    Log.d(tag, "extractLandingConfigUrls base=$baseUrl hits=${matches.size} first=${matches.first().previewForLog(120)}")
                }
            }
    }

    private fun normalizeCandidateUrl(baseUrl: String, value: String): String? {
        return when {
            value.startsWith("http", ignoreCase = true) -> value
            value.startsWith("/") -> runCatching {
                val base = URI(baseUrl)
                "${base.scheme}://${base.host}$value"
            }.getOrNull()
            else -> null
        }
    }

    private fun extractRawHttpUrls(body: String): List<String> {
        val regex = Pattern.compile("""https?://[^\s"'<>]+""")
        val matcher = regex.matcher(body)
        val matches = mutableListOf<String>()
        while (matcher.find()) {
            matches += matcher.group()
        }
        return matches
    }

    private fun String.toHttpFallbackUrl(): String? {
        return if (startsWith("https://", ignoreCase = true)) {
            "http://" + removePrefix("https://")
        } else {
            null
        }
    }

    private fun Throwable.isSslLikeError(): Boolean {
        return this is SSLException ||
            this is SSLHandshakeException ||
            this is SSLPeerUnverifiedException ||
            message.orEmpty().contains("certificate", ignoreCase = true) ||
            message.orEmpty().contains("hostname", ignoreCase = true) ||
            message.orEmpty().contains("SSL", ignoreCase = true)
    }

    private fun ByteArray.decodeMaybeGzip(contentEncoding: String?): ByteArray {
        if (!contentEncoding.orEmpty().contains("gzip", ignoreCase = true)) {
            return this
        }
        return runCatching {
            GZIPInputStream(inputStream()).use { it.readBytes() }
        }.getOrDefault(this)
    }
}
