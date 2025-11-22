package com.example.vitruvianredux.domain.model

import com.example.vitruvianredux.util.HardwareDetection
import com.example.vitruvianredux.util.VitruvianModel

sealed class ConnectionState {

    data object Disconnected : ConnectionState()

    data object Disconnecting : ConnectionState()

    data object Scanning : ConnectionState()

    data object Connecting : ConnectionState()

    data class Connected(
        val deviceName: String,
        val deviceAddress: String,
        val hardwareModel: VitruvianModel = HardwareDetection.detectModel(deviceName)
    ) : ConnectionState()

    data class Error(
        val message: String,
        val throwable: Throwable? = null
    ) : ConnectionState()
}
