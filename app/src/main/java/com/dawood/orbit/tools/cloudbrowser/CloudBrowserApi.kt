package com.dawood.orbit.tools.cloudbrowser

import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Talks to the VPS control API (nginx /api/… → CDP → Chromium).
 *
 * The VPS uses a self-signed certificate on :8443. OkHttp is configured to
 * accept that host only so API calls (status, mouse, navigate) work.
 */
class CloudBrowserApi(
    private val settings: CloudBrowserSettings,
) {
    private val client: OkHttpClient = buildClient()

    private fun buildClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        if (settings.useHttps) {
            // Trust the personal VPS self-signed cert (not used for public internet).
            val trustAll = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
            val ssl = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(trustAll), SecureRandom())
            }
            builder.sslSocketFactory(ssl.socketFactory, trustAll)
            builder.hostnameVerifier(HostnameVerifier { hostname, _ ->
                hostname.equals(settings.host, ignoreCase = true) ||
                    hostname == "127.0.0.1" ||
                    hostname == "localhost"
            })
        }
        return builder.build()
    }

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
            StatusResult(ok = false, error = friendlyError(e))
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
            LayoutResult(ok = false, width = 1280f, height = 720f, error = friendlyError(e))
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
            ActionResult(ok = false, error = friendlyError(e))
        }
    }

    private fun friendlyError(e: Exception): String {
        val msg = e.message.orEmpty()
        return when {
            msg.contains("CertPath", ignoreCase = true) ||
                msg.contains("Trust anchor", ignoreCase = true) ||
                msg.contains("SSLHandshake", ignoreCase = true) ->
                "Certificate error — use HTTPS port 8443 and update the app"
            msg.contains("Failed to connect", ignoreCase = true) ||
                msg.contains("Connection refused", ignoreCase = true) ->
                "Cannot reach VPS — check host/port"
            msg.contains("timeout", ignoreCase = true) ->
                "VPS timed out"
            else -> msg.ifBlank { "Network error" }.take(120)
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
