package com.github.catvod.utils.okhttp

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object OkHttpUtil {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    @JvmStatic
    fun string(url: String, headers: Map<String, String>? = null): String {
        val requestBuilder = Request.Builder().url(url)
        headers.orEmpty().forEach { (key, value) -> requestBuilder.header(key, value) }
        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    @JvmStatic
    fun post(url: String, body: String, headers: Map<String, String>? = null): String {
        val requestBuilder = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull()))
        headers.orEmpty().forEach { (key, value) -> requestBuilder.header(key, value) }
        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            return response.body?.string().orEmpty()
        }
    }
}
