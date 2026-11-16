package com.freepakistancall.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** code == 0 means network failure. */
class ApiException(val code: Int, message: String) : Exception(message)

object Api {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()

    suspend fun req(method: String, path: String, body: JSONObject? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val rb = when {
                body != null -> body.toString().toRequestBody("application/json".toMediaType())
                method == "GET" || method == "DELETE" -> null
                else -> "{}".toRequestBody("application/json".toMediaType())
            }
            val b = Request.Builder().url(Prefs.serverUrl + path).method(method, rb)
            Prefs.token?.let { b.header("Authorization", "Bearer $it") }
            try {
                client.newCall(b.build()).execute().use { r ->
                    val txt = r.body?.string().orEmpty()
                    val json = try { JSONObject(txt) } catch (e: Exception) { JSONObject() }
                    if (!r.isSuccessful) throw ApiException(r.code, json.optString("error", "error"))
                    json
                }
            } catch (e: IOException) { throw ApiException(0, "network") }
        }
}
