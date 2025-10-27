package com.example.vitruvianredux.presentation.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vitruvianredux.data.repository.BleRepository
import com.example.vitruvianredux.data.repository.WorkoutRepository
import com.example.vitruvianredux.domain.model.*
import com.example.vitruvianredux.domain.usecase.RepDetectionEngine
import com.example.vitruvianredux.service.WorkoutForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
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

    // Use application context directly instead of storing it
    private fun getContext(): Context = getApplication<Application>().applicationContext

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
    
    // Rep notification tracking (like web app)
    private var lastTopCounter: Int? = null
    private var lastRepCounter: Int? = null

    init {
        Timber.d("MainViewModel initialized")
        
        // Collect monitor data (for position/load display only)
        viewModelScope.launch {
            Timber.d("Starting to collect monitor data...")
            bleRepository.monitorData.collect { metric ->
                Timber.v("Monitor metric received in ViewModel: pos=(${metric.positionA},${metric.positionB})")
                _currentMetric.value = metric
                
                // Collect metrics for history if workout active
                if (_workoutState.value is WorkoutState.Active) {
                    collectMetricForHistory(metric)
                }
            }
        }
        
        // Collect rep notifications from machine (the CORRECT way to count reps!)
        viewModelScope.launch {
            Timber.d("Starting to collect rep notifications...")
            bleRepository.repEvents.collect { repNotification ->
                val state = _workoutState.value
                Timber.d("Rep notification received: top=${repNotification.topCounter}, complete=${repNotification.completeCounter}, state=$state")
                
                if (state is WorkoutState.Active) {
                    handleRepNotification(repNotification)
                } else {
                    Timber.w("Rep notification ignored - workout not active (state=$state)")
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
                Timber.d("ViewModel received scan result: ${scanResult.device.address}")
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
                    Timber.d("Added device to list: ${scannedDevice.name} (${scannedDevice.address}) - Total devices: ${currentDevices.size}")
                } else {
                    Timber.d("Device already in list, skipping: ${scanResult.device.address}")
                }
            }
        }
    }

    /**
     * Handle rep notifications from the machine (like web app does)
     * This is the CORRECT way to count reps - use the machine's own counters!
     */
    private fun handleRepNotification(notification: com.example.vitruvianredux.data.ble.RepNotification) {
        val topCounter = notification.topCounter
        val completeCounter = notification.completeCounter
        
        Timber.d("╔══════════════════════════════════════════════════════════════")
        Timber.d("║ REP NOTIFICATION")
        Timber.d("║ Top Counter: $topCounter | Complete Counter: $completeCounter")
        Timber.d("║ Last Top: $lastTopCounter | Last Complete: $lastRepCounter")
        Timber.d("║ Current Counts: Warmup=${_repCount.value.warmupReps}/${_workoutParameters.value.warmupReps}, Working=${_repCount.value.workingReps}/${_workoutParameters.value.reps}")
        Timber.d("╚══════════════════════════════════════════════════════════════")
        
        // Track top of range (reaching extension)
        if (lastTopCounter == null) {
            lastTopCounter = topCounter
            Timber.d("→ Initialized lastTopCounter to $topCounter")
        } else {
            val topDelta = calculateCounterDelta(lastTopCounter!!, topCounter)
            Timber.d("→ Top delta: $topDelta (from $lastTopCounter to $topCounter)")
            if (topDelta > 0) {
                Timber.d("✓ TOP DETECTED! Counter: $lastTopCounter -> $topCounter")
                lastTopCounter = topCounter
                
                // Check if stopping at top of final rep
                if (_workoutParameters.value.stopAtTop && 
                    !_workoutParameters.value.isJustLift &&
                    _workoutParameters.value.reps > 0 &&
                    _repCount.value.workingReps == _workoutParameters.value.reps - 1) {
                    Timber.d("🏁 Reached top of final rep! Auto-completing workout...")
                    stopWorkout()
                    return
                }
            }
        }
        
        // Track rep complete (bottom of range)
        if (lastRepCounter == null) {
            lastRepCounter = completeCounter
            Timber.d("→ Initialized lastRepCounter to $completeCounter (SKIP first notification)")
            return
        }
        
        val delta = calculateCounterDelta(lastRepCounter!!, completeCounter)
        Timber.d("→ Complete delta: $delta (from $lastRepCounter to $completeCounter)")
        if (delta > 0) {
            Timber.d("✓ BOTTOM DETECTED! Counter: $lastRepCounter -> $completeCounter")
            lastRepCounter = completeCounter
            
            // Increment counters
            val currentWarmup = _repCount.value.warmupReps
            val currentWorking = _repCount.value.workingReps
            val totalReps = currentWarmup + currentWorking + 1
            
            Timber.d("→ Total reps so far: $totalReps (warmup=$currentWarmup, working=$currentWorking)")
            
            if (totalReps <= _workoutParameters.value.warmupReps) {
                // Still in warmup
                val newWarmup = currentWarmup + 1
                _repCount.value = _repCount.value.copy(
                    warmupReps = newWarmup,
                    isWarmupComplete = newWarmup >= _workoutParameters.value.warmupReps
                )
                Timber.d("🔥 WARMUP REP $newWarmup/${_workoutParameters.value.warmupReps} COMPLETE!")
            } else {
                // Working reps
                val newWorking = currentWorking + 1
                _repCount.value = _repCount.value.copy(
                    workingReps = newWorking
                )
                Timber.d("💪 WORKING REP $newWorking/${_workoutParameters.value.reps} COMPLETE!")
                
                // Auto-complete when target reached (not for Just Lift, not when stopping at top)
                if (!_workoutParameters.value.stopAtTop &&
                    !_workoutParameters.value.isJustLift &&
                    _workoutParameters.value.reps > 0 &&
                    newWorking >= _workoutParameters.value.reps) {
                    Timber.d("🏁 Target reps reached! Auto-completing workout...")
                    stopWorkout()
                }
            }
        } else {
            Timber.d("→ No change in complete counter, skipping")
        }
    }
    
    /**
     * Calculate delta between counters, handling u16 wraparound
     */
    private fun calculateCounterDelta(last: Int, current: Int): Int {
        return if (current >= last) {
            current - last
        } else {
            // Wraparound
            0xFFFF - last + current + 1
        }
    }

    private fun collectMetricForHistory(metric: WorkoutMetric) {
        collectedMetrics.add(metric)
    }

    fun startScanning() {
        Timber.d("MainViewModel.startScanning() called")
        viewModelScope.launch {
            _scannedDevices.value = emptyList() // Clear previous scan results
            Timber.d("Cleared previous scan results, calling bleRepository.startScanning()")
            val result = bleRepository.startScanning()
            if (result.isSuccess) {
                Timber.d("Scan started successfully")
            } else {
                Timber.e("Scan failed: ${result.exceptionOrNull()?.message}")
            }
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
        Timber.d("⭐⭐⭐ startWorkout() CALLED! ⭐⭐⭐")
        viewModelScope.launch {
            _workoutState.value = WorkoutState.Initializing

            // Initialize workout session
            currentSessionId = java.util.UUID.randomUUID().toString()
            workoutStartTime = System.currentTimeMillis()
            collectedMetrics.clear()
            
            // Reset rep notification counters BEFORE starting workout
            lastTopCounter = null
            lastRepCounter = null
            _repCount.value = RepCount()
            
            Timber.d("╔════════════════════════════════════════════════")
            Timber.d("║ STARTING COUNTDOWN")
            Timber.d("║ Mode: ${_workoutParameters.value.mode.displayName}")
            Timber.d("║ Target: ${_workoutParameters.value.warmupReps} warmup + ${_workoutParameters.value.reps} working reps")
            Timber.d("╚════════════════════════════════════════════════")

            // Start 5-second countdown to let user get into position
            viewModelScope.launch {
                for (i in 5 downTo 1) {
                    _workoutState.value = WorkoutState.Countdown(i)
                    delay(1000)
                }
                
                // Countdown complete - transition to Active BEFORE sending command
                _workoutState.value = WorkoutState.Active
                
                Timber.d("╔════════════════════════════════════════════════")
                Timber.d("║ COUNTDOWN COMPLETE - SENDING WORKOUT COMMAND")
                Timber.d("╚════════════════════════════════════════════════")
                
                // NOW send the workout command to the machine
                val result = bleRepository.startWorkout(_workoutParameters.value)
                
                if (result.isSuccess) {
                    // Start foreground service
                    WorkoutForegroundService.startWorkoutService(
                        getContext(),
                        _workoutParameters.value.mode.displayName,
                        _workoutParameters.value.reps
                    )
                    
                    Timber.d("Workout command sent successfully! Tracking reps now. Session: $currentSessionId")
                } else {
                    _workoutState.value = WorkoutState.Error(
                        result.exceptionOrNull()?.message ?: "Unknown error"
                    )
                    Timber.e("Failed to start workout: ${result.exceptionOrNull()?.message}")
                }
            }
        }
    }

    fun stopWorkout() {
        viewModelScope.launch {
            val result = bleRepository.stopWorkout()

            if (result.isSuccess) {
                _workoutState.value = WorkoutState.Completed
                
                // Stop foreground service
                WorkoutForegroundService.stopWorkoutService(getContext())
                
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
        val warmup = _repCount.value.warmupReps
        val working = _repCount.value.workingReps
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


