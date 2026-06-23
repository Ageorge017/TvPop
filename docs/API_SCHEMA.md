# TvPop API Reference

The TvPop API allows external clients to trigger and control overlays on the Android TV screen. The server runs on port **7979** by default.

For high-level architecture decisions, see [ARCHITECTURE.md](ARCHITECTURE.md). For common troubleshooting, see [KNOWN_ISSUES.md](KNOWN_ISSUES.md).

## Endpoints

### POST `/notify`

Triggers a new overlay or updates an existing one.

- **Content-Type:** `application/json`

#### Request Schema

| Field              | Type    | Required    | Default          | Validation Rules                                        | Notes                                                                    |
| :----------------- | :------ | :---------- | :--------------- | :------------------------------------------------------ | :----------------------------------------------------------------------- |
| `media_type`       | String  | **Yes**     | N/A              | Must be one of: `text`, `image`, `stream`.              | `bitmap` is explicitly rejected.                                         |
| `media_url`        | String  | Conditional | N/A              | Required if `media_type` is `image` or `stream`.        | URL of the image or video stream.                                        |
| `title`            | String  | No          | `""`             | Any string.                                             | Bold header text.                                                        |
| `message`          | String  | No          | `""`             | Any string.                                             | Secondary descriptive text.                                              |
| `duration`         | Int     | No          | `15`             | Seconds.                                                | Time before auto-dismissal. `0` defaults to 15.                          |
| `position`         | String  | No          | `"bottom_right"` | `top_left`, `top_right`, `bottom_left`, `bottom_right`. | Overlay screen position.                                                 |
| `width`            | Int     | No          | `320`            | Width in DP.                                            | Total width of the notification card.                                    |
| `corner_radius`    | Float   | No          | `12.0`           | Radius in DP.                                           | Rounding of the card corners.                                            |
| `background_color` | String  | No          | `"#CC000000"`    | Hex ARGB (e.g., `#RRGGBB` or `#AARRGGBB`).              | Background fill of the card.                                             |
| `title_color`      | String  | No          | `"#FFFFFF"`      | Hex RGB/ARGB.                                           | Color of the title text.                                                 |
| `message_color`    | String  | No          | `"#CCCCCC"`      | Hex RGB/ARGB.                                           | Color of the message text.                                               |
| `muted`            | Boolean | No          | `false`          | `true` or `false`.                                      | Mutes stream audio when `true`. Only meaningful for `stream` media type. |

#### Responses

| HTTP Status          | JSON Body                                             | Condition                                            |
| :------------------- | :---------------------------------------------------- | :--------------------------------------------------- |
| `200 OK`             | `{"ok": true}`                                        | Success. Overlay is shown or updated.                |
| `400 Bad Request`    | `{"ok": false, "error": "invalid_json"}`              | Malformed JSON or type mismatch.                     |
| `400 Bad Request`    | `{"ok": false, "error": "unsupported_media_type"}`    | `media_type` is missing, unknown, or `bitmap`.       |
| `400 Bad Request`    | `{"ok": false, "error": "media_url_required"}`        | `image` or `stream` requested without a `media_url`. |
| `500 Internal Error` | `{"ok": false, "error": "overlay_permission_denied"}` | App lacks "Display over other apps" permission.      |
| `500 Internal Error` | `{"ok": false, "error": "..."}`                       | Unexpected internal server error.                    |

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

| HTTP Status | JSON Body      | Condition                                       |
| :---------- | :------------- | :---------------------------------------------- |
| `200 OK`    | `{"ok": true}` | Always returned, even if no overlay was active. |

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

## MQTT Transport

TvPop also supports MQTT as a parallel transport to HTTP. MQTT and HTTP are both optional and can be enabled independently in the on-device settings screen.

### MQTT Topics

| Topic                                    | Direction | Purpose                                 | QoS | Retained            |
| :--------------------------------------- | :-------- | :-------------------------------------- | :-- | :------------------ |
| `tvpop/notifications/all`                | Subscribe | Broadcast notify events for all devices | 0   | Recommended `false` |
| `tvpop/notifications/<device_id>`        | Subscribe | Device-scoped notify events             | 0   | Recommended `false` |
| `tvpop/notifications/cancel/all`         | Subscribe | Broadcast cancel events                 | 0   | Recommended `false` |
| `tvpop/notifications/cancel/<device_id>` | Subscribe | Device-scoped cancel events             | 0   | Recommended `false` |
| `tvpop/status/<device_id>`               | Publish   | Online/offline status (birth/will)      | 0   | `true`              |

### MQTT Notify Payload

For notify topics, the payload uses the same JSON contract as HTTP `POST /notify`.

Example:

```json
{
  "media_type": "stream",
  "media_url": "http://example.com/camera.m3u8",
  "title": "Front Door",
  "message": "Motion Detected",
  "duration": 15,
  "position": "top_right",
  "muted": true
}
```

Validation rules are the same as HTTP:

1. `media_type` must be one of `text`, `image`, `stream`.
2. `media_url` is required for `image` and `stream`.
3. Overlay permission must be granted.

### MQTT Cancel Payload and Topics

TvPop supports two cancel methods:

1. Publish `{"action":"cancel"}` to either notify topic.
2. Publish any payload to dedicated cancel topics:
3. `tvpop/notifications/cancel/all`
4. `tvpop/notifications/cancel/<device_id>`

Cancel remains idempotent; if no overlay is active, nothing is shown and no error is returned.

### MQTT Delivery Semantics

1. Fire-and-forget (`QoS 0`) is the default and recommended mode.
2. Messages can be dropped while TV/network is offline.
3. Do not retain notify/cancel topics unless stale replay is explicitly desired.
4. Status topic is retained by design and indicates online/offline state only.

### Burst Event Debounce

TvPop applies ingress debounce in `OverlayManager.show()` for both HTTP and MQTT.

1. Default debounce window is `1000` ms.
2. During that window, incoming events are coalesced and only the latest event is rendered.
3. Auto-dismiss countdown starts when the coalesced overlay is actually rendered.
4. After rendering, normal idempotency behavior still applies (in-place update vs replace).

### MQTT Examples

Notify all devices:

```bash
mosquitto_pub -h BROKER_IP -t tvpop/notifications/all -m '{
  "media_type":"text",
  "title":"Doorbell",
  "message":"Someone is at the front door"
}'
```

Notify one device:

```bash
mosquitto_pub -h BROKER_IP -t tvpop/notifications/TV_DEVICE_ID -m '{
  "media_type":"image",
  "media_url":"https://example.com/cam.jpg",
  "title":"Driveway",
  "message":"Motion detected"
}'
```

Cancel via action payload:

```bash
mosquitto_pub -h BROKER_IP -t tvpop/notifications/TV_DEVICE_ID -m '{"action":"cancel"}'
```

Cancel via dedicated cancel topic:

```bash
mosquitto_pub -h BROKER_IP -t tvpop/notifications/cancel/TV_DEVICE_ID -m '{}'
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

| Type     | Relevant Fields                                  | Ignored Fields | Notes                                                           |
| :------- | :----------------------------------------------- | :------------- | :-------------------------------------------------------------- |
| `text`   | `title`, `message`, styles                       | `media_url`    | Purely informational text overlay.                              |
| `image`  | `media_url`, `title`, `message`, styles          | N/A            | Loads image via Coil. Supports transparency.                    |
| `stream` | `media_url`, `title`, `message`, `muted`, styles | N/A            | Renders via ExoPlayer. Supports HLS, RTSP, and progressive MP4. |

---

## Error Codes

| Error String                | HTTP Status | Meaning                                                                 |
| :-------------------------- | :---------- | :---------------------------------------------------------------------- |
| `invalid_json`              | 400         | The request body is not valid JSON or violates the schema types.        |
| `unsupported_media_type`    | 400         | `media_type` is invalid or set to the prohibited `bitmap` type.         |
| `media_url_required`        | 400         | An `image` or `stream` was requested but `media_url` was null or empty. |
| `overlay_permission_denied` | 500         | The TV user has not granted the "Display over other apps" permission.   |
| `unknown_error`             | 500         | An unhandled exception occurred during processing.                      |

---

## Schema Change Process

1. **Forward Compatibility**: New fields must be optional in `NotifyRequest.kt` to avoid breaking existing Home Assistant configurations.
2. **Ktor Mapping**: Any new field must be added to `NotifyRequest.kt` with a `@SerialName` if it uses snake_case in JSON.
3. **Idempotency Check**: If a new field affects whether a stream should be restarted, `OverlayManager.shouldUpdateInPlace` must be updated.
