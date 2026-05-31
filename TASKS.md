## In Progress

## Backlog
- Implement a 10-second watchdog timer in `OverlayManager` that calls `cancelInternal()` if an RTSP or HLS stream fails to render its first frame within the timeout.
- Wrap the `windowManager.removeView` call in `cancelInternal()` with an `isAttachedToWindow` check to prevent crashes during race conditions between auto-dismiss and manual cancel.
- Add a `/status` GET endpoint to `HttpServer` that returns JSON containing the current service uptime, overlay permission status, and details of any currently active overlay.
- Acquire a partial `PowerManager.WakeLock` in `TvPopService` while an overlay is active to ensure network and player threads are not suspended by Doze mode.
- Refactor `OverlayManager.shouldUpdateInPlace` and `showInternal` to allow "text" media types to update in place instead of triggering a full teardown.
- Implement support for the `bitmap` media type in `HttpServer` and `OverlayManager` to allow clients to send base64-encoded image data directly in the JSON payload.
- Add a `BindException` catch block in `HttpServer.start()` that logs a specific "Port 7979 already in use" error message to help users diagnose conflicts.
- Update the foreground notification in `TvPopService` to dynamically include the device's local IP address in the `contentText` for easier client configuration.
- Implement a 5-second `ExoPlayer` connection timeout that triggers auto-dismiss and logs `ERROR_CODE_IO_NETWORK_CONNECTION_FAILED` to Logcat if the socket fails to open.
- Add a "Send Test Notification" button to `MainActivity` that triggers a local "text" overlay to provide immediate feedback after granting permissions.
- Support a new `volume` field in `NotifyRequest` (Float 0.0 to 1.0) and apply it to the `ExoPlayer` instance for `stream` type notifications.
- Implement an optional "Stacking" mode where a second notification can appear in a different quadrant instead of replacing the current one.

## Completed
- [x] MVP: text, image, and stream overlay types via WindowManager
- [x] Ktor HTTP server on port 7979 with /notify and /cancel routes
- [x] Idempotency: same media_url updates in place without player teardown
- [x] Foreground service with boot receiver and START_STICKY survival
- [x] Low-latency ExoPlayer live config for HLS/RTSP streams
- [x] Silent IMPORTANCE_LOW foreground notification

## Agent instructions
When starting work, pick the top unchecked item from the Backlog. 
Always read CLAUDE.md first to understand the project's critical rules and architecture. 
Once a task is fully implemented and tested, move it to the Completed section.

## See Also
- [.claude/CLAUDE.md](.claude/CLAUDE.md): Critical rules and project brief.
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): Technical decision records.
- [docs/API_SCHEMA.md](docs/API_SCHEMA.md): Full REST API specification.
