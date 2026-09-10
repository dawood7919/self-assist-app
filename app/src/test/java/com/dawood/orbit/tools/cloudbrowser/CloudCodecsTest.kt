package com.dawood.orbit.tools.cloudbrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudCodecsTest {

    @Test
    fun serverCodecRoundTrip() {
        val servers = listOf(
            SavedServer(id = "s1", name = "VPS", host = "185.xxx.xxx.xxx", port = 2222, username = "root", authMethod = AuthMethod.SshKey, keyPath = "/keys/id", protocol = Protocol.Ssh, lastLatencyMs = 42L),
            SavedServer(id = "s2", name = "Home", host = "example.com", authMethod = AuthMethod.Password),
        )

        assertEquals(servers, CloudServerCodec.decode(CloudServerCodec.encode(servers)))
    }

    @Test
    fun sessionCodecRoundTrip() {
        val sessions = listOf(
            BrowserSession(id = "a", serverId = "s1", browser = BrowserKind.Firefox, profile = "Work", resolution = Resolution.P1440, quality = Quality.Ultra, frameRate = 60, state = SessionState.Paused, startedAtEpochMs = 5L, lastSeenEpochMs = 6L),
        )

        assertEquals(sessions, CloudSessionCodec.decode(CloudSessionCodec.encode(sessions)))
    }

    @Test
    fun settingsCodecRoundTrip() {
        val settings = listOf(
            CloudSettings(defaultBrowser = BrowserKind.Chrome, defaultQuality = Quality.High, defaultResolution = Resolution.Auto, defaultFrameRate = 60, hwAccel = false, autoReconnect = false, keepRunning = true, dataSaver = true, screenshotCacheBytes = 1024L),
        )

        assertEquals(settings, CloudSettingsCodec.decode(CloudSettingsCodec.encode(settings)))
    }

    @Test
    fun downloadCodecRoundTrip() {
        val items = listOf(
            DownloadItem(id = "d1", fileName = "a.zip", type = FileType.Archive, sizeBytes = 100L, downloadedBytes = 40L, state = DownloadState.Downloading, sourcePath = "/a.zip"),
            DownloadItem(id = "d2", fileName = "b.mp4", type = FileType.Video, sizeBytes = 100L, downloadedBytes = 100L, state = DownloadState.Failed, sourcePath = "/b.mp4"),
        )

        assertEquals(items, CloudDownloadCodec.decode(CloudDownloadCodec.encode(items)))
    }

    @Test
    fun decodeOfEmptyAndGarbageNeverThrows() {
        listOf("", "   ", "[]", "{}", "not json", "[{broken]").forEach { text ->
            assertTrue(CloudServerCodec.decode(text).isEmpty())
            assertTrue(CloudSessionCodec.decode(text).isEmpty())
            assertTrue(CloudSettingsCodec.decode(text).isEmpty())
            assertTrue(CloudDownloadCodec.decode(text).isEmpty())
        }
    }

    @Test
    fun decodeOfMissingFieldsFallsBackToDefaults() {
        val servers = CloudServerCodec.decode("[{}]")
        assertEquals(1, servers.size)
        assertEquals(SavedServer(id = ""), servers[0])

        val sessions = CloudSessionCodec.decode("[{}]")
        assertEquals(BrowserSession(id = ""), sessions[0])

        val settings = CloudSettingsCodec.decode("[{}]")
        assertEquals(CloudSettings(), settings[0])

        val downloads = CloudDownloadCodec.decode("[{}]")
        assertEquals(DownloadItem(id = ""), downloads[0])
    }

    @Test
    fun decodeOfUnknownEnumsFallsBackToDefaults() {
        val decoded = CloudServerCodec.decode("[{\"id\":\"s\",\"authMethod\":\"Retina\",\"protocol\":\"Pigeon\"}]")
        assertEquals(1, decoded.size)
        assertEquals(AuthMethod.SshKey, decoded[0].authMethod)
        assertEquals(Protocol.Ssh, decoded[0].protocol)
        assertNull(decoded[0].keyPath)
        assertNull(decoded[0].lastLatencyMs)
    }
}
