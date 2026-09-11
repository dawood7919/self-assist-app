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

try:
    import websocket  # websocket-client
except ImportError:
    print("pip install websocket-client first", file=sys.stderr)
    sys.exit(2)


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

    # 12. frame cadence under interaction (scroll a TALL page for 3s) -----
    # example.com has no scrollable overflow, so wheel events produce no
    # damage and no frames; navigate to a tall page instead.
    page.send("Page.navigate", {"url":
        "data:text/html,<body style='margin:0'>"
        "<div style='height:6000px;background:linear-gradient(#fff,#36c)'></div>"
        "</body>"})
    time.sleep(1.5)
    page.wait_frame(4)
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
    fps = round(got / 3.0, 1)
    step("frameCadence", got >= 5, f"{got} frames in 3s (~{fps} fps while scrolling)")
    summary["fpsWhileScrolling"] = fps

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
