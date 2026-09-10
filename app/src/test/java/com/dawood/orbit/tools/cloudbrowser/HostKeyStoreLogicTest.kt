package com.dawood.orbit.tools.cloudbrowser

import com.dawood.orbit.tools.cloudbrowser.real.sha256FingerprintHex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HostKeyStoreLogicTest {

    @Test
    fun sha256OfAbcMatchesKnownVector() {
        val actual = sha256FingerprintHex("abc".toByteArray(Charsets.UTF_8))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", actual)
    }

    @Test
    fun sha256OfEmptyMatchesKnownVector() {
        val actual = sha256FingerprintHex(ByteArray(0))
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", actual)
    }

    @Test
    fun outputIs64LowercaseHexChars() {
        val actual = sha256FingerprintHex("orbit-fingerprint-check".toByteArray(Charsets.UTF_8))
        assertEquals(64, actual.length)
        assertTrue(actual.all { it in '0'..'9' || it in 'a'..'f' })
        assertEquals(actual, actual.lowercase())
    }
}
