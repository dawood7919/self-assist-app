package com.dawood.orbit.tools.cloudbrowser

import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Talks to the VPS control API (nginx /api/… → CDP → Chromium). */
class CloudBrowserApi(
    private val settings: CloudBrowserSettings,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
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

    fun layout(): LayoutResult {
        val request = Request.Builder()
            .url(url("/api/layout"))
            .header("Authorization", authHeader())
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(body) }.getOrNull()
                LayoutResult(
                    ok = response.isSuccessful && (json?.optBoolean("ok") != false),
                    width = json?.optDouble("viewportWidth")?.toFloat()
                        ?: json?.optDouble("width")?.toFloat()
                        ?: 1280f,
                    height = json?.optDouble("viewportHeight")?.toFloat()
                        ?: json?.optDouble("height")?.toFloat()
                        ?: 720f,
                    error = json?.optString("error")?.takeIf { it.isNotBlank() },
                )
            }
        } catch (e: Exception) {
            LayoutResult(ok = false, width = 1280f, height = 720f, error = e.message)
        }
    }

    fun navigate(rawUrl: String): ActionResult =
        post("/api/navigate", JSONObject().put("url", rawUrl))

    fun back(): ActionResult = post("/api/back")
    fun forward(): ActionResult = post("/api/forward")
    fun reload(): ActionResult = post("/api/reload")
    fun home(): ActionResult = post("/api/home")

    fun mouse(
        type: String,
        x: Float,
        y: Float,
        button: String = "left",
        deltaX: Float = 0f,
        deltaY: Float = 0f,
    ): ActionResult {
        val body = JSONObject()
            .put("type", type)
            .put("x", x.toDouble())
            .put("y", y.toDouble())
            .put("button", button)
            .put("deltaX", deltaX.toDouble())
            .put("deltaY", deltaY.toDouble())
        return post("/api/mouse", body)
    }

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

    data class LayoutResult(
        val ok: Boolean,
        val width: Float,
        val height: Float,
        val error: String? = null,
    )

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
