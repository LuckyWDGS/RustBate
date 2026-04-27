package com.moviecat.app.data.remote

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.moviecat.app.data.model.CatalogItem
import com.moviecat.app.data.model.CategoryParam
import com.moviecat.app.data.model.Episode
import com.moviecat.app.data.model.ParsedPayload
import com.moviecat.app.data.model.PlaylistGroup
import com.moviecat.app.data.model.SourceItem
import com.moviecat.app.data.model.SourceKind
import com.moviecat.app.data.model.SourceSettings
import com.moviecat.app.data.model.SpiderMode
import com.moviecat.app.util.debugSummary
import com.moviecat.app.util.previewForLog
import java.util.Base64
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

object SourcePayloadParser {
    private const val tag = "MovieCatParser"

    fun parseBytes(bodyBytes: ByteArray): ParsedPayload {
        decodeBytesToJsonCandidate(bodyBytes)?.let { decoded ->
            Log.d(tag, "parseBytes decoded embedded config, bytes=${bodyBytes.size}, head=${decoded.take(32)}")
            return parse(decoded)
        }
        Log.d(tag, "parseBytes fallback utf8, bytes=${bodyBytes.size}")
        val fallback = bodyBytes.toString(StandardCharsets.UTF_8)
        return parse(fallback)
    }

    fun parse(body: String): ParsedPayload {
        val normalized = sanitizeJsonLikeText(normalizeJsonCandidate(body))
        val root = runCatching { JsonParser.parseString(normalized) }.getOrNull()
            ?: return ParsedPayload(
                warnings = listOf("当前源不是标准 JSON，Jar/JS Spider 将走单独的运行链路。")
            ).also {
                Log.w(tag, "parse failed as JSON, preview=${normalized.previewForLog()}")
            }

        if (root.isJsonObject && root.asJsonObject.has("sites")) {
            return parseTvBoxConfig(root.asJsonObject)
        }

        return parseCatalog(root)
    }

    fun parseSpiderJson(body: String): ParsedPayload {
        return parse(body)
    }

    fun parseDetailItem(body: String): CatalogItem? {
        val payload = parse(body)
        return payload.catalogItems.firstOrNull()
    }

    fun parsePlayerEpisode(raw: String, fallbackName: String, fallbackFlag: String?): Episode? {
        val normalized = normalizeJsonCandidate(raw)
        val root = runCatching { JsonParser.parseString(normalized) }.getOrNull()
            ?: return null
        if (!root.isJsonObject) {
            return null
        }
        val json = root.asJsonObject
        val url = json.stringValue("url")
            ?: json.stringValue("playUrl")
            ?: return null
        val parse = json.intValue("parse")
        val headers = parseHeaderMap(
            json.get("header")?.takeIf { it.isJsonObject }?.asJsonObject
        )
        return Episode(
            name = fallbackName,
            url = url,
            flag = fallbackFlag,
            headers = headers,
            parse = parse
        ).also {
            Log.d(tag, "parsePlayerEpisode -> ${it.debugSummary()}")
        }
    }

    private fun parseTvBoxConfig(root: JsonObject): ParsedPayload {
        val rootSpiderJar = root.stringValue("spider")?.normalizedSpiderJarUrl()
        val sites = root.get("sites")
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
            ?: JsonArray()

        val discoveredSources = buildList {
            for (site in sites) {
                val siteObject = site.asJsonObjectOrNull() ?: continue
                val api = siteObject.stringValue("api") ?: continue
                val label = siteObject.stringValue("name") ?: "未命名站点"
                val type = siteObject.intValue("type")
                val ext = siteObject.stringValue("ext")
                val jar = siteObject.stringValue("jar")?.normalizedSpiderJarUrl() ?: rootSpiderJar
                val categories = siteObject.stringValue("categories").orEmpty()
                val settings = SourceSettings(
                    spiderJarUrl = jar,
                    spiderExt = ext,
                    categoryParamsText = categories.split(",")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .joinToString("\n") { "$it|$it" },
                    spiderMode = when {
                        api.startsWith("csp_") && !jar.isNullOrBlank() -> SpiderMode.CATVOD_JAR
                        api.endsWith(".js", ignoreCase = true) || ext?.endsWith(".js", ignoreCase = true) == true -> SpiderMode.QUICKJS
                        else -> SpiderMode.NONE
                    },
                    spiderClass = api.removePrefix("csp_").takeIf { api.startsWith("csp_") },
                    spiderScriptUrl = when {
                        api.endsWith(".js", ignoreCase = true) -> api
                        ext?.endsWith(".js", ignoreCase = true) == true -> ext
                        else -> null
                    },
                    extraNotes = buildList {
                        siteObject.stringValue("key")?.let { add("key=$it") }
                        add("type=${type ?: 0}")
                    }.joinToString("\n")
                )

                val kind = when {
                    api.startsWith("http") -> SourceKind.DISCOVERED_SITE
                    api.startsWith("csp_") -> SourceKind.SPIDER
                    api.endsWith(".js", ignoreCase = true) -> SourceKind.SPIDER
                    type == 3 -> SourceKind.SPIDER
                    else -> SourceKind.UNSUPPORTED
                }

                add(
                    SourceItem(
                        id = buildSourceId(api + ext.orEmpty() + jar.orEmpty(), kind),
                        label = label,
                        url = if (api.startsWith("http")) api else ext ?: api,
                        kind = kind,
                        isPinned = true,
                        settings = settings.copy(
                            apiPath = if (api.startsWith("http")) api else null
                        )
                    )
                )
            }
        }

        val spiderCount = discoveredSources.count { it.kind == SourceKind.SPIDER }
        val unsupportedCount = discoveredSources.count { it.kind == SourceKind.UNSUPPORTED }
        val warnings = buildList {
            add("识别到 TVBox 配置，共解析出 ${discoveredSources.size} 个站点。")
            if (spiderCount > 0) {
                add("其中 $spiderCount 个站点已转入 Spider 运行链路。")
            }
            if (unsupportedCount > 0) {
                add("还有 $unsupportedCount 个站点未识别出可执行协议。")
            }
        }

        return ParsedPayload(
            discoveredSources = discoveredSources,
            warnings = warnings,
            title = root.stringValue("spider") ?: "TVBox 配置"
        ).also {
            Log.d(tag, "parseTvBoxConfig discovered=${discoveredSources.size}, spider=$spiderCount, unsupported=$unsupportedCount")
        }
    }

    private fun parseCatalog(root: JsonElement): ParsedPayload {
        val categories = extractCategories(root)
        val featuredItems = extractFeaturedItems(root)
        val itemsArray = extractCatalogArray(root)
        if (itemsArray == null || itemsArray.size() == 0) {
            return ParsedPayload(
                featuredItems = featuredItems,
                categories = categories,
                warnings = listOf("没有在当前响应里找到常见的 `list` / `data.list` / 数组格式。")
            )
        }

        val items = buildList {
            itemsArray.forEach { element ->
                val itemObject = element.asJsonObjectOrNull() ?: return@forEach
                add(mapCatalogItem(itemObject))
            }
        }.filter { it.title.isNotBlank() }

        return ParsedPayload(
            catalogItems = items,
            featuredItems = featuredItems,
            categories = categories
        ).also {
            Log.d(tag, "parseCatalog items=${items.size}, featured=${featuredItems.size}, categories=${categories.size}")
        }
    }

    private fun extractCategories(root: JsonElement): List<CategoryParam> {
        if (!root.isJsonObject) {
            return emptyList()
        }
        val rootObject = root.asJsonObject
        val categoryArray = when {
            rootObject.get("class")?.isJsonArray == true -> rootObject.getAsJsonArray("class")
            rootObject.get("classes")?.isJsonArray == true -> rootObject.getAsJsonArray("classes")
            rootObject.get("data")?.isJsonObject == true &&
                rootObject.getAsJsonObject("data").get("class")?.isJsonArray == true -> {
                rootObject.getAsJsonObject("data").getAsJsonArray("class")
            }
            else -> null
        } ?: return emptyList()

        return categoryArray.mapNotNull { element ->
            val item = element.asJsonObjectOrNull() ?: return@mapNotNull null
            val typeId = item.stringValue("type_id") ?: item.stringValue("id") ?: ""
            val typeName = item.stringValue("type_name") ?: item.stringValue("name") ?: ""
            if (typeName.isBlank()) null else CategoryParam(name = typeName, value = typeId)
        }
    }

    private fun extractFeaturedItems(root: JsonElement): List<CatalogItem> {
        if (!root.isJsonObject) {
            return emptyList()
        }
        val rootObject = root.asJsonObject
        val arrays = listOfNotNull(
            rootObject.get("recommend")?.takeIf { it.isJsonArray }?.asJsonArray,
            rootObject.get("featured")?.takeIf { it.isJsonArray }?.asJsonArray,
            rootObject.get("data")?.takeIf { it.isJsonObject }?.asJsonObject?.get("recommend")
                ?.takeIf { it.isJsonArray }?.asJsonArray
        )
        return arrays.firstOrNull()
            ?.mapNotNull { it.asJsonObjectOrNull() }
            ?.map(::mapCatalogItem)
            .orEmpty()
    }

    private fun mapCatalogItem(itemObject: JsonObject): CatalogItem {
        val title = itemObject.stringValue("vod_name")
            ?: itemObject.stringValue("title")
            ?: itemObject.stringValue("name")
            ?: "未命名影片"
        val coverUrl = itemObject.stringValue("vod_pic")
            ?: itemObject.stringValue("pic")
            ?: itemObject.stringValue("cover")
            ?: itemObject.stringValue("img")
        val description = itemObject.stringValue("vod_content")
            ?: itemObject.stringValue("vod_remarks")
            ?: itemObject.stringValue("remarks")
            ?: itemObject.stringValue("note")
        val year = itemObject.stringValue("vod_year") ?: itemObject.stringValue("year")
        val area = itemObject.stringValue("vod_area") ?: itemObject.stringValue("area")
        val tags = itemObject.stringValue("type_name") ?: itemObject.stringValue("vod_class")
        val detailToken = itemObject.stringValue("vod_id")
            ?: itemObject.stringValue("id")
            ?: itemObject.stringValue("detail")
        val playlists = parsePlaylists(itemObject)

        return CatalogItem(
            id = buildItemId(title, detailToken, coverUrl),
            title = title,
            coverUrl = coverUrl,
            description = description,
            year = year,
            area = area,
            tags = tags,
            detailToken = detailToken,
            playlists = playlists
        )
    }

    private fun parsePlaylists(itemObject: JsonObject): List<PlaylistGroup> {
        val directUrl = itemObject.stringValue("url")
            ?: itemObject.stringValue("playUrl")
            ?: itemObject.stringValue("play_url")
            ?: itemObject.stringValue("m3u8")

        val playFromGroups = itemObject.stringValue("vod_play_from")
            ?.split("$$$")
            ?.mapIndexed { index, name -> name.ifBlank { "线路 ${index + 1}" } }
            .orEmpty()

        val playUrlRawGroups = itemObject.stringValue("vod_play_url")
            ?.split("$$$")
            .orEmpty()

        val parsedGroups = buildList {
            playUrlRawGroups.forEachIndexed { index, rawGroup ->
                val groupName = playFromGroups.getOrNull(index) ?: "线路 ${index + 1}"
                val episodes = rawGroup.split("#")
                    .mapNotNull { rawEpisode -> rawEpisode.toEpisodeOrNull(groupName) }
                if (episodes.isNotEmpty()) {
                    add(
                        PlaylistGroup(
                            name = groupName,
                            episodes = episodes
                        )
                    )
                }
            }
        }

        if (parsedGroups.isNotEmpty()) {
            return parsedGroups
        }

        if (directUrl.isNullOrBlank()) {
            return emptyList()
        }

        return listOf(
            PlaylistGroup(
                name = "默认线路",
                episodes = listOf(Episode(name = "立即播放", url = directUrl, flag = "默认线路"))
            )
        )
    }

    private fun String.toEpisodeOrNull(flag: String): Episode? {
        val trimmed = trim()
        if (trimmed.isEmpty()) {
            return null
        }

        val separatorIndex = trimmed.indexOf('$')
        if (separatorIndex == -1) {
            return if (trimmed.isPlayableUrl()) {
                Episode(name = "播放", url = trimmed, flag = flag)
            } else {
                null
            }
        }

        val name = trimmed.substring(0, separatorIndex).ifBlank { "播放" }
        val url = trimmed.substring(separatorIndex + 1)
        if (!url.isPlayableUrl()) {
            return null
        }
        return Episode(name = name, url = url, flag = flag)
    }

    private fun extractCatalogArray(root: JsonElement): JsonArray? {
        if (root.isJsonArray) {
            return root.asJsonArray
        }

        if (!root.isJsonObject) {
            return null
        }

        val rootObject = root.asJsonObject
        return when {
            rootObject.get("list")?.isJsonArray == true -> rootObject.getAsJsonArray("list")
            rootObject.get("data")?.isJsonArray == true -> rootObject.getAsJsonArray("data")
            rootObject.get("data")?.isJsonObject == true &&
                rootObject.getAsJsonObject("data").get("list")?.isJsonArray == true -> {
                rootObject.getAsJsonObject("data").getAsJsonArray("list")
            }
            rootObject.get("result")?.isJsonObject == true &&
                rootObject.getAsJsonObject("result").get("list")?.isJsonArray == true -> {
                rootObject.getAsJsonObject("result").getAsJsonArray("list")
            }
            else -> null
        }
    }

    private fun normalizeJsonCandidate(raw: String): String {
        val trimmed = raw.trim().removePrefix("\uFEFF")
        if ((trimmed.startsWith("{") && trimmed.endsWith("}")) ||
            (trimmed.startsWith("[") && trimmed.endsWith("]"))
        ) {
            return trimmed
        }

        decodeBase64JsonCandidate(trimmed)?.let { decoded ->
            return decoded
        }

        val objectStart = trimmed.indexOf('{')
        val objectEnd = trimmed.lastIndexOf('}')
        if (objectStart >= 0 && objectEnd > objectStart) {
            return trimmed.substring(objectStart, objectEnd + 1)
        }

        val arrayStart = trimmed.indexOf('[')
        val arrayEnd = trimmed.lastIndexOf(']')
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            return trimmed.substring(arrayStart, arrayEnd + 1)
        }

        return trimmed
    }

    private fun decodeBase64JsonCandidate(raw: String): String? {
        val sanitized = raw
            .replace("\r", "")
            .replace("\n", "")
            .replace("\t", "")
            .replace(" ", "")
        if (sanitized.length < 32) {
            return null
        }
        if (!sanitized.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' || it == '-' || it == '_' }) {
            return null
        }

        val candidates = listOf(sanitized, sanitized.padEnd((sanitized.length + 3) / 4 * 4, '='))
        candidates.forEach { candidate ->
            runCatching {
                Base64.getDecoder().decode(candidate)
            }.getOrNull()?.let { bytes ->
                val decoded = bytes.toString(StandardCharsets.UTF_8).trim().removePrefix("\uFEFF")
                if (
                    (decoded.startsWith("{") && decoded.endsWith("}")) ||
                    (decoded.startsWith("[") && decoded.endsWith("]"))
                ) {
                    return decoded
                }
            }
        }

        extractEmbeddedBase64Candidates(raw).forEach { candidate ->
            runCatching {
                Base64.getDecoder().decode(candidate)
            }.getOrNull()?.let { bytes ->
                val decoded = bytes.toString(StandardCharsets.UTF_8).trim().removePrefix("\uFEFF")
                if (
                    (decoded.startsWith("{") && decoded.endsWith("}")) ||
                    (decoded.startsWith("[") && decoded.endsWith("]"))
                ) {
                    return decoded
                }
            }
        }
        return null
    }

    private fun decodeBytesToJsonCandidate(bodyBytes: ByteArray): String? {
        val asciiOnly = bodyBytes.toString(StandardCharsets.ISO_8859_1)
        val matcher = Pattern.compile("""[A-Za-z0-9+/=_-]{512,}""")
        val runs = mutableListOf<String>()
        val m = matcher.matcher(asciiOnly)
        while (m.find()) {
            runs += m.group()
        }
        Log.d(tag, "decodeBytesToJsonCandidate runs=${runs.size}, bytes=${bodyBytes.size}")
        runs.sortedByDescending { it.length }.forEach { candidate ->
            runCatching {
                Base64.getDecoder().decode(candidate + "=".repeat((4 - candidate.length % 4) % 4))
            }.getOrNull()?.let { decodedBytes ->
                val decoded = sanitizeJsonLikeText(
                    decodedBytes.toString(StandardCharsets.UTF_8).trim().removePrefix("\uFEFF")
                )
                if (decoded.isParsableJsonLike()) {
                    Log.d(tag, "decodeBytesToJsonCandidate embedded hit, candidateLen=${candidate.length}")
                    return decoded
                }
            }
        }

        val utf8 = runCatching { bodyBytes.toString(StandardCharsets.UTF_8) }.getOrNull()
        if (!utf8.isNullOrBlank()) {
            val normalized = sanitizeJsonLikeText(normalizeJsonCandidate(utf8))
            if (normalized.isParsableJsonLike()) {
                Log.d(tag, "decodeBytesToJsonCandidate direct utf8 hit, bytes=${bodyBytes.size}")
                return normalized
            }
        }
        return null
    }

    private fun extractEmbeddedBase64Candidates(raw: String): List<String> {
        val matcher = Pattern.compile("""[A-Za-z0-9+/=_-]{512,}""").matcher(raw)
        val matches = mutableListOf<String>()
        while (matcher.find()) {
            matches += matcher.group()
        }
        return matches.distinct().sortedByDescending { it.length }
    }

    private fun sanitizeJsonLikeText(raw: String): String {
        return raw.lineSequence()
            .map { line -> line.trimEnd() }
            .filterNot { line ->
                val trimmed = line.trimStart()
                trimmed.startsWith("//")
            }
            .joinToString("\n")
    }

    private fun String.isParsableJsonLike(): Boolean {
        val trimmed = trim()
        if (
            !((trimmed.startsWith("{") && trimmed.endsWith("}")) ||
                (trimmed.startsWith("[") && trimmed.endsWith("]")))
        ) {
            return false
        }
        return runCatching { JsonParser.parseString(trimmed) }.isSuccess
    }

    private fun JsonObject.stringValue(key: String): String? {
        val element = get(key)?.takeIf { !it.isJsonNull } ?: return null
        val value = when {
            element.isJsonPrimitive -> element.asString
            element.isJsonObject || element.isJsonArray -> element.toString()
            else -> null
        }
        return value
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun JsonObject.intValue(key: String): Int? {
        return get(key)
            ?.takeIf { !it.isJsonNull }
            ?.asInt
    }

    private fun JsonElement.asJsonObjectOrNull(): JsonObject? {
        return takeIf { isJsonObject }?.asJsonObject
    }

    private fun parseHeaderMap(headerObject: JsonObject?): Map<String, String> {
        if (headerObject == null) {
            return emptyMap()
        }
        return headerObject.entrySet()
            .mapNotNull { (key, value) ->
                val parsed = value.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()
                if (key.isBlank() || parsed.isBlank()) null else key to parsed
            }
            .toMap()
    }

    private fun String.isPlayableUrl(): Boolean {
        return startsWith("http", ignoreCase = true) ||
            startsWith("rtsp", ignoreCase = true) ||
            startsWith("ftp", ignoreCase = true) ||
            startsWith("magnet", ignoreCase = true)
    }

    private fun buildItemId(title: String, detailToken: String?, coverUrl: String?): String {
        return "${title.hashCode()}-${detailToken.orEmpty().hashCode()}-${coverUrl.orEmpty().hashCode()}"
    }

    private fun buildSourceId(url: String, kind: SourceKind): String {
        return "${kind.name.lowercase()}-${url.hashCode()}"
    }

    private fun String.normalizedSpiderJarUrl(): String {
        return substringBefore(";md5;").trim()
    }
}
