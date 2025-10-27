package com.example.vitruvianredux.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vitruvianredux.data.repository.BleRepository
import com.example.vitruvianredux.data.repository.WorkoutRepository
import com.example.vitruvianredux.domain.model.*
import com.example.vitruvianredux.domain.usecase.RepDetectionEngine
import com.example.vitruvianredux.service.WorkoutForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val bleRepository: BleRepository,
    private val workoutRepository: WorkoutRepository,
    private val repDetectionEngine: RepDetectionEngine
) : AndroidViewModel(application) {

    private val context = application.applicationContext

    val connectionState: StateFlow<ConnectionState> = bleRepository.connectionState

    private val _workoutState = MutableStateFlow<WorkoutState>(WorkoutState.Idle)
    val workoutState: StateFlow<WorkoutState> = _workoutState.asStateFlow()

    private val _currentMetric = MutableStateFlow<WorkoutMetric?>(null)
    val currentMetric: StateFlow<WorkoutMetric?> = _currentMetric.asStateFlow()

    private val _workoutParameters = MutableStateFlow(
        WorkoutParameters(
            mode = WorkoutMode.OldSchool,
            reps = 10,
            weightPerCableKg = 10f,
            progressionKg = 0f,
            isJustLift = false,
            stopAtTop = false,
            warmupReps = 3
        )
    )
    val workoutParameters: StateFlow<WorkoutParameters> = _workoutParameters.asStateFlow()

    private val _repCount = MutableStateFlow(RepCount())
    val repCount: StateFlow<RepCount> = _repCount.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedDevice>> = _scannedDevices.asStateFlow()

    private val _workoutHistory = MutableStateFlow<List<WorkoutSession>>(emptyList())
    val workoutHistory: StateFlow<List<WorkoutSession>> = _workoutHistory.asStateFlow()

    // Current workout tracking
    private var currentSessionId: String? = null
    private var workoutStartTime: Long = 0
    private val collectedMetrics = mutableListOf<WorkoutMetric>()

    init {
        // Collect monitor data and process for rep detection
        viewModelScope.launch {
            bleRepository.monitorData.collect { metric ->
                _currentMetric.value = metric
                
                // Process rep detection when workout is active
                if (_workoutState.value is WorkoutState.Active) {
                    processMetricForReps(metric)
                    collectMetricForHistory(metric)
                }
            }
        }

        // Load recent workout history
        viewModelScope.launch {
            workoutRepository.getRecentSessions(20).collect { sessions ->
                _workoutHistory.value = sessions
            }
        }

        // Collect scanned devices
        viewModelScope.launch {
            bleRepository.scannedDevices.collect { scanResult ->
                // Add to list if not already present
                val currentDevices = _scannedDevices.value.toMutableList()
                val existingDevice = currentDevices.find { it.address == scanResult.device.address }
                if (existingDevice == null) {
                    val scannedDevice = ScannedDevice(
                        name = scanResult.device.name ?: "Unknown",
                        address = scanResult.device.address,
                        rssi = scanResult.rssi
                    )
                    currentDevices.add(scannedDevice)
                    _scannedDevices.value = currentDevices
                    Timber.d("Found device: ${scannedDevice.name} (${scannedDevice.address})")
                }
            }
        }
    }

    private fun processMetricForReps(metric: WorkoutMetric) {
        val repEvent = repDetectionEngine.processPosition(
            metric.positionA,
            metric.positionB,
            metric.ticks
        )

        if (repEvent != null) {
            val (warmup, working) = repDetectionEngine.getRepCounts()
            _repCount.value = RepCount(
                warmupReps = warmup,
                workingReps = working,
                isWarmupComplete = warmup >= _workoutParameters.value.warmupReps
            )

            // Check for auto-stop in Just Lift mode
            if (repDetectionEngine.shouldAutoStop(_workoutParameters.value.isJustLift)) {
                Timber.d("Auto-stopping workout - target reps reached")
                stopWorkout()
            }
        }
    }

    private fun collectMetricForHistory(metric: WorkoutMetric) {
        collectedMetrics.add(metric)
    }

    fun startScanning() {
        viewModelScope.launch {
            _scannedDevices.value = emptyList() // Clear previous scan results
            bleRepository.startScanning()
        }
    }

    fun stopScanning() {
        viewModelScope.launch {
            bleRepository.stopScanning()
        }
    }

    fun connectToDevice(deviceAddress: String) {
        viewModelScope.launch {
            val result = bleRepository.connectToDevice(deviceAddress)
            if (result.isFailure) {
                Timber.e("Failed to connect: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            bleRepository.disconnect()
            _workoutState.value = WorkoutState.Idle
            _currentMetric.value = null
        }
    }

    fun updateWorkoutParameters(params: WorkoutParameters) {
        _workoutParameters.value = params
    }

    fun startWorkout() {
        viewModelScope.launch {
            _workoutState.value = WorkoutState.Initializing

            // Reset rep detection
            repDetectionEngine.reset()
            repDetectionEngine.setWarmupTarget(_workoutParameters.value.warmupReps)
            repDetectionEngine.setTargetReps(_workoutParameters.value.reps)
            _repCount.value = RepCount()

            // Initialize workout session
            currentSessionId = java.util.UUID.randomUUID().toString()
            workoutStartTime = System.currentTimeMillis()
            collectedMetrics.clear()

            val result = bleRepository.startWorkout(_workoutParameters.value)

            if (result.isSuccess) {
                _workoutState.value = WorkoutState.Active
                
                // Start foreground service to keep app alive
                WorkoutForegroundService.startWorkoutService(
                    context,
                    _workoutParameters.value.mode.displayName,
                    _workoutParameters.value.reps
                )
                
                Timber.d("Workout started successfully - Session: $currentSessionId")
            } else {
                _workoutState.value = WorkoutState.Error(
                    result.exceptionOrNull()?.message ?: "Unknown error"
                )
                Timber.e("Failed to start workout: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun stopWorkout() {
        viewModelScope.launch {
            val result = bleRepository.stopWorkout()

            if (result.isSuccess) {
                _workoutState.value = WorkoutState.Completed
                
                // Stop foreground service
                WorkoutForegroundService.stopWorkoutService(context)
                
                Timber.d("Workout stopped successfully")

                // Save workout to database
                saveWorkoutSession()
            } else {
                Timber.e("Failed to stop workout: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    private suspend fun saveWorkoutSession() {
        val sessionId = currentSessionId ?: return
        val params = _workoutParameters.value
        val (warmup, working) = repDetectionEngine.getRepCounts()
        val duration = System.currentTimeMillis() - workoutStartTime

        val session = WorkoutSession(
            id = sessionId,
            timestamp = workoutStartTime,
            mode = params.mode.displayName,
            reps = params.reps,
            weightPerCableKg = params.weightPerCableKg,
            progressionKg = params.progressionKg,
            duration = duration,
            totalReps = warmup + working,
            warmupReps = warmup,
            workingReps = working,
            isJustLift = params.isJustLift,
            stopAtTop = params.stopAtTop
        )

        // Save session
        workoutRepository.saveSession(session)

        // Save metrics (batch insert)
        if (collectedMetrics.isNotEmpty()) {
            workoutRepository.saveMetrics(sessionId, collectedMetrics)
        }

        Timber.d("Saved workout session: $sessionId with ${collectedMetrics.size} metrics")
    }

    fun setColorScheme(schemeIndex: Int) {
        viewModelScope.launch {
            bleRepository.setColorScheme(schemeIndex)
        }
    }

    fun deleteWorkout(sessionId: String) {
        viewModelScope.launch {
            workoutRepository.deleteWorkout(sessionId)
        }
    }

    fun deleteAllWorkouts() {
        viewModelScope.launch {
            workoutRepository.deleteAllWorkouts()
        }
    }
}

/**
 * Scanned device data class
 */
data class ScannedDevice(
    val name: String,
    val address: String,
    val rssi: Int = 0
)


