package com.example.vitruvianredux.presentation.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vitruvianredux.data.preferences.PreferencesManager
import com.example.vitruvianredux.data.repository.BleRepository
import com.example.vitruvianredux.data.repository.ExerciseRepository
import com.example.vitruvianredux.data.repository.PersonalRecordRepository
import com.example.vitruvianredux.data.repository.WorkoutRepository
import com.example.vitruvianredux.domain.model.*
import com.example.vitruvianredux.domain.usecase.RepCounterFromMachine
import com.example.vitruvianredux.service.WorkoutForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.math.ceil

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val bleRepository: BleRepository,
    private val workoutRepository: WorkoutRepository,
    val exerciseRepository: ExerciseRepository,
    val personalRecordRepository: PersonalRecordRepository,
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
            workoutType = WorkoutType.Program(ProgramMode.OldSchool),
            reps = 10,
            weightPerCableKg = 10f,
            progressionRegressionKg = 0f,
            isJustLift = false,
            stopAtTop = false,  // User preference - can be toggled in settings
            warmupReps = 3
        )
    )
    val workoutParameters: StateFlow<WorkoutParameters> = _workoutParameters.asStateFlow()

    private val _repCount = MutableStateFlow(RepCount())
    val repCount: StateFlow<RepCount> = _repCount.asStateFlow()

    private val _autoStopState = MutableStateFlow(AutoStopUiState())
    val autoStopState: StateFlow<AutoStopUiState> = _autoStopState.asStateFlow()

    private val _autoStartCountdown = MutableStateFlow<Int?>(null)
    val autoStartCountdown: StateFlow<Int?> = _autoStartCountdown.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedDevice>> = _scannedDevices.asStateFlow()

    private val _workoutHistory = MutableStateFlow<List<WorkoutSession>>(emptyList())
    val workoutHistory: StateFlow<List<WorkoutSession>> = _workoutHistory.asStateFlow()

    // PR Celebration Events
    private val _prCelebrationEvent = MutableSharedFlow<PRCelebrationEvent>()
    val prCelebrationEvent: SharedFlow<PRCelebrationEvent> = _prCelebrationEvent.asSharedFlow()

    // User preferences
    val userPreferences: StateFlow<UserPreferences> = preferencesManager.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())
    
    val weightUnit: StateFlow<WeightUnit> = userPreferences
        .map { it.weightUnit }
        .stateIn(viewModelScope, SharingStarted.Eagerly, WeightUnit.KG)

    val stopAtTop: StateFlow<Boolean> = userPreferences
        .map { it.stopAtTop }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Feature 4: Routine Management
    private val _routines = MutableStateFlow<List<Routine>>(emptyList())
    val routines: StateFlow<List<Routine>> = _routines.asStateFlow()

    private val _loadedRoutine = MutableStateFlow<Routine?>(null)
    val loadedRoutine: StateFlow<Routine?> = _loadedRoutine.asStateFlow()

    private val _currentExerciseIndex = MutableStateFlow(0)
    val currentExerciseIndex: StateFlow<Int> = _currentExerciseIndex.asStateFlow()

    private val _currentSetIndex = MutableStateFlow(0)
    val currentSetIndex: StateFlow<Int> = _currentSetIndex.asStateFlow()

    // Weekly Programs
    val weeklyPrograms: StateFlow<List<com.example.vitruvianredux.data.local.WeeklyProgramWithDays>> =
        workoutRepository.getAllPrograms()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val activeProgram: StateFlow<com.example.vitruvianredux.data.local.WeeklyProgramWithDays?> =
        workoutRepository.getActiveProgram()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null
            )

    // Personal Records
    @Suppress("unused")
    val personalBests: StateFlow<List<com.example.vitruvianredux.data.local.PersonalRecordEntity>> =
        workoutRepository.getAllPersonalRecords()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    // ========== Stats for HomeScreen ==========

    // All workout sessions for stats calculation
    val allWorkoutSessions: StateFlow<List<WorkoutSession>> =
        workoutRepository.getAllSessions()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    // All personal records for analytics
    val allPersonalRecords: StateFlow<List<PersonalRecord>> =
        personalRecordRepository.getAllPRsGrouped()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    // Total completed workouts
    val completedWorkouts: StateFlow<Int?> = allWorkoutSessions.map { sessions ->
        sessions.size.takeIf { it > 0 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Current workout streak (consecutive days)
    val workoutStreak: StateFlow<Int?> = allWorkoutSessions.map { sessions ->
        if (sessions.isEmpty()) {
            return@map null
        }

        val workoutDates = sessions
            .map { Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate() }
            .distinct()
            .sortedDescending()

        // Check if streak is current (workout today or yesterday)
        val today = LocalDate.now()
        val lastWorkoutDate = workoutDates.first()
        if (lastWorkoutDate.isBefore(today.minusDays(1))) {
            return@map null // Streak broken
        }

        var streak = 1
        for (i in 1 until workoutDates.size) {
            if (workoutDates[i] == workoutDates[i-1].minusDays(1)) {
                streak++
            } else {
                break // Found a gap
            }
        }
        streak
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Progress percentage (volume change between last two workouts)
    val progressPercentage: StateFlow<Int?> = allWorkoutSessions.map { sessions ->
        if (sessions.size < 2) {
            return@map null
        }

        // Sessions are already sorted by timestamp DESC from repository
        val latestSession = sessions[0]
        val previousSession = sessions[1]

        // Volume = (Total Weight) * (Total Reps). Weight is per cable, so multiply by 2.
        val latestVolume = (latestSession.weightPerCableKg * 2) * latestSession.totalReps
        val previousVolume = (previousSession.weightPerCableKg * 2) * previousSession.totalReps

        if (previousVolume <= 0f) {
            return@map null
        }

        val percentageChange = ((latestVolume - previousVolume) / previousVolume) * 100
        percentageChange.toInt()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Feature 1: Dialog state for workout setup
    private val _isWorkoutSetupDialogVisible = MutableStateFlow(false)
    @Suppress("unused")
    val isWorkoutSetupDialogVisible: StateFlow<Boolean> = _isWorkoutSetupDialogVisible.asStateFlow()

    // Auto-connect UI state
    private val _isAutoConnecting = MutableStateFlow(false)
    val isAutoConnecting: StateFlow<Boolean> = _isAutoConnecting.asStateFlow()

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    // Pending callback for after connection completes
    private var _pendingConnectionCallback: (() -> Unit)? = null

    // Haptic feedback events
    private val _hapticEvents = MutableSharedFlow<HapticEvent>(
        extraBufferCapacity = 10,  // Buffer events to prevent drops
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val hapticEvents: SharedFlow<HapticEvent> = _hapticEvents.asSharedFlow()

    // Connection loss detection (Issue #43)
    private val _connectionLostDuringWorkout = MutableStateFlow(false)
    val connectionLostDuringWorkout: StateFlow<Boolean> = _connectionLostDuringWorkout.asStateFlow()

    // Current workout tracking
    private var currentSessionId: String? = null
    private var workoutStartTime: Long = 0
    private val collectedMetrics = mutableListOf<WorkoutMetric>()

    // Auto-stop tracking for Just Lift
    private var autoStopStartTime: Long? = null
    private val autoStopTriggered = AtomicBoolean(false)
    private val autoStopStopRequested = AtomicBoolean(false)

    private var autoStartJob: Job? = null
    private var restTimerJob: Job? = null

    // Store collection jobs for monitor and rep event flows so they can be cancelled during stop
    private var monitorDataCollectionJob: Job? = null
    private var repEventsCollectionJob: Job? = null

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
        monitorDataCollectionJob = viewModelScope.launch {
            Timber.d("Starting to collect monitor data...")
            bleRepository.monitorData.collect { metric ->
                Timber.v("Monitor metric received in ViewModel: pos=(${metric.positionA},${metric.positionB})")
                _currentMetric.value = metric
                handleMonitorMetric(metric)
            }
        }

        // Collect rep notifications from machine (the CORRECT way to count reps!)
        repEventsCollectionJob = viewModelScope.launch {
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

        // Collect handle state for auto-start/stop
        viewModelScope.launch {
            bleRepository.handleState.collect { state ->
                Timber.d("Handle state received in ViewModel: $state, useAutoStart=${workoutParameters.value.useAutoStart}, workoutState=${workoutState.value}")
                if (workoutParameters.value.useAutoStart && workoutState.value is WorkoutState.Idle) {
                    when (state) {
                        com.example.vitruvianredux.data.ble.HandleState.Grabbed -> {
                            Timber.d("Handles grabbed! Starting auto-start timer")
                            startAutoStartTimer()
                        }
                        com.example.vitruvianredux.data.ble.HandleState.Released -> {
                            Timber.d("Handles released! Canceling auto-start timer")
                            cancelAutoStartTimer()
                        }
                        else -> { /* Do nothing */ }
                    }
                }
            }
        }

        // Monitor connection state for loss detection during workouts (Issue #43)
        viewModelScope.launch {
            connectionState.collect { connState ->
                val currentWorkoutState = _workoutState.value

                // Check if we lost connection during an active workout
                val isWorkoutActive = currentWorkoutState is WorkoutState.Active ||
                        currentWorkoutState is WorkoutState.Countdown ||
                        currentWorkoutState is WorkoutState.Resting ||
                        currentWorkoutState is WorkoutState.Initializing

                val isDisconnected = connState is ConnectionState.Disconnected ||
                        connState is ConnectionState.Error

                if (isWorkoutActive && isDisconnected) {
                    Timber.e("⚠️ CONNECTION LOST DURING WORKOUT! State: $currentWorkoutState, Connection: $connState")
                    _connectionLostDuringWorkout.value = true

                    // Emit haptic alert for connection loss
                    _hapticEvents.emit(HapticEvent.ERROR)
                } else if (!isWorkoutActive && _connectionLostDuringWorkout.value) {
                    // Reset the flag when workout ends
                    _connectionLostDuringWorkout.value = false
                }
            }
        }
    }

    private fun cancelAutoStartTimer() {
        autoStartJob?.cancel()
        autoStartJob = null
        _autoStartCountdown.value = null
    }

    private fun startAutoStartTimer() {
        if (autoStartJob != null || workoutState.value !is WorkoutState.Idle) {
            Timber.d("Auto-start timer NOT started: autoStartJob=$autoStartJob, workoutState=${workoutState.value}")
            return
        }

        Timber.d("Auto-start timer STARTING! (1.2 seconds)")
        autoStartJob = viewModelScope.launch {
            // Official app: 1.2 second hold timer with visible countdown
            _autoStartCountdown.value = 1  // Show "1" during hold
            delay(1200)  // Official app: 1200ms hold
            _autoStartCountdown.value = null
            Timber.d("Auto-start hold complete (1.2s)! Starting workout...")
            // Just Lift mode: Pass isJustLiftMode=true to ensure flag is preserved
            startWorkout(isJustLiftMode = true)
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
        if (autoStopStopRequested.getAndSet(true)) return
        triggerAutoStop()
    }

    private fun triggerAutoStop() {
        autoStopTriggered.set(true)
        if (_workoutParameters.value.isJustLift) {
            _autoStopState.value = _autoStopState.value.copy(progress = 1f, secondsRemaining = 0, isActive = true)
        } else {
            _autoStopState.value = AutoStopUiState()
        }
        handleSetCompletion()
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
        if (!autoStopTriggered.get()) {
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
            } else {
                // Wait for connection to be established
                connectionState
                    .filter { it is ConnectionState.Connected }
                    .take(1)
                    .collect {
                        // Call pending callback if any
                        _pendingConnectionCallback?.invoke()
                        _pendingConnectionCallback = null
                    }
            }
        }
    }

    /**
     * Ensures BLE connection before proceeding with callback.
     * If already connected, immediately calls onConnected.
     * If not connected, starts scan and shows device selection dialog.
     */
    fun ensureConnection(onConnected: () -> Unit, onFailed: () -> Unit = {}) {
        viewModelScope.launch {
            when (connectionState.value) {
                is ConnectionState.Connected -> {
                    onConnected()
                }
                else -> {
                    _isAutoConnecting.value = true
                    _connectionError.value = null

                    // Start scanning
                    startScanning()

                    // Wait for first discovered device (with timeout)
                    val found = withTimeoutOrNull(30000) {
                        scannedDevices
                            .filter { it.isNotEmpty() }
                            .take(1)
                            .collect { devices ->
                                stopScanning()
                                val device = devices.firstOrNull()
                                if (device != null) {
                                    _pendingConnectionCallback = onConnected
                                    connectToDevice(device.address)

                                    // Wait for Connected state with timeout (15 seconds)
                                    val connected = withTimeoutOrNull(15000) {
                                        connectionState
                                            .filter { it is ConnectionState.Connected }
                                            .take(1)
                                            .collect { }
                                        true // Return true if we got Connected
                                    }

                                    _isAutoConnecting.value = false
                                    if (connected == true) {
                                        onConnected()
                                    } else {
                                        _pendingConnectionCallback = null  // Clear callback on failure
                                        _connectionError.value = "Connection failed"
                                        onFailed()
                                    }
                                } else {
                                    _pendingConnectionCallback = null  // Clear callback on failure
                                    _isAutoConnecting.value = false
                                    _connectionError.value = "No device found"
                                    onFailed()
                                }
                            }
                    }

                    if (found == null) {
                        // Timeout
                        _pendingConnectionCallback = null  // Clear callback on timeout
                        stopScanning()
                        _isAutoConnecting.value = false
                        _connectionError.value = "Scan timeout - no device found"
                        onFailed()
                    }
                }
            }
        }
    }

    fun clearConnectionError() {
        _connectionError.value = null
    }

    fun dismissConnectionLostAlert() {
        _connectionLostDuringWorkout.value = false
    }

    // Device selection dialog removed in favor of auto-connect flow

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

    fun enableHandleDetection() {
        Timber.d("MainViewModel: Enabling handle detection for auto-start")
        bleRepository.enableHandleDetection()
    }

    /**
     * Prepare the ViewModel for Just Lift mode.
     * If a previous workout was completed, reset the state to Idle so Just Lift can work.
     * This is called when entering the JustLiftScreen with a completed workout.
     */
    fun prepareForJustLift() {
        viewModelScope.launch {
            if (_workoutState.value is WorkoutState.Completed) {
                Timber.d("Preparing for Just Lift: Resetting completed workout state")
                resetForNewWorkout()
                _workoutState.value = WorkoutState.Idle
                enableHandleDetection()
                _workoutParameters.value = _workoutParameters.value.copy(
                    isJustLift = true,
                    useAutoStart = true
                )
                Timber.d("Just Lift ready: State=Idle, AutoStart=enabled")
            }
        }
    }

    fun startWorkout(skipCountdown: Boolean = false, isJustLiftMode: Boolean = false) {
        Timber.d("$$$ startWorkout() CALLED! skipCountdown=$skipCountdown, isJustLiftMode=$isJustLiftMode $$$")

        // CRITICAL: Re-set rep event callback to ensure it's active for this workout
        // This fixes the issue where callback set in init block may not persist


        viewModelScope.launch {
            // Forcefully apply the isJustLift flag to ensure it's correct
            val params = _workoutParameters.value.copy(
                isJustLift = isJustLiftMode,
                useAutoStart = if (isJustLiftMode) true else _workoutParameters.value.useAutoStart
            )
            // Update the state flow so the rest of the app is consistent
            _workoutParameters.value = params
            
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
            autoStopStopRequested.set(false)

            currentSessionId = java.util.UUID.randomUUID().toString()
            workoutStartTime = System.currentTimeMillis()
            collectedMetrics.clear()

            // Countdown (optional) - Skip countdown for Just Lift mode or if explicitly requested
            if (!skipCountdown && !params.isJustLift) {
                Timber.d("")
                Timber.d(" STARTING COUNTDOWN")
                Timber.d(" Mode: ${params.workoutType.displayName}")
                Timber.d(" Target: ${params.warmupReps} warmup + ${params.reps} working reps")
                Timber.d("")

                for (i in 5 downTo 1) {
                    _workoutState.value = WorkoutState.Countdown(i)
                    delay(1000)
                }
            } else {
                Timber.d(" SKIPPING COUNTDOWN - ${if (params.isJustLift) "Just Lift mode" else "Auto-advancing"}")
            }

            Timber.d("")
            Timber.d(" COUNTDOWN COMPLETE - SENDING WORKOUT COMMAND")
            Timber.d("")
            Timber.d("?? TIMING: About to call bleRepository.startWorkout() at ${System.currentTimeMillis()}ms")
            val startTime = System.currentTimeMillis()

            // Set state to Active immediately before sending BLE command for instant UI response
            _workoutState.value = WorkoutState.Active

            val result = bleRepository.startWorkout(params)

            val commandLatency = System.currentTimeMillis() - startTime
            Timber.d("?? TIMING: bleRepository.startWorkout() completed in ${commandLatency}ms")

            if (result.isFailure) {
                _workoutState.value = WorkoutState.Error(
                    result.exceptionOrNull()?.message ?: "Unknown error"
                )
                Timber.e("Failed to start workout: ${result.exceptionOrNull()?.message}")
                return@launch
            }

            val activeStateTime = System.currentTimeMillis()
            Timber.d("?? TIMING: State set to Active at ${activeStateTime}ms (${activeStateTime - startTime}ms after command)")

            WorkoutForegroundService.startWorkoutService(
                getContext(),
                params.workoutType.displayName,
                params.reps
            )

            Timber.d("Workout command sent successfully! Tracking reps now. Session: $currentSessionId")

            // Emit haptic feedback for workout start
            _hapticEvents.emit(HapticEvent.WORKOUT_START)
        }
    }

    fun stopWorkout() {
        viewModelScope.launch {
            val timestamp = System.currentTimeMillis()
            val currentState = _workoutState.value
            
            Timber.d("STOP_DEBUG: ============================================")
            Timber.d("STOP_DEBUG: [$timestamp] stopWorkout() called from UI")
            Timber.d("STOP_DEBUG: Current workout state: $currentState")
            Timber.d("STOP_DEBUG: Current rep count: warmup=${_repCount.value.warmupReps}, working=${_repCount.value.workingReps}")
            Timber.d("STOP_DEBUG: Loaded routine: ${_loadedRoutine.value?.name ?: "None"}")
            Timber.d("STOP_DEBUG: ============================================")
            
            // Cancel any running rest timer to prevent auto-restart
            restTimerJob?.cancel()
            restTimerJob = null

            // CRITICAL SAFETY: Stop all active polling and data collection
            // This ensures the machine fully exits workout mode
            val beforeRepoStop = System.currentTimeMillis()
            Timber.d("STOP_DEBUG: [$beforeRepoStop] About to call bleRepository.stopWorkout()")
            
            // Stop hardware immediately (this will stop monitor/property polling in BLE layer)
            bleRepository.stopWorkout()
            
            val afterRepoStop = System.currentTimeMillis()
            Timber.d("STOP_DEBUG: [$afterRepoStop] bleRepository.stopWorkout() completed (took ${afterRepoStop - beforeRepoStop}ms)")
            
            // Stop foreground service
            WorkoutForegroundService.stopWorkoutService(getApplication())
            _hapticEvents.emit(HapticEvent.WORKOUT_END)

            // Mark as completed - NO AUTOPLAY
            _workoutState.value = WorkoutState.Completed
            Timber.d("STOP_DEBUG: Workout state changed to: ${_workoutState.value}")

            // Save current progress
            saveWorkoutSession()

            // Reset state
            repCounter.reset()
            resetAutoStopState()

            val finalTimestamp = System.currentTimeMillis()
            Timber.d("STOP_DEBUG: [$finalTimestamp] stopWorkout() complete - Total time: ${finalTimestamp - timestamp}ms")
            Timber.d("STOP_DEBUG: ============================================")
        }
    }

    /**
     * Test official app protocol - systematically try 9-byte commands on all characteristics
     * This is a diagnostic function to identify which characteristic triggers workout start
     */
    @Suppress("unused")
    fun testOfficialAppProtocol() {
        viewModelScope.launch {
            try {
                Timber.d("ViewModel: Starting official app protocol test")
                bleRepository.testOfficialAppProtocol()
                Timber.d("ViewModel: Test complete - check logs for results")
            } catch (e: Exception) {
                Timber.e(e, "ViewModel: Test failed")
            }
        }
    }

    /**
     * Handle automatic set completion (when rep target is reached via auto-stop)
     * This is DIFFERENT from user manually stopping
     */
    private fun handleSetCompletion() {
        viewModelScope.launch {
            Timber.d("???????????????????????????????????????????????????")
            Timber.d("HANDLE SET COMPLETION CALLED")
            Timber.d("???????????????????????????????????????????????????")

            // Stop hardware
            bleRepository.stopWorkout()
            WorkoutForegroundService.stopWorkoutService(getApplication())
            _hapticEvents.emit(HapticEvent.WORKOUT_END)

            // Save progress
            saveWorkoutSession()

            val routine = _loadedRoutine.value
            val isJustLift = workoutParameters.value.isJustLift

            Timber.d("Current state:")
            Timber.d("  _loadedRoutine.value = ${routine?.name ?: "NULL"}")
            Timber.d("  routine.id = ${routine?.id}")
            Timber.d("  isJustLift = $isJustLift")
            Timber.d("  _currentExerciseIndex = ${_currentExerciseIndex.value}")
            Timber.d("  _currentSetIndex = ${_currentSetIndex.value}")

            if (routine != null) {
                val currentExercise = routine.exercises.getOrNull(_currentExerciseIndex.value)
                Timber.d("  Current exercise: ${currentExercise?.exercise?.displayName ?: "NULL"}")
                Timber.d("  Exercise setReps.size = ${currentExercise?.setReps?.size}")
                Timber.d("  Exercise setReps = ${currentExercise?.setReps}")
                Timber.d("  Total exercises in routine = ${routine.exercises.size}")
            }

            // Check if there are more sets or exercises remaining
            val hasMoreSets = routine?.let {
                val currentExercise = it.exercises.getOrNull(_currentExerciseIndex.value)
                val result = currentExercise != null && _currentSetIndex.value < currentExercise.setReps.size - 1
                Timber.d("  hasMoreSets calculation: currentExercise=$currentExercise, currentSetIndex=${_currentSetIndex.value}, setReps.size=${currentExercise?.setReps?.size}, result=$result")
                result
            } ?: false

            val hasMoreExercises = routine?.let {
                val result = _currentExerciseIndex.value < it.exercises.size - 1
                Timber.d("  hasMoreExercises calculation: currentExerciseIndex=${_currentExerciseIndex.value}, exercises.size=${it.exercises.size}, result=$result")
                result
            } ?: false

            val shouldShowRestTimer = (hasMoreSets || hasMoreExercises) && !isJustLift

            Timber.d("Decision:")
            Timber.d("  hasMoreSets = $hasMoreSets")
            Timber.d("  hasMoreExercises = $hasMoreExercises")
            Timber.d("  shouldShowRestTimer = $shouldShowRestTimer")
            Timber.d("???????????????????????????????????????????????????")

            // Show rest timer if there are more sets/exercises (regardless of autoplay preference)
            // Autoplay preference only controls whether we auto-advance after rest
            if (shouldShowRestTimer) {
                Timber.d("? Starting rest timer...")
                startRestTimer()
            } else {
                Timber.d("? No rest timer - marking as completed")
                repCounter.reset()
                resetAutoStopState()

                // Auto-reset for Just Lift mode to enable immediate restart
                if (isJustLift) {
                    Timber.d("Just Lift mode: Auto-resetting to Idle (no completion state)")
                    resetForNewWorkout()
                    _workoutState.value = WorkoutState.Idle  // Explicitly set to Idle for Just Lift
                    Timber.d("Just Lift mode: Re-enabling handle detection for next auto-start (useAutoStart=${_workoutParameters.value.useAutoStart})")
                    enableHandleDetection() // Re-enable for next auto-start

                    // Enable velocity-based wake-up detection for next exercise
                    bleRepository.enableJustLiftWaitingMode()
                    Timber.d("Just Lift mode: Velocity wake-up detection enabled - ready for next exercise")
                } else {
                    _workoutState.value = WorkoutState.Completed
                }
            }
        }
    }

    /**
     * Cancel routine - stops everything with no autoplay
     * Use this when user explicitly wants to end the routine during rest
     */
    fun cancelRoutine() {
        viewModelScope.launch {
            // Stop everything, no autoplay
            bleRepository.stopWorkout()
            WorkoutForegroundService.stopWorkoutService(getApplication())
            _hapticEvents.emit(HapticEvent.WORKOUT_END)
            
            _workoutState.value = WorkoutState.Completed
            _loadedRoutine.value = null  // Clear routine
            
            // Save if there was any progress
            saveWorkoutSession()
            
            repCounter.reset()
            resetAutoStopState()
            Timber.d("Routine cancelled by user")
        }
    }

    private fun startRestTimer() {
        // Cancel any existing rest timer
        restTimerJob?.cancel()

        restTimerJob = viewModelScope.launch {
            val routine = _loadedRoutine.value ?: run {
                Timber.e("startRestTimer: No routine loaded!")
                return@launch
            }
            val currentExercise = routine.exercises.getOrNull(_currentExerciseIndex.value) ?: run {
                Timber.e("startRestTimer: No exercise at index ${_currentExerciseIndex.value}")
                return@launch
            }
            val restDuration = currentExercise.restSeconds.takeIf { it > 0 } ?: 90
            val autoplay = userPreferences.value.autoplayEnabled

            Timber.d("???????????????????????????????????????????????????")
            Timber.d("REST TIMER STARTING")
            Timber.d("  Exercise: ${currentExercise.exercise.displayName}")
            Timber.d("  Rest duration: ${restDuration}s")
            Timber.d("  Autoplay enabled: $autoplay")
            Timber.d("  Current set: ${_currentSetIndex.value + 1}/${currentExercise.setReps.size}")
            Timber.d("???????????????????????????????????????????????????")

            for (i in restDuration downTo 1) {
                val isLastSet = _currentSetIndex.value >= currentExercise.setReps.size - 1
                val nextExercise = routine.exercises.getOrNull(_currentExerciseIndex.value + 1)
                val nextName = if (isLastSet) {
                    nextExercise?.exercise?.name ?: "Workout Complete"
                } else {
                    "Set ${_currentSetIndex.value + 2} of ${currentExercise.exercise.name}"
                }

                _workoutState.value = WorkoutState.Resting(
                    restSecondsRemaining = i,
                    nextExerciseName = nextName,
                    isLastExercise = isLastSet && nextExercise == null,
                    currentSet = _currentSetIndex.value + 1,
                    totalSets = currentExercise.setReps.size
                )
                delay(1000)
            }

            Timber.d("Rest timer complete. Autoplay=$autoplay")

            // Only auto-advance if autoplay is enabled
            // If autoplay is disabled, user must manually start next set via skipRest()
            if (autoplay) {
                Timber.d("Autoplay enabled - starting next set/exercise")
                startNextSetOrExercise()
            } else {
                // Stay in resting state with 0 seconds remaining
                // User will see "Start Next Set" button in UI
                Timber.d("Autoplay disabled - staying in resting state")

                // Recalculate next exercise info after loop ends
                val isLastSet = _currentSetIndex.value >= currentExercise.setReps.size - 1
                val nextExercise = routine.exercises.getOrNull(_currentExerciseIndex.value + 1)
                val nextName = if (isLastSet) {
                    nextExercise?.exercise?.name ?: "Workout Complete"
                } else {
                    "Set ${_currentSetIndex.value + 2} of ${currentExercise.exercise.name}"
                }

                _workoutState.value = WorkoutState.Resting(
                    restSecondsRemaining = 0,
                    nextExerciseName = nextName,
                    isLastExercise = isLastSet && nextExercise == null,
                    currentSet = _currentSetIndex.value + 1,
                    totalSets = currentExercise.setReps.size
                )
            }
        }
    }

    private fun startNextSetOrExercise() {
        // Enhanced state guard: Prevent race conditions by checking valid transition states
        val currentState = _workoutState.value
        if (currentState is WorkoutState.Completed) {
            Timber.w("startNextSetOrExercise called but workout already completed - ignoring")
            return
        }
        // Only allow transition from Resting state - prevents race conditions if called multiple times
        if (currentState !is WorkoutState.Resting) {
            Timber.w("startNextSetOrExercise called in invalid state: $currentState - ignoring (expected Resting)")
            return
        }

        val routine = _loadedRoutine.value ?: run {
            Timber.e("startNextSetOrExercise: No routine loaded!")
            return
        }
        val currentExercise = routine.exercises.getOrNull(_currentExerciseIndex.value) ?: run {
            Timber.e("startNextSetOrExercise: No exercise at index ${_currentExerciseIndex.value}")
            return
        }

        Timber.d("???????????????????????????????????????????????????")
        Timber.d("START NEXT SET OR EXERCISE")
        Timber.d("  Current exercise: ${currentExercise.exercise.displayName}")
        Timber.d("  Current set index: ${_currentSetIndex.value}")
        Timber.d("  Total sets: ${currentExercise.setReps.size}")
        Timber.d("  Current exercise index: ${_currentExerciseIndex.value}")
        Timber.d("  Total exercises: ${routine.exercises.size}")

        if (_currentSetIndex.value < currentExercise.setReps.size - 1) {
            // More sets in current exercise
            Timber.d("  ? Moving to next set")
            _currentSetIndex.value++
            val targetReps = currentExercise.setReps[_currentSetIndex.value]
            Timber.d("  New set index: ${_currentSetIndex.value}")
            Timber.d("  Target reps: $targetReps")
            _workoutParameters.value = workoutParameters.value.copy(
                reps = targetReps,
                // Preserve all other parameters from current exercise
                progressionRegressionKg = workoutParameters.value.progressionRegressionKg,
                weightPerCableKg = workoutParameters.value.weightPerCableKg,
                workoutType = workoutParameters.value.workoutType,
                selectedExerciseId = workoutParameters.value.selectedExerciseId
            )
            Timber.d("???????????????????????????????????????????????????")
            startWorkout(skipCountdown = true)
        } else {
            // Move to next exercise
            Timber.d("  No more sets in current exercise")
            if (_currentExerciseIndex.value < routine.exercises.size - 1) {
                Timber.d("  ? Moving to next exercise")
                _currentExerciseIndex.value++
                _currentSetIndex.value = 0
                // Update workout parameters for new exercise
                val nextExercise = routine.exercises[_currentExerciseIndex.value]
                Timber.d("  New exercise index: ${_currentExerciseIndex.value}")
                Timber.d("  Next exercise: ${nextExercise.exercise.displayName}")
                _workoutParameters.value = workoutParameters.value.copy(
                    weightPerCableKg = nextExercise.weightPerCableKg,
                    reps = nextExercise.setReps[0],
                    workoutType = nextExercise.workoutType,
                    progressionRegressionKg = nextExercise.progressionKg,
                    selectedExerciseId = nextExercise.exercise.id
                )
                Timber.d("???????????????????????????????????????????????????")
                startWorkout(skipCountdown = true)
            } else {
                // Routine complete - clear routine to prevent auto-restart
                Timber.d("  ? ROUTINE COMPLETE!")
                Timber.d("  Clearing routine and resetting indices")
                _workoutState.value = WorkoutState.Completed
                _loadedRoutine.value = null  // CRITICAL: Clear routine to prevent infinite loop
                _currentSetIndex.value = 0
                _currentExerciseIndex.value = 0
                repCounter.reset()
                resetAutoStopState()
                Timber.d("???????????????????????????????????????????????????")
                Timber.d("Routine completed successfully")
            }
        }
    }

    fun skipRest() {
        Timber.d("???????????????????????????????????????????????????")
        Timber.d("SKIP REST CALLED")
        Timber.d("  Current state: ${_workoutState.value}")
        Timber.d("  Current exercise index: ${_currentExerciseIndex.value}")
        Timber.d("  Current set index: ${_currentSetIndex.value}")
        Timber.d("???????????????????????????????????????????????????")
        
        if (_workoutState.value is WorkoutState.Resting) {
            // Cancel the rest timer
            restTimerJob?.cancel()
            restTimerJob = null
            Timber.d("Rest timer cancelled, starting next set/exercise")
            startNextSetOrExercise()
        } else {
            Timber.w("skipRest called but state is not Resting: ${_workoutState.value}")
        }
    }

    /**
     * Manually advance to the next exercise in a routine.
     * Called when user clicks "Start Next Exercise" button after completing an exercise.
     */
    fun advanceToNextExercise() {
        val routine = _loadedRoutine.value ?: return

        // Move to next exercise
        if (_currentExerciseIndex.value < routine.exercises.size - 1) {
            _currentExerciseIndex.value++
            _currentSetIndex.value = 0

            // Update workout parameters for new exercise
            val nextExercise = routine.exercises[_currentExerciseIndex.value]
            _workoutParameters.value = workoutParameters.value.copy(
                weightPerCableKg = nextExercise.weightPerCableKg,
                reps = nextExercise.setReps[0],
                workoutType = nextExercise.workoutType,
                progressionRegressionKg = nextExercise.progressionKg,
                selectedExerciseId = nextExercise.exercise.id
            )

            // Start the next exercise
            startWorkout(skipCountdown = true)
        }
    }

    private fun resetAutoStopState() {
        autoStopStartTime = null
        autoStopTriggered.set(false)
        autoStopStopRequested.set(false)
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

        val (eccentricLoad, echoLevel) = when (val wt = params.workoutType) {
            is WorkoutType.Echo -> wt.eccentricLoad.percentage to wt.level.levelValue
            is WorkoutType.Program -> 100 to 2 // Defaults for program modes
        }
        
        val session = WorkoutSession(
            id = sessionId,
            timestamp = workoutStartTime,
            mode = params.workoutType.displayName,
            reps = params.reps,
            weightPerCableKg = actualPerCableWeightKg, // Store per-cable weight
            progressionKg = params.progressionRegressionKg,
            duration = duration,
            totalReps = working,  // Exclude warm-up reps from total count
            warmupReps = warmup,
            workingReps = working,
            isJustLift = params.isJustLift,
            stopAtTop = params.stopAtTop,
            eccentricLoad = eccentricLoad,
            echoLevel = echoLevel,
            exerciseId = params.selectedExerciseId
        )

        workoutRepository.saveSession(session)

        if (collectedMetrics.isNotEmpty()) {
            workoutRepository.saveMetrics(sessionId, collectedMetrics)
        }

        // Track personal record if exercise is selected
        params.selectedExerciseId?.let { exerciseId ->
            // Only track PRs for completed working sets (not warmups, not just lift, not echo mode)
            // Echo mode is excluded because it uses adaptive weight (machine calculates)
            val isEchoMode = params.workoutType is WorkoutType.Echo
            if (working > 0 && !params.isJustLift && !isEchoMode) {
                val isNewPR = workoutRepository.updatePersonalRecordIfNeeded(
                    exerciseId = exerciseId,
                    weightPerCableKg = actualPerCableWeightKg,
                    reps = working,
                    workoutMode = params.workoutType.displayName
                )
                if (isNewPR) {
                    Timber.d("NEW PERSONAL RECORD! Exercise: $exerciseId, Weight: ${actualPerCableWeightKg}kg, Reps: $working")
                    // Trigger PR celebration
                    viewModelScope.launch {
                        try {
                            val exercise = exerciseRepository.getExerciseById(exerciseId)
                            _prCelebrationEvent.emit(
                                PRCelebrationEvent(
                                    exerciseName = exercise?.name ?: "Unknown Exercise",
                                    weightPerCableKg = actualPerCableWeightKg,
                                    reps = working,
                                    workoutMode = params.workoutType.displayName
                                )
                            )
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to trigger PR celebration")
                        }
                    }
                }
            }
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

    fun setAutoplayEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setAutoplayEnabled(enabled)
        }
    }

    fun setStopAtTop(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setStopAtTop(enabled)
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
        _currentSetIndex.value = 0
        resetAutoStopState()
        Timber.d("Reset for new workout - state returned to Idle")
    }

    // Feature 1: Dialog Management
    /**
     * Show the workout setup dialog
     */
    @Suppress("unused")
    fun showWorkoutSetupDialog() {
        _isWorkoutSetupDialogVisible.value = true
    }

    /**
     * Hide the workout setup dialog
     */
    @Suppress("unused")
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
        _currentSetIndex.value = 0

        // Load parameters from first exercise
        val firstExercise = routine.exercises[0]
        val firstSetReps = firstExercise.setReps.firstOrNull() ?: 10

        Timber.d("???????????????????????????????????????????????????")
        Timber.d("LOADING ROUTINE: ${routine.name}")
        Timber.d("  ID: ${routine.id}")
        Timber.d("  Total exercises: ${routine.exercises.size}")
        Timber.d("  First exercise: ${firstExercise.exercise.displayName}")
        Timber.d("  Sets: ${firstExercise.setReps.size} (${firstExercise.setReps})")
        Timber.d("  Weight: ${firstExercise.weightPerCableKg}kg")
        Timber.d("  First set reps: $firstSetReps")
        Timber.d("  Setting isJustLift = false")
        Timber.d("???????????????????????????????????????????????????")

        updateWorkoutParameters(
            WorkoutParameters(
                workoutType = _workoutParameters.value.workoutType, // Keep current workout type
                reps = firstSetReps,
                weightPerCableKg = firstExercise.weightPerCableKg,
                progressionRegressionKg = firstExercise.progressionKg,
                isJustLift = false,  // CRITICAL: Routines are NOT just lift mode (enables autoplay)
                stopAtTop = stopAtTop.value,   // Use user preference from settings
                warmupReps = _workoutParameters.value.warmupReps
            )
        )

        // Mark routine as used
        viewModelScope.launch {
            workoutRepository.markRoutineUsed(routine.id)
        }

        Timber.d("Routine loaded successfully - _loadedRoutine.value is now: ${_loadedRoutine.value?.name}")
    }

    /**
     * Load a routine and immediately start the workout
     * Convenience method for one-click routine start from UI
     */
    @Suppress("unused")
    fun startRoutineWorkout(routine: Routine) {
        loadRoutine(routine)
        startWorkout()
    }

    /**
     * Move to next exercise in loaded routine
     */
    @Suppress("unused")
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
                    progressionRegressionKg = nextExercise.progressionKg
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
    @Suppress("unused")
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
                    progressionRegressionKg = prevExercise.progressionKg
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
        _currentSetIndex.value = 0
        Timber.d("Cleared loaded routine")
    }

    /**
     * Get current exercise from loaded routine
     */
    @Suppress("unused")
    fun getCurrentExercise(): RoutineExercise? {
        val routine = _loadedRoutine.value ?: return null
        val index = _currentExerciseIndex.value
        return routine.exercises.getOrNull(index)
    }

    // ========== Weekly Programs ==========

    /**
     * Save a weekly program
     */
    fun saveProgram(program: com.example.vitruvianredux.data.local.WeeklyProgramWithDays) {
        viewModelScope.launch {
            val result = workoutRepository.saveProgram(program)
            if (result.isSuccess) {
                Timber.d("Saved weekly program: ${program.program.title}")
            } else {
                Timber.e("Failed to save weekly program: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    /**
     * Delete a weekly program
     */
    fun deleteProgram(programId: String) {
        viewModelScope.launch {
            val result = workoutRepository.deleteProgram(programId)
            if (result.isSuccess) {
                Timber.d("Deleted weekly program: $programId")
            } else {
                Timber.e("Failed to delete weekly program: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    /**
     * Activate a weekly program
     */
    fun activateProgram(programId: String) {
        viewModelScope.launch {
            val result = workoutRepository.activateProgram(programId)
            if (result.isSuccess) {
                Timber.d("Activated weekly program: $programId")
            } else {
                Timber.e("Failed to activate weekly program: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    /**
     * Load a routine by ID (for weekly program day selection)
     */
    fun loadRoutineById(routineId: String) {
        viewModelScope.launch {
            workoutRepository.getRoutineById(routineId)
                .firstOrNull()?.let { routine ->
                    loadRoutine(routine)
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Timber.d("MainViewModel clearing - cancelling collection jobs")

        // Cancel collection jobs to prevent memory leaks
        monitorDataCollectionJob?.cancel()
        repEventsCollectionJob?.cancel()
        autoStartJob?.cancel()
        restTimerJob?.cancel()
    }

    companion object {
        private const val AUTO_STOP_DURATION_SECONDS = 3f  // Official app: 3 seconds
    }
}

/**
 * UI state for the Just Lift auto-stop timer.
 */
data class AutoStopUiState(
    val isActive: Boolean = false,
    val progress: Float = 0f,
    val secondsRemaining: Int = 3  // Official app: 3 seconds
)

/**
 * Scanned device data class
 */
data class ScannedDevice(
    val name: String,
    val address: String,
    val rssi: Int = 0
)

