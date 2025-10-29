package com.example.vitruvianredux.presentation.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vitruvianredux.data.preferences.PreferencesManager
import com.example.vitruvianredux.data.repository.BleRepository
import com.example.vitruvianredux.data.repository.ExerciseRepository
import com.example.vitruvianredux.data.repository.WorkoutRepository
import com.example.vitruvianredux.domain.model.*
import com.example.vitruvianredux.domain.usecase.RepCounterFromMachine
import com.example.vitruvianredux.service.WorkoutForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.ceil

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val bleRepository: BleRepository,
    private val workoutRepository: WorkoutRepository,
    val exerciseRepository: ExerciseRepository,
    private val repCounter: RepCounterFromMachine,
    private val preferencesManager: PreferencesManager
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

    private val _autoStopState = MutableStateFlow(AutoStopUiState())
    val autoStopState: StateFlow<AutoStopUiState> = _autoStopState.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedDevice>> = _scannedDevices.asStateFlow()

    private val _workoutHistory = MutableStateFlow<List<WorkoutSession>>(emptyList())
    val workoutHistory: StateFlow<List<WorkoutSession>> = _workoutHistory.asStateFlow()

    // User preferences - Weight unit
    val weightUnit: StateFlow<WeightUnit> = preferencesManager.preferencesFlow
        .map { it.weightUnit }
        .stateIn(viewModelScope, SharingStarted.Eagerly, WeightUnit.KG)

    // Feature 4: Routine Management
    private val _routines = MutableStateFlow<List<Routine>>(emptyList())
    val routines: StateFlow<List<Routine>> = _routines.asStateFlow()

    private val _loadedRoutine = MutableStateFlow<Routine?>(null)
    val loadedRoutine: StateFlow<Routine?> = _loadedRoutine.asStateFlow()

    private val _currentExerciseIndex = MutableStateFlow(0)
    val currentExerciseIndex: StateFlow<Int> = _currentExerciseIndex.asStateFlow()

    // Feature 1: Dialog state for workout setup
    private val _isWorkoutSetupDialogVisible = MutableStateFlow(false)
    val isWorkoutSetupDialogVisible: StateFlow<Boolean> = _isWorkoutSetupDialogVisible.asStateFlow()

    // Haptic feedback events
    private val _hapticEvents = MutableSharedFlow<HapticEvent>(
        extraBufferCapacity = 10,  // Buffer events to prevent drops
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val hapticEvents: SharedFlow<HapticEvent> = _hapticEvents.asSharedFlow()

    // Current workout tracking
    private var currentSessionId: String? = null
    private var workoutStartTime: Long = 0
    private val collectedMetrics = mutableListOf<WorkoutMetric>()

    // Auto-stop tracking for Just Lift
    private var autoStopStartTime: Long? = null
    private var autoStopTriggered = false
    private var autoStopStopRequested = false

    init {
        Timber.d("MainViewModel initialized")

        // Set up rep event callback for haptic feedback
        repCounter.onRepEvent = { repEvent ->
            viewModelScope.launch {
                // Update UI with new rep count
                val newRepCount = repCounter.getRepCount()
                _repCount.value = newRepCount

                Timber.d(
                    "Rep counters updated: warmup=${newRepCount.warmupReps}/${_workoutParameters.value.warmupReps}, " +
                        "working=${newRepCount.workingReps}/${_workoutParameters.value.reps}"
                )

                // Emit haptic feedback
                when (repEvent.type) {
                    RepType.WARMUP_COMPLETED, RepType.WORKING_COMPLETED -> {
                        Timber.d("Emitting haptic event: REP_COMPLETED")
                        _hapticEvents.emit(HapticEvent.REP_COMPLETED)
                    }
                    RepType.WARMUP_COMPLETE -> {
                        Timber.d("Emitting haptic event: WARMUP_COMPLETE")
                        _hapticEvents.emit(HapticEvent.WARMUP_COMPLETE)
                    }
                    RepType.WORKOUT_COMPLETE -> {
                        Timber.d("Emitting haptic event: WORKOUT_COMPLETE")
                        _hapticEvents.emit(HapticEvent.WORKOUT_COMPLETE)
                    }
                }

                // Check if workout should stop
                if (repCounter.shouldStopWorkout()) {
                    Timber.d("Machine indicates workout should stop - requesting stop")
                    requestAutoStop()
                }
            }
        }

        // Collect monitor data (for position/load display only)
        viewModelScope.launch {
            Timber.d("Starting to collect monitor data...")
            bleRepository.monitorData.collect { metric ->
                Timber.v("Monitor metric received in ViewModel: pos=(${metric.positionA},${metric.positionB})")
                _currentMetric.value = metric
                handleMonitorMetric(metric)
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

        // Load routines
        viewModelScope.launch {
            workoutRepository.getAllRoutines().collect { routinesList ->
                _routines.value = routinesList
            }
        }

        // Collect scanned devices
        viewModelScope.launch {
            bleRepository.scannedDevices.collect { scanResult ->
                Timber.d("ViewModel received scan result: ${scanResult.device.address}")
                val currentDevices = _scannedDevices.value.toMutableList()
                val existingDevice = currentDevices.find { it.address == scanResult.device.address }
                if (existingDevice == null) {
                    @SuppressLint("MissingPermission")
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

    private fun handleMonitorMetric(metric: WorkoutMetric) {
        if (_workoutState.value is WorkoutState.Active) {
            collectMetricForHistory(metric)
            val params = _workoutParameters.value
            if (params.isJustLift) {
                checkAutoStop(metric)
            } else {
                resetAutoStopTimer()
            }
        } else {
            resetAutoStopTimer()
        }
    }

    /**
     * Handle rep notifications provided by the machine.
     */
    private fun handleRepNotification(notification: com.example.vitruvianredux.data.ble.RepNotification) {
        val currentPositions = _currentMetric.value
        repCounter.process(
            topCounter = notification.topCounter,
            completeCounter = notification.completeCounter,
            posA = currentPositions?.positionA ?: 0,
            posB = currentPositions?.positionB ?: 0
        )
        // All other logic (UI update, haptics, auto-stop) handled in onRepEvent callback
    }

    private fun requestAutoStop() {
        if (autoStopStopRequested) return
        autoStopStopRequested = true
        triggerAutoStop()
    }

    private fun triggerAutoStop() {
        autoStopTriggered = true
        if (_workoutParameters.value.isJustLift) {
            _autoStopState.value = _autoStopState.value.copy(progress = 1f, secondsRemaining = 0, isActive = true)
        } else {
            _autoStopState.value = AutoStopUiState()
        }
        stopWorkout()
    }

    private fun checkAutoStop(metric: WorkoutMetric) {
        if (!repCounter.hasMeaningfulRange()) {
            resetAutoStopTimer()
            return
        }

        val inDangerZone = repCounter.isInDangerZone(metric.positionA, metric.positionB)
        if (inDangerZone) {
            val startTime = autoStopStartTime ?: run {
                autoStopStartTime = System.currentTimeMillis()
                Timber.d("Entered Just Lift auto-stop danger zone - starting timer")
                System.currentTimeMillis()
            }

            val elapsed = (System.currentTimeMillis() - startTime) / 1000f
            val progress = (elapsed / AUTO_STOP_DURATION_SECONDS).coerceIn(0f, 1f)
            val remaining = (AUTO_STOP_DURATION_SECONDS - elapsed).coerceAtLeast(0f)

            _autoStopState.value = AutoStopUiState(
                isActive = true,
                progress = progress,
                secondsRemaining = ceil(remaining).toInt()
            )

            if (elapsed >= AUTO_STOP_DURATION_SECONDS) {
                Timber.d("Auto-stop threshold reached in Just Lift - stopping workout")
                triggerAutoStop()
            }
        } else {
            if (autoStopStartTime != null) {
                Timber.d("Left auto-stop danger zone - resetting timer")
            }
            resetAutoStopTimer()
        }
    }

    private fun resetAutoStopTimer() {
        autoStopStartTime = null
        if (!autoStopTriggered) {
            _autoStopState.value = AutoStopUiState()
        }
    }

    private fun collectMetricForHistory(metric: WorkoutMetric) {
        collectedMetrics.add(metric)
    }

    fun startScanning() {
        Timber.d("MainViewModel.startScanning() called")
        viewModelScope.launch {
            _scannedDevices.value = emptyList()
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
            repCounter.reset()
            resetAutoStopState()
        }
    }

    fun updateWorkoutParameters(params: WorkoutParameters) {
        _workoutParameters.value = params
    }

    fun startWorkout() {
        Timber.d("??? startWorkout() CALLED! ???")

        // CRITICAL: Re-set rep event callback to ensure it's active for this workout
        // This fixes the issue where callback set in init block may not persist


        viewModelScope.launch {
            _workoutState.value = WorkoutState.Initializing

            val params = _workoutParameters.value
            val workingTarget = if (params.isJustLift) 0 else params.reps
            repCounter.reset()
            repCounter.configure(
                warmupTarget = params.warmupReps,
                workingTarget = workingTarget,
                isJustLift = params.isJustLift,
                stopAtTop = params.stopAtTop
            )
            _repCount.value = repCounter.getRepCount()
            resetAutoStopState()
            autoStopStopRequested = false

            currentSessionId = java.util.UUID.randomUUID().toString()
            workoutStartTime = System.currentTimeMillis()
            collectedMetrics.clear()

            Timber.d("")
            Timber.d(" STARTING COUNTDOWN")
            Timber.d(" Mode: ${params.mode.displayName}")
            Timber.d(" Target: ${params.warmupReps} warmup + ${params.reps} working reps")
            Timber.d("")

            // Countdown
            for (i in 5 downTo 1) {
                _workoutState.value = WorkoutState.Countdown(i)
                delay(1000)
            }

            _workoutState.value = WorkoutState.Active

            Timber.d("")
            Timber.d(" COUNTDOWN COMPLETE - SENDING WORKOUT COMMAND")
            Timber.d("")

            val result = bleRepository.startWorkout(params)

            if (result.isSuccess) {
                WorkoutForegroundService.startWorkoutService(
                    getContext(),
                    params.mode.displayName,
                    params.reps
                )

                Timber.d("Workout command sent successfully! Tracking reps now. Session: $currentSessionId")

                // Emit haptic feedback for workout start
                _hapticEvents.emit(HapticEvent.WORKOUT_START)
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

                WorkoutForegroundService.stopWorkoutService(getContext())

                Timber.d("Workout stopped successfully")

                // Emit haptic feedback for workout end
                _hapticEvents.emit(HapticEvent.WORKOUT_END)

                saveWorkoutSession()
                repCounter.reset()
                resetAutoStopState()
            } else {
                Timber.e("Failed to stop workout: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    private fun resetAutoStopState() {
        autoStopStartTime = null
        autoStopTriggered = false
        autoStopStopRequested = false
        _autoStopState.value = AutoStopUiState()
    }

    private suspend fun saveWorkoutSession() {
        val sessionId = currentSessionId ?: return
        val params = _workoutParameters.value
        val warmup = _repCount.value.warmupReps
        val working = _repCount.value.workingReps
        val duration = System.currentTimeMillis() - workoutStartTime

        // Store actual per-cable weight from machine output (for tracking progress)
        // Divide totalLoad by 2 to get per-cable resistance
        val actualPerCableWeightKg = if (collectedMetrics.isNotEmpty()) {
            collectedMetrics.maxOf { it.totalLoad } / 2f
        } else {
            params.weightPerCableKg // Fallback to configured if no metrics
        }

        val session = WorkoutSession(
            id = sessionId,
            timestamp = workoutStartTime,
            mode = params.mode.displayName,
            reps = params.reps,
            weightPerCableKg = actualPerCableWeightKg, // Store per-cable weight
            progressionKg = params.progressionKg,
            duration = duration,
            totalReps = warmup + working,
            warmupReps = warmup,
            workingReps = working,
            isJustLift = params.isJustLift,
            stopAtTop = params.stopAtTop
        )

        workoutRepository.saveSession(session)

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

    // Feature 2: Weight Unit Management
    fun setWeightUnit(unit: WeightUnit) {
        viewModelScope.launch {
            preferencesManager.setWeightUnit(unit)
        }
    }

    /**
     * Convert weight from KG to display unit
     */
    fun kgToDisplay(kg: Float, unit: WeightUnit): Float =
        if (unit == WeightUnit.LB) kg * 2.20462f else kg

    /**
     * Convert weight from display unit to KG
     */
    fun displayToKg(display: Float, unit: WeightUnit): Float =
        if (unit == WeightUnit.LB) display / 2.20462f else display

    /**
     * Format weight for display with unit suffix
     */
    fun formatWeight(kg: Float, unit: WeightUnit): String {
        val displayValue = kgToDisplay(kg, unit)
        return "%.1f %s".format(displayValue, unit.name.lowercase())
    }

    // Feature 3: Reset for New Workout
    /**
     * Reset the workout state to Idle to allow starting a new workout
     * without disconnecting from the device
     */
    fun resetForNewWorkout() {
        _workoutState.value = WorkoutState.Idle
        _repCount.value = RepCount()
        resetAutoStopState()
        Timber.d("Reset for new workout - state returned to Idle")
    }

    // Feature 1: Dialog Management
    /**
     * Show the workout setup dialog
     */
    fun showWorkoutSetupDialog() {
        _isWorkoutSetupDialogVisible.value = true
    }

    /**
     * Hide the workout setup dialog
     */
    fun hideWorkoutSetupDialog() {
        _isWorkoutSetupDialogVisible.value = false
    }

    // Feature 4: Routine Management Methods

    /**
     * Save a new routine or update an existing one
     */
    fun saveRoutine(routine: Routine) {
        viewModelScope.launch {
            val result = workoutRepository.saveRoutine(routine)
            if (result.isSuccess) {
                Timber.d("Routine saved: ${routine.name}")
            } else {
                Timber.e("Failed to save routine: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    /**
     * Update an existing routine
     */
    fun updateRoutine(routine: Routine) {
        viewModelScope.launch {
            val result = workoutRepository.updateRoutine(routine)
            if (result.isSuccess) {
                Timber.d("Routine updated: ${routine.name}")
            } else {
                Timber.e("Failed to update routine: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    /**
     * Delete a routine
     */
    fun deleteRoutine(routineId: String) {
        viewModelScope.launch {
            val result = workoutRepository.deleteRoutine(routineId)
            if (result.isSuccess) {
                Timber.d("Routine deleted: $routineId")
            } else {
                Timber.e("Failed to delete routine: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    /**
     * Load a routine for workout - sets parameters from first exercise
     */
    fun loadRoutine(routine: Routine) {
        if (routine.exercises.isEmpty()) {
            Timber.w("Cannot load routine with no exercises")
            return
        }

        _loadedRoutine.value = routine
        _currentExerciseIndex.value = 0

        // Load parameters from first exercise
        val firstExercise = routine.exercises[0]
        updateWorkoutParameters(
            WorkoutParameters(
                mode = _workoutParameters.value.mode, // Keep current mode
                reps = firstExercise.reps,
                weightPerCableKg = firstExercise.weightPerCableKg,
                progressionKg = firstExercise.progressionKg,
                isJustLift = _workoutParameters.value.isJustLift,
                stopAtTop = _workoutParameters.value.stopAtTop,
                warmupReps = _workoutParameters.value.warmupReps
            )
        )

        // Mark routine as used
        viewModelScope.launch {
            workoutRepository.markRoutineUsed(routine.id)
        }

        Timber.d("Loaded routine: ${routine.name}, exercise 1/${routine.exercises.size}: ${firstExercise.exercise.displayName}")
    }

    /**
     * Move to next exercise in loaded routine
     */
    fun nextExercise() {
        val routine = _loadedRoutine.value ?: return
        val currentIndex = _currentExerciseIndex.value

        if (currentIndex < routine.exercises.size - 1) {
            val nextIndex = currentIndex + 1
            _currentExerciseIndex.value = nextIndex

            val nextExercise = routine.exercises[nextIndex]
            updateWorkoutParameters(
                _workoutParameters.value.copy(
                    reps = nextExercise.reps,
                    weightPerCableKg = nextExercise.weightPerCableKg,
                    progressionKg = nextExercise.progressionKg
                )
            )

            Timber.d("Moved to exercise ${nextIndex + 1}/${routine.exercises.size}: ${nextExercise.exercise.displayName}")
        } else {
            Timber.d("Last exercise in routine completed")
            clearLoadedRoutine()
        }
    }

    /**
     * Move to previous exercise in loaded routine
     */
    fun previousExercise() {
        val routine = _loadedRoutine.value ?: return
        val currentIndex = _currentExerciseIndex.value

        if (currentIndex > 0) {
            val prevIndex = currentIndex - 1
            _currentExerciseIndex.value = prevIndex

            val prevExercise = routine.exercises[prevIndex]
            updateWorkoutParameters(
                _workoutParameters.value.copy(
                    reps = prevExercise.reps,
                    weightPerCableKg = prevExercise.weightPerCableKg,
                    progressionKg = prevExercise.progressionKg
                )
            )

            Timber.d("Moved to exercise ${prevIndex + 1}/${routine.exercises.size}: ${prevExercise.exercise.displayName}")
        }
    }

    /**
     * Clear loaded routine
     */
    fun clearLoadedRoutine() {
        _loadedRoutine.value = null
        _currentExerciseIndex.value = 0
        Timber.d("Cleared loaded routine")
    }

    /**
     * Get current exercise from loaded routine
     */
    fun getCurrentExercise(): RoutineExercise? {
        val routine = _loadedRoutine.value ?: return null
        val index = _currentExerciseIndex.value
        return routine.exercises.getOrNull(index)
    }

    companion object {
        private const val AUTO_STOP_DURATION_SECONDS = 5f
    }
}

/**
 * UI state for the Just Lift auto-stop timer.
 */
data class AutoStopUiState(
    val isActive: Boolean = false,
    val progress: Float = 0f,
    val secondsRemaining: Int = 5
)

/**
 * Scanned device data class
 */
data class ScannedDevice(
    val name: String,
    val address: String,
    val rssi: Int = 0
)


