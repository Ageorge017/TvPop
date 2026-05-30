package com.tvpop

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.getSystemService
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultLivePlaybackSpeedControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.datasource.DefaultDataSource
import coil.load
import com.tvpop.model.NotifyRequest
import com.tvpop.model.OverlayState

class OverlayManager(private val context: Context) {
    private val logTag = "TvPop"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val dismissHandler = Handler(Looper.getMainLooper())
    private val windowManager: WindowManager =
        context.getSystemService<WindowManager>()
            ?: throw IllegalStateException("WindowManager unavailable")
    private val inflater: LayoutInflater = LayoutInflater.from(context)

    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var cardView: CardView? = null
    private var surfaceView: SurfaceView? = null
    private var imageView: ImageView? = null
    private var textContainer: LinearLayout? = null
    private var titleText: TextView? = null
    private var messageText: TextView? = null

    private var player: ExoPlayer? = null
    private var currentOverlayState: OverlayState? = null

    private val dismissRunnable = Runnable {
        try {
            cancelInternal()
        } catch (t: Throwable) {
            Log.e(logTag, "Auto-dismiss failed", t)
        }
    }

    fun show(request: NotifyRequest) {
        val normalized = request.toOverlayState()
        mainHandler.post {
            try {
                showInternal(normalized)
            } catch (t: Throwable) {
                Log.e(logTag, "Overlay show failed", t)
            }
        }
    }

    fun cancel() {
        mainHandler.post {
            try {
                cancelInternal()
            } catch (t: Throwable) {
                Log.e(logTag, "Overlay cancel failed", t)
            }
        }
    }

    fun hasOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    private fun showInternal(newState: OverlayState) {
        val existing = currentOverlayState
        if (existing != null && overlayView != null && shouldUpdateInPlace(existing, newState)) {
            applyTextAndStyles(newState)
            val updatedParams = createLayoutParams(newState)
            layoutParams = updatedParams
            overlayView?.let { windowManager.updateViewLayout(it, updatedParams) }
            currentOverlayState = newState
            scheduleAutoDismiss(newState.durationSeconds)
            return
        }

        if (overlayView != null) {
            cancelInternal()
        }

        inflateOverlayIfNeeded()
        applyTextAndStyles(newState)
        configureMedia(newState)

        val params = createLayoutParams(newState)
        layoutParams = params
        if (!hasOverlayPermission()) {
            Log.e(logTag, "Overlay permission denied: SYSTEM_ALERT_WINDOW not granted")
            cancelInternal()
            return
        }
        overlayView?.let { windowManager.addView(it, params) }
        if (newState.mediaType == "stream") {
            bindAndStartStream(newState.mediaUrl)
        }

        currentOverlayState = newState
        scheduleAutoDismiss(newState.durationSeconds)
    }

    private fun shouldUpdateInPlace(current: OverlayState, incoming: OverlayState): Boolean {
        if (incoming.mediaType == "text" && current.mediaType == "text") {
            return false
        }
        return current.mediaUrl == incoming.mediaUrl && current.mediaType == incoming.mediaType
    }

    private fun inflateOverlayIfNeeded() {
        if (overlayView != null) return

        val view = inflater.inflate(R.layout.overlay_layout, null)
        overlayView = view
        cardView = view.findViewById(R.id.overlayCard)
        surfaceView = view.findViewById(R.id.surfaceView)
        imageView = view.findViewById(R.id.imageView)
        textContainer = view.findViewById(R.id.textContainer)
        titleText = view.findViewById(R.id.titleText)
        messageText = view.findViewById(R.id.messageText)
    }

    private fun applyTextAndStyles(state: OverlayState) {
        cardView?.radius = dpToPxF(state.cornerRadiusDp)
        cardView?.setCardBackgroundColor(parseColorSafe(state.backgroundColor, "#CC000000"))

        titleText?.text = state.title
        titleText?.setTextColor(parseColorSafe(state.titleColor, "#FFFFFF"))
        titleText?.visibility = if (state.title.isBlank()) View.GONE else View.VISIBLE

        messageText?.text = state.message
        messageText?.setTextColor(parseColorSafe(state.messageColor, "#CCCCCC"))
        messageText?.visibility = if (state.message.isBlank()) View.GONE else View.VISIBLE

        textContainer?.visibility = if (state.title.isBlank() && state.message.isBlank()) {
            View.GONE
        } else {
            View.VISIBLE
        }
    }

    private fun configureMedia(state: OverlayState) {
        when (state.mediaType) {
            "stream" -> {
                surfaceView?.visibility = View.VISIBLE
                imageView?.visibility = View.GONE
            }

            "image" -> {
                surfaceView?.visibility = View.GONE
                imageView?.visibility = View.VISIBLE
                val url = state.mediaUrl.orEmpty()
                imageView?.load(url) {
                    crossfade(true)
                }
            }

            else -> {
                surfaceView?.visibility = View.GONE
                imageView?.visibility = View.GONE
            }
        }
    }

    private fun bindAndStartStream(mediaUrl: String?) {
        val targetUrl = mediaUrl.orEmpty()
        if (targetUrl.isBlank()) {
            Log.e(logTag, "Stream URL is blank")
            return
        }

        val root = overlayView ?: return
        val surface = surfaceView ?: return
        val uri = Uri.parse(targetUrl)

        root.post {
            try {
                val speedControl = DefaultLivePlaybackSpeedControl.Builder()
                    .setFallbackMinPlaybackSpeed(0.97f)
                    .setFallbackMaxPlaybackSpeed(1.03f)
                    .setMinUpdateIntervalMs(1000)
                    .build()

                val loadControl = DefaultLoadControl.Builder()
                    .setBufferDurationsMs(1000, 5000, 500, 1000)
                    .build()

                val renderersFactory = DefaultRenderersFactory(context)
                    .setEnableDecoderFallback(true)

                val exo = ExoPlayer.Builder(context, renderersFactory)
                    .setLivePlaybackSpeedControl(speedControl)
                    .setLoadControl(loadControl)
                    .build()

                player = exo
                exo.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(logTag, "Stream playback error: ${error.errorCodeName} ${error.message}", error)
                        if (error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED) {
                            Log.e(logTag, "Decoder init failed. Stream codec/profile likely unsupported by this TV decoder.")
                        }
                    }
                })
                exo.setVideoSurfaceView(surface)

                val mediaItem = MediaItem.fromUri(uri)
                val lowerPath = (uri.path ?: "").lowercase()
                val scheme = uri.scheme?.lowercase().orEmpty()

                val mediaSource = when {
                    scheme == "rtsp" -> {
                        RtspMediaSource.Factory()
                            .setForceUseRtpTcp(true)
                            .createMediaSource(mediaItem)
                    }

                    lowerPath.endsWith(".m3u8") -> {
                        HlsMediaSource.Factory(DefaultDataSource.Factory(context))
                            .createMediaSource(mediaItem)
                    }

                    else -> {
                        ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context))
                            .createMediaSource(mediaItem)
                    }
                }

                exo.setMediaSource(mediaSource)
                exo.playWhenReady = true
                exo.prepare()
            } catch (t: Throwable) {
                Log.e(logTag, "Stream playback setup failed", t)
            }
        }
    }

    private fun createLayoutParams(state: OverlayState): WindowManager.LayoutParams {
        val marginPx = dpToPx(16)
        return WindowManager.LayoutParams(
            dpToPx(state.widthDp),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = when (state.position) {
                "top_left" -> Gravity.TOP or Gravity.START
                "top_right" -> Gravity.TOP or Gravity.END
                "bottom_left" -> Gravity.BOTTOM or Gravity.START
                else -> Gravity.BOTTOM or Gravity.END
            }
            x = marginPx
            y = marginPx
        }
    }

    private fun scheduleAutoDismiss(durationSeconds: Int) {
        val safeDuration = if (durationSeconds <= 0) 15 else durationSeconds
        dismissHandler.removeCallbacks(dismissRunnable)
        dismissHandler.postDelayed(dismissRunnable, safeDuration * 1000L)
    }

    private fun cancelInternal() {
        dismissHandler.removeCallbacks(dismissRunnable)

        try {
            player?.stop()
        } catch (t: Throwable) {
            Log.e(logTag, "Player stop failed", t)
        }

        try {
            player?.release()
        } catch (t: Throwable) {
            Log.e(logTag, "Player release failed", t)
        }

        player = null

        try {
            overlayView?.let { windowManager.removeView(it) }
        } catch (t: Throwable) {
            Log.e(logTag, "Overlay remove failed", t)
        }

        overlayView = null
        layoutParams = null
        cardView = null
        surfaceView = null
        imageView = null
        textContainer = null
        titleText = null
        messageText = null
        currentOverlayState = null
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    private fun dpToPxF(dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }

    private fun parseColorSafe(value: String, fallback: String): Int {
        return try {
            Color.parseColor(value)
        } catch (_: IllegalArgumentException) {
            Color.parseColor(fallback)
        }
    }

    private fun NotifyRequest.toOverlayState(): OverlayState {
        return OverlayState(
            mediaUrl = mediaUrl,
            mediaType = mediaType.orEmpty(),
            title = title,
            message = message,
            durationSeconds = if (duration <= 0) 15 else duration,
            position = position,
            widthDp = if (widthDp <= 0) 320 else widthDp,
            cornerRadiusDp = if (cornerRadiusDp <= 0f) 12.0f else cornerRadiusDp,
            backgroundColor = backgroundColor,
            titleColor = titleColor,
            messageColor = messageColor
        )
    }
}