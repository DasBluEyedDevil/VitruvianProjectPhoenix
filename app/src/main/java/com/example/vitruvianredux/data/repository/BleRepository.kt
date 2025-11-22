package com.example.vitruvianredux.data.repository

import android.bluetooth.le.ScanResult
import com.example.vitruvianredux.data.ble.HandleState
import com.example.vitruvianredux.data.ble.RepNotification
import com.example.vitruvianredux.domain.model.ConnectionState
import com.example.vitruvianredux.domain.model.HeuristicStatistics
import com.example.vitruvianredux.domain.model.WorkoutMetric
import com.example.vitruvianredux.domain.model.WorkoutParameters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository interface for BLE (Bluetooth Low Energy) operations.
 * Provides methods for device scanning, connection management, and workout control.
 */
interface BleRepository {

    /** Current connection state as a StateFlow */
    val connectionState: StateFlow<ConnectionState>

    /** Flow of workout monitoring data */
    val monitorData: Flow<WorkoutMetric>

    /** Flow of rep notification events */
    val repEvents: Flow<RepNotification>

    /** Flow of scanned BLE devices */
    val scannedDevices: Flow<ScanResult>

    /** Current handle state (grabbed/released) */
    val handleState: StateFlow<HandleState>

    /** Heuristic statistics data (nullable) */
    val heuristicData: StateFlow<HeuristicStatistics?>

    /**
     * Start scanning for BLE devices.
     * @return Result indicating success or failure
     */
    suspend fun startScanning(): Result<Unit>

    /**
     * Stop scanning for BLE devices.
     */
    suspend fun stopScanning()

    /**
     * Connect to a BLE device by its address.
     * @param deviceAddress The MAC address of the device to connect to
     * @return Result indicating success or failure
     */
    suspend fun connectToDevice(deviceAddress: String): Result<Unit>

    /**
     * Cancel an ongoing connection attempt.
     */
    suspend fun cancelConnection()

    /**
     * Disconnect from the currently connected device.
     */
    suspend fun disconnect()

    /**
     * Send the initialization sequence to the connected device.
     * @return Result indicating success or failure
     */
    suspend fun sendInitSequence(): Result<Unit>

    /**
     * Start a workout with the given parameters.
     * @param params The workout parameters
     * @return Result indicating success or failure
     */
    suspend fun startWorkout(params: WorkoutParameters): Result<Unit>

    /**
     * Stop the current workout.
     * @return Result indicating success or failure
     */
    suspend fun stopWorkout(): Result<Unit>

    /**
     * Set the LED color scheme on the device.
     * @param schemeIndex The index of the color scheme to use
     * @return Result indicating success or failure
     */
    suspend fun setColorScheme(schemeIndex: Int): Result<Unit>

    /**
     * Test the official app protocol.
     * @return Result indicating success or failure
     */
    suspend fun testOfficialAppProtocol(): Result<Unit>

    /**
     * Enable handle detection mode - starts monitor polling for auto-start.
     */
    fun enableHandleDetection()

    /**
     * Enable Just Lift waiting mode - position-based handle detection.
     */
    fun enableJustLiftWaitingMode()

    /**
     * Restart monitor polling - clears danger zone alarm state on machine.
     */
    fun restartMonitorPolling()

    /**
     * Set whether strict validation is enabled.
     * @param enabled Whether to enable strict validation
     */
    fun setStrictValidationEnabled(enabled: Boolean)
}
