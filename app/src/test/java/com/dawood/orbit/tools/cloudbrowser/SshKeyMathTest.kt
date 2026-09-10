package com.dawood.orbit.tools.cloudbrowser

import com.dawood.orbit.tools.cloudbrowser.real.sha256Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SshKeyMathTest {

    @Test
    fun sha256Base64OfAbcMatchesKnownVector() {
        val actual = sha256Base64("abc".toByteArray(Charsets.UTF_8))
        assertEquals("ungWv48Bz+pBQUDeXa4iI7ADYaOWF3qctBD/YfIAFa0=", actual)
    }

    @Test
    fun sha256Base64OfEmptyMatchesKnownVector() {
        val actual = sha256Base64(ByteArray(0))
        assertEquals("47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU=", actual)
    }

    @Test
    fun outputIs44CharPaddedBase64() {
        val actual = sha256Base64("orbit-fingerprint-check".toByteArray(Charsets.UTF_8))
        assertEquals(44, actual.length)
        assertTrue(actual.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '+' || it == '/' || it == '=' })
        assertTrue(actual.endsWith("="))
    }
}
