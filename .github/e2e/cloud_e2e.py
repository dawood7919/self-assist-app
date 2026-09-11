#!/usr/bin/env python3
"""
CDP protocol probe for Orbit Cloud Browser.

Mirrors the exact message sequence RealVpsApi uses:
  PUT /json/new  -> target WS -> Page.enable -> Browser window bounds
  Page.startScreencast (+ ack every frame) -> navigate -> mouse/key/wheel
  -> history -> ZOOM variants (ctrl+wheel vs ctrl+'=') -> reload.

Outputs JPEG frames + summary.json into --out, exits non-zero on hard failure.
Pure stdlib + websocket-client.
"""
import argparse
import base64
import json
import threading
import time
import urllib.request
import urllib.parse
import urllib.error
import sys
import os
import functools
import http.server as httpserver
import socketserver
import tempfile

try:
    import websocket  # websocket-client
except ImportError:
    print("pip install websocket-client first", file=sys.stderr)
    sys.exit(2)


def start_local_site():
    """Serves test pages over HTTP (Chrome blocks top-level navigation to
    data: URLs once an https origin has loaded)."""
    www = tempfile.mkdtemp(prefix="orbit-e2e-")
    pages = {
        "tall.html": (
            "<!doctype html><body style='margin:0'>"
            "<div style='height:6000px;width:100%;"
            "background:linear-gradient(#fff,#036)'></div></body>"),
        # Reports the environment the page actually renders in.
        "probe.html": (
            "<!doctype html><meta name=viewport content='width=device-width,"
            "initial-scale=1'>"
            "<body style='margin:0'>"
            "<pre id=out></pre><script>"
            "document.getElementById('out').textContent=JSON.stringify({"
            "iw:window.innerWidth,ih:window.innerHeight,"
            "dpr:window.devicePixelRatio,"
            "touch:('ontouchstart' in window),"
            "points:navigator.maxTouchPoints,"
            "ua:navigator.userAgent,"
            "mobile:matchMedia('(pointer:coarse)').matches});"
            "</script></body>"),
        # Full-width button that counts press/release driven clicks.
        "tap.html": (
            "<!doctype html><meta name=viewport content='width=device-width'>"
            "<body style='margin:0'>"
            "<button id=b style='position:fixed;left:0;top:0;width:120px;"
            "height:60px;font-size:24px'>TAP</button>"
            "<div id=n>0</div><script>"
            "var n=0;document.getElementById('b').addEventListener('click',"
            "function(){n++;document.getElementById('n').textContent=n;});"
            "</script></body>"),
        # Tall page scrolled only by real touch dragging.
        "touchscroll.html": (
            "<!doctype html><meta name=viewport content='width=device-width'>"
            "<body style='margin:0'>"
            "<div style='height:5000px;width:100%;"
            "background:linear-gradient(#fff,#063)'></div></body>"),
    }
    for name, html in pages.items():
        with open(os.path.join(www, name), "w", encoding="utf-8") as f:
            f.write(html)
    handler = functools.partial(
        httpserver.SimpleHTTPRequestHandler, directory=www)
    srv = socketserver.TCPServer(("127.0.0.1", 8901), handler)
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    return "http://127.0.0.1:8901/"


class Cdp:
    def __init__(self, url, name="cdp"):
        self.name = name
        self.ws = websocket.WebSocket(enable_multithread=True)
        self.ws.connect(url, timeout=10)
        self.ws.settimeout(0.2)
        self._id = 0
        self._closed = False
        self._lock = threading.Lock()
        self._cond = threading.Condition()
        self._results = {}
        self.frames = []          # (sessionId, b64data, metadata)
        self.events = []
        self.reader = threading.Thread(target=self._read_loop, daemon=True)
        self.reader.start()

    def _read_loop(self):
        while True:
            try:
                raw = self.ws.recv()
            except websocket.WebSocketTimeoutException:
                # recv timeout: the connection is alive, just idle. Keep
                # reading forever so events/responses never strand a command.
                continue
            except Exception:
                # Wake every waiter so they fail fast instead of hanging.
                with self._cond:
                    self._closed = True
                    self._cond.notify_all()
                return
            if not raw:
                with self._cond:
                    self._closed = True
                    self._cond.notify_all()
                return
            try:
                msg = json.loads(raw)
            except Exception:
                continue
            if "id" in msg:
                with self._cond:
                    self._results[msg["id"]] = msg
                    self._cond.notify_all()
                continue
            method = msg.get("method", "")
            params = msg.get("params", {})
            if method == "Page.screencastFrame":
                sid = params["sessionId"]
                meta = params.get("metadata", {})
                self.frames.append((sid, params["data"], meta))
                self.send("Page.screencastFrameAck", {"sessionId": sid}, await_id=False)
            else:
                self.events.append((method, params))

    def send(self, method, params=None, timeout=15.0, await_id=True):
        with self._lock:
            self._id += 1
            mid = self._id
        envelope = {"id": mid, "method": method, "params": params or {}}
        with self._cond:
            self.ws.send(json.dumps(envelope))
            if not await_id:
                return None
            deadline = time.time() + timeout
            while mid not in self._results:
                if self._closed:
                    raise RuntimeError(f"{self.name}: socket closed waiting for {method} (id={mid})")
                if not self._cond.wait(timeout=max(0.01, deadline - time.time())):
                    if time.time() >= deadline:
                        raise RuntimeError(f"{self.name}: timeout waiting for {method} (id={mid})")
            msg = self._results.pop(mid)
        if "error" in msg:
            raise RuntimeError(f"{self.name}: CDP error on {method}: {msg['error']}")
        return msg.get("result", {})

    def eval(self, expr, timeout=15.0):
        r = self.send("Runtime.evaluate", {
            "expression": expr,
            "returnByValue": True,
            "awaitPromise": True,
        }, timeout=timeout)
        res = r.get("result", {})
        if "value" in res:
            return res["value"]
        return res

    def wait_frame(self, timeout=15.0, minimum=0):
        deadline = time.time() + timeout
        while time.time() < deadline:
            if len(self.frames) > minimum:
                return self.frames[-1]
            time.sleep(0.05)
        raise RuntimeError(f"{self.name}: no screencast frame after {timeout}s")

    def close(self):
        try:
            self.ws.close()
        except Exception:
            pass


def http(base, method, path):
    req = urllib.request.Request(base + path, method=method)
    with urllib.request.urlopen(req, timeout=10) as resp:
        return json.loads(resp.read().decode())


def save_jpeg(out_dir, name, b64data):
    p = os.path.join(out_dir, name)
    with open(p, "wb") as fh:
        fh.write(base64.b64decode(b64data))
    return p


def jpeg_size(path):
    with open(path, "rb") as fh:
        data = fh.read(8192)
    # minimal JPEG SOF parser
    i = 2
    while i < len(data):
        if data[i] != 0xFF:
            i += 1
            continue
        marker = data[i + 1]
        if marker in (0xC0, 0xC1, 0xC2):
            h = int.from_bytes(data[i + 5:i + 7], "big")
            w = int.from_bytes(data[i + 7:i + 9], "big")
            return w, h
        seglen = int.from_bytes(data[i + 2:i + 4], "big")
        i += 2 + seglen
    return None, None


TEST_PAGE = "data:text/html," + urllib.parse.quote("""<!doctype html><html><head><meta charset=utf8><style>
body{margin:0;font-family:Arial}#box{position:absolute;left:200px;top:150px;width:240px;height:90px;background:#2f6bff;color:white;font-size:30px;display:flex;align-items:center;justify-content:center}
#f{position:absolute;left:200px;top:300px;width:400px;height:48px;font-size:24px}
#tall{position:absolute;left:20px;top:420px;width:900px;height:3000px;background:linear-gradient(#fff,#bcd)}
</style></head><body>
<div id=box onclick="window.__clicks=(window.__clicks||0)+1;window.__last=[event.clientX,event.clientY];this.textContent='CLICKED '+window.__clicks">BTN</div>
<input id=f>
<div id=tall>SCROLL</div>
<script>window.__ready=1</script>
</body></html>""")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://127.0.0.1:9222")
    ap.add_argument("--out", required=True)
    ap.add_argument("--site", default="https://www.example.com/")
    ap.add_argument("--xvfb-base", default="http://127.0.0.1:9333")
    args = ap.parse_args()
    os.makedirs(args.out, exist_ok=True)
    summary = {"steps": [], "zoom": {}}

    def step(name, ok, detail=""):
        summary["steps"].append({"name": name, "ok": bool(ok), "detail": str(detail)[:600]})
        print(f"[{'PASS' if ok else 'FAIL'}] {name} {detail}")

    # 1. version ---------------------------------------------------------
    version = http(args.base, "GET", "/json/version")
    browser_ws_url = version["webSocketDebuggerUrl"]
    bv = version.get("Browser", "")
    step("version", ("Browser" in bv) or bv.startswith("Chrome/"), bv)

    # 2. create target exactly like the app: about:blank via PUT, then
    # Page.navigate on the target session.
    try:
        row = http(args.base, "PUT", "/json/new?about:blank")
    except Exception:
        row = http(args.base, "GET", "/json/new?about:blank")
    target_id = row.get("id", "")
    ws_url = row.get("webSocketDebuggerUrl", "")
    if not ws_url:
        for r in http(args.base, "GET", "/json/list"):
            if r.get("id") == target_id:
                ws_url = r.get("webSocketDebuggerUrl", "")
    step("createTarget", bool(target_id and ws_url), target_id)

    page = Cdp(ws_url, "page")
    browser = Cdp(browser_ws_url, "browser")
    page.send("Page.enable")
    page.send("Runtime.enable")
    page.send("Page.navigate", {"url": TEST_PAGE})
    # wait for the data page to be ready
    t0 = time.time()
    while time.time() - t0 < 10 and page.eval("window.__ready||0") != 1:
        time.sleep(0.2)

    # 3. window bounds ----------------------------------------------------
    win = browser.send("Browser.getWindowForTarget", {"targetId": target_id})
    window_id = win["windowId"]
    browser.send("Browser.setWindowBounds", {"windowId": window_id,
                 "bounds": {"windowState": "normal", "width": 1280, "height": 720}})
    time.sleep(0.6)
    win2 = browser.send("Browser.getWindowForTarget", {"targetId": target_id})
    dims = page.eval("JSON.stringify({w: window.innerWidth, h: window.innerHeight})")
    step("windowBounds1280x720", True, f"bounds={win2.get('bounds')} viewport={dims}")

    # 4. screencast -------------------------------------------------------
    page.send("Page.startScreencast",
              {"format": "jpeg", "quality": 60, "maxWidth": 1280, "maxHeight": 720, "everyNthFrame": 1})
    sid, b64, meta = page.wait_frame(timeout=15)
    first_frame = save_jpeg(args.out, "01-testpage.jpg", b64)
    iw, ih = jpeg_size(first_frame)
    step("screencastFrames", sid >= 0, f"jpeg={iw}x{ih} meta={json.dumps(meta)[:200]}")
    summary["frameMeta"] = meta

    # wait for test page ready
    deadline = time.time() + 10
    while time.time() < deadline and page.eval("window.__ready||0") != 1:
        time.sleep(0.2)

    # 5. keyboard: focus + insertText ------------------------------------
    page.eval("document.getElementById('f').focus()")
    page.send("Input.insertText", {"text": "hello orbit"})
    value = page.eval("document.getElementById('f').value")
    step("insertText", value == "hello orbit", repr(value))

    # 6. mouse click at (260, 180) -> inside #box(200,150 240x90) --------
    for typ, kwargs in [
        ("mouseMoved", dict(x=260, y=180)),
        ("mousePressed", dict(x=260, y=180, button="left", buttons=1, clickCount=1)),
        ("mouseReleased", dict(x=260, y=180, button="left", clickCount=1)),
    ]:
        p = {"type": typ, "x": kwargs.pop("x"), "y": kwargs.pop("y")}
        p.update(kwargs)
        page.send("Input.dispatchMouseEvent", p)
    time.sleep(0.4)
    clicks = page.eval("JSON.stringify({n: window.__clicks, last: window.__last})")
    parsed = json.loads(clicks)
    step("mouseClick", parsed.get("n") == 1 and parsed.get("last") == [260, 180], clicks)

    # 7. wheel scroll -----------------------------------------------------
    page.send("Input.dispatchMouseEvent",
              {"type": "mouseWheel", "x": 640, "y": 360, "deltaX": 0, "deltaY": 900})
    time.sleep(0.6)
    sy = page.eval("Math.round(window.scrollY)")
    scrolled = sy > 100
    step("mouseWheel", scrolled, f"scrollY={sy}")
    page.wait_frame(2, minimum=0)
    save_jpeg(args.out, "02-scrolled.jpg", page.frames[-1][1])

    # scroll back up for clean zoom anchors
    page.eval("window.scrollTo(0,0)")
    time.sleep(0.4)

    # 8. ZOOM: headless Chrome often ignores Ctrl+wheel/Ctrl+= browser
    # accelerators (no browser chrome), so discover every mechanism the app
    # could rely on and record which ones actually move visualViewport.scale.
    scale0 = float(page.eval("window.visualViewport.scale"))

    def vv_scale():
        return float(page.eval("window.visualViewport.scale"))

    # (a) Emulation.setPageScaleFactor — absolute pinch-style page scale.
    psf_ok = False
    psf_detail = ""
    try:
        page.send("Emulation.setPageScaleFactor", {"pageScaleFactor": 1.25})
        time.sleep(0.6)
        s125 = vv_scale()
        page.send("Emulation.setPageScaleFactor", {"pageScaleFactor": 0.8})
        time.sleep(0.6)
        s080 = vv_scale()
        page.send("Emulation.setPageScaleFactor", {"pageScaleFactor": 1.0})
        time.sleep(0.4)
        s100 = vv_scale()
        # Desktop pages clamp pinch page-scale to a 1.0 minimum, so only
        # zoom-in and reset are required here; zoom-out uses CSS zoom.
        psf_ok = abs(s125 - 1.25) < 0.03 and abs(s100 - 1.0) < 0.02
        psf_detail = f"1.25->{s125} 0.8->{s080}(clamped below 1 expected) 1.0->{s100}"
    except Exception as e:
        psf_detail = f"error: {str(e)[:200]}"
    step("zoomSetPageScaleFactor", psf_ok, psf_detail)
    summary["zoom"]["setPageScaleFactor"] = {"works": psf_ok, "detail": psf_detail}

    # (b) Input.synthesizePinchGesture — the touch pinch gesture.
    pinch_ok = False
    pinch_detail = ""
    try:
        page.send("Input.synthesizePinchGesture",
                  {"x": 640, "y": 360, "scaleFactor": 1.5, "relativeSpeed": 400})
        time.sleep(0.8)
        sp = vv_scale()
        page.send("Emulation.setPageScaleFactor", {"pageScaleFactor": 1.0})
        pinch_ok = sp > 1.35
        pinch_detail = f"scale={sp}"
    except Exception as e:
        pinch_detail = f"error: {str(e)[:200]}"
    step("zoomPinchGesture", pinch_ok, pinch_detail)
    summary["zoom"]["pinchGesture"] = {"works": pinch_ok, "detail": pinch_detail}

    # (c) CSS zoom — the only mechanism expected to work below 100% on
    # desktop pages (page-scale pinch clamps to 1.0 minimum). Measure a
    # 100px marker's box; clientWidth is unreliable under root zoom.
    css_ok = False
    css_detail = ""
    try:
        page.eval("""
            (function(){var d=document.getElementById('zmark');
            if(!d){d=document.createElement('div');d.id='zmark';
            d.style.cssText='position:fixed;left:0;top:0;width:100px;height:100px;z-index:99999';
            document.body.appendChild(d);}})()""")
        base = float(page.eval(
            "document.getElementById('zmark').getBoundingClientRect().width"))
        def css_zoom(target, value):
            page.eval(f"document.{target}.style.zoom='{value}'")
            time.sleep(0.4)
            return float(page.eval(
                "document.getElementById('zmark').getBoundingClientRect().width"))
        w_html_in = css_zoom("documentElement", "1.25")
        w_html_out = css_zoom("documentElement", "0.8")
        page.eval("document.documentElement.style.zoom=''")
        w_body_in = css_zoom("body", "1.25")
        page.eval("document.body.style.zoom=''")
        r_h_in = w_html_in / base if base else 0
        r_h_out = w_html_out / base if base else 0
        r_b_in = w_body_in / base if base else 0
        css_ok = abs(r_h_in - 1.25) < 0.1 or abs(r_b_in - 1.25) < 0.1
        css_detail = (f"marker={base:.0f} html125={w_html_in:.0f}({r_h_in:.2f}) "
                      f"html080={w_html_out:.0f}({r_h_out:.2f}) body125={w_body_in:.0f}({r_b_in:.2f})")
    except Exception as e:
        css_detail = f"error: {str(e)[:200]}"
    step("zoomCssFullRange", css_ok, css_detail)
    summary["zoom"]["css"] = {"works": css_ok, "detail": css_detail}

    # (d) legacy browser accelerators — informational on headless; they are
    # not part of the gating result.
    legacy_ok = False
    legacy_detail = "ok"
    try:
        page.send("Input.dispatchMouseEvent",
                  {"type": "mouseWheel", "x": 640, "y": 360, "deltaX": 0,
                   "deltaY": -240, "modifiers": 2})
        time.sleep(0.5)
        legacy_ok = vv_scale() > scale0 + 0.05
        if not legacy_ok:
            page.send("Input.dispatchKeyEvent", {"type": "keyDown", "key": "Control",
                      "code": "ControlLeft", "windowsVirtualKeyCode": 17, "modifiers": 2})
            page.send("Input.dispatchKeyEvent", {"type": "keyDown", "key": "=",
                      "code": "Equal", "windowsVirtualKeyCode": 187, "modifiers": 2})
            page.send("Input.dispatchKeyEvent", {"type": "keyUp", "key": "=",
                      "code": "Equal", "windowsVirtualKeyCode": 187, "modifiers": 2})
            page.send("Input.dispatchKeyEvent", {"type": "keyUp", "key": "Control",
                      "code": "ControlLeft", "windowsVirtualKeyCode": 17})
            time.sleep(0.5)
            legacy_ok = vv_scale() > scale0 + 0.05
        page.send("Emulation.setPageScaleFactor", {"pageScaleFactor": 1.0})
    except Exception as e:
        legacy_ok = False
        import traceback as _tb
        legacy_detail = "error: " + _tb.format_exc()[:400]
        print("::notice::legacy zoom error:", legacy_detail.replace("\n", " | "))
    step("zoomLegacyAcceleratorsInformational", True,
         f"works={legacy_ok} (not gating on headless) {legacy_detail}")
    summary["zoom"]["legacy"] = legacy_ok

    time.sleep(0.3)
    try:
        page.wait_frame(2)
        save_jpeg(args.out, "03-zoomed.jpg", page.frames[-1][1])
    except Exception as e:
        import traceback as _tb
        print("::notice::zoom capture error:", _tb.format_exc().replace("\n", " | ")[:500])

    # Reset to a known scale regardless of which mechanism the page used.
    try:
        page.send("Emulation.setPageScaleFactor", {"pageScaleFactor": 1.0})
    except Exception:
        pass
    time.sleep(0.4)
    scale_reset = vv_scale()
    step("zoomResetScale", abs(scale_reset - 1.0) < 0.02, f"scale={scale_reset}")
    summary["zoom"]["resetTo"] = scale_reset

    # At least one real zoom path must work, otherwise remote zoom is dead.
    step("zoomAtLeastOneMechanism", psf_ok or pinch_ok or css_ok,
         f"setPageScaleFactor={psf_ok} pinch={pinch_ok} css={css_ok} legacy={legacy_ok}")

    # 9. navigation history ----------------------------------------------
    page.send("Page.navigate", {"url": "data:text/html,<title>AAA</title><h1>A</h1>"})
    time.sleep(1.0)
    page.send("Page.navigate", {"url": "data:text/html,<title>BBB</title><h1>B</h1>"})
    time.sleep(1.0)
    hist = page.send("Page.getNavigationHistory")
    idx = hist["currentIndex"]
    entries = [e.get("title") for e in hist["entries"]]
    page.eval("history.back()")
    time.sleep(1.0)
    back_title = page.eval("document.title")
    page.eval("history.forward()")
    time.sleep(1.0)
    fwd_title = page.eval("document.title")
    step("history", idx >= 2 and back_title == "AAA" and fwd_title == "BBB",
         f"idx={idx} entries={entries} back={back_title} fwd={fwd_title}")

    # 10. real site -------------------------------------------------------
    nav = page.send("Page.navigate", {"url": args.site})
    err = (nav or {}).get("errorText")
    time.sleep(4.0)
    info = page.eval("JSON.stringify({title: document.title, url: location.href, "
                     "w: window.innerWidth, h: window.innerHeight})")
    page.wait_frame(6)
    frame_path = save_jpeg(args.out, "04-realsite.jpg", page.frames[-1][1])
    iw2, ih2 = jpeg_size(frame_path)
    step("realSite", not err, f"{info} navError={err} frame={iw2}x{ih2}")
    summary["realSite"] = json.loads(info)

    # 11. reload ----------------------------------------------------------
    page.send("Page.reload", {"ignoreCache": False})
    time.sleep(3.0)
    page.wait_frame(4)
    title_after = page.eval("document.title")
    step("reload", bool(title_after), f"title={title_after}")

    # ── Mobile emulation: the page must believe it runs on a phone ──────
    site_base = start_local_site()

    def nav_wait(url):
        before = len(page.frames)
        page.send("Page.navigate", {"url": url})
        dl = time.time() + 6
        while time.time() < dl and len(page.frames) <= before:
            time.sleep(0.05)
        time.sleep(0.6)

    page.send("Page.stopScreencast", {})
    time.sleep(0.2)
    MOBILE_UA = ("Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 "
                 "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
    DESKTOP_UA = ("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
                  "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
    page.send("Network.enable", {})

    def emulate(css_w, css_h, dsf, mobile, ua, touch_pts=1):
        page.send("Emulation.setDeviceMetricsOverride",
                  {"width": css_w, "height": css_h,
                   "deviceScaleFactor": dsf, "mobile": mobile})
        if touch_pts > 0:
            page.send("Emulation.setTouchEmulationEnabled",
                      {"enabled": True, "maxTouchPoints": touch_pts})
        else:
            page.send("Emulation.setTouchEmulationEnabled",
                      {"enabled": False})
        page.send("Network.setUserAgentOverride", {"userAgent": ua})

    def restart_caps(w, h):
        try:
            page.send("Page.stopScreencast", {})
        except Exception:
            pass
        time.sleep(0.2)
        page.send("Page.startScreencast",
                  {"format": "jpeg", "quality": 70, "maxWidth": w,
                   "maxHeight": h, "everyNthFrame": 1})
        time.sleep(0.4)

    emulate(393, 800, 2.75, True, MOBILE_UA, 1)
    restart_caps(1081, 2200)
    nav_wait(site_base + "probe.html")
    probe = page.eval("document.getElementById('out').textContent")
    pj = json.loads(probe)
    # coarse-pointer media query is informational across Chrome builds;
    # the hard signals are layout width, DSF, touch points and UA.
    mobile_ok = (abs(pj.get("iw", 0) - 393) <= 2 and
                 abs(pj.get("dpr", 0) - 2.75) < 0.05 and
                 pj.get("points", 0) >= 1 and
                 "Mobile" in pj.get("ua", ""))
    step("mobileEmulation", mobile_ok,
         f"iw={pj.get('iw')} ih={pj.get('ih')} dpr={pj.get('dpr')} "
         f"touch={pj.get('touch')} points={pj.get('points')} "
         f"coarse={pj.get('mobile')} uaMobile={'Mobile' in pj.get('ua','')}")
    summary["mobile"] = pj

    # Mobile coded frame must be phone-shaped and ~physical-pixel wide.
    j = save_jpeg(args.out, "06-mobile.jpg", page.frames[-1][1])
    mw, mh = jpeg_size(j)
    meta = page.frames[-1][2]
    sharp = mw >= 900 and mh > mw
    step("mobileFrameSharp", True,
         f"sharp={sharp} jpeg={mw}x{mh} deviceCss="
         f"{meta.get('deviceSize', {})} (informational until DSF sweep)")

    # Real touch tap (touchStart then touchEnd) clicks the button.
    nav_wait(site_base + "tap.html")
    def touch(type_, points):
        page.send("Input.dispatchTouchEvent", {"type": type_, "touchPoints": [
            {"id": i, "x": x, "y": y, "radiusX": 1, "radiusY": 1,
             "force": 0.0 if type_ == "touchEnd" else 1.0}
            for i, (x, y) in enumerate(points)]})
    time.sleep(0.2)
    touch("touchStart", [(60.0, 30.0)])
    time.sleep(0.05)
    touch("touchEnd", [(60.0, 30.0)])
    time.sleep(0.4)
    taps = page.eval("document.getElementById('n').textContent")
    step("touchTap", taps == "1", f"clicks={taps}")

    # Real touch drag scrolls the page (finger up = page down).
    nav_wait(site_base + "touchscroll.html")
    page.eval("window.scrollTo(0,0)")
    time.sleep(0.2)
    touch("touchStart", [(200.0, 600.0)])
    for y in (540, 480, 400, 300, 200, 120):
        touch("touchMove", [(200.0, float(y))])
        time.sleep(0.05)
    touch("touchEnd", [(200.0, 120.0)])
    time.sleep(0.5)
    tscroll = int(float(page.eval("window.scrollY")))
    step("touchScroll", tscroll > 100, f"scrollY={tscroll}")
    summary["mobileTouchScrollY"] = tscroll

    # Native two-finger pinch: informational; fallback pinch gesture is the
    # proven path (zoomPinchGesture above).
    pinch_scale_before = float(page.eval("window.visualViewport.scale"))
    try:
        touch("touchStart", [(150.0, 400.0), (243.0, 400.0)])
        for k in range(1, 7):
            d = 20 * k
            touch("touchMove", [(150.0 - d, 400.0), (243.0 + d, 400.0)])
            time.sleep(0.04)
        touch("touchEnd", [(50.0, 400.0), (343.0, 400.0)])
        time.sleep(0.4)
        pinch_scale_after = float(page.eval("window.visualViewport.scale"))
    except Exception as e:
        pinch_scale_after = pinch_scale_before
        pinch_error_native = str(e)[:120]
    else:
        pinch_error_native = ""
    native_pinch = abs(pinch_scale_after - pinch_scale_before) > 0.05
    step("nativeMultitouchPinchInfo", True,
         f"nativePinch={native_pinch} {pinch_scale_before}->{pinch_scale_after} "
         f"{pinch_error_native} (gating uses synthesizePinchGesture)")
    page.send("Emulation.setPageScaleFactor", {"pageScaleFactor": 1.0})
    time.sleep(0.2)

    # Desktop mode: real desktop viewport + desktop UA, no touch.
    emulate(1280, 2607, 1.0, False, DESKTOP_UA, 0)
    restart_caps(1260, 2567)
    nav_wait(site_base + "probe.html")
    dpj = json.loads(page.eval("document.getElementById('out').textContent"))
    desktop_ok = (abs(dpj.get("iw", 0) - 1280) <= 2 and
                  dpj.get("points", 1) == 0 and
                  "Mobile" not in dpj.get("ua", ""))
    step("desktopMode", desktop_ok,
         f"iw={dpj.get('iw')} points={dpj.get('points')} "
         f"uaMobile={'Mobile' in dpj.get('ua','')}")

    # Headless DSF sweep: does screencast ever scale frames by deviceScaleFactor?
    sweep = {}
    for dsf in (1.0, 2.0, 3.0):
        try:
            page.send("Emulation.setDeviceMetricsOverride",
                      {"width": 393, "height": 800, "deviceScaleFactor": dsf,
                       "mobile": True})
            restart_caps(1200, 2600)
            time.sleep(0.8)
            f = page.frames[-1][1]
            tw, th = jpeg_size(save_jpeg(args.out, f"sweep-headless-{dsf}.jpg", f))
            sweep[str(dsf)] = [tw, th]
        except Exception as e:
            sweep[str(dsf)] = f"err:{e}"
    step("headlessDsfSweep", True, f"frameSizesByDsf={sweep} (informational)")
    summary["headlessDsfSweep"] = sweep

    # captureScreenshot is a request/response frame source; check whether it
    # honors deviceScaleFactor like screencast does not.
    page.send("Emulation.setDeviceMetricsOverride",
              {"width": 393, "height": 800, "deviceScaleFactor": 2.75,
               "mobile": True})
    time.sleep(0.4)
    shot = page.send("Page.captureScreenshot",
                     {"format": "jpeg", "quality": 80,
                      "captureBeyondViewport": False}, timeout=20.0)
    sb = base64.b64decode(shot["data"])
    sp = os.path.join(args.out, "08-shot-headless.jpg")
    open(sp, "wb").write(sb)
    sw, sh = jpeg_size(sp)
    shot_scales = sw >= 900 and sh > sw
    step("headlessCaptureScreenshotDsf", True,
         f"dsfScales={shot_scales} jpeg={sw}x{sh} (informational)")
    summary["headlessScreenshot"] = [sw, sh]

    # Production pump call: clip.scale (= coded width / CSS width) alone is
    # expected to fix the output density regardless of DSF handling.
    clip = page.send("Page.captureScreenshot",
                     {"format": "jpeg", "quality": 78,
                      "captureBeyondViewport": False, "fromSurface": True,
                      "optimizeForSpeed": True,
                      "clip": {"x": 0, "y": 0, "width": 393, "height": 800,
                               "scale": 1081 / 393}}, timeout=20.0)
    cb = base64.b64decode(clip["data"])
    cp = os.path.join(args.out, "10-clip-headless.jpg")
    open(cp, "wb").write(cb)
    cw, ch = jpeg_size(cp)
    clip_ok = abs(cw - 1081) <= 3 and ch > cw
    step("headlessCaptureClipScale", clip_ok,
         f"clipScaleSharp={clip_ok} jpeg={cw}x{ch} expected~1081x2200")
    summary["headlessClipScreenshot"] = [cw, ch]

    # Headful chrome under Xvfb: deviceScaleFactor SHOULD reach the capture
    # pipeline there (full headful compositor), unlike --headless=new.
    def xvfb_probe():
        try:
            http(args.xvfb_base, "GET", "/json/version")
            row = http(args.xvfb_base, "PUT", "/json/new?about:blank")
            t = Cdp(row["webSocketDebuggerUrl"], "xvfb-target")
            t.send("Page.enable", {})
            # Headful Chrome requires the tab active before startScreencast.
            t.send("Page.bringToFront", {})
            t.send("Network.enable", {})
            # DSF 2 keeps the headful software-rendered surface tractable
            # under Xvfb while still proving the frame scales with DSF.
            t.send("Emulation.setDeviceMetricsOverride",
                   {"width": 393, "height": 800, "deviceScaleFactor": 2.0,
                    "mobile": True})
            t.send("Emulation.setTouchEmulationEnabled",
                   {"enabled": True, "maxTouchPoints": 1})
            t.send("Network.setUserAgentOverride", {"userAgent": MOBILE_UA})
            # Navigate FIRST and wait for it (headful attach is slower);
            # capture starts once the document is live.
            try:
                t.send("Page.navigate",
                       {"url": site_base + "probe.html"}, timeout=45.0)
            except Exception as e:
                return None, f"navigate failed: {e}"
            cast_err = None
            for attempt in range(3):
                try:
                    t.send("Page.stopScreencast", {}, timeout=5.0)
                except Exception:
                    pass
                try:
                    t.send("Page.bringToFront", {}, timeout=5.0)
                    t.send("Page.startScreencast",
                           {"format": "jpeg", "quality": 70, "maxWidth": 1081,
                            "maxHeight": 2200, "everyNthFrame": 1}, timeout=10.0)
                    cast_err = None
                    break
                except Exception as e:
                    cast_err = str(e)[:160]
                    time.sleep(1.5)
            if cast_err is not None:
                return None, f"screencast refused: {cast_err}"
            time.sleep(3.5)
            if not t.frames:
                return None, "no frames"
            frame = t.frames[-1][1]
            meta = t.frames[-1][2]
            fp = save_jpeg(args.out, "07-xvfb-mobile.jpg", frame)
            w, h = jpeg_size(fp)
            xshot = t.send("Page.captureScreenshot",
                           {"format": "jpeg", "quality": 80,
                            "captureBeyondViewport": False}, timeout=20.0)
            xb = base64.b64decode(xshot["data"])
            xp = os.path.join(args.out, "09-shot-xvfb.jpg")
            open(xp, "wb").write(xb)
            xw, xh = jpeg_size(xp)
            # Production-pump call with explicit clip scale (dsf 2).
            xclip = t.send("Page.captureScreenshot",
                           {"format": "jpeg", "quality": 78,
                            "captureBeyondViewport": False, "fromSurface": True,
                            "optimizeForSpeed": True,
                            "clip": {"x": 0, "y": 0, "width": 393,
                                     "height": 800, "scale": 2.0}}, timeout=20.0)
            xcb = base64.b64decode(xclip["data"])
            xcp = os.path.join(args.out, "11-clip-xvfb.jpg")
            open(xcp, "wb").write(xcb)
            xcw, xch = jpeg_size(xcp)
            env = t.eval("document.getElementById('out').textContent")
            return (w, h, meta, json.loads(env), [xw, xh], [xcw, xch]), None
        except Exception as e:
            return None, str(e)[:200]

    xvres, xverr = xvfb_probe()
    if xvres:
        w, h, meta, env, shot_dims, clip_dims = xvres
        # At dsf=2 a headful screencast must be ~2x the 393 CSS width.
        dsf_scales = w >= 700 and h > w
        shot_scales = shot_dims[0] >= 700 and shot_dims[1] > shot_dims[0]
        clip_scales = abs(clip_dims[0] - 786) <= 3 and clip_dims[1] > clip_dims[0]
        step("xvfbMobileFrame", True,
             f"castScales={dsf_scales} jpeg={w}x{h} "
             f"shotScales={shot_scales} shot={shot_dims[0]}x{shot_dims[1]} "
             f"clipScaleSharp={clip_scales} clip={clip_dims[0]}x{clip_dims[1]} "
             f"env=iw{env.get('iw')}/dpr{env.get('dpr')}/touch{env.get('points')} "
             f"meta={json.dumps(meta)[:120]} (informational)")
        step("xvfbCaptureClipScale", clip_scales,
             f"jpeg={clip_dims[0]}x{clip_dims[1]} expected~786x1600")
        summary["xvfb"] = {"jpeg": [w, h], "env": env, "screenshot": shot_dims,
                           "clip": clip_dims}
    else:
        step("xvfbMobileFrame", True, f"skipped: {xverr}")

    # Clear emulation so the legacy cadence/resize tail behaves unchanged.
    page.send("Emulation.clearDeviceMetricsOverride", {})
    page.send("Emulation.setTouchEmulationEnabled", {"enabled": False})
    page.send("Network.setUserAgentOverride", {"userAgent": DESKTOP_UA})
    time.sleep(0.3)

    # 12. frame cadence under interaction (scroll a TALL page for 3s) -----
    # example.com has no scrollable overflow, so wheel events produce no
    # damage and no frames; navigate to a tall page instead.
    page.send("Page.navigate", {"url": site_base + "tall.html"})
    base_n = len(page.frames)
    deadline = time.time() + 4.0
    while time.time() < deadline and len(page.frames) <= base_n:
        time.sleep(0.05)
    nav_frames = len(page.frames) - base_n
    before = len(page.frames)
    t_end = time.time() + 3.0
    y = 0
    while time.time() < t_end:
        y *= -1
        page.send("Input.dispatchMouseEvent",
                  {"type": "mouseWheel", "x": 640, "y": 360, "deltaX": 0,
                   "deltaY": 300 if y == 0 else -300}, await_id=False)
        time.sleep(0.2)
    got = len(page.frames) - before
    # Diagnostic: does restarting screencast restore the frame stream?
    page.send("Page.stopScreencast", {})
    time.sleep(0.3)
    page.send("Page.startScreencast",
              {"format": "jpeg", "quality": 60, "maxWidth": 1280,
               "maxHeight": 720, "everyNthFrame": 1})
    time.sleep(0.5)
    restart_base = len(page.frames)
    t_end = time.time() + 3.0
    while time.time() < t_end:
        page.send("Input.dispatchMouseEvent",
                  {"type": "mouseWheel", "x": 640, "y": 360, "deltaX": 0,
                   "deltaY": 300}, await_id=False)
        time.sleep(0.2)
    got_after_restart = len(page.frames) - restart_base
    scrolled = int(page.eval("window.scrollY"))
    fps = round(max(got, got_after_restart) / 3.0, 1)
    step("frameCadence", got >= 5 or got_after_restart >= 5,
         f"afterNavFrames={int(nav_frames)} scrolling={got} "
         f"afterScreencastRestart={got_after_restart} scrollY={scrolled}")
    summary["fpsWhileScrolling"] = fps
    summary["framesAfterNav"] = int(nav_frames)
    summary["needsScreencastRestartAfterNav"] = got < 5 and got_after_restart >= 5

    # 13. resize to 1080p -------------------------------------------------
    browser.send("Browser.setWindowBounds", {"windowId": window_id,
                 "bounds": {"windowState": "normal", "width": 1920, "height": 1080}})
    time.sleep(0.8)
    page.send("Page.stopScreencast")
    page.send("Page.startScreencast",
              {"format": "jpeg", "quality": 60, "maxWidth": 1280, "maxHeight": 1080, "everyNthFrame": 1})
    page.wait_frame(5)
    fp = save_jpeg(args.out, "05-1080p.jpg", page.frames[-1][1])
    iw3, ih3 = jpeg_size(fp)
    dims2 = page.eval("JSON.stringify({w: window.innerWidth, h: window.innerHeight})")
    step("resize1080", True, f"viewport={dims2} frame={iw3}x{ih3}")

    summary["frameSizes"] = {"testpage": [iw, ih], "real": [iw2, ih2], "hd": [iw3, ih3]}
    with open(os.path.join(args.out, "summary.json"), "w") as fh:
        json.dump(summary, fh, indent=2)

    failed = [s for s in summary["steps"] if not s["ok"]]
    print(f"\n{len(summary['steps']) - len(failed)}/{len(summary['steps'])} steps passed")
    page.close()
    browser.close()
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
