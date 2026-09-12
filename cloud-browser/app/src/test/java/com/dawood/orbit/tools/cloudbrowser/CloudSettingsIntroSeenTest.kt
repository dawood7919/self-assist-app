package com.dawood.orbit.tools.cloudbrowser

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Explicit true / false / missing-field triple for the `introSeen` backend
 * additive (CloudModels.introSeen + CloudCodecs encode/tolerant-decode).
 *
 * CloudCodecsTest already covers introSeen=true round-trip and the
 * missing-field default; this file locks the false round-trip, the explicit
 * JSON values, and that encode always writes the key.
 *
 * JUnit4 + org.json only (test deps are junit + json).
 */
class CloudSettingsIntroSeenTest {

    @Test
    fun introSeenFalseRoundTrip() {
        val settings = listOf(CloudSettings(introSeen = false))

        assertEquals(settings, CloudSettingsCodec.decode(CloudSettingsCodec.encode(settings)))
    }

    @Test
    fun introSeenTrueRoundTrip() {
        val settings = listOf(CloudSettings(introSeen = true))

        assertEquals(settings, CloudSettingsCodec.decode(CloudSettingsCodec.encode(settings)))
    }

    @Test
    fun introSeenExplicitTrueAndFalseDecode() {
        val decodedTrue = CloudSettingsCodec.decode("[{\"introSeen\":true}]")
        assertEquals(1, decodedTrue.size)
        assertTrue(decodedTrue[0].introSeen)

        val decodedFalse = CloudSettingsCodec.decode("[{\"introSeen\":false}]")
        assertEquals(1, decodedFalse.size)
        assertFalse(decodedFalse[0].introSeen)
    }

    @Test
    fun introSeenMissingFieldDefaultsFalse() {
        val decoded = CloudSettingsCodec.decode("[{}]")
        assertEquals(1, decoded.size)
        assertFalse(decoded[0].introSeen)
    }

    @Test
    fun introSeenMissingFieldPreservesOtherFields() {
        val decoded = CloudSettingsCodec.decode("[{\"defaultBrowser\":\"Chrome\",\"defaultQuality\":\"High\"}]")
        assertEquals(1, decoded.size)
        assertEquals(BrowserKind.Chrome, decoded[0].defaultBrowser)
        assertEquals(Quality.High, decoded[0].defaultQuality)
        assertFalse(decoded[0].introSeen)
    }

    @Test
    fun introSeenDefaultIsFalse() {
        assertFalse(CloudSettings().introSeen)
        assertEquals(CloudSettings(), CloudSettingsCodec.decode("[{}]")[0])
    }

    @Test
    fun encodeAlwaysWritesIntroSeenKey() {
        val encodedTrue = CloudSettingsCodec.encode(listOf(CloudSettings(introSeen = true)))
        val parsedTrue = JSONArray(encodedTrue).getJSONObject(0)
        assertTrue(parsedTrue.has("introSeen"))
        assertTrue(parsedTrue.getBoolean("introSeen"))

        val encodedFalse = CloudSettingsCodec.encode(listOf(CloudSettings(introSeen = false)))
        val parsedFalse = JSONArray(encodedFalse).getJSONObject(0)
        assertTrue(parsedFalse.has("introSeen"))
        assertFalse(parsedFalse.getBoolean("introSeen"))
    }
}
