package com.dawood.orbit.tools.cloudbrowser

/**
 * Pure input mapping for the Cloud Browser remote stream.
 *
 * Pure Kotlin with no imports beyond the Kotlin standard library and the
 * sibling [Resolution]/[Quality] models (same package): zero Android /
 * OkHttp / coroutines imports, so this file runs on plain JVM unit tests.
 * Nothing here touches the network or any thread.
 *
 * Three groups live here:
 *  1. Key name -> Windows virtual-key code, plus matching DOM key/code.
 *  2. Phone touch fraction <-> remote CSS pixel mapping (letterbox aware).
 *  3. Address normalisation and browser-zoom stepping.
 */
object CdpInput {

    /**
     * Friendly key name to Windows virtual-key code, as expected by
     * `Input.dispatchKeyEvent` (`windowsVirtualKeyCode`).
     */
    val KEY_TABLE: Map<String, Int> = buildMap {
        put("Enter", 13)
        put("Backspace", 8)
        put("Tab", 9)
        put("Escape", 27)
        put(" ", 32)
        put("Space", 32)
        put("PageUp", 33)
        put("PageDown", 34)
        put("End", 35)
        put("Home", 36)
        put("ArrowLeft", 37)
        put("ArrowUp", 38)
        put("ArrowRight", 39)
        put("ArrowDown", 40)
        put("Delete", 46)
        // Modifiers
        put("Shift", 16)
        put("Control", 17)
        put("Alt", 18)
        put("Meta", 91)
        // Browser zoom chords
        put("Minus", 189)   // Ctrl+- zoom out
        put("Equal", 187)   // Ctrl+= zoom in
        put("Digit0", 48)   // Ctrl+0 reset
        for (i in 1..12) {
            put("F$i", 111 + i)
        }
    }

    /**
     * DOM `key` / `code` pair for a friendly key name. Used so chorded
     * shortcuts (Control+Equal) arrive with the correct DOM identities.
     */
    val KEY_DOM: Map<String, Pair<String, String>> = mapOf(
        "Enter" to ("Enter" to "Enter"),
        "Backspace" to ("Backspace" to "Backspace"),
        "Tab" to ("Tab" to "Tab"),
        "Escape" to ("Escape" to "Escape"),
        " " to (" " to "Space"),
        "Space" to (" " to "Space"),
        "PageUp" to ("PageUp" to "PageUp"),
        "PageDown" to ("PageDown" to "PageDown"),
        "End" to ("End" to "End"),
        "Home" to ("Home" to "Home"),
        "ArrowLeft" to ("ArrowLeft" to "ArrowLeft"),
        "ArrowUp" to ("ArrowUp" to "ArrowUp"),
        "ArrowRight" to ("ArrowRight" to "ArrowRight"),
        "ArrowDown" to ("ArrowDown" to "ArrowDown"),
        "Delete" to ("Delete" to "Delete"),
        "Shift" to ("Shift" to "ShiftLeft"),
        "Control" to ("Control" to "ControlLeft"),
        "Alt" to ("Alt" to "AltLeft"),
        "Meta" to ("Meta" to "MetaLeft"),
        "Minus" to ("-" to "Minus"),
        "Equal" to ("=" to "Equal"),
        "Digit0" to ("0" to "Digit0"),
    )

    /** DOM key/code for [label], falling back to the label itself. */
    fun domIdentity(label: String): Pair<String, String> =
        KEY_DOM[label] ?: (label to label)

    /**
     * Maps a touch point expressed as fractions of the phone view
     * ([fx], [fy] in 0..1) onto remote pixels for a [w] by [h] target.
     */
    fun pointFromFractions(fx: Float, fy: Float, w: Int, h: Int): Pair<Double, Double> =
        Pair(fx.coerceIn(0f, 1f).toDouble() * w.toDouble(),
            fy.coerceIn(0f, 1f).toDouble() * h.toDouble())

    /**
     * Maps a touch point in PIXEL coordinates inside the on-phone letterbox
     * container ([boxW] x [boxH]) onto content fractions for a remote frame
     * coded at [imgW] x [imgH] (displayed with ContentScale.Fit). Returns
     * null when the touch lands in the letterbox margin, where there is no
     * remote content. Never throws on bad dimensions.
     */
    fun contentFractions(
        px: Float,
        py: Float,
        boxW: Float,
        boxH: Float,
        imgW: Float,
        imgH: Float,
    ): Pair<Float, Float>? {
        if (boxW <= 0f || boxH <= 0f || imgW <= 0f || imgH <= 0f) return null
        val scale = minOf(boxW / imgW, boxH / imgH)
        val contentW = imgW * scale
        val contentH = imgH * scale
        val originX = (boxW - contentW) / 2f
        val originY = (boxH - contentH) / 2f
        if (px < originX || px > originX + contentW ||
            py < originY || py > originY + contentH
        ) {
            return null
        }
        val fx = ((px - originX) / contentW).coerceIn(0f, 1f)
        val fy = ((py - originY) / contentH).coerceIn(0f, 1f)
        return fx to fy
    }

    /**
     * Host Chrome WINDOW size (the OS window behind the emulated viewport).
     * The emulated phone viewport renders independently of it, but it must
     * be at least as large as the desktop-mode CSS viewport. Kept wide.
     */
    fun remoteSize(resolution: Resolution): Pair<Int, Int> =
        when (resolution) {
            Resolution.P720 -> Pair(1280, 900)
            Resolution.P1080 -> Pair(1366, 900)
            Resolution.P1440 -> Pair(1366, 900)
            Resolution.Auto -> Pair(1366, 900)
        }

    /**
     * The emulated viewport for one render mode, given the phone's content
     * box in PHYSICAL pixels ([boxPhysW] x [boxPhysH]) and its display
     * density (1 dp = [density] px). Returns CSS pixel dimensions and the
     * device scale factor the remote browser must use. The three are chosen
     * so a screencast frame coded at (cssW*dsf) x (cssH*dsf) maps 1:1 onto
     * the phone content box — no scaling, no letterbox bars.
     */
    fun emulatedViewport(
        boxPhysW: Int,
        boxPhysH: Int,
        density: Float,
        mode: BrowserMode,
    ): ViewportGeometry {
        if (boxPhysW <= 0 || boxPhysH <= 0 || density <= 0f) {
            // Defensive phone default (iPhone-class 390x844 @3x).
            return if (mode == BrowserMode.Mobile) {
                ViewportGeometry(390, 844, 3.0)
            } else {
                ViewportGeometry(1280, 720, 1.0)
            }
        }
        return if (mode == BrowserMode.Mobile) {
            // Use the device's real density (Chrome accepts fractional DSF):
            // the coded frame then maps 1:1 to physical px — no rescale.
            val dsf = density.coerceIn(1.5f, 4.0f).toDouble()
            val cssW = maxOf(320, Math.round(boxPhysW / dsf).toInt())
            val cssH = maxOf(480, Math.round(boxPhysH / dsf).toInt())
            ViewportGeometry(cssW, cssH, dsf)
        } else {
            // Real desktop layout: fixed desktop CSS width, height follows
            // the phone aspect ratio, DSF 1 (screencast downscales the frame
            // to the box width itself).
            val cssW = DESKTOP_CSS_WIDTH
            val cssH = maxOf(600, Math.round(cssW * boxPhysH.toFloat() / boxPhysW).toInt())
            ViewportGeometry(cssW, cssH, 1.0)
        }
    }

    /** Physical coded-frame size for a geometry, capped for bandwidth. */
    fun frameSize(geo: ViewportGeometry, quality: Quality): Pair<Int, Int> {
        // Single source of truth: the cap width lives in screencastTuning so
        // JPEG quality and coded resolution can never drift apart.
        val cap = screencastTuning(quality).third
        val physW = Math.round(geo.cssWidth * geo.deviceScaleFactor).toInt()
        val physH = Math.round(geo.cssHeight * geo.deviceScaleFactor).toInt()
        val scale = (cap.toDouble() / physW).coerceAtMost(1.0)
        return if (scale >= 1.0) {
            physW to physH
        } else {
            Math.round(physW * scale).toInt() to Math.round(physH * scale).toInt()
        }
    }

    /**
     * Screencast tuning for a [Quality] preset as
     * `Triple(jpegQuality, everyNthFrame, capWidth)`: JPEG quality, the
     * frame downsampling factor, and the max coded width (see [frameSize]).
     */
    fun screencastTuning(quality: Quality): Triple<Int, Int, Int> =
        when (quality) {
            // Mobile frames are ~1080 px wide physically; text-heavy web
            // pages need the higher JPEG bands below to stay crisp, and the
            // caps must not downscale a native-resolution phone frame.
            Quality.Low -> Triple(46, 2, 960)
            Quality.Balanced -> Triple(64, 1, 1320)
            Quality.High -> Triple(78, 1, 1620)
            Quality.Ultra -> Triple(88, 1, 1920)
        }

    /**
     * Maps a touch fraction (0..1 of the displayed frame) to REMOTE CSS
     * coordinates inside the emulated viewport. [cssW]/[cssH] are the
     * emulated CSS dimensions; [scale] is the current pinch page-scale (1.0
     * when unzoomed) reported by screencast metadata; [scrollX]/[scrollY] are
     * the metadata scroll offsets and [offsetTop] the browser top inset.
     * CDP `Input.dispatch*` coordinates are visual-viewport CSS pixels:
     * under pinch scale s the visual viewport is cssW/s large, and the
     * visible window starts at (scrollX, scrollY + offsetTop).
     */
    fun remoteCssPoint(
        fx: Float,
        fy: Float,
        cssW: Int,
        cssH: Int,
        scale: Double = 1.0,
        scrollX: Double = 0.0,
        scrollY: Double = 0.0,
        offsetTop: Double = 0.0,
    ): Pair<Double, Double> {
        val s = if (scale > 0.0) scale else 1.0
        val x = fx.coerceIn(0f, 1f).toDouble() * cssW / s + scrollX
        val y = fy.coerceIn(0f, 1f).toDouble() * cssH / s + scrollY + offsetTop
        return x to y
    }

    const val DESKTOP_CSS_WIDTH = 1280

    /** A modern Android-phone Chrome UA; the Chrome/<ver> token is cosmetic. */
    const val MOBILE_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    /** A desktop Linux Chrome UA for Desktop mode. */
    const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    /**
     * Normalises whatever the user typed into the address bar into a URL
     * the remote browser can load. Blank input returns null. Rules:
     *  - explicit schemes (http/https/about/chrome/file/data) pass through;
     *  - host-like input (a dot or "localhost"/IP, no spaces) gets https://,
     *    except plain hosts/IPs which get http://;
     *  - everything else becomes a Google search.
     */
    fun normalizeAddress(raw: String): String? {
        val q = raw.trim()
        if (q.isEmpty()) return null
        if (q.startsWith("http://", ignoreCase = true) ||
            q.startsWith("https://", ignoreCase = true) ||
            q.startsWith("about:", ignoreCase = true) ||
            q.startsWith("chrome://", ignoreCase = true) ||
            q.startsWith("file://", ignoreCase = true) ||
            q.startsWith("data:", ignoreCase = true)
        ) {
            return q
        }
        val looksIp = Regex("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?(/.*)?$").matches(q)
        val isLocalhost = q.startsWith("localhost", ignoreCase = true) ||
            q.startsWith("127.0.0.1")
        val looksHost = !q.contains(' ') &&
            (looksIp || isLocalhost ||
                (q.contains('.') && q.substringBefore('/').none(Char::isWhitespace)))
        if (looksHost) {
            val scheme = if (looksIp || isLocalhost) "http://" else "https://"
            return scheme + q
        }
        return "https://www.google.com/search?q=" + java.net.URLEncoder.encode(q, "UTF-8")
    }

    /** Chrome page-zoom bounds as percentages (25%..500%). */
    const val ZOOM_MIN_PCT = 25
    const val ZOOM_MAX_PCT = 500

    /** Clamps a browser zoom percentage to the supported range. */
    fun clampBrowserZoom(pct: Int): Int = pct.coerceIn(ZOOM_MIN_PCT, ZOOM_MAX_PCT)

    /**
     * Steps the zoom by [steps] notches (one notch ≈ 10%, multiplicative like
     * Chrome's own zoom, anchored at 100 and rounded to the nearest 5).
     */
    fun stepZoom(currentPct: Int, steps: Int): Int {
        if (steps == 0) return clampBrowserZoom(currentPct)
        val factor = Math.pow(1.10, steps.toDouble())
        val next = (currentPct * factor).toInt()
        val rounded = ((next.toDouble() / 5.0).let { Math.round(it) } * 5).toInt()
        return clampBrowserZoom(rounded)
    }

    /**
     * Maps a pinch ratio (>1 spread out = zoom in) onto a discrete zoom
     * percentage, accumulating from [currentPct].
     */
    fun pinchZoom(currentPct: Int, ratio: Float): Int {
        if (ratio <= 0f) return clampBrowserZoom(currentPct)
        val steps = Math.round(Math.log(ratio.toDouble()) / Math.log(1.10)).toInt()
        return if (steps == 0) clampBrowserZoom(currentPct) else stepZoom(currentPct, steps)
    }

    /**
     * Pixel wheel delta for a two-finger drag of [dragPixels] phone pixels.
     * Wheel deltas are CSS pixels on the remote (deltaMode 0); the factor
     * keeps a phone-sized swipe scrolling a full desktop area.
     */
    fun wheelDeltaPixels(dragPixels: Float): Double = (dragPixels * 2.5).toDouble()

    /**
     * Remote cursor movement in FRACTIONS for trackpad mode: a full phone
     * width of drag crosses [fullSwipePct] of the remote desktop.
     */
    fun trackpadFractionDelta(dragPhonePixels: Float, phoneAxisPx: Float): Float {
        if (phoneAxisPx <= 0f) return 0f
        val fullSwipePct = 0.9f
        return (dragPhonePixels / phoneAxisPx) * fullSwipePct
    }
}
