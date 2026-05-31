# Architecture Decision Records (ADR)

This document logs the significant technical decisions for the TvPop project.

## ADR-001: HTTP Server Library and Threading Model
**Decision:** Ktor with Netty engine. Threading is managed by Ktor's coroutine-based dispatcher, with explicit context switching to `Dispatchers.Main` for UI/WindowManager operations.
**Alternatives rejected:** NanoHTTPD (blocking I/O), OkHttp MockWebServer.
**Reason:** Ktor provides a modern, asynchronous, and idiomatic Kotlin API. It handles high-concurrency REST requests efficiently on Android TV hardware.
**Consequences:** Requires careful management of `CoroutineScope` in `TvPopService` and strict `Dispatchers.Main` usage for all view manipulations.
**Status:** Permanent

## ADR-002: WindowManager Overlay Type and Flags
**Decision:** Using `TYPE_APPLICATION_OVERLAY` with `FLAG_NOT_FOCUSABLE`, `FLAG_NOT_TOUCH_MODAL`, and `FLAG_LAYOUT_IN_SCREEN`.
**Alternatives rejected:** `TYPE_SYSTEM_ALERT` (deprecated).
**Reason:** Required for compatibility with Android 10+ (API 29+). The flags ensure the overlay does not intercept user interactions intended for the background application (e.g., playback controls in Netflix).
**Consequences:** Requires the user to manually grant the "Display over other apps" permission in system settings.
**Status:** Permanent

## ADR-003: ExoPlayer SurfaceView Binding Timing
**Decision:** Initializing ExoPlayer and setting the video surface within a `root.post { ... }` block.
**Alternatives rejected:** Immediate initialization during `show()`.
**Reason:** Ensures the `SurfaceView` is fully attached and its underlying `Surface` is valid before the player attempts to use it. This prevents "no surface" errors and initialization race conditions.
**Consequences:** Introduces a negligible delay (one message loop iteration) before video starts.
**Status:** Permanent

## ADR-004: ExoPlayer Teardown Sequence
**Decision:** Strict order: 1. `player.stop()`, 2. `player.release()`, 3. `player = null`, 4. `windowManager.removeView(overlayView)`.
**Alternatives rejected:** Direct `removeView()` without player cleanup.
**Reason:** Prevents hardware decoder lockups on specific Android TV chipsets. Releasing the player before removing the view ensures the hardware resources are freed before the UI context is destroyed.
**Consequences:** Centralized in `cancelInternal()` to ensure it's always followed, regardless of whether dismissal is auto-timed or manual.
**Status:** Permanent

## ADR-005: Idempotency Strategy (Media URL Match)
**Decision:** If an incoming request's `media_url` and `media_type` match the current state, update text and styles in place. Otherwise, perform a full teardown and recreation.
**Alternatives rejected:** Always recreate; partial updates based on complex diffing.
**Reason:** Avoids the visual flicker and resource overhead of restarting a stream if only the text message (e.g., "Motion Detected" -> "Person Spotted") changed for the same camera.
**Consequences:** Static UI properties are updated immediately, but player configuration (like low-latency settings) remains tied to the initial URL load.
**Status:** Permanent

## ADR-006: Foreground Service Survival Strategy
**Decision:** Returning `START_STICKY` from `onStartCommand` and calling `startForeground()` immediately in `onCreate()`.
**Alternatives rejected:** `START_NOT_STICKY`.
**Reason:** Android TV launchers aggressively kill background processes. `START_STICKY` tells the OS to recreate the service if killed. Early `startForeground()` avoids "service did not call startForeground" crashes.
**Consequences:** Requires a persistent, silent notification in the system tray.
**Status:** Permanent

## ADR-007: Image Loading Library Choice
**Decision:** Coil (Coroutine Image Loader).
**Alternatives rejected:** Glide, Picasso.
**Reason:** Coil is modern, lightweight, and built specifically for Kotlin and Coroutines, making it a perfect match for the project's Ktor-based architecture.
**Consequences:** Simplifies image fetching from camera URLs with built-in crossfade and error handling.
**Status:** Permanent

## ADR-008: Error Response Envelope Design
**Decision:** Standard JSON envelope: `{"ok": boolean, "error": string?}` with appropriate HTTP status codes.
**Alternatives rejected:** Raw text errors; HTTP status codes only.
**Reason:** Provides a consistent API for home automation integrations (like Home Assistant), allowing them to parse the reason for failure (e.g., `invalid_json`, `media_url_required`).
**Consequences:** Every API response, including success, must be wrapped in this envelope.
**Status:** Permanent

## ADR-009: Exclusion of WebView
**Decision:** Explicit prohibition of `WebView` components.
**Alternatives rejected:** Using `WebView` for rich HTML notifications.
**Reason:** WebView teardown is unreliable on Android TV and frequently causes hardware video surface race conditions. If a WebView teardown and a new stream's initialization overlap, the player may attempt to claim a surface that hasn't been fully released yet. Since these operations occur on different threads without native synchronization, explicit prohibition ensures stability.
**Consequences:** Notifications are limited to native text, images, and video streams. Rich HTML content is not supported.
**Status:** Permanent

## ADR-010: Concurrent Request Handling Model
**Decision:** Last-request-wins serial execution on the Main thread.
**Alternatives rejected:** Request queuing.
**Reason:** On a TV, the most recent event (e.g., a doorbell press) is usually the most relevant. Queuing could lead to stale notifications popping up minutes late. Stacking is avoided to keep the screen uncluttered.
**Consequences:** Rapid sequential requests will cause the current overlay to be immediately replaced by the new one. Stacking/side-by-side display may be considered for future improvements.
**Status:** Permanent

## ADR-011: Video Display via SurfaceView
**Decision:** Using `SurfaceView` instead of `TextureView` for video rendering.
**Alternatives rejected:** `TextureView`.
**Reason:** `SurfaceView` provides lower latency, minimal graphical overhead, and more accurate framing. This is critical for real-time security camera streams on TV hardware where performance and latency are prioritized over UI transition flexibility.
**Consequences:** Limits the ability to apply complex view transforms (like rotation or certain alpha animations) to the video surface, but ensures the best possible playback performance.
**Status:** Permanent

## ADR-012: Network Security Model
**Decision:** No application-level authentication (API keys/tokens) for the REST API.
**Alternatives rejected:** Bearer tokens, Basic Auth.
**Reason:** Conscious decision to rely on local network isolation. The app is intended to be used within a trusted home network (e.g., controlled by Home Assistant) where the TV and trigger sources reside on the same VLAN.
**Consequences:** The device must be protected by network-level security (Firewalls/VLANs) as the `/notify` endpoint is open to any device on the local network.
**Status:** Permanent

## ADR-013: HTTP Server Port Selection
**Decision:** Using port 7979 for the Ktor server.
**Alternatives rejected:** Port 80, 8080, or other standard ports.
**Reason:** Arbitrary selection of a high port to avoid conflicts with common Android and TV-specific services (Cast, DIAL, etc.).
**Consequences:** Clients must be configured to target this specific port.
**Status:** Permanent

---
**See also:** [API_SCHEMA.md](API_SCHEMA.md) for the external surface and [KNOWN_ISSUES.md](KNOWN_ISSUES.md) for technical trade-offs.
