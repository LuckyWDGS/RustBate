package com.moviecat.app.data.remote

import android.util.Log
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.moviecat.app.data.model.CatalogItem
import com.moviecat.app.data.model.ParsedPayload
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

enum class DoubanSection {
    Home,
    HotMovie,
    HotTv,
    HotVariety,
    MovieRanking,
    TvRanking
}

class DoubanRemoteDataSource {
    private val tag = "MovieCatDouban"
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun fetchHomeRecommendations(limit: Int = 24): ParsedPayload = withContext(Dispatchers.IO) {
        val halfLimit = limit / 2
        val tv = async { fetchRecentHotItemsOrEmpty(category = "tv", type = "tv", limit = halfLimit) }
        val movie = async { fetchRecentHotItemsOrEmpty(category = "movie", type = null, limit = halfLimit) }
        ParsedPayload(catalogItems = tv.await() + movie.await(), title = "豆瓣热播")
    }

    suspend fun fetchSection(section: DoubanSection, limit: Int = 36): ParsedPayload = withContext(Dispatchers.IO) {
        val items = when (section) {
            DoubanSection.Home -> fetchHomeRecommendations(limit).catalogItems
            DoubanSection.HotMovie -> fetchRecentHotItems(category = "movie", type = null, limit = limit)
            DoubanSection.HotTv -> fetchRecentHotItems(category = "tv", type = "tv", limit = limit)
            DoubanSection.HotVariety -> fetchRecentHotItems(category = "tv", type = "show", limit = limit)
            DoubanSection.MovieRanking -> fetchSearchSubjects(type = "movie", sort = "rank", limit = limit)
            DoubanSection.TvRanking -> fetchSearchSubjects(type = "tv", sort = "recommend", limit = limit)
        }
        ParsedPayload(catalogItems = items, title = section.name)
    }

    private fun fetchRecentHotItems(category: String, type: String?, limit: Int): List<CatalogItem> {
        val url = "https://m.douban.com/rexxar/api/v2/subject/recent_hot/$category" +
            "?start=0&limit=$limit" +
            type?.let { "&type=${encode(it)}" }.orEmpty()
        val root = executeJson(url, referer = "https://m.douban.com/movie/")
        val array = root.asJsonObjectOrNull()
            ?.get("items")
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
            ?: return emptyList()
        return array.mapNotNull { item ->
            val obj = item.asJsonObjectOrNull() ?: return@mapNotNull null
            val id = obj.stringValue("id") ?: return@mapNotNull null
            val title = obj.stringValue("title") ?: return@mapNotNull null
            val subtitle = obj.stringValue("card_subtitle")
            val rating = obj.get("rating")?.asJsonObjectOrNull()?.doubleValue("value")
            val cover = obj.get("pic")?.asJsonObjectOrNull()?.stringValue("large")
                ?: obj.get("pic")?.asJsonObjectOrNull()?.stringValue("normal")
            CatalogItem(
                id = "douban-$category-$id",
                title = title,
                coverUrl = cover,
                description = subtitle,
                year = subtitle?.substringBefore(" / ")?.takeIf { it.length == 4 && it.all(Char::isDigit) },
                area = subtitle?.split(" / ")?.getOrNull(1),
                tags = listOfNotNull("豆瓣热播", rating?.let { "评分 $it" }, obj.stringValue("episodes_info"))
                    .joinToString(" / "),
                detailToken = title,
                playlists = emptyList(),
                sourceId = DoubanSourceId,
                sourceLabel = "豆瓣热播"
            )
        }
    }

    private fun fetchRecentHotItemsOrEmpty(category: String, type: String?, limit: Int): List<CatalogItem> {
        return runCatching {
            fetchRecentHotItems(category = category, type = type, limit = limit)
        }.onFailure {
            Log.w(tag, "recent hot failed category=$category type=$type", it)
        }.getOrDefault(emptyList())
    }

    private fun fetchSearchSubjects(type: String, sort: String, limit: Int): List<CatalogItem> {
        val url = "https://movie.douban.com/j/search_subjects" +
            "?type=${encode(type)}&tag=${encode("热门")}&sort=${encode(sort)}" +
            "&page_limit=$limit&page_start=0"
        val root = executeJson(url, referer = "https://movie.douban.com/")
        val array = root.asJsonObjectOrNull()
            ?.get("subjects")
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
            ?: return emptyList()
        return array.mapNotNull { item ->
            val obj = item.asJsonObjectOrNull() ?: return@mapNotNull null
            val id = obj.stringValue("id") ?: return@mapNotNull null
            val title = obj.stringValue("title") ?: return@mapNotNull null
            CatalogItem(
                id = "douban-ranking-$type-$id",
                title = title,
                coverUrl = obj.stringValue("cover"),
                description = obj.stringValue("url"),
                year = null,
                area = null,
                tags = listOfNotNull("豆瓣榜单", obj.stringValue("rate")?.takeIf { it.isNotBlank() }?.let { "评分 $it" })
                    .joinToString(" / "),
                detailToken = title,
                playlists = emptyList(),
                sourceId = DoubanSourceId,
                sourceLabel = "豆瓣榜单"
            )
        }
    }

    private fun executeJson(url: String, referer: String): JsonElement {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", BrowserUserAgent)
            .header("Referer", referer)
            .header("Accept", "application/json, text/plain, */*")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("豆瓣接口请求失败：HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) {
                error("豆瓣接口返回为空")
            }
            Log.d(tag, "douban response code=${response.code} url=$url size=${body.length}")
            return JsonParser.parseString(body)
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    private fun JsonElement.asJsonObjectOrNull(): JsonObject? = if (isJsonObject) asJsonObject else null

    private fun JsonObject.stringValue(name: String): String? {
        return get(name)
            ?.takeIf { !it.isJsonNull }
            ?.asString
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun JsonObject.doubleValue(name: String): Double? {
        return get(name)
            ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
            ?.asDouble
    }

    companion object {
        const val DoubanSourceId = "external-douban"
        private const val BrowserUserAgent =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36"
    }
}
