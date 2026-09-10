package com.dawood.orbit.tools.cloudbrowser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpsSetupScriptTest {

    @Test
    fun bindsLoopbackOnly() {
        assertTrue(VpsSetupScript.TEXT.contains("--remote-debugging-address=127.0.0.1"))
        assertFalse(VpsSetupScript.TEXT.contains("0.0.0.0"))
    }

    @Test
    fun installsAndEnablesUserService() {
        assertTrue(VpsSetupScript.TEXT.contains("orbit-chrome.service"))
        assertTrue(VpsSetupScript.TEXT.contains("systemctl --user enable --now"))
    }

    @Test
    fun verifiesDevToolsEndpoint() {
        assertTrue(VpsSetupScript.TEXT.contains("/json/version"))
        assertTrue(VpsSetupScript.TEXT.contains("about:blank"))
    }

    @Test
    fun locksDownDataDir() {
        assertTrue(VpsSetupScript.TEXT.contains("chmod 700"))
    }
}
