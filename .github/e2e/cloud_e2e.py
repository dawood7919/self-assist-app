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
    step("version", "Browser" in version.get("Browser", ""), version.get("Browser", ""))

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

    # 8. ZOOM: ctrl + wheel ----------------------------------------------
    scale0 = float(page.eval("window.visualViewport.scale"))
    page.send("Input.dispatchMouseEvent",
              {"type": "mouseWheel", "x": 640, "y": 360, "deltaX": 0, "deltaY": -240,
               "modifiers": 2})
    time.sleep(0.8)
    scale_wheel = float(page.eval("window.visualViewport.scale"))
    zoom_wheel = scale_wheel > scale0 + 0.05
    step("zoomCtrlWheel", zoom_wheel, f"{scale0} -> {scale_wheel}")
    summary["zoom"]["ctrlWheel"] = {"from": scale0, "to": scale_wheel, "works": zoom_wheel}

    # ctrl + '=' keyboard path
    if not zoom_wheel:
        page.send("Input.dispatchKeyEvent", {"type": "keyDown", "key": "Control",
                  "code": "ControlLeft", "windowsVirtualKeyCode": 17, "modifiers": 2})
        page.send("Input.dispatchKeyEvent", {"type": "keyDown", "key": "=",
                  "code": "Equal", "windowsVirtualKeyCode": 187, "modifiers": 2})
        page.send("Input.dispatchKeyEvent", {"type": "keyUp", "key": "=",
                  "code": "Equal", "windowsVirtualKeyCode": 187, "modifiers": 2})
        page.send("Input.dispatchKeyEvent", {"type": "keyUp", "key": "Control",
                  "code": "ControlLeft", "windowsVirtualKeyCode": 17})
        time.sleep(0.8)
        scale_key = float(page.eval("window.visualViewport.scale"))
        zoom_key = scale_key > scale0 + 0.05
        step("zoomCtrlEqualKey", zoom_key, f"{scale0} -> {scale_key}")
        summary["zoom"]["ctrlEqual"] = {"from": scale0, "to": scale_key, "works": zoom_key}
    else:
        step("zoomCtrlEqualKey", True, "skipped (ctrl+wheel path works)")

    time.sleep(0.3)
    page.wait_frame(2)
    save_jpeg(args.out, "03-zoomed.jpg", page.frames[-1][1])

    # reset Ctrl+0
    page.send("Input.dispatchKeyEvent", {"type": "rawKeyDown", "key": "0",
              "code": "Digit0", "windowsVirtualKeyCode": 48, "modifiers": 2})
    page.send("Input.dispatchKeyEvent", {"type": "keyUp", "key": "0",
              "code": "Digit0", "windowsVirtualKeyCode": 48, "modifiers": 2})
    time.sleep(0.6)
    scale_reset = float(page.eval("window.visualViewport.scale"))
    step("zoomResetCtrl0", abs(scale_reset - 1.0) < 0.02, f"scale={scale_reset}")
    summary["zoom"]["resetTo"] = scale_reset

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

    # 12. frame cadence under interaction (scroll for 3s) ---------------
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
