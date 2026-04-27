package com.moviecat.app.data.remote

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class WeatherSnapshot(
    val district: String?,
    val temperatureC: Int?,
    val condition: String?
)

data class WeatherLocation(
    val latitude: Double,
    val longitude: Double,
    val district: String?
)

class WeatherRemoteDataSource {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(14, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun fetchCurrentWeather(location: WeatherLocation): WeatherSnapshot = withContext(Dispatchers.IO) {
        val weatherUrl = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code&timezone=auto",
            location.latitude,
            location.longitude
        )
        val current = executeJson(weatherUrl)
            .asJsonObjectOrNull()
            ?.get("current")
            ?.asJsonObjectOrNull()
            ?: error("天气接口暂时没有返回当前天气。")
        val temperature = current.doubleValue("temperature_2m")?.let { Math.round(it).toInt() }
        val code = current.intValue("weather_code")
        WeatherSnapshot(
            district = location.district,
            temperatureC = temperature,
            condition = code?.toWeatherLabel()
        )
    }

    private fun executeJson(url: String): JsonElement {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", BrowserUserAgent)
            .header("Accept", "application/json, text/plain, */*")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("天气接口请求失败：HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) {
                error("天气接口返回为空。")
            }
            return JsonParser.parseString(body)
        }
    }

    private fun JsonElement.asJsonObjectOrNull(): JsonObject? = if (isJsonObject) asJsonObject else null

    private fun JsonObject.doubleValue(name: String): Double? {
        return get(name)
            ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
            ?.asDouble
    }

    private fun JsonObject.intValue(name: String): Int? {
        return get(name)
            ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
            ?.asInt
    }

    private fun Int.toWeatherLabel(): String {
        return when (this) {
            0 -> "晴"
            1, 2 -> "少云"
            3 -> "多云"
            45, 48 -> "雾"
            51, 53, 55, 56, 57 -> "小雨"
            61, 63, 65, 66, 67, 80, 81, 82 -> "雨"
            71, 73, 75, 77, 85, 86 -> "雪"
            95, 96, 99 -> "雷雨"
            else -> "天气"
        }
    }

    companion object {
        private const val BrowserUserAgent =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36"
    }
}
