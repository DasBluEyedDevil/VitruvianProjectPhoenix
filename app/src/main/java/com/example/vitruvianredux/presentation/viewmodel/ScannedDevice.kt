package com.example.vitruvianredux.presentation.viewmodel

/**
 * Represents a Bluetooth device discovered during scanning.
 */
data class ScannedDevice(
    val name: String,
    val address: String,
    val rssi: Int = 0
)
