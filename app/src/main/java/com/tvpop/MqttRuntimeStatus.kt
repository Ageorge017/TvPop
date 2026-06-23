package com.tvpop

enum class MqttConnectionState {
    DISABLED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    DISCONNECTED,
    ERROR
}

data class MqttRuntimeStatus(
    val state: MqttConnectionState = MqttConnectionState.DISABLED,
    val lastError: String? = null,
    val lastUpdatedAtMs: Long = System.currentTimeMillis(),
    val lastConnectedAtMs: Long? = null,
    val reconnectAttempts: Int = 0,
    val subscribedTopics: List<String> = emptyList()
)

object MqttRuntimeStatusStore {
    @Volatile
    private var status: MqttRuntimeStatus = MqttRuntimeStatus()

    fun snapshot(): MqttRuntimeStatus = status

    @Synchronized
    fun update(transform: (MqttRuntimeStatus) -> MqttRuntimeStatus) {
        status = transform(status).copy(lastUpdatedAtMs = System.currentTimeMillis())
    }

    @Synchronized
    fun setState(
        state: MqttConnectionState,
        error: String? = null,
        reconnectAttempts: Int? = null,
        subscribedTopics: List<String>? = null,
        connectedNow: Boolean = false
    ) {
        val current = status
        status = current.copy(
            state = state,
            lastError = error,
            reconnectAttempts = reconnectAttempts ?: current.reconnectAttempts,
            subscribedTopics = subscribedTopics ?: current.subscribedTopics,
            lastConnectedAtMs = if (connectedNow) System.currentTimeMillis() else current.lastConnectedAtMs,
            lastUpdatedAtMs = System.currentTimeMillis()
        )
    }
}
