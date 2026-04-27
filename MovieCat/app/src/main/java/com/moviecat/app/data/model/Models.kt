package com.moviecat.app.data.model

enum class SourceKind {
    DIRECT,
    TVBOX_CONFIG,
    DISCOVERED_SITE,
    SPIDER,
    UNSUPPORTED
}

enum class DnsMode {
    SYSTEM,
    DOH_CLOUDFLARE,
    DOH_GOOGLE,
    CUSTOM_DOH
}

enum class SpiderMode {
    NONE,
    QUICKJS,
    CATVOD_JAR
}

data class SourceSettings(
    val dnsMode: DnsMode = DnsMode.SYSTEM,
    val dohUrl: String? = null,
    val userAgent: String? = null,
    val blockVideoAds: Boolean = false,
    val adSkipSeconds: Int = 35,
    val requestHeadersText: String = "",
    val categoryParamsText: String = "",
    val homeRecommendationsText: String = "",
    val spiderMode: SpiderMode = SpiderMode.NONE,
    val spiderClass: String? = null,
    val spiderJarUrl: String? = null,
    val spiderScriptUrl: String? = null,
    val spiderScriptCode: String? = null,
    val spiderExt: String? = null,
    val apiPath: String? = null,
    val detailPath: String? = null,
    val searchPath: String? = null,
    val extraNotes: String? = null
) {
    fun normalizedAdSkipSeconds(): Int {
        return adSkipSeconds.coerceIn(5, 180)
    }

    fun headerMap(): Map<String, String> {
        if (requestHeadersText.isBlank()) {
            return emptyMap()
        }

        val normalized = requestHeadersText.trim()
        if (normalized.startsWith("{") && normalized.endsWith("}")) {
            return runCatching {
                @Suppress("UNCHECKED_CAST")
                com.google.gson.Gson().fromJson(normalized, Map::class.java) as Map<String, Any?>
            }.getOrDefault(emptyMap())
                .mapNotNull { (key, value) ->
                    val stringKey = key.trim()
                    val stringValue = value?.toString()?.trim().orEmpty()
                    if (stringKey.isBlank() || stringValue.isBlank()) null else stringKey to stringValue
                }
                .toMap()
        }

        return requestHeadersText.lineSequence()
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) {
                    return@mapNotNull null
                }
                val separator = trimmed.indexOf(':')
                if (separator <= 0) {
                    return@mapNotNull null
                }
                val key = trimmed.substring(0, separator).trim()
                val value = trimmed.substring(separator + 1).trim()
                if (key.isBlank() || value.isBlank()) null else key to value
            }
            .toMap()
    }

    fun parsedCategories(): List<CategoryParam> {
        return categoryParamsText.lineSequence()
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isBlank()) {
                    return@mapNotNull null
                }
                val parts = trimmed.split("|").map { it.trim() }
                CategoryParam(
                    name = parts.getOrNull(0).orEmpty(),
                    value = parts.getOrNull(1).orEmpty(),
                    extra = parts.drop(2).filter { it.isNotBlank() }
                ).takeIf { it.name.isNotBlank() }
            }
            .toList()
    }

    fun parsedHomeRecommendations(sourceId: String): List<CatalogItem> {
        return homeRecommendationsText.lineSequence()
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isBlank()) {
                    return@mapNotNull null
                }
                val parts = trimmed.split("|").map { it.trim() }
                val title = parts.getOrNull(0).orEmpty()
                if (title.isBlank()) {
                    return@mapNotNull null
                }
                val cover = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
                val playUrl = parts.getOrNull(2)?.takeIf { it.isNotBlank() }
                val note = parts.getOrNull(3)?.takeIf { it.isNotBlank() }
                CatalogItem(
                    id = "manual-${sourceId.hashCode()}-${title.hashCode()}",
                    title = title,
                    coverUrl = cover,
                    description = note,
                    year = null,
                    area = null,
                    tags = "后台推荐",
                    detailToken = playUrl ?: title,
                    playlists = if (playUrl.isNullOrBlank()) {
                        emptyList()
                    } else {
                        listOf(
                            PlaylistGroup(
                                name = "推荐位",
                                episodes = listOf(Episode(name = "立即播放", url = playUrl))
                            )
                        )
                    }
                )
            }
            .toList()
    }
}

data class CategoryParam(
    val name: String,
    val value: String,
    val extra: List<String> = emptyList()
)

data class SourceItem(
    val id: String,
    val label: String,
    val url: String,
    val kind: SourceKind,
    val isPinned: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val settings: SourceSettings = SourceSettings()
)

data class Episode(
    val name: String,
    val url: String,
    val flag: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val parse: Int? = null
)

data class PlaylistGroup(
    val name: String,
    val episodes: List<Episode>
)

data class CatalogItem(
    val id: String,
    val title: String,
    val coverUrl: String?,
    val description: String?,
    val year: String?,
    val area: String?,
    val tags: String?,
    val detailToken: String?,
    val playlists: List<PlaylistGroup>,
    val sourceId: String? = null,
    val sourceLabel: String? = null
) {
    val firstPlayableUrl: String?
        get() = playlists.firstOrNull()?.episodes?.firstOrNull()?.url

    fun stableEntryKey(sourceId: String): String {
        val playKey = firstPlayableUrl ?: detailToken ?: title
        return "$sourceId|$playKey"
    }
}

data class LibraryEntry(
    val entryKey: String,
    val sourceId: String,
    val title: String,
    val coverUrl: String?,
    val playUrl: String,
    val note: String?,
    val updatedAt: Long,
    val lastPositionMs: Long = 0L
)

data class ParsedPayload(
    val catalogItems: List<CatalogItem> = emptyList(),
    val featuredItems: List<CatalogItem> = emptyList(),
    val discoveredSources: List<SourceItem> = emptyList(),
    val categories: List<CategoryParam> = emptyList(),
    val warnings: List<String> = emptyList(),
    val title: String? = null
)

data class PlayerSession(
    val source: SourceItem,
    val item: CatalogItem,
    val selectedGroupIndex: Int = 0,
    val selectedEpisodeIndex: Int = 0
) {
    val currentGroup: PlaylistGroup?
        get() = item.playlists.getOrNull(selectedGroupIndex)

    val currentEpisode: Episode?
        get() = currentGroup?.episodes?.getOrNull(selectedEpisodeIndex)
}
