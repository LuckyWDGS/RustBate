package com.moviecat.app.data.repository

import android.content.Context
import com.google.gson.Gson
import com.moviecat.app.data.local.FavoriteEntity
import com.moviecat.app.data.local.HistoryEntity
import com.moviecat.app.data.local.MovieCatDatabase
import com.moviecat.app.data.local.SourceEntity
import com.moviecat.app.data.model.CatalogItem
import com.moviecat.app.data.model.CategoryParam
import com.moviecat.app.data.model.DnsMode
import com.moviecat.app.data.model.LibraryEntry
import com.moviecat.app.data.model.ParsedPayload
import com.moviecat.app.data.model.Episode
import com.moviecat.app.data.model.SourceItem
import com.moviecat.app.data.model.SourceKind
import com.moviecat.app.data.model.SourceSettings
import com.moviecat.app.data.model.SpiderMode
import com.moviecat.app.data.remote.CatalogRemoteDataSource
import com.moviecat.app.data.remote.DoubanRemoteDataSource
import com.moviecat.app.data.remote.DoubanSection
import java.net.URI
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class SourceDraft(
    val id: String? = null,
    val label: String,
    val url: String,
    val kind: SourceKind? = null,
    val isPinned: Boolean = true,
    val settings: SourceSettings = SourceSettings()
)

class CatalogRepository(
    private val database: MovieCatDatabase,
    private val remoteDataSource: CatalogRemoteDataSource,
    private val doubanDataSource: DoubanRemoteDataSource
) {
    private val gson = Gson()

    fun observeSources(): Flow<List<SourceItem>> {
        return database.sourceDao().observeAll().map { entities -> entities.map { it.toModel() } }
    }

    fun observeFavorites(): Flow<List<LibraryEntry>> {
        return database.favoriteDao().observeAll().map { entities ->
            entities.map {
                LibraryEntry(
                    entryKey = it.entryKey,
                    sourceId = it.sourceId,
                    title = it.title,
                    coverUrl = it.coverUrl,
                    playUrl = it.playUrl,
                    note = it.note,
                    updatedAt = it.updatedAt
                )
            }
        }
    }

    fun observeHistory(): Flow<List<LibraryEntry>> {
        return database.historyDao().observeAll().map { entities ->
            entities.map {
                LibraryEntry(
                    entryKey = it.entryKey,
                    sourceId = it.sourceId,
                    title = it.title,
                    coverUrl = it.coverUrl,
                    playUrl = it.playUrl,
                    note = it.note,
                    updatedAt = it.updatedAt,
                    lastPositionMs = it.lastPositionMs
                )
            }
        }
    }

    suspend fun seedDefaultSourcesIfNeeded() {
        if (database.sourceDao().count() > 0) {
            return
        }

        database.sourceDao().upsertAll(
            listOf(
                SourceDraft(
                    label = "饭太硬",
                    url = "http://www.饭太硬.com/tv",
                    kind = SourceKind.TVBOX_CONFIG,
                    settings = SourceSettings(
                        dnsMode = DnsMode.SYSTEM,
                        userAgent = "MovieCat/0.2 Android TV",
                        blockVideoAds = true
                    )
                ).toEntity(),
                SourceDraft(
                    label = "肥猫",
                    url = "https://肥猫.com/",
                    kind = SourceKind.TVBOX_CONFIG,
                    settings = SourceSettings(
                        dnsMode = DnsMode.SYSTEM,
                        userAgent = "MovieCat/0.2 Android TV",
                        blockVideoAds = true
                    )
                ).toEntity(updatedAt = System.currentTimeMillis() - 1)
            )
        )
    }

    suspend fun addOrUpdateSource(draft: SourceDraft): SourceItem {
        val entity = draft.toEntity()
        database.sourceDao().upsert(entity)
        return entity.toModel()
    }

    suspend fun addOrUpdateSource(
        label: String,
        url: String,
        kind: SourceKind = guessKind(url),
        pinned: Boolean = true,
        settings: SourceSettings = SourceSettings()
    ): SourceItem {
        return addOrUpdateSource(
            SourceDraft(
                label = label,
                url = url,
                kind = kind,
                isPinned = pinned,
                settings = settings
            )
        )
    }

    suspend fun getSource(sourceId: String): SourceItem? {
        return observeSources().first().firstOrNull { it.id == sourceId }
    }

    suspend fun removeSource(sourceId: String) {
        database.sourceDao().deleteById(sourceId)
    }

    suspend fun fetchPayload(source: SourceItem): ParsedPayload {
        val payload = remoteDataSource.fetchPayload(source)
        return payload.copy(
            catalogItems = payload.catalogItems.withSource(source),
            featuredItems = (payload.featuredItems + source.settings.parsedHomeRecommendations(source.id)).withSource(source),
            categories = payload.categories.ifEmpty { source.settings.parsedCategories() }
        )
    }

    suspend fun resolveDetail(source: SourceItem, item: CatalogItem): CatalogItem {
        return remoteDataSource.resolveDetail(source, item).withSource(source)
    }

    suspend fun resolveEpisode(source: SourceItem, episode: Episode): Episode {
        return remoteDataSource.resolveEpisode(source, episode)
    }

    suspend fun searchCatalog(source: SourceItem, query: String): ParsedPayload {
        val payload = remoteDataSource.searchCatalog(source, query)
        return payload.copy(
            catalogItems = payload.catalogItems.withSource(source),
            featuredItems = payload.featuredItems.withSource(source)
        )
    }

    suspend fun fetchCategory(source: SourceItem, category: CategoryParam, page: Int = 1): ParsedPayload {
        val payload = remoteDataSource.fetchCategory(source, category, page)
        return payload.copy(
            catalogItems = payload.catalogItems.withSource(source),
            featuredItems = payload.featuredItems.withSource(source)
        )
    }

    suspend fun fetchDoubanHomeRecommendations(): ParsedPayload {
        return doubanDataSource.fetchHomeRecommendations()
    }

    suspend fun fetchDoubanSection(section: DoubanSection): ParsedPayload {
        return doubanDataSource.fetchSection(section)
    }

    suspend fun toggleFavorite(source: SourceItem, item: CatalogItem): Boolean {
        val entryKey = item.stableEntryKey(source.id)
        val dao = database.favoriteDao()
        return if (dao.exists(entryKey)) {
            dao.deleteById(entryKey)
            false
        } else {
            dao.upsert(
                FavoriteEntity(
                    entryKey = entryKey,
                    sourceId = source.id,
                    title = item.title,
                    coverUrl = item.coverUrl,
                    playUrl = item.firstPlayableUrl.orEmpty(),
                    note = item.description,
                    updatedAt = System.currentTimeMillis()
                )
            )
            true
        }
    }

    suspend fun saveHistory(source: SourceItem, item: CatalogItem, playUrl: String, positionMs: Long) {
        database.historyDao().upsert(
            HistoryEntity(
                entryKey = item.stableEntryKey(source.id),
                sourceId = source.id,
                title = item.title,
                coverUrl = item.coverUrl,
                playUrl = playUrl,
                note = item.description,
                updatedAt = System.currentTimeMillis(),
                lastPositionMs = positionMs
            )
        )
    }

    private fun SourceEntity.toModel(): SourceItem {
        return SourceItem(
            id = id,
            label = label,
            url = url,
            kind = SourceKind.valueOf(kind),
            isPinned = isPinned,
            updatedAt = updatedAt,
            settings = parseSettings(settingsJson)
        )
    }

    private fun SourceDraft.toEntity(updatedAt: Long = System.currentTimeMillis()): SourceEntity {
        val resolvedKind = kind ?: guessKind(url)
        val resolvedId = id ?: buildSourceId(url, resolvedKind)
        return SourceEntity(
            id = resolvedId,
            label = label.ifBlank { buildLabelFromUrl(url) },
            url = url,
            kind = resolvedKind.name,
            isPinned = isPinned,
            updatedAt = updatedAt,
            settingsJson = gson.toJson(settings)
        )
    }

    private fun parseSettings(settingsJson: String): SourceSettings {
        return runCatching { gson.fromJson(settingsJson, SourceSettings::class.java) }
            .getOrDefault(SourceSettings())
    }

    private fun buildLabelFromUrl(url: String): String {
        return runCatching { URI(url).host.orEmpty().removePrefix("www.") }
            .getOrNull()
            ?.ifBlank { url }
            ?: url
    }

    private fun buildSourceId(url: String, kind: SourceKind): String {
        return "${kind.name.lowercase()}-${url.hashCode()}"
    }

    companion object {
        fun create(context: Context): CatalogRepository {
            return CatalogRepository(
                database = MovieCatDatabase.getInstance(context),
                remoteDataSource = CatalogRemoteDataSource(context.applicationContext),
                doubanDataSource = DoubanRemoteDataSource()
            )
        }

        private fun guessKind(url: String): SourceKind {
            return when {
                url.endsWith(".js", ignoreCase = true) || url.contains("/tv") -> SourceKind.TVBOX_CONFIG
                url.contains("csp_", ignoreCase = true) -> SourceKind.SPIDER
                else -> SourceKind.DIRECT
            }
        }
    }
}

private fun List<CatalogItem>.withSource(source: SourceItem): List<CatalogItem> {
    return map { it.withSource(source) }
}

private fun CatalogItem.withSource(source: SourceItem): CatalogItem {
    return copy(sourceId = source.id, sourceLabel = source.label)
}
