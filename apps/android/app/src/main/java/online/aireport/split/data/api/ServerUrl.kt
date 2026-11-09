package online.aireport.split.data.api

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Normalises and validates the address of a Split server as typed by a person. */
object ServerUrl {
    private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

    /**
     * Trims the input, drops trailing slashes and assumes https when no scheme was typed.
     * Returns null when the result is not an http(s) URL.
     */
    fun normalize(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = if (SCHEME.containsMatchIn(trimmed)) trimmed else "https://$trimmed"
        return withScheme.takeIf { it.toHttpUrlOrNull() != null }?.trimEnd('/')
    }

    /** The Retrofit base URL for a normalised server URL; null when it is not valid. */
    fun apiBaseUrl(serverUrl: String): HttpUrl? = "${serverUrl.trimEnd('/')}/api/".toHttpUrlOrNull()
}
