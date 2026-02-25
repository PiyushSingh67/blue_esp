package com.example.blue_esp.server

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
data class EspState(
    val lastReceivedData: String = "No data",
    val timestamp: Long = 0,
    val connectionStatus: String = "Disconnected"
)

object EspDataRepository {
    private val _state = MutableStateFlow(EspState())
    val state: StateFlow<EspState> = _state

    fun updateData(data: String) {
        _state.value = _state.value.copy(
            lastReceivedData = data,
            timestamp = System.currentTimeMillis()
        )
    }

    fun updateConnectionStatus(status: String) {
        _state.value = _state.value.copy(connectionStatus = status)
    }
}
