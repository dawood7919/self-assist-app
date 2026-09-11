package com.dawood.orbit.tools.cloudbrowser

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CdpMessagesInputTest {

    private fun params(json: String): JSONObject = JSONObject(json).getJSONObject("params")

    @Test
    fun wheelCarriesDeltasModeAndCtrlModifier() {
        val json = CdpMessages.mouse(
            id = 7,
            type = "mouseWheel",
            x = 100.0,
            y = 200.0,
            deltaX = 0.0,
            deltaY = -120.0,
            modifiers = CdpMessages.MOD_CTRL,
            deltaMode = 0,
        )
        val p = params(json)
        assertEquals("mouseWheel", p.getString("type"))
        assertEquals(-120.0, p.getDouble("deltaY"), 0.0)
        assertEquals(0, p.getInt("deltaMode"))
        assertEquals(CdpMessages.MOD_CTRL, p.getInt("modifiers"))
    }

    @Test
    fun pressedCarriesButtonClickCountAndButtonsMask() {
        val json = CdpMessages.mouse(
            id = 1,
            type = "mousePressed",
            x = 10.0,
            y = 20.0,
            button = "left",
            buttons = 1,
            clickCount = 2,
        )
        val p = params(json)
        assertEquals("left", p.getString("button"))
        assertEquals(2, p.getInt("clickCount"))
        assertEquals(1, p.getInt("buttons"))
    }

    @Test
    fun plainMoveOmitsOptionalFields() {
        val json = CdpMessages.mouse(2, "mouseMoved", 1.0, 2.0)
        val p = params(json)
        assertFalse(p.has("button"))
        assertFalse(p.has("deltaY"))
        assertFalse(p.has("modifiers"))
    }

    @Test
    fun keyDownCarriesTextAndModifier() {
        val down = params(CdpMessages.keyDown(3, 17, "Control", "ControlLeft", modifiers = 2))
        assertEquals("keyDown", down.getString("type"))
        assertEquals(2, down.getInt("modifiers"))
        val text = params(CdpMessages.keyDown(4, 65, "a", "KeyA", text = "a"))
        assertEquals("a", text.getString("text"))
    }

    @Test
    fun keyUpNeverCarriesText() {
        val up = params(CdpMessages.keyUp(5, 13, "Enter", "Enter"))
        assertEquals("keyUp", up.getString("type"))
        assertFalse(up.has("text"))
    }

    @Test
    fun reloadAndStopUsePageDomain() {
        assertTrue(CdpMessages.reload(6).contains("Page.reload"))
        assertTrue(CdpMessages.stopLoading(7).contains("Page.stopLoading"))
        assertTrue(CdpMessages.getNavigationHistory(8).contains("Page.getNavigationHistory"))
    }

    @Test
    fun evaluateValueRequestsReturnByValue() {
        val p = params(CdpMessages.evaluateValue(9, "1+1"))
        assertTrue(p.getBoolean("returnByValue"))
        assertEquals("1+1", p.getString("expression"))
    }

    @Test
    fun parsesPageInfoFromEvaluatedJson() {
        val response = JSONObject(
            """{"result":{"value":"{\"url\":\"https://x.test\",\"title\":\"X\",\"zoom\":1.25}"}}""",
        )
        val info = CdpMessages.parsePageInfo(response)!!
        assertEquals("https://x.test", info.url)
        assertEquals("X", info.title)
        assertEquals(125, info.zoomPct)
    }

    @Test
    fun parsesHistoryFlags() {
        val response = JSONObject(
            """{"currentIndex":1,"entries":[
                {"id":1,"url":"https://a.test","title":"A"},
                {"id":2,"url":"https://b.test","title":"B"}
            ]}""",
        )
        val history = CdpMessages.parseHistory(response)
        assertTrue(history.canGoBack)
        assertFalse(history.canGoForward)
        assertEquals("https://b.test", history.currentUrl)
    }

    @Test
    fun mainFrameNavigationDetectedByMissingParent() {
        val main = CdpMessages.parseEvent(
            """{"method":"Page.frameNavigated","params":{"frame":{"url":"https://x.test"}}}""",
        ) as CdpEvent.FrameNavigated
        assertTrue(main.isMainFrame)
        assertEquals("https://x.test", main.url)

        val child = CdpMessages.parseEvent(
            """{"method":"Page.frameNavigated","params":{"frame":{
                "url":"https://frame.test","parentId":"abc"}}}""",
        ) as CdpEvent.FrameNavigated
        assertFalse(child.isMainFrame)
    }

    @Test
    fun screencastMetadataReadsNestedDeviceSize() {
        val json = """{"method":"Page.screencastFrame","params":{"sessionId":3,
            "data":"aGk=","metadata":{"pageScaleFactor":1,
            "deviceSize":{"width":1920,"height":1080},
            "scrollOffset":{"x":0,"y":120}}}}"""
        val frame = CdpMessages.parseEvent(json) as CdpEvent.ScreencastFrame
        assertEquals(1920, frame.deviceWidth)
        assertEquals(1080, frame.deviceHeight)
        assertEquals(120.0, frame.scrollOffsetY, 0.0)
    }

    @Test
    fun loadingEventsMapToPageEvents() {
        val start = CdpMessages.parseEvent("""{"method":"Page.frameStartedLoading","params":{}}""")
        val stop = CdpMessages.parseEvent("""{"method":"Page.frameStoppedLoading","params":{}}""")
        assertEquals(true, (start as CdpEvent.LoadStateChanged).loading)
        assertEquals(false, (stop as CdpEvent.LoadStateChanged).loading)
    }
}
