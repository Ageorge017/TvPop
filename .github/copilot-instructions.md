# TvPop Copilot Instructions

TvPop is an Android TV foreground service that renders overlays (text, images, live video) on top of any running app, triggered by HTTP POST requests from home automation systems like Home Assistant. It listens on port **7979**.

## Build commands

```bash
./gradlew assembleDebug       # Build debug APK
./gradlew installDebug        # Build and install to connected ADB device
./gradlew clean               # Clean build outputs
```

No automated test suite exists yet. To manually verify, connect via ADB and use:

```bash
adb logcat -s TvPop           # Live log filter
adb shell dumpsys activity services com.tvpop   # Confirm service is running
curl -X POST http://<TV_IP>:7979/notify \
  -H "Content-Type: application/json" \
  -d '{"media_type":"text","title":"Test","message":"Hello"}'
```

## Architecture

The app is a single `Service` + `WindowManager` overlay stack. There is no Activity after the permission grant flow completes.

```
MainActivity      → one-time permission check → starts TvPopService → moves to back
TvPopService      → owns the Ktor server (HttpServer) and OverlayManager lifecycle
HttpServer        → validates/parses REST requests → calls OverlayManager.show() / .cancel()
OverlayManager    → manages WindowManager view lifecycle, ExoPlayer, and Coil image loading
BootReceiver      → restarts TvPopService on device boot
ShutdownReceiver  → handles graceful shutdown on system broadcast
```

**Package layout:**
- `com.tvpop` — core orchestrators and UI controllers
- `com.tvpop.model` — DTOs (`NotifyRequest`) and internal state (`OverlayState`)
- `com.tvpop.receiver` — `BootReceiver`, `ShutdownReceiver`

**Data flow for `/notify`:**  
Ktor (IO thread) → parse JSON into `NotifyRequest` → idempotency check via `OverlayState` → `withContext(Dispatchers.Main)` → `OverlayManager.showInternal()`

**Idempotency (ADR-005):** If `media_url` and `media_type` match the current `OverlayState`, only text/style properties are updated in-place (no stream restart). Any other change triggers a full teardown and recreation.

**Concurrency model (ADR-010):** Last-request-wins. A new `/notify` immediately replaces any active overlay. No queuing.

## Critical rules — never violate

- **Main thread for all UI**: Every `WindowManager` call (`addView`, `updateViewLayout`, `removeView`) and every ExoPlayer state change must run on the main thread. Use `Handler(Looper.getMainLooper()).post { }` inside `OverlayManager`, or `withContext(Dispatchers.Main)` in Ktor route handlers.
- **ExoPlayer teardown order**: Always: `player.stop()` → `player.release()` → `player = null` → `windowManager.removeView(overlayView)`. Reversing this order causes hardware decoder lockups on common Android TV chipsets.
- **Permission guard**: Every overlay attempt must be preceded by `Settings.canDrawOverlays(context)`. Skipping this crashes the app.
- **No WebView, no Compose, no ViewBinding**: Use `findViewById` directly. These restrictions are permanent (see ADR-009).
- **Port 7979 is fixed**: Do not change the Ktor server port (ADR-013).
- **Foreground service**: `TvPopService` must call `startForeground()` in `onCreate()` and return `START_STICKY`. Do not weaken this.

## Key conventions

**Threading:**
- `OverlayManager` — use `Handler(Looper.getMainLooper()).post { }` or `View.post { }` (needed when view must be attached)
- Ktor route handlers — use `withContext(Dispatchers.Main)` for side effects
- `TvPopService` — maintains `serviceScope = SupervisorJob() + Dispatchers.IO`; cancel it in `onDestroy()`

**Logging:**
- Every class defines `private val logTag = "TvPop"` — use this consistently so `adb logcat -s TvPop` captures all app output
- `Log.e` for caught exceptions and permission failures; Ktor TRACE for request/response

**HTTP responses:**
All API responses use `{"ok": true}` or `{"ok": false, "error": "error_code"}`. Never return raw strings or different JSON shapes. Known error codes: `invalid_json`, `unsupported_media_type`, `media_url_required`, `overlay_permission_denied`, `unknown_error`.

**Naming:**
- Classes: `PascalCase` | Functions/properties: `camelCase` | Resources: `snake_case`
- JSON fields use `snake_case` via `@SerialName`; Kotlin properties use `camelCase`

**ExoPlayer surface init:** Always wrap ExoPlayer setup and `setVideoSurfaceView()` inside `root.post { }` to avoid "no surface" race conditions (ADR-003).

## Pinned dependency versions

Do not upgrade these without verifying hardware decoder compatibility:

| Dependency | Version |
|---|---|
| `androidx.core:core-ktx` | `1.13.1` |
| `androidx.leanback:leanback` | `1.0.0` |
| `io.ktor:ktor-server-*` | `2.3.11` |
| `androidx.media3:media3-exoplayer-*` | `1.3.1` |
| `io.coil-kt:coil` | `2.6.0` |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | `1.6.3` |

## Before modifying any file

1. Is this operation safe for the main thread, or does it belong on `Dispatchers.IO`?
2. If touching overlay logic, does the teardown sequence still follow: `stop → release → null → removeView`?
3. Does the change preserve the fixed API contract (field names, error codes, response envelope)?
4. Am I introducing WebView, Compose, ViewBinding, or a new dependency?
