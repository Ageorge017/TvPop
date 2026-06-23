package com.tvpop

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import com.tvpop.config.AppPreferences
import com.tvpop.model.NotifyRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.eclipse.paho.client.mqttv3.MqttMessage
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.math.min
import kotlin.random.Random
import java.util.concurrent.atomic.AtomicBoolean

class MqttTransport(
    context: Context,
    private val overlayManager: OverlayManager,
    private val prefs: AppPreferences,
    private val scope: CoroutineScope
) {
    private val logTag = "TvPop"
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }

    private val startStopMutex = Mutex()
    private val isStarted = AtomicBoolean(false)
    private val jitter = Random(System.currentTimeMillis())
    private val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)

    @Volatile
    private var reconnectJob: Job? = null

    @Volatile
    private var reconnectAttempt = 0

    @Volatile
    private var networkCallbackRegistered = false

    @Volatile
    private var client: MqttAsyncClient? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (!isStarted.get()) {
                return
            }

            Log.i(logTag, "Network available; triggering MQTT reconnect")
            connect(resetBackoff = true)
        }
    }

    suspend fun start() {
        startStopMutex.withLock {
            if (isStarted.get()) {
                return
            }
            isStarted.set(true)
        }

        MqttRuntimeStatusStore.setState(
            state = MqttConnectionState.CONNECTING,
            reconnectAttempts = 0,
            subscribedTopics = emptyList()
        )
        registerNetworkCallback()
        connect(resetBackoff = true)
    }

    suspend fun stop() {
        startStopMutex.withLock {
            isStarted.set(false)
            reconnectJob?.cancel()
            reconnectJob = null
            reconnectAttempt = 0
            unregisterNetworkCallback()

            val localClient = client
            client = null

            if (localClient == null) {
                MqttRuntimeStatusStore.setState(
                    state = MqttConnectionState.DISABLED,
                    reconnectAttempts = 0,
                    subscribedTopics = emptyList()
                )
                return
            }

            try {
                if (localClient.isConnected) {
                    publishStatus(localClient, online = false)
                    localClient.disconnect().waitForCompletion(3000)
                }
            } catch (t: Throwable) {
                Log.e(logTag, "MQTT disconnect failed", t)
            }

            try {
                localClient.close()
            } catch (t: Throwable) {
                Log.e(logTag, "MQTT close failed", t)
            }

            MqttRuntimeStatusStore.setState(
                state = MqttConnectionState.DISABLED,
                reconnectAttempts = 0,
                subscribedTopics = emptyList()
            )
        }
    }

    private fun connect(resetBackoff: Boolean) {
        scope.launch(Dispatchers.IO) {
            if (!isStarted.get()) {
                return@launch
            }

            if (resetBackoff) {
                reconnectAttempt = 0
            }

            if (client?.isConnected == true) {
                return@launch
            }

            MqttRuntimeStatusStore.setState(
                state = MqttConnectionState.CONNECTING,
                reconnectAttempts = reconnectAttempt
            )

            val connected = connectOnce()
            if (!connected) {
                scheduleReconnect()
            }
        }
    }

    private suspend fun connectOnce(): Boolean {
        val brokerUrl = prefs.mqttBrokerUrl
        if (brokerUrl.isBlank()) {
            val error = "MQTT enabled but broker URL is blank"
            Log.w(logTag, error)
            MqttRuntimeStatusStore.setState(
                state = MqttConnectionState.ERROR,
                error = error,
                reconnectAttempts = reconnectAttempt
            )
            return false
        }

        val existingClient = client
        if (existingClient?.isConnected == true) {
            return true
        }

        if (existingClient != null) {
            try {
                existingClient.close()
            } catch (_: Throwable) {
            }
            client = null
        }

        val clientId = "tvpop_${prefs.mqttDeviceId.ifBlank { UUID.randomUUID().toString() }}"
        val localClient = try {
            MqttAsyncClient(
                brokerUrl,
                clientId,
                MemoryPersistence()
            )
        } catch (t: Throwable) {
            val error = "MQTT client init failed: ${t.message.orEmpty()}"
            Log.e(logTag, "MQTT client init failed", t)
            MqttRuntimeStatusStore.setState(
                state = MqttConnectionState.ERROR,
                error = error,
                reconnectAttempts = reconnectAttempt
            )
            return false
        }

        localClient.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                Log.i(logTag, "MQTT connected (reconnect=$reconnect, broker=$serverURI)")
                reconnectAttempt = 0
                MqttRuntimeStatusStore.setState(
                    state = MqttConnectionState.CONNECTED,
                    error = null,
                    reconnectAttempts = 0,
                    connectedNow = true,
                    subscribedTopics = configuredTopics()
                )
            }

            override fun connectionLost(cause: Throwable?) {
                val error = cause?.message ?: "Connection lost"
                Log.e(logTag, "MQTT connection lost", cause)
                MqttRuntimeStatusStore.setState(
                    state = MqttConnectionState.DISCONNECTED,
                    error = error,
                    reconnectAttempts = reconnectAttempt
                )
                scheduleReconnect()
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                val topicName = topic.orEmpty()
                if (topicName == prefs.mqttCancelAllTopic || topicName == prefs.mqttCancelDeviceTopic) {
                    scope.launch(Dispatchers.IO) {
                        handleCancel(topicName)
                    }
                    return
                }

                val payload = message?.payload?.toString(StandardCharsets.UTF_8).orEmpty()
                if (payload.isBlank()) {
                    Log.w(logTag, "MQTT message ignored: empty payload")
                    return
                }
                scope.launch(Dispatchers.IO) {
                    handleMessage(topicName, payload)
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
            }
        })

        val connectOptions = MqttConnectOptions().apply {
            isCleanSession = prefs.mqttCleanSession
            keepAliveInterval = prefs.mqttKeepAliveSeconds
            isAutomaticReconnect = false

            val username = prefs.mqttUsername
            if (username.isNotBlank()) {
                userName = username
                password = prefs.mqttPassword.toCharArray()
            }

            val willPayload = buildStatusPayload(online = false)
            setWill(
                prefs.mqttStatusTopic,
                willPayload.toByteArray(StandardCharsets.UTF_8),
                0,
                true
            )
        }

        return try {
            localClient.connect(connectOptions).waitForCompletion(10_000)
            client = localClient

            if (!subscribe(localClient)) {
                return false
            }

            publishStatus(localClient, online = true)
            MqttRuntimeStatusStore.setState(
                state = MqttConnectionState.CONNECTED,
                error = null,
                reconnectAttempts = reconnectAttempt,
                connectedNow = true,
                subscribedTopics = configuredTopics()
            )
            reconnectJob?.cancel()
            reconnectJob = null
            true
        } catch (t: Throwable) {
            val error = "MQTT connect failed: ${t.message.orEmpty()}"
            Log.e(logTag, "MQTT connect failed", t)
            MqttRuntimeStatusStore.setState(
                state = MqttConnectionState.ERROR,
                error = error,
                reconnectAttempts = reconnectAttempt
            )
            try {
                localClient.close()
            } catch (_: Throwable) {
            }
            false
        }
    }

    private fun subscribe(localClient: MqttAsyncClient): Boolean {
        if (!isStarted.get() || !localClient.isConnected) {
            return false
        }

        val topics = configuredTopics().toTypedArray()
        val qos = intArrayOf(0, 0, 0, 0)

        return try {
            localClient.subscribe(topics, qos).waitForCompletion(10_000)
            Log.i(logTag, "MQTT subscribed to ${topics.joinToString()}")
            true
        } catch (t: Throwable) {
            Log.e(logTag, "MQTT subscribe failed", t)
            MqttRuntimeStatusStore.setState(
                state = MqttConnectionState.ERROR,
                error = "MQTT subscribe failed: ${t.message.orEmpty()}",
                reconnectAttempts = reconnectAttempt
            )
            false
        }
    }

    private suspend fun handleMessage(topic: String, payload: String) {
        val parsed: JsonObject = try {
            json.parseToJsonElement(payload).jsonObject
        } catch (t: Throwable) {
            Log.e(logTag, "MQTT invalid JSON payload on topic=$topic", t)
            return
        }

        val action = parsed["action"]?.jsonPrimitive?.content?.lowercase().orEmpty()
        if (action == "cancel") {
            withContext(Dispatchers.Main) {
                try {
                    overlayManager.cancel()
                } catch (t: Throwable) {
                    Log.e(logTag, "MQTT cancel failed", t)
                }
            }
            return
        }

        val request = try {
            json.decodeFromJsonElement<NotifyRequest>(parsed)
        } catch (t: Throwable) {
            Log.e(logTag, "MQTT notify decode failed", t)
            return
        }

        if (!isValidNotifyRequest(request)) {
            return
        }

        if (!overlayManager.hasOverlayPermission()) {
            Log.e(logTag, "MQTT overlay permission denied")
            return
        }

        withContext(Dispatchers.Main) {
            try {
                overlayManager.show(request)
            } catch (t: Throwable) {
                Log.e(logTag, "MQTT overlay show failed", t)
            }
        }
    }

    private suspend fun handleCancel(topic: String) {
        withContext(Dispatchers.Main) {
            try {
                overlayManager.cancel()
                Log.i(logTag, "MQTT cancel received on topic=$topic")
            } catch (t: Throwable) {
                Log.e(logTag, "MQTT cancel failed", t)
            }
        }
    }

    private fun isValidNotifyRequest(req: NotifyRequest): Boolean {
        val mediaType = req.mediaType
        if (mediaType !in setOf("text", "image", "stream")) {
            Log.e(logTag, "MQTT unsupported media type: $mediaType")
            return false
        }

        if ((mediaType == "image" || mediaType == "stream") && req.mediaUrl.isNullOrBlank()) {
            Log.e(logTag, "MQTT media_url required for media type: $mediaType")
            return false
        }

        return true
    }

    private fun publishStatus(localClient: MqttAsyncClient, online: Boolean) {
        if (!localClient.isConnected) {
            return
        }

        val payload = buildStatusPayload(online)
        val message = MqttMessage(payload.toByteArray(StandardCharsets.UTF_8)).apply {
            qos = 0
            isRetained = true
        }

        try {
            localClient.publish(prefs.mqttStatusTopic, message).waitForCompletion(3000)
        } catch (t: Throwable) {
            Log.e(logTag, "MQTT status publish failed", t)
        }
    }

    private fun buildStatusPayload(online: Boolean): String {
        val jsonPayload = buildJsonObject {
            put("online", JsonPrimitive(online))
            put("device_id", JsonPrimitive(prefs.mqttDeviceId))
            put("timestamp_ms", JsonPrimitive(System.currentTimeMillis()))
        }
        return jsonPayload.toString()
    }

    private fun configuredTopics(): List<String> {
        return listOf(
            prefs.mqttAllTopic,
            prefs.mqttDeviceTopic,
            prefs.mqttCancelAllTopic,
            prefs.mqttCancelDeviceTopic
        )
    }

    private fun registerNetworkCallback() {
        if (networkCallbackRegistered) {
            return
        }

        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
            networkCallbackRegistered = true
        } catch (t: Throwable) {
            Log.e(logTag, "Failed to register network callback", t)
        }
    }

    private fun unregisterNetworkCallback() {
        if (!networkCallbackRegistered) {
            return
        }

        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (t: Throwable) {
            Log.e(logTag, "Failed to unregister network callback", t)
        } finally {
            networkCallbackRegistered = false
        }
    }

    private fun scheduleReconnect() {
        if (!isStarted.get()) {
            return
        }

        if (reconnectJob?.isActive == true) {
            return
        }

        reconnectJob = scope.launch(Dispatchers.IO) {
            while (isActive && isStarted.get()) {
                if (client?.isConnected == true) {
                    reconnectJob = null
                    return@launch
                }

                val waitMs = computeBackoffDelayMs(reconnectAttempt)
                MqttRuntimeStatusStore.setState(
                    state = MqttConnectionState.RECONNECTING,
                    reconnectAttempts = reconnectAttempt
                )
                delay(waitMs)

                if (!isStarted.get()) {
                    reconnectJob = null
                    return@launch
                }

                val connected = connectOnce()
                if (connected) {
                    reconnectAttempt = 0
                    reconnectJob = null
                    return@launch
                }

                reconnectAttempt += 1
            }

            reconnectJob = null
        }
    }

    private fun computeBackoffDelayMs(attempt: Int): Long {
        val cappedAttempt = min(attempt, 5)
        val base = 1000L * (1L shl cappedAttempt)
        val bounded = min(base, 30_000L)
        val jitterMs = jitter.nextLong(0L, 500L)
        return bounded + jitterMs
    }
}
