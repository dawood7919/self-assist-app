# Cloud Browser → mobile-cloud-browser redesign (Puffin model)

## Root cause of the current "tiny desktop page / 500% / black bars" state
- Headless Chrome window is created at a DESKTOP 1280×720 viewport; the
  screencast JPEG is letterboxed (ContentScale.Fit) into a portrait phone,
  so the desktop page floats in black.
- The only way users could read it was browser page-zoom up to 500%.
- Input is mouse-only (`Input.dispatchMouseEvent`); touch is faked via mouse.

## Target architecture (CDP mobile emulation, no remote desktop)
1. Per-target `Emulation.setDeviceMetricsOverride {width,height,deviceScaleFactor,mobile}`
   + `Emulation.setTouchEmulationEnabled` + `Emulation.setEmitTouchEventsForMouse`
   + `Network.setUserAgentOverride` (Android UA in Mobile mode, desktop UA in
   Desktop mode). Same target for both modes → cookies/storage/URL preserved;
   switching changes override + `Page.reload`.
2. Viewport dims come from the ACTUAL phone content box
   (DisplayMetrics minus toolbar/bottom bar): cssW = physW / dsf.
   Screencast maxWidth/maxHeight = physical content pixels → frame aspect ==
   content box aspect → Image fills bounds, zero black margins.
3. Mobile input = real `Input.dispatchTouchEvent` sequences (tap / double tap /
   long press / drag-scroll / two-finger pinch + pan). Pointer Mode keeps the
   mouse path with an on-screen cursor, hidden by default.
4. Zoom = genuine page zoom (pinch / +/- / double tap), default 100%, pill
   hidden at 100%. In: `Emulation.setPageScaleFactor` /
   `Input.synthesizePinchGesture` (probe-verified). Out below 100%: root CSS
   `zoom` (probe-verified full range).
5. Coordinate layer: fractions × emulated CSS dims; zoom/scroll correction
   from screencastFrame metadata (pageScaleFactor/scrollOffset) is a pure,
   unit-tested function in CdpInput.
6. Connection states are friendly (Connecting / Reconnecting / Disconnected /
   Error); internal strings like "connectBlocking()" are humanised and never
   shown. SSH control connection auto-heals (done) + CDP session relaunch.
7. Adaptive quality stays (Low/Balanced/High/Ultra); scrolling keeps current
   quality, stationary frames are full quality (screencast intrinsic).

## Files
- CdpMessages.kt: device metrics / touch / UA override / dispatchTouchEvent /
  synthesizePinchGesture / clearOverride.
- CdpInput.kt: ViewportSpec math, UA constants, touch-coordinate transform,
  physical screencast params.
- CloudModels.kt: BrowserMode, PageInfo.inputFocused/mode.
- VpsApi.kt + RealVpsApi.kt + FakeVpsApi.kt: launchSession viewport params,
  applyViewport/setMode, touch input, frame-metadata-driven coordinate mapping.
- BrowserInput.kt + BrowserGestures.kt: touch gesture surface + pinch.
- BrowserViewScreen.kt: fill-bounds frame, viewport size reporting,
  Mobile/Desktop + Pointer toggles, zoom pill hidden at 100%, auto keyboard on
  remote input focus, friendly overlays.
- CloudBrowserTool.kt: DisplayMetrics-based viewport at launch, mode state,
  error humaniser + reconnect.
- cloud_e2e.py: mobile emulation / touch tap / touch scroll / native pinch /
  desktop-switch verification.
