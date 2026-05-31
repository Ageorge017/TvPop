# TvPop Project Brief

TvPop is an Android TV utility that displays real-time overlays (text, images, or video streams) triggered by REST API requests. It acts as a bridge between home automation systems (like Home Assistant) and the TV, allowing camera feeds, doorbells, or notifications to pop up over any active app (Netflix, YouTube, etc.) without interrupting the user.

## Project Documentation
- [ARCHITECTURE.md](../docs/ARCHITECTURE.md): Architectural Decision Records (ADR) and core design principles.
- [CONVENTIONS.md](../docs/CONVENTIONS.md): Coding standards, threading models, and naming conventions.
- [API_SCHEMA.md](../docs/API_SCHEMA.md): REST API reference, request/response formats, and idempotency logic.
- [KNOWN_ISSUES.md](../docs/KNOWN_ISSUES.md): Technical hurdles, Android TV quirks, and workarounds.
- [TASKS.md](../TASKS.md): Project roadmap, backlog, and task status.

## Critical rules — never violate these
- **Main Thread UI**: All WindowManager operations (`addView`, `updateViewLayout`, `removeView`) and ExoPlayer state changes must occur on the Main thread using `Handler(Looper.getMainLooper())`.
- **ExoPlayer Teardown**: To prevent memory leaks and decoder lockup, always follow this strict order: 1. `player.stop()`, 2. `player.release()`, 3. `player = null`, 4. `windowManager.removeView(overlayView)`.
- **Foreground Service**: The app must run as a sticky foreground service (`START_STICKY`) with a persistent notification to prevent the system from killing it.
- **Permission Guard**: Always check `Settings.canDrawOverlays(context)` before attempting to show an overlay; failure to do so will crash the app.
- **Architectural Restrictions**: No WebView, no Jetpack Compose, and no ViewBinding are permitted in this project. Use `findViewById` directly.
- **Server Port**: The Ktor server must remain on port 7979.

## Key files and what they own
- `TvPopService.kt`: Single owner of the application lifecycle, managing the Ktor server and OverlayManager.
- `HttpServer.kt`: Owns the REST API surface (POST `/notify`, POST `/cancel`) and JSON request validation.
- `OverlayManager.kt`: Controls the WindowManager overlay lifecycle, ExoPlayer stream binding, and Coil image loading.
- `MainActivity.kt`: Responsible for requesting `SYSTEM_ALERT_WINDOW` permissions and bootstrapping the service.
- `NotifyRequest.kt`: Defines the public API contract for incoming notification data.
- `OverlayState.kt`: Maintains the current internal state of the active overlay for idempotency checks.
- `BootReceiver.kt`: Ensures the service restarts automatically on device boot or locked boot.

## Build and install commands
- Build: `./gradlew assembleDebug`
- Install: `./gradlew installDebug`
- Clean: `./gradlew clean`

## Pinned dependency versions
- `androidx.core:core-ktx:1.13.1`
- `androidx.leanback:leanback:1.0.0`
- `io.ktor:ktor-server-*:2.3.11`
- `androidx.media3:media3-exoplayer-*:1.3.1`
- `io.coil-kt:coil:2.6.0`
- `org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3`

## Before touching any file
- Is this operation safe for the Main thread, or does it belong on `Dispatchers.IO`?
- If modifying the overlay, does the teardown logic still release the ExoPlayer and remove the view correctly?
- Does the change maintain compatibility with the fixed API schema for Home Assistant?
- Have I verified that no new dependencies or "modern" UI frameworks (Compose) are being introduced?
