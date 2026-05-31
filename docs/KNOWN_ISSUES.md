# Known Issues and Workarounds

This document tracks technical hurdles, Android TV quirks, and defensive implementations within the TvPop codebase.

## ExoPlayer Surface Initialization Race Condition
**Symptom:** Video stream fails to render or crashes with "no surface" errors on the first attempt.
**Cause:** Attempting to bind a `SurfaceView` to ExoPlayer before the view has been fully attached to the `WindowManager` and its underlying `Surface` created.
**Fix / Workaround:** Initialization is wrapped in a `root.post { ... }` block in `OverlayManager.kt` (`bindAndStartStream` function) to ensure the view hierarchy is stable.
**Status:** Mitigated (See [ADR-003](ARCHITECTURE.md#adr-003-exoplayer-surfaceview-binding-timing))

## Hardware Decoder Lockups on Chipset
**Symptom:** Subsequent video streams fail to load, or the entire TV UI becomes sluggish after a notification dismissal.
**Cause:** Some Android TV chipsets (e.g., older Amlogic/Mediatek) fail to release hardware decoder resources if the view is removed from the window before the player is released.
**Fix / Workaround:** A strict teardown sequence is enforced in `cancelInternal()`: 1. `stop()`, 2. `release()`, 3. `null`, 4. `removeView()`. (OverlayManager.kt).
**Status:** Fixed (See [ADR-004](ARCHITECTURE.md#adr-004-exoplayer-teardown-sequence))

## Aggressive Background Process Killing
**Symptom:** The TvPop HTTP server stops responding after the TV has been idle or running heavy apps (Netflix/YouTube) for a while.
**Cause:** Android TV launchers aggressively kill background services to reclaim memory for the foreground media app.
**Fix / Workaround:** `TvPopService` uses `START_STICKY` and calls `startForeground()` immediately in `onCreate()`.
**Status:** Mitigated (See [ADR-006](ARCHITECTURE.md#adr-006-foreground-service-survival-strategy))

## Unsupported Codec/Profile Failure
**Symptom:** A valid stream URL (e.g., a high-profile 4K RTSP feed) fails with a silent error or a "Decoder init failed" log.
**Cause:** The TV's hardware decoder does not support the specific codec, profile, or level of the incoming stream.
**Fix / Workaround:** Explicit error listener in `bindAndStartStream` logs a descriptive warning when `ERROR_CODE_DECODER_INIT_FAILED` is detected.
**Status:** Known limitation

## "Display over other apps" Permission Loss
**Symptom:** The app fails to show overlays, and logs show "Overlay permission denied".
**Cause:** Android 10+ requires manual user consent for `SYSTEM_ALERT_WINDOW`. This permission can be revoked or may not persist across certain system updates.
**Fix / Workaround:** `MainActivity.kt` checks permission on every launch and provides a direct intent to the system settings if missing. `HttpServer` also checks this before processing `/notify`.
**Status:** Mitigated

---

## Watch List

Android TV specific risks and failure modes to monitor:

- **Permission Revocation after APK Reinstall**: Some Android TV OEMs revoke `SYSTEM_ALERT_WINDOW` during side-loaded APK updates.
  - *Mitigation:* Re-verify permission in `MainActivity` on every version update or cold boot.
- **Doze Mode on OEM Firmware**: Low-power states on some TVs may suspend the Ktor network thread even if the service is foregrounded.
  - *Mitigation:* Use `PowerManager.WakeLock` if network connectivity drops are observed during standby.
- **ExoPlayer RTSP Connection Timeout**: RTSP streams may hang indefinitely if the camera goes offline without closing the socket.
  - *Mitigation:* Implement a custom `LoadControl` or a watchdog timer to restart/cancel the player if no frames are received for 10s.
- **WindowManager removeView() Crash**: Calling `removeView()` on a view that is not currently attached (e.g., due to a race between auto-dismiss and a manual `/cancel`).
  - *Mitigation:* Wrap `removeView` in `if (view.isAttachedToWindow)` or continue using the current broad `try-catch` safety net. (Tracked in [TASKS.md](../TASKS.md))
- **Port 7979 Conflicts**: Another home automation app or system service might claim port 7979.
  - *Mitigation:* Catch `BindException` in `HttpServer` and log a clear instruction to change the port. (Tracked in [TASKS.md](../TASKS.md))
- **HLS/RTSP Buffer Bloat**: Default ExoPlayer settings may favor stability over latency, causing the "live" feed to lag by 10+ seconds.
  - *Mitigation:* Fine-tune `DefaultLoadControl` and `LivePlaybackSpeedControl`. (See [ADR-005](ARCHITECTURE.md#adr-005-idempotency-strategy-media-url-match))
