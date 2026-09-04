package com.ambientcompanion.download.network

import android.content.Context
import com.ambientcompanion.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class DownloadSubmissionClient(context: Context) {
    private val preferences = context.getSharedPreferences("download_auth", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun saveForLater(sourceUrl: String): String = withContext(Dispatchers.IO) {
        requireConfiguration()
        var accessToken = accessToken()
        var response = request(
            url = "${BuildConfig.DOWNLOAD_API_BASE_URL.trimEnd('/')}/api/v1/downloads",
            body = buildJsonObject { put("url", sourceUrl); put("format", "video") }.toString(),
            bearerToken = accessToken,
        )
        if (response.code == HttpURLConnection.HTTP_UNAUTHORIZED) {
            clearSession()
            accessToken = accessToken()
            response = request(
                url = "${BuildConfig.DOWNLOAD_API_BASE_URL.trimEnd('/')}/api/v1/downloads",
                body = buildJsonObject { put("url", sourceUrl); put("format", "video") }.toString(),
                bearerToken = accessToken,
            )
        }
        if (response.code !in 200..299) throw SubmissionException(response.message(response.code))
        json.parseToJsonElement(response.body).jsonObject["jobId"]?.jsonPrimitive?.content
            ?: throw SubmissionException("The server saved no job ID.")
    }

    private fun requireConfiguration() {
        if (BuildConfig.DOWNLOAD_API_BASE_URL.isBlank() || BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank()) {
            throw SubmissionException("Configure DOWNLOAD_API_BASE_URL, SUPABASE_URL, and SUPABASE_ANON_KEY in local.properties.")
        }
    }

    private fun accessToken(): String {
        val cached = preferences.getString("access_token", null)
        val expiresAt = preferences.getLong("expires_at", 0L)
        if (cached != null && expiresAt > System.currentTimeMillis() + 60_000) return cached

        val refreshToken = preferences.getString("refresh_token", null)
        val response = if (refreshToken != null) {
            request(
                url = "${BuildConfig.SUPABASE_URL.trimEnd('/')}/auth/v1/token?grant_type=refresh_token",
                body = buildJsonObject { put("refresh_token", refreshToken) }.toString(),
                apiKey = BuildConfig.SUPABASE_ANON_KEY,
            )
        } else {
            request(
                url = "${BuildConfig.SUPABASE_URL.trimEnd('/')}/auth/v1/signup",
                body = "{}",
                apiKey = BuildConfig.SUPABASE_ANON_KEY,
            )
        }
        if (response.code !in 200..299) throw SubmissionException(response.message(response.code))
        return storeSession(response.body)
    }

    private fun storeSession(body: String): String {
        val value = json.parseToJsonElement(body).jsonObject
        val accessToken = value["access_token"]?.jsonPrimitive?.content
            ?: throw SubmissionException("Supabase returned no access token. Enable anonymous sign-ins for this project.")
        val refreshToken = value["refresh_token"]?.jsonPrimitive?.content
            ?: throw SubmissionException("Supabase returned no refresh token.")
        val expiresIn = value["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600L
        preferences.edit()
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .putLong("expires_at", System.currentTimeMillis() + expiresIn * 1000)
            .apply()
        return accessToken
    }

    private fun clearSession() {
        preferences.edit().clear().apply()
    }

    private fun request(url: String, body: String, bearerToken: String? = null, apiKey: String? = null): HttpResult {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            bearerToken?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            apiKey?.let {
                connection.setRequestProperty("apikey", it)
                connection.setRequestProperty("Authorization", "Bearer $it")
            }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            HttpResult(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }

    private data class HttpResult(val code: Int, val body: String) {
        fun message(fallbackCode: Int): String = parseApiError(body, fallbackCode)
    }
}

class SubmissionException(message: String) : Exception(message)

internal fun parseApiError(body: String, statusCode: Int): String {
    val payload = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
    val detail = sequenceOf("msg", "message", "error_description", "error")
        .mapNotNull { key -> runCatching { payload?.get(key)?.jsonPrimitive?.content }.getOrNull() }
        .firstOrNull { it.isNotBlank() }
    val errorCode = runCatching { payload?.get("error_code")?.jsonPrimitive?.content }.getOrNull()
    return when {
        detail != null && errorCode != null -> "$detail ($errorCode)"
        detail != null -> detail
        errorCode != null -> errorCode
        else -> "Request failed (HTTP $statusCode)."
    }
}
