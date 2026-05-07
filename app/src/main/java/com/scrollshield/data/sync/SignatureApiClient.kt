package com.scrollshield.data.sync

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.scrollshield.data.model.AdSignature
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SignatureApiClient @Inject constructor() {

    companion object {
        private const val BASE_URL = "https://api.scrollshield.com"
        private const val CONNECT_TIMEOUT_SECONDS = 30L
        private const val READ_TIMEOUT_SECONDS = 60L
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    fun fetchSignatures(sinceTimestamp: Long): List<AdSignature> {
        val locale = Locale.getDefault().toLanguageTag()
        val url = "$BASE_URL/api/v1/signatures?since=$sinceTimestamp&locale=$locale"

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            response.close()
            throw IOException("Server returned HTTP ${response.code}")
        }

        val body = response.body?.string()
            ?: throw IOException("Empty response body")

        val type = object : TypeToken<List<AdSignature>>() {}.type
        return gson.fromJson(body, type)
    }
}
