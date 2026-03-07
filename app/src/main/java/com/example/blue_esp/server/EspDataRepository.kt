package com.example.blue_esp.server

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Typed sensor payload expected from the ESP32.
 * Expected JSON format: {"temp":36.5,"spo2":98,"bpm":72}
 */
@Serializable
private data class SensorPayload(
    val temp: Float? = null,
    val spo2: Int? = null,
    val bpm: Int? = null
)

@Serializable
data class EspState(
    val lastReceivedData: String = "No data",
    val timestamp: Long = 0,
    val connectionStatus: String = "Disconnected",
    val temperature: Float? = null,
    val spO2: Int? = null,
    val heartRate: Int? = null
)

object EspDataRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow(EspState())
    val state: StateFlow<EspState> = _state

    fun updateData(data: String) {
        val parsed = runCatching { json.decodeFromString<SensorPayload>(data) }.getOrNull()
        _state.value = _state.value.copy(
            lastReceivedData = data,
            timestamp = System.currentTimeMillis(),
            temperature = parsed?.temp,
            spO2 = parsed?.spo2,
            heartRate = parsed?.bpm
        )
    }

    fun updateConnectionStatus(status: String) {
        _state.value = _state.value.copy(connectionStatus = status)
    }
}
