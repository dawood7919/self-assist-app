package com.dawood.orbit.tools.cloudbrowser

import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Talks to the VPS control API (nginx /api/… → CDP → Chromium).
 */
class CloudBrowserApi(
    private val settings: CloudBrowserSettings,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun authHeader(): String =
        Credentials.basic(settings.username, settings.password)

    private fun url(path: String): String =
        settings.baseUrl().trimEnd('/') + path

    fun status(): StatusResult {
        val request = Request.Builder()
            .url(url("/api/status"))
            .header("Authorization", authHeader())
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return StatusResult(ok = false, error = "HTTP ${response.code}")
                }
                val json = JSONObject(body)
                StatusResult(
                    ok = json.optBoolean("ok"),
                    url = json.optString("url").takeIf { it.isNotBlank() },
                    title = json.optString("title").takeIf { it.isNotBlank() },
                    browser = json.optString("browser").takeIf { it.isNotBlank() },
                    tabs = json.optInt("tabs"),
                    error = json.optString("error").takeIf { it.isNotBlank() },
                )
            }
        } catch (e: Exception) {
            StatusResult(ok = false, error = e.message ?: "Network error")
        }
    }

    fun navigate(rawUrl: String): ActionResult = post("/api/navigate", JSONObject().put("url", rawUrl))

    fun back(): ActionResult = post("/api/back")

    fun forward(): ActionResult = post("/api/forward")

    fun reload(): ActionResult = post("/api/reload")

    fun home(): ActionResult = post("/api/home")

    private fun post(path: String, body: JSONObject = JSONObject()): ActionResult {
        val request = Request.Builder()
            .url(url(path))
            .header("Authorization", authHeader())
            .post(body.toString().toRequestBody(JSON))
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(text) }.getOrNull()
                ActionResult(
                    ok = response.isSuccessful && (json?.optBoolean("ok") != false),
                    url = json?.optString("url")?.takeIf { it.isNotBlank() },
                    error = json?.optString("error")?.takeIf { it.isNotBlank() }
                        ?: if (!response.isSuccessful) "HTTP ${response.code}" else null,
                )
            }
        } catch (e: Exception) {
            ActionResult(ok = false, error = e.message ?: "Network error")
        }
    }

    data class StatusResult(
        val ok: Boolean,
        val url: String? = null,
        val title: String? = null,
        val browser: String? = null,
        val tabs: Int = 0,
        val error: String? = null,
    )

    data class ActionResult(
        val ok: Boolean,
        val url: String? = null,
        val error: String? = null,
    )

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
