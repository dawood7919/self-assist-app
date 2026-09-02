package com.dawood.orbit.tools.ai

import android.content.Context
import com.dawood.orbit.core.settings.AiSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** One turn of a conversation. */
data class AiMessage(val role: String, val content: String) {
    val isUser: Boolean get() = role == ROLE_USER

    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
    }
}

/**
 * A thin client for the Anthropic Messages API.
 *
 * Deliberately small: one request, one response, no streaming. Streaming would
 * mean a parser for server-sent events and a partial-message state in every
 * caller, for a nicer feel on a screen the user is already waiting on.
 */
object AiClient {

    sealed interface Result {
        data class Success(val text: String, val inputTokens: Int, val outputTokens: Int) : Result
        data class Failure(val message: String, val recoverable: Boolean = true) : Result
    }

    private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
    private const val API_VERSION = "2023-06-01"
    private val JSON = "application/json".toMediaType()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            // A long answer can take a while to come back in one piece.
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    suspend fun send(
        context: Context,
        messages: List<AiMessage>,
        systemPrompt: String? = null,
        maxTokens: Int = 2048,
    ): Result = withContext(Dispatchers.IO) {
        val settings = AiSettings.get(context)
        val key = settings.apiKey.value
        if (key.isBlank()) {
            return@withContext Result.Failure(
                "No API key yet. Add your own Anthropic key in this tool's settings.",
                recoverable = false,
            )
        }
        if (messages.none { it.isUser }) {
            return@withContext Result.Failure("There is nothing to ask")
        }

        val payload = JSONObject().apply {
            put("model", settings.model.value)
            put("max_tokens", maxTokens)
            if (!systemPrompt.isNullOrBlank()) put("system", systemPrompt)
            put(
                "messages",
                JSONArray().apply {
                    messages.forEach { message ->
                        put(
                            JSONObject().apply {
                                put("role", message.role)
                                put("content", message.content)
                            },
                        )
                    }
                },
            )
        }

        val request = Request.Builder()
            .url(ENDPOINT)
            .addHeader("x-api-key", key)
            .addHeader("anthropic-version", API_VERSION)
            .addHeader("content-type", "application/json")
            .post(payload.toString().toRequestBody(JSON))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.Failure(
                        message = describeError(response.code, body),
                        recoverable = response.code != 401 && response.code != 403,
                    )
                }
                parse(body)
            }
        } catch (error: IOException) {
            Result.Failure("Could not reach the API: ${error.message ?: "no connection"}")
        }
    }

    private fun parse(body: String): Result = runCatching {
        val json = JSONObject(body)
        val blocks = json.optJSONArray("content") ?: JSONArray()
        val text = (0 until blocks.length())
            .map { blocks.getJSONObject(it) }
            .filter { it.optString("type") == "text" }
            .joinToString("\n") { it.optString("text") }
            .trim()

        if (text.isEmpty()) {
            return@runCatching Result.Failure("The model returned nothing usable")
        }
        val usage = json.optJSONObject("usage")
        Result.Success(
            text = text,
            inputTokens = usage?.optInt("input_tokens", 0) ?: 0,
            outputTokens = usage?.optInt("output_tokens", 0) ?: 0,
        )
    }.getOrElse { Result.Failure("The response could not be read") }

    /** Turns an HTTP status into something worth showing a person. */
    internal fun describeError(code: Int, body: String): String {
        val detail = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
        }.getOrDefault("")
        return when (code) {
            401 -> "That API key was rejected. Check it in the tool's settings."
            403 -> "That key is not allowed to use this model."
            404 -> "That model name is not available on this key."
            429 -> "Rate limited. Wait a moment and try again."
            in 500..599 -> "The API had a problem at its end. Try again shortly."
            else -> detail.ifBlank { "The request failed with status $code" }
        }
    }

    /**
     * Long documents have to be cut down before they are sent. Roughly four
     * characters per token, so this keeps the request well inside a context
     * window while saying plainly that it trimmed.
     */
    fun trimForContext(text: String, maxCharacters: Int = 120_000): Pair<String, Boolean> =
        if (text.length <= maxCharacters) {
            text to false
        } else {
            text.take(maxCharacters) to true
        }
}
