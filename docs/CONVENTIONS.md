# TvPop Coding Conventions

This document captures the actual patterns and conventions used consistently throughout the TvPop codebase. See [.claude/CLAUDE.md](../.claude/CLAUDE.md) for project-wide critical rules.

### Package & file structure
The project follows a functional package structure:
- `com.tvpop`: Core orchestrators (`TvPopService`, `HttpServer`) and UI controllers (`OverlayManager`, `MainActivity`).
- `com.tvpop.model`: Data Transfer Objects (DTOs) and internal state models.
- `com.tvpop.receiver`: System event handlers for boot and shutdown.

### Naming conventions
- **Classes**: `PascalCase` (e.g., `OverlayManager`, `NotifyRequest`).
- **Functions**: `camelCase` (e.g., `showInternal`, `cancelInternal`).
- **Private Properties**: `camelCase` (e.g., `logTag`, `mainHandler`).
- **Resources**: `snake_case` (e.g., `overlay_layout.xml`, `ic_launcher_foreground.xml`).
- **JSON Fields**: `snake_case` for external API (via `@SerialName`), mapped to `camelCase` properties.

### Threading model
- **Main Thread**: All `WindowManager` calls (`addView`, `updateViewLayout`, `removeView`), `ExoPlayer` state changes, and property updates in `OverlayManager`.
- **IO Thread**: Ktor server startup and shutdown, disk I/O, and network operations.
- **Switching Strategy**: 
  - Use `Handler(Looper.getMainLooper()).post { ... }` within `OverlayManager`.
  - Use `withContext(Dispatchers.Main) { ... }` within Ktor route handlers.
  - Use `View.post { ... }` for operations requiring the view to be attached.

### Error handling patterns
- **Silent Failures**: The TV UI never displays error messages.
- **Catch-All Logging**: High-risk blocks (WindowManager, ExoPlayer, Service starts) are wrapped in `try { ... } catch (t: Throwable)` to prevent service crashes.
- **API Errors**: Surfaced to HTTP callers using the standard `ApiResponse` envelope with `HttpStatusCode.BadRequest` or `HttpStatusCode.InternalServerError`.

### Logging
- **Tag**: A private constant `logTag = "TvPop"` is used in every class.
- **Severity**: 
  - `Log.e`: Used for all caught exceptions and permission failures.
  - `TRACE`: Used for Ktor request/response logging.
- **Unified Filter**: All application logs can be filtered in Logcat using `tag:TvPop`.

### HTTP response format
All API responses use a standard JSON envelope:
- **Success**: `{"ok": true}` (HTTP 200)
- **Failure**: `{"ok": false, "error": "error_code"}` (HTTP 400/500)
- **Common Error Codes**: `invalid_json`, `unsupported_media_type`, `media_url_required`, `overlay_permission_denied`.

### Coroutine scope conventions
- **Service Scope**: `TvPopService` maintains a `serviceScope` with `SupervisorJob() + Dispatchers.IO`.
- **Lifecycle**: Scopes are created in `onCreate()` and explicitly cancelled in `onDestroy()`.
- **Server Scope**: Ktor's internal scope is used for routing, with explicit jumps to `Dispatchers.Main` for side effects.

### Android lifecycle patterns
- **Foreground Service**: Uses `START_STICKY` and immediate `startForeground()` to ensure process survival.
- **Overlay Permissions**: Handled by `MainActivity` which moves to the back immediately after ensuring permissions and service status.
- **Boot Persistence**: `BootReceiver` triggers `startForegroundService` to ensure TvPop is always ready.

### Patterns to preserve
- **Manual Thread Marshaling**: Never assume a callback is on the main thread; explicitly switch to `Dispatchers.Main` or `mainHandler` for UI.
- **Teardown Sequence**: Always release `ExoPlayer` hardware resources *before* removing the view from the `WindowManager`.
- **ApiResponse Consistency**: Never return raw strings or different JSON structures from the HTTP server.
- **Permission Guards**: Every attempt to show an overlay must be preceded by a `Settings.canDrawOverlays` check.
- **LogTag Uniformity**: Keep the `logTag = "TvPop"` pattern to ensure system-wide troubleshooting remains possible.
