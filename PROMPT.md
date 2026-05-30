You are an expert Android TV engineer specializing in Kotlin, WindowManager overlays,
foreground services, and media playback. Your task is to generate a COMPLETE,
COMPILE-READY Android Studio project for an app called TvPop.

Do not summarize, stub, or omit any file. Every file must be complete and functional.
Do not add explanatory prose between files — output pure code, clearly labeled with
its full relative path as a comment header.

Before writing any file, confirm in your reasoning that all import statements, dependency versions, and API calls are mutually consistent. Do not reference any class or method without first verifying it exists in the pinned dependency versions.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
PROJECT CONFIGURATION
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

- App name: TvPop
- Package name: com.tvpop
- Language: Kotlin only (no Java files)
- Min SDK: 29 (Android 10 Q)
- Target SDK: 34
- Build system: Gradle with Kotlin DSL (build.gradle.kts)
- No ViewBinding — use findViewById directly
- No Jetpack Compose
- No WebView anywhere in the project

Dependencies (pin to these exact versions):

- androidx.core:core-ktx:1.13.1
- androidx.leanback:leanback:1.0.0
- io.ktor:ktor-server-core:2.3.11
- io.ktor:ktor-server-netty:2.3.11
- io.ktor:ktor-server-content-negotiation:2.3.11
- io.ktor:ktor-serialization-kotlinx-json:2.3.11
- io.ktor:ktor-server-call-logging:2.3.11
- androidx.media3:media3-exoplayer:1.3.1
- androidx.media3:media3-exoplayer-hls:1.3.1
- androidx.media3:media3-datasource-rtsp:1.3.1
- io.coil-kt:coil:2.6.0
- org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
FILE MANIFEST — generate every file listed below
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
settings.gradle.kts
build.gradle.kts (root)
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/res/layout/activity_main.xml
app/src/main/res/layout/overlay_layout.xml
app/src/main/res/values/strings.xml
app/src/main/res/values/colors.xml
app/src/main/res/drawable/rounded_bg.xml
app/src/main/java/com/tvpop/MainActivity.kt
app/src/main/java/com/tvpop/TvPopService.kt
app/src/main/java/com/tvpop/OverlayManager.kt
app/src/main/java/com/tvpop/HttpServer.kt
app/src/main/java/com/tvpop/model/NotifyRequest.kt
app/src/main/java/com/tvpop/model/OverlayState.kt
app/src/main/java/com/tvpop/receiver/BootReceiver.kt
app/src/main/java/com/tvpop/receiver/ShutdownReceiver.kt

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
ARCHITECTURE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TvPopService (ForegroundService)
│
├── Starts on BOOT_COMPLETED via BootReceiver
├── Stops cleanly on ACTION_SHUTDOWN via ShutdownReceiver  
├── Posts a persistent IMPORTANCE_LOW silent notification
│ (no heads-up, no sound, invisible on Android TV UI)
│ Channel ID: "tvpop_service", Channel name: "TvPop Service"
│
├── HttpServer — Ktor embedded Netty on port 7979
│ ├── POST /notify → parses JSON → calls OverlayManager.show()
│ └── POST /cancel → calls OverlayManager.cancel()
│
└── OverlayManager — manages WindowManager overlay lifecycle
├── Inflates overlay_layout.xml into WindowManager TYPE_APPLICATION_OVERLAY
├── Handles all four media types
└── Manages ExoPlayer lifecycle for stream type

MainActivity
└── Minimal leanback Activity — sole purpose is to prompt the user
to grant SYSTEM_ALERT_WINDOW permission if not already granted,
then immediately move to background. Shows a single TextView:
"TvPop is running. You may close this screen."
Uses Settings.ACTION_MANAGE_OVERLAY_PERMISSION intent.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
JSON SCHEMA — POST /notify
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Content-Type: application/json

{
"media_type": string, // REQUIRED. One of: "text","image","stream","bitmap"
"media_url": string, // Required for image and stream types
"title": string, // Optional. Default: ""
"message": string, // Optional. Default: ""
"duration": int, // Optional. Default: 15. Seconds before auto-dismiss
"position": string, // Optional. One of: "top_left","top_right",
// "bottom_left","bottom_right". Default: "bottom_right"
"width": int, // Optional. Width in dp. Default: 320
"corner_radius": float, // Optional. Corner radius in dp. Default: 12.0
"background_color": string, // Optional. Hex ARGB e.g. "#CC000000". Default: "#CC000000"
"title_color": string, // Optional. Hex RGB e.g. "#FFFFFF". Default: "#FFFFFF"
"message_color": string, // Optional. Hex RGB e.g. "#CCCCCC". Default: "#CCCCCC"
}

Validation rules:

- If media_type is missing or not one of the four supported values →
  return HTTP 400: {"ok": false, "error": "unsupported_media_type"}
- If media_type is "image" or "stream" and media_url is null or blank →
  return HTTP 400: {"ok": false, "error": "media_url_required"}
- If media_type is "bitmap" →
  return HTTP 400: {"ok": false, "error": "unsupported_media_type"}
- All other parsing or internal errors →
  return HTTP 500: {"ok": false, "error": "<exception message>"}
- Success → HTTP 200: {"ok": true}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
POST /cancel
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
No body required. Immediately dismisses the active overlay.
Always returns HTTP 200: {"ok": true}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
CONCURRENT REQUEST / IDEMPOTENCY LOGIC
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
OverlayManager tracks the currently active overlay as an OverlayState object.

When POST /notify arrives:
IF no overlay is currently showing:
→ show new overlay normally

IF an overlay IS currently showing:
IF incoming media_url == currentOverlayState.mediaUrl (exact String match):
→ UPDATE IN PLACE: apply new title, message, duration, position, width,
corner_radius, background_color, title_color, message_color to the
existing view WITHOUT tearing down the media player or reloading the image.
Reset the auto-dismiss timer to the new duration.
ELSE (different media_url OR media_type is "text" and previous was also "text"):
→ REPLACE: cancel existing overlay fully (stop+release player, remove view),
then show new overlay from scratch.

OverlayState model must hold:

- mediaUrl: String?
- mediaType: String
- all current display properties

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
OVERLAY LAYOUT — overlay_layout.xml
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Root: FrameLayout (match_parent width set via WindowManager LayoutParams)
└── CardView (corner_radius applied programmatically, background_color applied)
├── SurfaceView (id: surfaceView) — GONE by default, used only for stream
├── ImageView (id: imageView) — GONE by default, used only for image
└── LinearLayout (vertical)
├── TextView (id: titleText) — GONE if title is blank
└── TextView (id: messageText) — GONE if message is blank

Layout must support showing/hiding each section based on media_type.
SurfaceView must be the lowest z-order child within the CardView so ExoPlayer
renders behind text. ImageView above SurfaceView. Text LinearLayout on top.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
OVERLAY MANAGER IMPLEMENTATION RULES
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
WindowManager LayoutParams:

- type: WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
- flags: FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCH_MODAL or FLAG_LAYOUT_IN_SCREEN
- format: PixelFormat.TRANSLUCENT
- gravity: compute from position field:
  top_left → Gravity.TOP or Gravity.START
  top_right → Gravity.TOP or Gravity.END
  bottom_left → Gravity.BOTTOM or Gravity.START
  bottom_right → Gravity.BOTTOM or Gravity.END (default)
- width: convert dp value to pixels
- height: WRAP_CONTENT
- x, y: 16dp margin from screen edge (hardcoded)

All WindowManager calls (addView, updateViewLayout, removeView) MUST run on
the main thread. Use Handler(Looper.getMainLooper()).post { } when called
from Ktor coroutine threads.

Auto-dismiss: use a single Handler + Runnable. Cancel and reschedule on each
show() or update-in-place call. Duration of 0 means use default of 15 seconds.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
EXOPLAYER — STREAM TYPE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

- Instantiate ExoPlayer using ExoPlayer.Builder(context).build()
- Apply low-latency live config:
  val liveConfig = LivePlaybackSpeedControl.Builder()
  .setFallbackMinPlaybackSpeed(0.97f)
  .setFallbackMaxPlaybackSpeed(1.03f)
  .setMinUpdateIntervalMs(1000)
  .build()
  Pass via ExoPlayer.Builder(...).setLivePlaybackSpeedControl(liveConfig)
- Set minBufferMs = 1000, maxBufferMs = 5000 via DefaultLoadControl
- Call player.setVideoSurfaceView(surfaceView) AFTER the overlay view is
  attached to WindowManager (inside a View.post { } callback)
- Set player.playWhenReady = true
- Prepare with MediaItem.fromUri(media_url)

On dismissal (both auto-dismiss and POST /cancel):

1. player.stop()
2. player.release()
3. player = null
4. windowManager.removeView(overlayView)
   All four steps in this exact order, on the main thread.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
IMAGE TYPE — Coil
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Load with: imageView.load(media_url) { crossfade(true) }
No caching configuration required.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
KTOR HTTP SERVER
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

- embeddedServer(Netty, port = 7979) { ... }
- Install ContentNegotiation with kotlinx.serialization JSON
- Install CallLogging at TRACE level (logs to Logcat only)
- All Ktor errors caught with a global StatusPages plugin
- Route handlers call OverlayManager methods via withContext(Dispatchers.Main)
- Server started in TvPopService.onCreate() inside a SupervisorJob coroutine scope
- Server stopped in TvPopService.onDestroy()

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
ANDROID MANIFEST REQUIREMENTS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Permissions required:
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"/>
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>

TvPopService declaration:
android:foregroundServiceType="mediaPlayback"
android:exported="false"

BootReceiver:
<intent-filter>
<action android:name="android.intent.action.BOOT_COMPLETED"/>
<action android:name="android.intent.action.LOCKED_BOOT_COMPLETED"/>
</intent-filter>
android:exported="true"
android:directBootAware="true"

ShutdownReceiver:
<intent-filter>
<action android:name="android.intent.action.ACTION_SHUTDOWN"/>
</intent-filter>
android:exported="true"

MainActivity:
android:launchMode="singleTask"
Include leanback launcher intent-filter so it appears in Android TV home screen:
<intent-filter>
<action android:name="android.intent.action.MAIN"/>
<category android:name="android.intent.category.LEANBACK_LAUNCHER"/>
</intent-filter>

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
PROCESS SURVIVAL — ANDROID TV LAUNCHER KILLING
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
In TvPopService:

- Return START_STICKY from onStartCommand()
- Call startForeground() immediately in onCreate() before any other work
- Do NOT call stopSelf() under any normal operating condition
- BootReceiver uses startForegroundService() not startService()

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
ERROR HANDLING PHILOSOPHY
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

- The overlay UI NEVER shows error state. All failures are silent to the TV screen.
- All errors are logged to Logcat with tag "TvPop" at ERROR level.
- HTTP callers always receive structured JSON: {"ok":false,"error":"<message>"}
- Wrap all OverlayManager calls in try/catch; swallow exceptions after logging.
- Wrap all ExoPlayer calls in try/catch; a bad stream URL must not crash the service.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
HOME ASSISTANT INTEGRATION NOTE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
At the bottom of HttpServer.kt, add a block comment showing example
Home Assistant rest_command YAML for both /notify (stream example) and /cancel.
This is documentation only — not executable code in the app.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
OUTPUT FORMAT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Output each file in this exact format:

// ─── path/to/File.kt ───────────────────────────────────────
<full file contents>

Generate all 18 files in the order listed in the FILE MANIFEST.
Do not truncate any file. Do not write "// ... rest of implementation".
Every function must be fully implemented and the app compilable.

After generating all files, run:
./gradlew assembleDebug 2>&1

If the build fails:

1. Read the full error output carefully
2. Identify every failing file and line number
3. Fix all errors before attempting another build
4. Re-run ./gradlew assembleDebug
5. Repeat until the build succeeds with 0 errors and 0 warnings
   Do not stop until `BUILD SUCCESSFUL` is confirmed in the output.
