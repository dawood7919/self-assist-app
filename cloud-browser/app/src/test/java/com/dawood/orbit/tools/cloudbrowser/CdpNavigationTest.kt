package com.dawood.orbit.tools.cloudbrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CdpNavigationTest {

    @Test
    fun blankInputReturnsNull() {
        assertNull(CdpInput.normalizeAddress(""))
        assertNull(CdpInput.normalizeAddress("   "))
    }

    @Test
    fun explicitSchemesPassThroughUntouched() {
        assertEquals("https://example.com/x", CdpInput.normalizeAddress("https://example.com/x"))
        assertEquals("http://example.com", CdpInput.normalizeAddress("http://example.com"))
        assertEquals("about:blank", CdpInput.normalizeAddress("about:blank"))
        assertEquals("chrome://version", CdpInput.normalizeAddress("chrome://version"))
    }

    @Test
    fun bareDomainGetsHttps() {
        assertEquals("https://example.com", CdpInput.normalizeAddress("example.com"))
        assertEquals("https://sub.example.co.uk/path", CdpInput.normalizeAddress("sub.example.co.uk/path"))
    }

    @Test
    fun ipAndLocalhostGetPlainHttpBecauseTheyRarelyServeTls() {
        assertEquals("http://192.168.1.5:8080", CdpInput.normalizeAddress("192.168.1.5:8080"))
        assertEquals("http://localhost:3000", CdpInput.normalizeAddress("localhost:3000"))
        assertEquals("http://127.0.0.1:9222", CdpInput.normalizeAddress("127.0.0.1:9222"))
    }

    @Test
    fun freeTextBecomesGoogleSearch() {
        val url = CdpInput.normalizeAddress("kotlin coroutines flow")
        assertTrue(url, url!!.startsWith("https://www.google.com/search?q="))
        assertTrue(url, url.contains("kotlin"))
        assertTrue(url, url.contains("coroutines"))
    }

    @Test
    fun singleWordWithNoDotIsASearchNotADomain() {
        val url = CdpInput.normalizeAddress("news")
        assertTrue(url, url!!.startsWith("https://www.google.com/search?q="))
    }
}
