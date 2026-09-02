package com.dawood.orbit.core.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where the AI tools get their credentials.
 *
 * The key belongs to the user, not to the app: there is no shared key bundled
 * in the build, and nothing is sent anywhere until one is entered. It is kept
 * in the app's private preferences, which other apps cannot read, but it is not
 * encrypted at rest — the tools say so rather than implying more protection
 * than is there.
 */
class AiSettings private constructor(context: Context) {

    private val preferences = context.getSharedPreferences("ai", Context.MODE_PRIVATE)

    private val _apiKey = MutableStateFlow(preferences.getString(KEY_API, "").orEmpty())
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _model = MutableStateFlow(preferences.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL)
    val model: StateFlow<String> = _model.asStateFlow()

    val isConfigured: Boolean get() = _apiKey.value.isNotBlank()

    /** The key with everything but its ends hidden, for showing it is set. */
    fun maskedKey(): String {
        val key = _apiKey.value
        return when {
            key.isBlank() -> "Not set"
            key.length <= 12 -> "•".repeat(key.length)
            else -> key.take(8) + "…" + key.takeLast(4)
        }
    }

    fun setApiKey(value: String) {
        val trimmed = value.trim()
        _apiKey.value = trimmed
        preferences.edit().putString(KEY_API, trimmed).apply()
    }

    fun setModel(value: String) {
        _model.value = value
        preferences.edit().putString(KEY_MODEL, value).apply()
    }

    fun clear() {
        setApiKey("")
    }

    companion object {
        const val DEFAULT_MODEL = "claude-sonnet-4-5"

        /** The models the tools offer, newest first. */
        val MODELS = listOf(
            "claude-sonnet-4-5",
            "claude-opus-4-1",
            "claude-haiku-4-5",
        )

        private const val KEY_API = "api_key"
        private const val KEY_MODEL = "model"

        @Volatile
        private var instance: AiSettings? = null

        fun get(context: Context): AiSettings =
            instance ?: synchronized(this) {
                instance ?: AiSettings(context.applicationContext).also { instance = it }
            }
    }
}
