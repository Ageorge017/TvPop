# TvPop API Reference

The TvPop API allows external clients to trigger and control overlays on the Android TV screen. The server runs on port **7979** by default.

For high-level architecture decisions, see [ARCHITECTURE.md](ARCHITECTURE.md). For common troubleshooting, see [KNOWN_ISSUES.md](KNOWN_ISSUES.md).

## Endpoints

### POST `/notify`
Triggers a new overlay or updates an existing one.

- **Content-Type:** `application/json`

#### Request Schema

| Field | Type | Required | Default | Validation Rules | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `media_type` | String | **Yes** | N/A | Must be one of: `text`, `image`, `stream`. | `bitmap` is explicitly rejected. |
| `media_url` | String | Conditional | N/A | Required if `media_type` is `image` or `stream`. | URL of the image or video stream. |
| `title` | String | No | `""` | Any string. | Bold header text. |
| `message` | String | No | `""` | Any string. | Secondary descriptive text. |
| `duration` | Int | No | `15` | Seconds. | Time before auto-dismissal. `0` defaults to 15. |
| `position` | String | No | `"bottom_right"` | `top_left`, `top_right`, `bottom_left`, `bottom_right`. | Overlay screen position. |
| `width` | Int | No | `320` | Width in DP. | Total width of the notification card. |
| `corner_radius` | Float | No | `12.0` | Radius in DP. | Rounding of the card corners. |
| `background_color` | String | No | `"#CC000000"` | Hex ARGB (e.g., `#RRGGBB` or `#AARRGGBB`). | Background fill of the card. |
| `title_color` | String | No | `"#FFFFFF"` | Hex RGB/ARGB. | Color of the title text. |
| `message_color` | String | No | `"#CCCCCC"` | Hex RGB/ARGB. | Color of the message text. |
| `muted` | Boolean | No | `false` | `true` or `false`. | Mutes stream audio when `true`. Only meaningful for `stream` media type. |

#### Responses

| HTTP Status | JSON Body | Condition |
| :--- | :--- | :--- |
| `200 OK` | `{"ok": true}` | Success. Overlay is shown or updated. |
| `400 Bad Request` | `{"ok": false, "error": "invalid_json"}` | Malformed JSON or type mismatch. |
| `400 Bad Request` | `{"ok": false, "error": "unsupported_media_type"}` | `media_type` is missing, unknown, or `bitmap`. |
| `400 Bad Request` | `{"ok": false, "error": "media_url_required"}` | `image` or `stream` requested without a `media_url`. |
| `500 Internal Error` | `{"ok": false, "error": "overlay_permission_denied"}` | App lacks "Display over other apps" permission. |
| `500 Internal Error` | `{"ok": false, "error": "..."}` | Unexpected internal server error. |

#### Examples

**curl**
```bash
curl -X POST http://TV_IP:7979/notify \
  -H "Content-Type: application/json" \
  -d '{
    "media_type": "stream",
    "media_url": "http://example.com/camera.m3u8",
    "title": "Front Door",
    "message": "Motion Detected",
    "position": "top_right"
  }'
```

**Home Assistant (`rest_command`)**
```yaml
rest_command:
  tvpop_notify:
    url: "http://YOUR_TV_IP:7979/notify"
    method: POST
    content_type: "application/json"
    payload: >
      {
        "media_type": "{{ media_type | default('text') }}",
        "media_url": "{{ media_url }}",
        "title": "{{ title }}",
        "message": "{{ message }}",
        "duration": {{ duration | default(15) }}
      }
```

---

### POST `/cancel`
Immediately dismisses any active overlay.

- **Content-Type:** `application/json` (body ignored)

#### Responses

| HTTP Status | JSON Body | Condition |
| :--- | :--- | :--- |
| `200 OK` | `{"ok": true}` | Always returned, even if no overlay was active. |

#### Examples

**curl**
```bash
curl -X POST http://TV_IP:7979/cancel
```

**Home Assistant (`rest_command`)**
```yaml
rest_command:
  tvpop_cancel:
    url: "http://YOUR_TV_IP:7979/cancel"
    method: POST
```

---

## Idempotency Behavior

When a `/notify` request is received while an overlay is already visible, TvPop decides whether to perform a full recreation (heavy) or an in-place update (light).

### Decision Logic
1. If the current overlay and the new request both have `media_type: "text"`, **REPLACE** (always recreate for text notifications).
2. If the `media_url` and `media_type` of the new request exactly match the current state, **UPDATE IN-PLACE**.
   - This keeps the video stream or image loaded and only updates text, colors, layout parameters, and mute state.
   - The auto-dismiss timer is reset.
3. In all other cases, **REPLACE**.
   - The old player is released, the view is removed, and a brand new overlay is created.

### Pseudocode
```kotlin
if (currentOverlay != null) {
    val isSameMedia = current.mediaUrl == incoming.mediaUrl && 
                      current.mediaType == incoming.mediaType
    
    val isTextToText = current.mediaType == "text" && 
                       incoming.mediaType == "text"

    if (isSameMedia && !isTextToText) {
        updateExistingView(incoming)
        resetTimer(incoming.duration)
    } else {
        fullTeardown()
        createNewOverlay(incoming)
    }
}
```

---

## Media Type Reference

| Type | Relevant Fields | Ignored Fields | Notes |
| :--- | :--- | :--- | :--- |
| `text` | `title`, `message`, styles | `media_url` | Purely informational text overlay. |
| `image` | `media_url`, `title`, `message`, styles | N/A | Loads image via Coil. Supports transparency. |
| `stream` | `media_url`, `title`, `message`, `muted`, styles | N/A | Renders via ExoPlayer. Supports HLS, RTSP, and progressive MP4. |

---

## Error Codes

| Error String | HTTP Status | Meaning |
| :--- | :--- | :--- |
| `invalid_json` | 400 | The request body is not valid JSON or violates the schema types. |
| `unsupported_media_type` | 400 | `media_type` is invalid or set to the prohibited `bitmap` type. |
| `media_url_required` | 400 | An `image` or `stream` was requested but `media_url` was null or empty. |
| `overlay_permission_denied` | 500 | The TV user has not granted the "Display over other apps" permission. |
| `unknown_error` | 500 | An unhandled exception occurred during processing. |

---

## Schema Change Process

1. **Forward Compatibility**: New fields must be optional in `NotifyRequest.kt` to avoid breaking existing Home Assistant configurations.
2. **Ktor Mapping**: Any new field must be added to `NotifyRequest.kt` with a `@SerialName` if it uses snake_case in JSON.
3. **Idempotency Check**: If a new field affects whether a stream should be restarted, `OverlayManager.shouldUpdateInPlace` must be updated.
