package com.tvpop

import android.util.Log
import com.tvpop.model.NotifyRequest
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

class HttpServer(private val overlayManager: OverlayManager) {
    private val logTag = "TvPop"

    @Volatile
    private var server = embeddedServer(Netty, port = 7979) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                }
            )
        }

        install(CallLogging) {
            level = Level.TRACE
        }

        install(StatusPages) {
            exception<BadRequestException> { call, cause ->
                Log.e(logTag, "HTTP bad request", cause)
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse(ok = false, error = "invalid_json")
                )
            }

            exception<Throwable> { call, cause ->
                Log.e(logTag, "HTTP server error", cause)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse(ok = false, error = cause.message ?: "unknown_error")
                )
            }
        }

        routing {
            post("/notify") {
                val req = call.receive<NotifyRequest>()
                val mediaType = req.mediaType
                if (mediaType !in setOf("text", "image", "stream", "bitmap")) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse(ok = false, error = "unsupported_media_type")
                    )
                    return@post
                }

                if (mediaType == "bitmap") {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse(ok = false, error = "unsupported_media_type")
                    )
                    return@post
                }

                if ((mediaType == "image" || mediaType == "stream") && req.mediaUrl.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse(ok = false, error = "media_url_required")
                    )
                    return@post
                }

                if (!overlayManager.hasOverlayPermission()) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse(ok = false, error = "overlay_permission_denied")
                    )
                    return@post
                }

                withContext(Dispatchers.Main) {
                    try {
                        overlayManager.show(req)
                    } catch (t: Throwable) {
                        Log.e(logTag, "Overlay show call failed", t)
                    }
                }

                call.respond(HttpStatusCode.OK, ApiResponse(ok = true))
            }

            post("/cancel") {
                withContext(Dispatchers.Main) {
                    try {
                        overlayManager.cancel()
                    } catch (t: Throwable) {
                        Log.e(logTag, "Overlay cancel call failed", t)
                    }
                }
                call.respond(HttpStatusCode.OK, ApiResponse(ok = true))
            }
        }
    }

    fun start() {
        server.start(wait = false)
    }

    fun stop() {
        server.stop(gracePeriodMillis = 1000, timeoutMillis = 2000)
    }

    @Serializable
    private data class ApiResponse(
        val ok: Boolean,
        val error: String? = null
    )
}

/*
Home Assistant rest_command examples:

rest_command:
  tvpop_notify_stream:
    url: "http://YOUR_TV_IP:7979/notify"
    method: POST
    content_type: "application/json"
    payload: >
      {
        "media_type": "stream",
        "media_url": "https://example.com/live.m3u8",
        "title": "Live Feed",
        "message": "Camera 1",
        "duration": 20,
        "position": "bottom_right",
        "width": 420,
        "corner_radius": 14,
        "background_color": "#CC000000",
        "title_color": "#FFFFFF",
        "message_color": "#CCCCCC"
      }

  tvpop_cancel:
    url: "http://YOUR_TV_IP:7979/cancel"
    method: POST
*/