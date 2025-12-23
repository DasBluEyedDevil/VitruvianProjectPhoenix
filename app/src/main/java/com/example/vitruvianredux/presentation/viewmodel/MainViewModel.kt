package com.example.vitruvianredux.presentation.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
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
import com.example.vitruvianredux.util.DataBackupManager
import com.example.vitruvianredux.util.ImportResult
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

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Sealed class hierarchy for workout history items
 * Allows displaying both single workout sessions and grouped routine sessions
 */
sealed class HistoryItem {
    abstract val timestamp: Long
}

data class SingleSessionHistoryItem(val session: WorkoutSession) : HistoryItem() {
    override val timestamp: Long = session.timestamp
}

data class GroupedRoutineHistoryItem(
    val routineSessionId: String,
    val routineName: String,
    val sessions: List<WorkoutSession>,
    val totalDuration: Long,
    val totalReps: Int,
    val exerciseCount: Int,
    override val timestamp: Long
) : HistoryItem()

data class TopBarAction(
    val icon: ImageVector,
    val description: String,
    val onClick: () -> Unit
)

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val bleRepository: BleRepository,
    private val workoutRepository: WorkoutRepository,
    val exerciseRepository: ExerciseRepository,
    val personalRecordRepository: PersonalRecordRepository,
    private val repCounter: RepCounterFromMachine,
    private val preferencesManager: PreferencesManager,
    private val dataBackupManager: DataBackupManager
) : AndroidViewModel(application) {

    // Use application context directly instead of storing it
    private fun getContext(): Context = getApplication<Application>().applicationContext

    val connectionState: StateFlow<ConnectionState> = bleRepository.connectionState

    private val _workoutState = MutableStateFlow<WorkoutState>(WorkoutState.Idle)
    val workoutState: StateFlow<WorkoutState> = _workoutState.asStateFlow()

    private val _currentMetric = MutableStateFlow<WorkoutMetric?>(null)
    val currentMetric: StateFlow<WorkoutMetric?> = _currentMetric.asStateFlow()

    // Current heuristic force (kgMax per cable) for Echo mode live display
    // This is the actual measured force from the device's force telemetry
    private val _currentHeuristicKgMax = MutableStateFlow(0f)
    val currentHeuristicKgMax: StateFlow<Float> = _currentHeuristicKgMax.asStateFlow()

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

    private val _repRanges = MutableStateFlow<com.example.vitruvianredux.domain.usecase.RepRanges?>(null)
    val repRanges: StateFlow<com.example.vitruvianredux.domain.usecase.RepRanges?> = _repRanges.asStateFlow()

    private val _autoStopState = MutableStateFlow(AutoStopUiState())
    val autoStopState: StateFlow<AutoStopUiState> = _autoStopState.asStateFlow()

    // Track if user modified parameters during rest period (so we don't overwrite their changes)
    private val _parametersModifiedDuringRest = MutableStateFlow(false)

    private val _autoStartCountdown = MutableStateFlow<Int?>(null)
    val autoStartCountdown: StateFlow<Int?> = _autoStartCountdown.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedDevice>> = _scannedDevices.asStateFlow()

    private val _workoutHistory = MutableStateFlow<List<WorkoutSession>>(emptyList())
    val workoutHistory: StateFlow<List<WorkoutSession>> = _workoutHistory.asStateFlow()

    // Top Bar Title State
    private val _topBarTitle = MutableStateFlow("Vitruvian Project Phoenix")
    val topBarTitle: StateFlow<String> = _topBarTitle.asStateFlow()

    fun updateTopBarTitle(title: String) {
        _topBarTitle.value = title
    }

    // Top Bar Actions State
    private val _topBarActions = MutableStateFlow<List<TopBarAction>>(emptyList())
    val topBarActions: StateFlow<List<TopBarAction>> = _topBarActions.asStateFlow()

    fun setTopBarActions(actions: List<TopBarAction>) {
        _topBarActions.value = actions
    }

    fun clearTopBarActions() {
        _topBarActions.value = emptyList()
    }

    // Top Bar Back Action Override
    private val _topBarBackAction = MutableStateFlow<(() -> Unit)?>(null)
    val topBarBackAction: StateFlow<(() -> Unit)?> = _topBarBackAction.asStateFlow()

    fun setTopBarBackAction(action: () -> Unit) {
        _topBarBackAction.value = action
    }

    fun clearTopBarBackAction() {
        _topBarBackAction.value = null
    }

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

    val enableVideoPlayback: StateFlow<Boolean> = userPreferences
        .map { it.enableVideoPlayback }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    // Data Export/Import State
    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _importResult = MutableStateFlow<ImportResult?>(null)
    val importResult: StateFlow<ImportResult?> = _importResult.asStateFlow()

    private val _showImportResultDialog = MutableStateFlow(false)
    val showImportResultDialog: StateFlow<Boolean> = _showImportResultDialog.asStateFlow()

    // Feature 4: Routine Management
    private val _routines = MutableStateFlow<List<Routine>>(emptyList())
    val routines: StateFlow<List<Routine>> = _routines.asStateFlow()

    private val _loadedRoutine = MutableStateFlow<Routine?>(null)
    val loadedRoutine: StateFlow<Routine?> = _loadedRoutine.asStateFlow()

    private val _currentExerciseIndex = MutableStateFlow(0)
    val currentExerciseIndex: StateFlow<Int> = _currentExerciseIndex.asStateFlow()

    private val _currentSetIndex = MutableStateFlow(0)
    val currentSetIndex: StateFlow<Int> = _currentSetIndex.asStateFlow()

    // Bodyweight exercise state (Issue #218)
    private val _isCurrentExerciseBodyweight = MutableStateFlow(false)
    val isCurrentExerciseBodyweight: StateFlow<Boolean> = _isCurrentExerciseBodyweight.asStateFlow()

    // Bodyweight timer state: Pair(remainingSeconds, totalSeconds) or null if not active
    private val _bodyweightTimerState = MutableStateFlow<Pair<Int, Int>?>(null)
    val bodyweightTimerState: StateFlow<Pair<Int, Int>?> = _bodyweightTimerState.asStateFlow()

    // Session-level eccentric load: persists across exercises within a workout session
    // Used as fallback when an exercise has no saved defaults
    private val _sessionEccentricLoad = MutableStateFlow(EccentricLoad.LOAD_100)
    val sessionEccentricLoad: StateFlow<EccentricLoad> = _sessionEccentricLoad.asStateFlow()

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

    // Grouped workout history for the History tab
    val groupedWorkoutHistory: StateFlow<List<HistoryItem>> = allWorkoutSessions.map { sessions ->
        Timber.d("📊 GROUPING HISTORY: Total sessions = ${sessions.size}")
        sessions.forEach { session ->
            Timber.d("  - Session ${session.id.take(8)}: routineSessionId=${session.routineSessionId?.take(8)}, routineName=${session.routineName}, exerciseId=${session.exerciseId}, totalReps=${session.totalReps}")
        }

        val groupedByRoutine = sessions.filter { it.routineSessionId != null }
            .groupBy { it.routineSessionId!! }
            .map { (id, sessionList) ->
                Timber.d("📦 GROUPED ROUTINE: routineSessionId=${id.take(8)}, sessions=${sessionList.size}, name=${sessionList.first().routineName}")
                GroupedRoutineHistoryItem(
                    routineSessionId = id,
                    routineName = sessionList.first().routineName ?: "Unnamed Routine",
                    sessions = sessionList.sortedBy { it.timestamp },
                    totalDuration = sessionList.sumOf { it.duration },
                    totalReps = sessionList.sumOf { it.totalReps },
                    exerciseCount = sessionList.mapNotNull { it.exerciseId }.distinct().count(),
                    timestamp = sessionList.minOf { it.timestamp }
                )
            }
        val singleSessions = sessions.filter { it.routineSessionId == null }
            .map {
                Timber.d("📄 SINGLE SESSION: ${it.id.take(8)}, exerciseId=${it.exerciseId}, totalReps=${it.totalReps}")
                SingleSessionHistoryItem(it)
            }

        Timber.d("📊 RESULT: ${groupedByRoutine.size} grouped routines, ${singleSessions.size} single sessions")
        (groupedByRoutine + singleSessions).sortedByDescending { it.timestamp }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
    // Track max heuristic force (kgMax) for Echo mode - this is the actual measured force
    private var maxHeuristicKgMax: Float = 0f

    // Routine tracking (for grouping sets from the same routine)
    private var currentRoutineSessionId: String? = null
    private var currentRoutineName: String? = null

    // Auto-stop tracking for Just Lift and AMRAP modes
    private var autoStopStartTime: Long? = null
    private val autoStopTriggered = AtomicBoolean(false)
    private val autoStopStopRequested = AtomicBoolean(false)
    private var currentHandleState: com.example.vitruvianredux.data.ble.HandleState =
        com.example.vitruvianredux.data.ble.HandleState.WaitingForRest

    // Velocity-based stall detection for auto-stop (Issue #204)
    // Official app triggers auto-stop when movement stops for ~3 seconds, even if cables aren't fully retracted
    // This handles exercises like bench press where bar rests on chest with cables still partially extended
    private var stallDetectionStartTime: Long? = null

    private var autoStartJob: Job? = null
    private var restTimerJob: Job? = null
    private var connectionJob: Job? = null  // Track connection attempt for cancellation

    // Store collection jobs for monitor and rep event flows so they can be cancelled during stop
    private var monitorDataCollectionJob: Job? = null
    private var repEventsCollectionJob: Job? = null
    private var bodyweightTimerJob: Job? = null  // Timer for bodyweight exercises

    // Session-level peak loads (debug/analytics)
    private var maxConcentricPerCableKgThisSession: Float = 0f
    private var maxEccentricPerCableKgThisSession: Float = 0f

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

                // Debug/analytics: log approximate per-cable load at rep events and track session peaks.
                _currentMetric.value?.let { metric ->
                    val perCableKg = metric.totalLoad / 2f
                    when (repEvent.type) {
                        RepType.WORKING_COMPLETED, RepType.WARMUP_COMPLETED -> {
                            // Near concentric peak
                            Timber.d(
                                "REP_LOAD_CON: warmup=${newRepCount.warmupReps}, working=${newRepCount.workingReps}, " +
                                    "perCableKg=$perCableKg, totalKg=${metric.totalLoad}"
                            )
                            if (perCableKg > maxConcentricPerCableKgThisSession) {
                                maxConcentricPerCableKgThisSession = perCableKg
                            }
                        }
                        RepType.WORKOUT_COMPLETE -> {
                            // Close to end of last eccentric phase
                            Timber.d(
                                "REP_LOAD_ECC: warmup=${newRepCount.warmupReps}, working=${newRepCount.workingReps}, " +
                                    "perCableKg=$perCableKg, totalKg=${metric.totalLoad}"
                            )
                            if (perCableKg > maxEccentricPerCableKgThisSession) {
                                maxEccentricPerCableKgThisSession = perCableKg
                            }
                        }
                        else -> { /* no-op */ }
                    }
                }

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
                    RepType.WORKING_PENDING -> {
                        // No haptic for pending - it's just a visual preview
                        Timber.d("Rep pending (at TOP) - visual update only, no haptic")
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

        // Collect heuristic data (force telemetry for Echo mode)
        // Echo mode uses velocity-based resistance, and kgMax is the actual measured force
        viewModelScope.launch {
            bleRepository.heuristicData.collect { stats ->
                if (stats != null && _workoutState.value is WorkoutState.Active) {
                    // Track maximum force (kgMax) across both phases for Echo mode
                    // kgMax is per-cable force in kg
                    val concentricMax = stats.concentric.kgMax
                    val eccentricMax = stats.eccentric.kgMax
                    val currentMax = maxOf(concentricMax, eccentricMax)

                    // Update live display value for Echo mode
                    _currentHeuristicKgMax.value = currentMax

                    // Track session maximum for history recording
                    if (currentMax > maxHeuristicKgMax) {
                        maxHeuristicKgMax = currentMax
                        Timber.v("Echo force telemetry: kgMax=$currentMax (concentric=$concentricMax, eccentric=$eccentricMax)")
                    }
                }
            }
        }

        // Collect rep notifications from machine (the CORRECT way to count reps!)
        repEventsCollectionJob = viewModelScope.launch {
            Timber.d("Starting to collect rep notifications...")
            bleRepository.repEvents.collect { repNotification ->
                val state = _workoutState.value
                Timber.d("Rep notification received: top=${repNotification.topCounter}, complete=${repNotification.completeCounter}, state=$state")

                // FIX Issue #174: Process rep notifications during Countdown state too
                // After countdown completes, state stays as Countdown(1) while BLE commands
                // are sent (150ms+ delay). Machine starts workout and sends rep notifications
                // during this window. Previously these were DROPPED, causing warmup reps
                // to never register and users to get stuck in "0/3" warmup display.
                if (state is WorkoutState.Active || state is WorkoutState.Countdown) {
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
                // Track current handle state for auto-stop logic
                currentHandleState = state

                Timber.d("Handle state received in ViewModel: $state, useAutoStart=${workoutParameters.value.useAutoStart}, workoutState=${workoutState.value}")

                // Handle auto-START when Idle and waiting for handles
                // Also allow auto-start from SetSummary if in Just Lift mode (interrupting the summary)
                val isIdle = workoutState.value is WorkoutState.Idle
                val isSummaryAndJustLift = workoutState.value is WorkoutState.SetSummary && workoutParameters.value.isJustLift
                
                if (workoutParameters.value.useAutoStart && (isIdle || isSummaryAndJustLift)) {
                    when (state) {
                        com.example.vitruvianredux.data.ble.HandleState.Grabbed -> {
                            Timber.d("Handles grabbed! Starting auto-start timer (State: ${workoutState.value})")
                            startAutoStartTimer()
                        }
                        com.example.vitruvianredux.data.ble.HandleState.Released -> {
                            Timber.d("Handles released! Canceling auto-start timer")
                            cancelAutoStartTimer()
                        }
                        else -> { /* Do nothing */ }
                    }
                }

                // Handle auto-STOP when Active in Just Lift mode and handles released
                // This starts the countdown timer via checkAutoStop logic (triggered by handle state)
                if (workoutParameters.value.isJustLift && workoutState.value is WorkoutState.Active) {
                    if (state == com.example.vitruvianredux.data.ble.HandleState.Released) {
                        Timber.d("🛑 Just Lift: Handles RELEASED - starting auto-stop timer")
                        // Do NOT trigger immediately. Let checkAutoStop handle the timer.
                        // We ensure autoStopStartTime is set to start the countdown.
                        if (autoStopStartTime == null) {
                            autoStopStartTime = System.currentTimeMillis()
                            Timber.d("🛑 Auto-stop timer STARTED (Just Lift) - handles released")
                        }
                    } else {
                        // Handles grabbed or moving - reset timer
                        if (autoStopStartTime != null) {
                            Timber.d("🟢 Auto-stop timer RESET (Handles active: $state)")
                            resetAutoStopTimer()
                        }
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

        Timber.d("Auto-start timer STARTING! (5 seconds) at ${System.currentTimeMillis()}")
        autoStartJob = viewModelScope.launch {
            // User preference: 5 second hold timer with visible countdown
            _autoStartCountdown.value = 5
            delay(1000)
            _autoStartCountdown.value = 4
            delay(1000)
            _autoStartCountdown.value = 3
            delay(1000)
            _autoStartCountdown.value = 2
            delay(1000)
            _autoStartCountdown.value = 1
            delay(1000)
            _autoStartCountdown.value = null
            Timber.d("Auto-start hold complete (5s)! Starting workout at ${System.currentTimeMillis()}")
            // Just Lift mode: Pass isJustLiftMode=true to ensure flag is preserved
            startWorkout(isJustLiftMode = true)
        }
    }

    private fun handleMonitorMetric(metric: WorkoutMetric) {
        // CRITICAL: Track positions during handle detection phase (before workout starts)
        // This builds up min/max ranges for hasMeaningfulRange() auto-stop detection
        // useAutoStart is true when in Just Lift mode and waiting for handles
        if (_workoutParameters.value.useAutoStart && _workoutState.value is WorkoutState.Idle) {
            repCounter.updatePositionRangesContinuously(metric.positionA, metric.positionB)
        }

        if (_workoutState.value is WorkoutState.Active) {
            collectMetricForHistory(metric)
            val params = _workoutParameters.value
            // AMRAP also uses autostop like Just Lift mode
            if (params.isJustLift || params.isAMRAP) {
                // CRITICAL: In Just Lift/AMRAP modes, we must track positions continuously
                // because no rep events fire to establish min/max ranges.
                // This enables hasMeaningfulRange() to return true for auto-stop detection.
                repCounter.updatePositionRangesContinuously(metric.positionA, metric.positionB)

                if (params.isAMRAP && autoStopStartTime == null) {
                    // Log once per AMRAP set to confirm we're checking auto-stop
                    Timber.v("✓ AMRAP set: checkAutoStop will be called (isAMRAP=true)")
                }
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
     *
     * Supports TWO modes (Issue #187):
     * - MODERN: Uses repsRomCount/repsSetCount from 24-byte packets
     * - LEGACY: Uses topCounter increments from 6-byte packets (Beta 4 method)
     *
     * The isLegacyFormat flag determines which counting method to use.
     */
    private fun handleRepNotification(notification: com.example.vitruvianredux.data.ble.RepNotification) {
        val currentPositions = _currentMetric.value

        // Issue #187: Pass legacy flag to enable Beta 4 counting for Samsung devices
        repCounter.process(
            repsRomCount = notification.repsRomCount,   // Machine's warmup rep count (0 for legacy)
            repsSetCount = notification.repsSetCount,   // Machine's working rep count (0 for legacy)
            up = notification.topCounter,               // For position calibration / legacy counting
            down = notification.completeCounter,        // For position calibration
            posA = currentPositions?.positionA ?: 0f,
            posB = currentPositions?.positionB ?: 0f,
            isLegacyFormat = notification.isLegacyFormat  // Use Beta 4 counting if legacy packet
        )
        // Update rep ranges for position bars visualization
        _repRanges.value = repCounter.getRepRanges()
        // All other logic (UI update, haptics, auto-stop) handled in onRepEvent callback
    }

    private fun requestAutoStop() {
        if (autoStopStopRequested.getAndSet(true)) return
        triggerAutoStop()
    }

    private fun triggerAutoStop() {
        Timber.w("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
        Timber.w("AUTOSTOP_TRACE: triggerAutoStop() ENTERED")
        Timber.w("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
        autoStopTriggered.set(true)
        // Ensure UI reflects completion state immediately
        if (_workoutParameters.value.isJustLift || _workoutParameters.value.isAMRAP) {
            _autoStopState.value = _autoStopState.value.copy(progress = 1f, secondsRemaining = 0, isActive = true)
        } else {
            _autoStopState.value = AutoStopUiState()
        }
        Timber.w("AUTOSTOP_TRACE: About to call handleSetCompletion()")
        handleSetCompletion()
        Timber.w("AUTOSTOP_TRACE: handleSetCompletion() returned")
    }

    private fun checkAutoStop(metric: WorkoutMetric) {
        val hasMeaningful = repCounter.hasMeaningfulRange()
        val params = _workoutParameters.value

        // Diagnostic: Log when checkAutoStop is called for Just Lift mode
        if (params.isJustLift && autoStopStartTime == null && stallDetectionStartTime == null) {
            Timber.d("🎯 Just Lift auto-stop check: hasMeaningful=$hasMeaningful, " +
                "posA=${metric.positionA}, posB=${metric.positionB}, " +
                "velA=${metric.velocityA}, velB=${metric.velocityB}")
        }

        // ==================== VELOCITY-BASED STALL DETECTION (Issue #204) ====================
        // Two-tier hysteresis matching official app (<2.5 stalled, >10 moving):
        // - Below LOW threshold (<2.5): start/continue stall timer
        // - Above HIGH threshold (>10): reset stall timer (clear movement)
        // - Between LOW and HIGH (≥2.5 and ≤10): maintain current state (prevents toggling)
        val maxVelocity = maxOf(kotlin.math.abs(metric.velocityA), kotlin.math.abs(metric.velocityB))
        val isDefinitelyStalled = maxVelocity < STALL_VELOCITY_LOW
        val isDefinitelyMoving = maxVelocity > STALL_VELOCITY_HIGH

        // Check if handles are actually being used (not just sitting at rest)
        // Two conditions satisfy "actively using":
        // 1. Current position > 10mm (handles are extended) - catches shoulder press with short ranges
        // 2. OR meaningful motion has occurred (50mm range) - catches bench press where rest position
        //    might be below 10mm but exercise has clearly started (Issue #204 bench press fix)
        val maxPosition = maxOf(metric.positionA, metric.positionB)
        val hasExerciseStarted = hasMeaningful  // True if 50mm motion range achieved
        val isActivelyUsing = maxPosition > STALL_MIN_POSITION || hasExerciseStarted

        // Hysteresis state machine:
        // - Definitely stalled (< LOW): start timer if not already running
        // - Definitely moving (> HIGH): reset timer
        // - Hysteresis band (LOW to HIGH): maintain current state, keep timer running if active
        if (isDefinitelyStalled && isActivelyUsing && stallDetectionStartTime == null) {
            // Velocity below LOW threshold - start stall timer
            stallDetectionStartTime = System.currentTimeMillis()
            Timber.d("🛑 Stall detection STARTED - velocity below LOW threshold " +
                "(vel=$maxVelocity, lowThreshold=$STALL_VELOCITY_LOW)")
        } else if (isDefinitelyMoving && stallDetectionStartTime != null) {
            // Velocity above HIGH threshold - clear movement detected, reset timer
            Timber.d("🟢 Stall detection RESET - velocity above HIGH threshold (vel=$maxVelocity)")
            stallDetectionStartTime = null
        }
        // else: velocity in hysteresis band (2.5-10.0) - maintain current timer state

        // If timer is running (regardless of current velocity zone), check progress and update UI
        val stallStart = stallDetectionStartTime
        if (stallStart != null) {
            val stallElapsed = (System.currentTimeMillis() - stallStart) / 1000f

            // If stalled long enough, trigger auto-stop
            if (stallElapsed >= STALL_DURATION_SECONDS) {
                Timber.d("⏹️ Auto-stop TRIGGERED via STALL DETECTION - no movement for ${STALL_DURATION_SECONDS}s")
                triggerAutoStop()
                return
            }

            // Update UI with stall progress (always update when timer is active)
            val stallProgress = (stallElapsed / STALL_DURATION_SECONDS).coerceIn(0f, 1f)
            val stallRemaining = (STALL_DURATION_SECONDS - stallElapsed).coerceAtLeast(0f)

            if (autoStopStartTime == null || stallElapsed > (System.currentTimeMillis() - autoStopStartTime!!) / 1000f) {
                _autoStopState.value = AutoStopUiState(
                    isActive = true,
                    progress = stallProgress,
                    secondsRemaining = ceil(stallRemaining).toInt()
                )
            }
        }

        // ==================== POSITION-BASED AUTO-STOP (Original Logic) ====================
        if (!hasMeaningful) {
            if (params.isAMRAP || params.isJustLift) {
                Timber.d("⚠️ ${if (params.isJustLift) "Just Lift" else "AMRAP"} " +
                    "auto-stop blocked: NO meaningful range yet")
            }
            resetAutoStopTimer()
            return
        }

        val inDangerZone = repCounter.isInDangerZone(metric.positionA, metric.positionB)

        val repRanges = repCounter.getRepRanges()
        if (params.isAMRAP) {
            Timber.v("AMRAP auto-stop check: inDangerZone=$inDangerZone, " +
                "rangeA=${repRanges.maxPosA?.let { max -> repRanges.minPosA?.let { min -> max - min } }}, " +
                "rangeB=${repRanges.maxPosB?.let { max -> repRanges.minPosB?.let { min -> max - min } }}")
        }

        // Diagnostic: Log danger zone status for Just Lift
        if (params.isJustLift && autoStopStartTime == null) {
            Timber.d("🎯 Just Lift danger zone check: inDangerZone=$inDangerZone, " +
                "rangeA=${repRanges.maxPosA?.let { max -> repRanges.minPosA?.let { min -> max - min } }}, " +
                "rangeB=${repRanges.maxPosB?.let { max -> repRanges.minPosB?.let { min -> max - min } }}")
        }

        // For Just Lift and AMRAP modes, check if the cable(s) in danger zone are released
        // This supports both single-cable (left OR right) and double-cable exercises
        val HANDLE_REST_THRESHOLD = 2.5  // Position < 2.5 = handle at rest

        var cableInDangerAndReleased = false

        // Check cable A: is it in danger zone AND released?
        if (repRanges.minPosA != null && repRanges.maxPosA != null) {
            val rangeA = repRanges.maxPosA!! - repRanges.minPosA!!
            if (rangeA > 50) {  // minRangeThreshold
                val thresholdA = repRanges.minPosA!! + (rangeA * 0.05f).toInt()
                val cableAInDanger = metric.positionA <= thresholdA
                // Check if cable A is released: position is very low (at rest) or near minimum position
                val cableAReleased = metric.positionA.toDouble() < HANDLE_REST_THRESHOLD ||
                                     (metric.positionA - repRanges.minPosA!!) < 10
                if (cableAInDanger && cableAReleased) {
                    cableInDangerAndReleased = true
                    Timber.d("Cable A in danger zone and released: posA=${metric.positionA}, " +
                        "thresholdA=$thresholdA, minPosA=${repRanges.minPosA}")
                }
            }
        }

        // Check cable B: is it in danger zone AND released?
        if (repRanges.minPosB != null && repRanges.maxPosB != null) {
            val rangeB = repRanges.maxPosB!! - repRanges.minPosB!!
            if (rangeB > 50) {  // minRangeThreshold
                val thresholdB = repRanges.minPosB!! + (rangeB * 0.05f).toInt()
                val cableBInDanger = metric.positionB <= thresholdB
                // Check if cable B is released: position is very low (at rest) or near minimum position
                val cableBReleased = metric.positionB.toDouble() < HANDLE_REST_THRESHOLD ||
                                    (metric.positionB - repRanges.minPosB!!) < 10
                if (cableBInDanger && cableBReleased) {
                    cableInDangerAndReleased = true
                    Timber.d("Cable B in danger zone and released: posB=${metric.positionB}, " +
                        "thresholdB=$thresholdB, minPosB=${repRanges.minPosB}")
                }
            }
        }

        // Diagnostic: Log cable release detection for Just Lift
        if (params.isJustLift && autoStopStartTime == null) {
            Timber.d("🎯 Just Lift cable check: inDangerZone=$inDangerZone, " +
                "cableInDangerAndReleased=$cableInDangerAndReleased")
        }

        // Auto-stop triggers when BOTH conditions are met:
        // 1. Position is in danger zone (near bottom)
        // 2. The cable(s) in danger zone are actually released (not during eccentric phase)
        if (inDangerZone && cableInDangerAndReleased) {
            val startTime = autoStopStartTime ?: run {
                autoStopStartTime = System.currentTimeMillis()
                Timber.d("🔴 Auto-stop timer STARTED (${if (params.isJustLift) "Just Lift" else "AMRAP"}) " +
                    "- handles at rest")
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
                Timber.d("⏹️ Auto-stop TRIGGERED via POSITION - ${AUTO_STOP_DURATION_SECONDS}s elapsed")
                triggerAutoStop()
            }
        } else {
            if (autoStopStartTime != null) {
                Timber.d("🟢 Auto-stop timer RESET (inDangerZone=$inDangerZone, " +
                    "cableReleased=$cableInDangerAndReleased)")
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
                        // Connection succeeded - dismiss connecting overlay immediately
                        _isAutoConnecting.value = false
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
        // Cancel any existing connection attempt before starting a new one
        connectionJob?.cancel()
        connectionJob = null

        connectionJob = viewModelScope.launch {
            try {
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
                                            // Connection timeout or failure - clean up BLE connection
                                            Timber.d("Connection timeout or failure - cleaning up")
                                            bleRepository.cancelConnection()
                                            _pendingConnectionCallback = null  // Clear callback on failure
                                            _connectionError.value = "Connection timeout"
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
                            // Scan timeout - clean up properly
                            Timber.d("Scan timeout reached - cleaning up")
                            _pendingConnectionCallback = null  // Clear callback on timeout
                            stopScanning()
                            bleRepository.cancelConnection()  // Cancel any in-progress connection
                            _isAutoConnecting.value = false
                            _connectionError.value = "Scan timeout - no device found"
                            onFailed()
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // User explicitly cancelled - clean up without showing errors
                Timber.d("🔴 CancellationException caught - User cancelled connection")
                Timber.d("🔴 Cleaning up: stopping scan, cancelling BLE, clearing state")
                stopScanning()
                bleRepository.cancelConnection()  // Cancel any in-progress connection
                _isAutoConnecting.value = false
                _pendingConnectionCallback = null
                Timber.d("🔴 Cleanup complete, _isAutoConnecting set to false")
                // Don't show error or call onFailed() - user cancelled intentionally
                throw e  // Re-throw to properly cancel the coroutine
            } finally {
                // Always clear job reference when coroutine completes (success, failure, or cancellation)
                connectionJob = null
            }
        }
    }

    fun clearConnectionError() {
        _connectionError.value = null
    }

    /**
     * Cancel the auto-connection process.
     * Cancels the connection coroutine, which triggers cleanup in the exception handler.
     */
    fun cancelAutoConnecting() {
        Timber.d("🔴 cancelAutoConnecting() called - User cancelled connection")
        Timber.d("🔴 connectionJob exists: ${connectionJob != null}, isActive: ${connectionJob?.isActive}")

        // Immediately clear the connecting state to dismiss the overlay
        // This must happen synchronously, not in the exception handler
        _isAutoConnecting.value = false
        _pendingConnectionCallback = null

        // Cancel the connection coroutine
        connectionJob?.cancel()
        connectionJob = null

        // Clean up BLE (must be done in coroutine since it's a suspend function)
        viewModelScope.launch {
            stopScanning()
            bleRepository.cancelConnection()
            Timber.d("🔴 BLE cleanup complete")
        }

        Timber.d("🔴 Connection cancelled, overlay dismissed")
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
        Timber.d("⚖️ updateWorkoutParameters: weight=${params.weightPerCableKg} kg (${params.weightPerCableKg * 2.20462f} lbs)")
        _workoutParameters.value = params

        // Update session-level eccentric load for cross-exercise persistence
        val workoutType = params.workoutType
        if (workoutType is WorkoutType.Echo) {
            _sessionEccentricLoad.value = workoutType.eccentricLoad
        }

        // Track if user modified parameters during rest (so we preserve their changes for next set)
        if (_workoutState.value is WorkoutState.Resting) {
            Timber.d("⚖️ Parameters modified during rest - will preserve for next set")
            _parametersModifiedDuringRest.value = true
        }
    }

    fun enableHandleDetection() {
        Timber.d("MainViewModel: Enabling handle detection for auto-start")
        bleRepository.enableHandleDetection()
    }

    /**
     * Prepare the ViewModel for Just Lift mode.
     * If a previous workout was in any non-Idle state, reset to Idle so Just Lift can work.
     * This is called when entering the JustLiftScreen.
     */
    fun prepareForJustLift() {
        viewModelScope.launch {
            val currentState = _workoutState.value
            val currentWeight = _workoutParameters.value.weightPerCableKg
            Timber.d("⚖️ prepareForJustLift: BEFORE - weight=$currentWeight kg (${currentWeight * 2.20462f} lbs)")

            if (currentState !is WorkoutState.Idle) {
                Timber.d("Preparing for Just Lift: Resetting from ${currentState::class.simpleName} to Idle")
                resetForNewWorkout()
                _workoutState.value = WorkoutState.Idle
            } else {
                Timber.d("Just Lift already in Idle state, ensuring auto-start is enabled")
            }

            // Set parameters first before enabling handle detection
            _workoutParameters.value = _workoutParameters.value.copy(
                isJustLift = true,
                useAutoStart = true,
                selectedExerciseId = null
            )

            // Enable handle detection - auto-start triggers when user grabs handles
            // (3.0kg force sustained for 200ms as per protocol)
            enableHandleDetection()
            val newWeight = _workoutParameters.value.weightPerCableKg
            Timber.d("⚖️ prepareForJustLift: AFTER - weight=$newWeight kg (${newWeight * 2.20462f} lbs)")
            Timber.d("Just Lift ready: State=Idle, AutoStart=enabled, waiting for handle grab")
        }
    }

    fun startWorkout(skipCountdown: Boolean = false, isJustLiftMode: Boolean = false) {
        Timber.d("$$$ startWorkout() CALLED! skipCountdown=$skipCountdown, isJustLiftMode=$isJustLiftMode $$$")

        // CRITICAL: Re-set rep event callback to ensure it's active for this workout
        // This fixes the issue where callback set in init block may not persist


        viewModelScope.launch {
            // Reset per-session peak tracking
            maxConcentricPerCableKgThisSession = 0f
            maxEccentricPerCableKgThisSession = 0f
            // Forcefully apply the isJustLift flag to ensure it's correct
            val beforeWeight = _workoutParameters.value.weightPerCableKg
            Timber.d("⚖️ startWorkout: BEFORE copy - weight=$beforeWeight kg (${beforeWeight * 2.20462f} lbs)")

            // Check if current exercise is bodyweight BEFORE creating params
            val currentExercise = _loadedRoutine.value?.exercises?.getOrNull(_currentExerciseIndex.value)
            val isBodyweight = isBodyweightExercise(currentExercise)
            val isBodyweightDuration = isBodyweight && currentExercise?.duration != null

            // Issue #218: Don't modify warmupReps in persisted state for bodyweight exercises.
            // Bodyweight exercises are just timers - no BLE commands, no rep counting.
            // Modifying warmupReps here would incorrectly carry over to subsequent cable exercises.
            val params = _workoutParameters.value.copy(
                isJustLift = isJustLiftMode,
                useAutoStart = if (isJustLiftMode) true else _workoutParameters.value.useAutoStart
                // warmupReps intentionally NOT modified - preserve for subsequent cable exercises
            )
            Timber.d("⚖️ startWorkout: AFTER copy - weight=${params.weightPerCableKg} kg (${params.weightPerCableKg * 2.20462f} lbs)")
            Timber.d("⚖️ startWorkout: isBodyweight=$isBodyweight, warmupReps=${params.warmupReps}")

            // Update the state flow so the rest of the app is consistent
            _workoutParameters.value = params

            val workingTarget = if (params.isJustLift) 0 else params.reps
            // For Just Lift mode, preserve position ranges built during handle detection
            // A full reset() would wipe out hasMeaningfulRange() data needed for auto-stop
            if (params.isJustLift) {
                repCounter.resetCountsOnly()
            } else {
                repCounter.reset()
            }
            repCounter.configure(
                warmupTarget = params.warmupReps,
                workingTarget = workingTarget,
                isJustLift = params.isJustLift,
                stopAtTop = params.stopAtTop,
                isAMRAP = params.isAMRAP
            )
            _repCount.value = repCounter.getRepCount()
            resetAutoStopState()
            autoStopStopRequested.set(false)

            currentSessionId = java.util.UUID.randomUUID().toString()
            workoutStartTime = System.currentTimeMillis()
            collectedMetrics.clear()
            maxHeuristicKgMax = 0f // Reset Echo mode force tracking (for history)
            _currentHeuristicKgMax.value = 0f // Reset Echo mode live display

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
            Timber.d(" COUNTDOWN COMPLETE")
            Timber.d("")

            // For bodyweight exercises with duration, skip BLE commands and use timer
            // Issue #218: Expose bodyweight state to UI for proper timer display
            _isCurrentExerciseBodyweight.value = isBodyweightDuration
            if (isBodyweightDuration) {
                val duration = currentExercise?.duration ?: 30
                Timber.d("BODYWEIGHT EXERCISE DETECTED - Starting ${duration}s timer (no BLE commands)")

                _workoutState.value = WorkoutState.Active

                WorkoutForegroundService.startWorkoutService(
                    getContext(),
                    "Bodyweight - ${currentExercise?.exercise?.displayName ?: "Exercise"}",
                    0 // No rep count for bodyweight
                )

                // Emit haptic feedback for workout start
                _hapticEvents.emit(HapticEvent.WORKOUT_START)

                // Start a cancellable countdown timer for bodyweight exercise
                // Issue #218: Emit countdown state to UI so it can display remaining time
                bodyweightTimerJob?.cancel()  // Cancel any existing timer
                bodyweightTimerJob = viewModelScope.launch {
                    for (remaining in duration downTo 1) {
                        _bodyweightTimerState.value = Pair(remaining, duration)
                        delay(1000L)
                    }
                    _bodyweightTimerState.value = null // Timer complete
                    Timber.d("BODYWEIGHT EXERCISE TIMER COMPLETE (${duration}s) - Auto-completing set")
                    handleSetCompletion()
                }
            } else {
                // Normal cable-based exercise - send BLE commands
                Timber.d(" SENDING WORKOUT COMMAND TO CABLES")
                Timber.d("?? TIMING: About to call bleRepository.startWorkout() at ${System.currentTimeMillis()}ms")
                val startTime = System.currentTimeMillis()

                // Set initial baseline position for position bars calibration
                // This ensures bars start at 0% relative to the starting rope position
                _currentMetric.value?.let { metric ->
                    repCounter.setInitialBaseline(metric.positionA, metric.positionB)
                    _repRanges.value = repCounter.getRepRanges()
                    Timber.d("?? POSITION BASELINE: Set initial baseline to posA=${metric.positionA}, posB=${metric.positionB}")
                }

                // FIX Issue #174: Set state to Active BEFORE sending BLE commands
                // This ensures rep notifications are processed immediately when the machine
                // starts responding. Previously, there was a 150ms+ gap between BLE command
                // and Active state where warmup rep notifications were DROPPED, causing
                // users to get stuck showing "0/3" warmup progress.
                _workoutState.value = WorkoutState.Active
                Timber.d("?? TIMING: State set to Active BEFORE BLE command (early activation for rep capture)")

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

                Timber.d("?? TIMING: BLE command succeeded at ${System.currentTimeMillis()}ms (${commandLatency}ms latency)")

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
    }

    fun stopWorkout() {
        viewModelScope.launch {
            val params = _workoutParameters.value
            val isJustLift = params.isJustLift
            val isAMRAP = params.isAMRAP

            // Cancel any running timers to prevent auto-restart
            restTimerJob?.cancel()
            restTimerJob = null
            bodyweightTimerJob?.cancel()
            bodyweightTimerJob = null
            // Issue #218: Clear bodyweight state
            _isCurrentExerciseBodyweight.value = false
            _bodyweightTimerState.value = null

            // Check if current exercise is bodyweight (skip BLE calls if so)
            val currentExercise = _loadedRoutine.value?.exercises?.getOrNull(_currentExerciseIndex.value)
            val isBodyweight = isBodyweightExercise(currentExercise)

            // CRITICAL SAFETY: Stop all active polling and data collection
            // This ensures the machine fully exits workout mode
            // Only send stop command to cables if not bodyweight exercise
            if (!isBodyweight) {
                bleRepository.stopWorkout()
            } else {
                Timber.d("Bodyweight exercise - skipping BLE stop command")
            }

            // Stop foreground service
            WorkoutForegroundService.stopWorkoutService(getApplication())
            _hapticEvents.emit(HapticEvent.WORKOUT_END)

            // Save current progress
            saveWorkoutSession()

            // Just Lift mode: Reset to Idle and re-enable auto-start for next set
            // Issue #121: Manual finish must behave the same as auto-stop
            if (isJustLift) {
                // Reset state
                repCounter.reset()
                resetAutoStopState()

                Timber.d("Just Lift mode: Manual finish - resetting to Idle for next set")
                resetForNewWorkout()
                _workoutState.value = WorkoutState.Idle  // Back to Idle, ready for next set

                Timber.d("Just Lift mode: Re-enabling handle detection for next auto-start")
                enableHandleDetection() // Re-enable for next auto-start

                // Enable velocity-based wake-up detection for next exercise
                bleRepository.enableJustLiftWaitingMode()
                Timber.d("Just Lift mode: Velocity wake-up detection enabled - ready for next set")
            } else if (isAMRAP) {
                // Issue #221: AMRAP mode should show set summary and allow progression to next set
                // This mirrors handleSetCompletion() behavior for auto-stop
                Timber.d("AMRAP mode: Manual finish - showing set summary for progression")

                // Calculate metrics for summary BEFORE reset (per-cable load, not total)
                val peakPerCableKg = if (collectedMetrics.isNotEmpty()) {
                    collectedMetrics.maxOf { it.totalLoad } / 2f
                } else {
                    params.weightPerCableKg
                }
                val averagePerCableKg = if (collectedMetrics.isNotEmpty()) {
                    collectedMetrics.map { it.totalLoad / 2f }.average().toFloat()
                } else {
                    params.weightPerCableKg
                }
                val completedReps = _repCount.value.workingReps

                // Reset state after capturing metrics
                repCounter.reset()
                resetAutoStopState()

                // Show set summary - user can click "Continue" to proceed to next set
                _workoutState.value = WorkoutState.SetSummary(
                    metrics = collectedMetrics.toList(),
                    peakPower = peakPerCableKg,
                    averagePower = averagePerCableKg,
                    repCount = completedReps
                )

                Timber.d("AMRAP set summary: peakPerCableKg=$peakPerCableKg, avgPerCableKg=$averagePerCableKg, reps=$completedReps")
            } else {
                // Reset state
                repCounter.reset()
                resetAutoStopState()

                // Normal mode: Mark as completed
                _workoutState.value = WorkoutState.Completed
            }
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
            val completionStartTime = System.currentTimeMillis()
            Timber.w("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
            Timber.w("AUTOSTOP_TRACE: handleSetCompletion() STARTED at $completionStartTime")
            Timber.w("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")

            val params = _workoutParameters.value
            val isJustLift = params.isJustLift

            // Stop hardware
            Timber.w("AUTOSTOP_TRACE: Calling bleRepository.stopWorkout()...")
            val stopResult = bleRepository.stopWorkout()
            Timber.w("AUTOSTOP_TRACE: stopWorkout() returned: ${stopResult.isSuccess}, failure=${stopResult.exceptionOrNull()?.message}")

            // Just Lift mode: Immediately restart polling to clear machine fault state
            // The machine needs active polling to process the stop command and reset quickly.
            // Without this, the machine stays in fault state (red lights) until polling resumes.
            if (isJustLift) {
                Timber.w("AUTOSTOP_TRACE: Just Lift - immediately restarting polling to clear fault state")
                bleRepository.restartMonitorPolling()
                Timber.w("AUTOSTOP_TRACE: Polling restarted - machine should reset now")
            }

            // Just Lift mode: Don't stop foreground service, keep it running for next set
            if (!isJustLift) {
                WorkoutForegroundService.stopWorkoutService(getApplication())
            }

            _hapticEvents.emit(HapticEvent.WORKOUT_END)

            // Save progress
            saveWorkoutSession()

            // Calculate metrics for summary (per-cable load, not total load across both cables)
            val peakPerCableKg = if (collectedMetrics.isNotEmpty()) {
                collectedMetrics.maxOf { it.totalLoad } / 2f
            } else {
                params.weightPerCableKg
            }

            val averagePerCableKg = if (collectedMetrics.isNotEmpty()) {
                collectedMetrics.map { it.totalLoad / 2f }.average().toFloat()
            } else {
                params.weightPerCableKg
            }

            val completedReps = _repCount.value.workingReps

            // Show set summary for all modes
            _workoutState.value = WorkoutState.SetSummary(
                metrics = collectedMetrics.toList(),
                peakPower = peakPerCableKg,
                averagePower = averagePerCableKg,
                repCount = completedReps
            )

            Timber.d("Set summary: peakPerCableKg=$peakPerCableKg, avgPerCableKg=$averagePerCableKg, reps=$completedReps, metrics=${collectedMetrics.size}")

            // Just Lift mode: Auto-advance to next set after showing summary
            if (isJustLift) {
                Timber.d("⏱️ [${System.currentTimeMillis() - completionStartTime}ms] Just Lift: IMMEDIATE reset for next set (while showing summary)")

                // 1. Reset logical state immediately
                repCounter.reset()
                resetAutoStopState()
                
                // 2. Re-enable machine detection immediately (clears faults, allows instant restart)
                enableHandleDetection() 
                bleRepository.enableJustLiftWaitingMode()
                
                Timber.d("⏱️ [${System.currentTimeMillis() - completionStartTime}ms] Just Lift: Machine armed & ready. Showing summary for 5s...")
                
                // 3. Show summary for 5 seconds (User preference)
                // Note: If user grabs handles during this delay, auto-start logic in handleState collector
                // will interrupt this and start the next set immediately.
                delay(5000) 

                // 4. Transition UI to Idle (only if we haven't already started a new set)
                if (_workoutState.value is WorkoutState.SetSummary) {
                    Timber.d("⏱️ [${System.currentTimeMillis() - completionStartTime}ms] Just Lift: Summary complete, UI transitioning to Idle")
                    resetForNewWorkout() // Ensures clean state
                    _workoutState.value = WorkoutState.Idle
                } else {
                    Timber.d("⏱️ [${System.currentTimeMillis() - completionStartTime}ms] Just Lift: Summary interrupted by user action (state is ${_workoutState.value})")
                }
            } else if (params.isAMRAP) {
                // AMRAP mode: Auto-advance to rest timer and next set (like Just Lift)
                // This mirrors the Just Lift behavior - user can start next set by grabbing handles
                Timber.d("⏱️ [${System.currentTimeMillis() - completionStartTime}ms] AMRAP: Auto-advancing to rest timer")

                // 1. Reset logical state for next set
                repCounter.reset()
                resetAutoStopState()

                // 2. Restart monitor polling to clear machine fault state
                bleRepository.restartMonitorPolling()

                // 3. Enable handle detection for auto-start during rest
                enableHandleDetection()
                bleRepository.enableJustLiftWaitingMode()

                Timber.d("⏱️ [${System.currentTimeMillis() - completionStartTime}ms] AMRAP: Machine armed & ready. Showing summary for 5s...")

                // 4. Show summary for 5 seconds (matches Just Lift behavior)
                delay(5000)

                // 5. Auto-start rest timer if we haven't already started a new set via handle detection
                if (_workoutState.value is WorkoutState.SetSummary) {
                    Timber.d("⏱️ [${System.currentTimeMillis() - completionStartTime}ms] AMRAP: Summary complete, starting rest timer")
                    startRestTimer()
                } else {
                    Timber.d("⏱️ [${System.currentTimeMillis() - completionStartTime}ms] AMRAP: Summary interrupted by user action (state is ${_workoutState.value})")
                }
            }
            // Normal mode or Routine: Wait for user to click "Continue"

            Timber.d("???????????????????????????????????????????????????")
        }
    }

    /**
     * Check if current workout is in single exercise mode.
     * Single exercise mode is when:
     * 1. No routine is loaded (legacy single exercise), OR
     * 2. A temporary routine created by SingleExerciseScreen is loaded
     *    (ID starts with "temp_single_exercise_")
     */
    private fun isSingleExerciseMode(): Boolean {
        val routine = _loadedRoutine.value
        return routine == null || routine.id.startsWith(TEMP_SINGLE_EXERCISE_PREFIX)
    }

    /**
     * Check if the given exercise is a bodyweight exercise.
     * Bodyweight exercises don't use cables and should skip BLE commands and warmup sets.
     * Identified by empty equipment field OR equipment = "bodyweight".
     *
     * @param exercise The routine exercise to check, or null
     * @return true if this is a bodyweight exercise, false otherwise
     */
    private fun isBodyweightExercise(exercise: RoutineExercise?): Boolean {
        return exercise?.let {
            val equipment = it.exercise.equipment
            equipment.isEmpty() || equipment.equals("bodyweight", ignoreCase = true)
        } ?: false
    }

    /**
     * Proceed from set summary to rest timer or completion
     * Called when user clicks "Continue" button on set summary screen
     */
    fun proceedFromSummary() {
        viewModelScope.launch {
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
                // IMPORTANT: isAMRAPExercise is true ONLY if ALL sets in the exercise have null reps
                // If only some sets are AMRAP, this should be FALSE
                val isAMRAPExercise = currentExercise?.isAMRAP == true

                // Log warning if exercise-level isAMRAP might be incorrectly set
                val hasNullReps = currentExercise?.setReps?.any { it == null } == true
                val hasNonNullReps = currentExercise?.setReps?.any { it != null } == true
                if (isAMRAPExercise && hasNonNullReps) {
                    Timber.e("⚠️ BUG DETECTED: exercise.isAMRAP=true but setReps contains non-null values! setReps=${currentExercise?.setReps}")
                }
                if (!isAMRAPExercise && hasNullReps && !hasNonNullReps) {
                    Timber.e("⚠️ BUG DETECTED: exercise.isAMRAP=false but ALL setReps are null! setReps=${currentExercise?.setReps}")
                }

                // Issue #221: AMRAP exercises have a finite number of sets defined by setReps.size
                // The isAMRAP flag only means "user decides when to stop each set" (no rep target)
                // but the SET count is still defined by the number of entries in setReps
                val result = currentExercise != null && _currentSetIndex.value < currentExercise.setReps.size - 1
                Timber.d("  hasMoreSets calculation: currentExercise=$currentExercise, isAMRAPExercise=$isAMRAPExercise, currentSetIndex=${_currentSetIndex.value}, setReps.size=${currentExercise?.setReps?.size}, setReps=${currentExercise?.setReps}, result=$result")
                result
            } ?: false

            val hasMoreExercises = routine?.let {
                val result = _currentExerciseIndex.value < it.exercises.size - 1
                Timber.d("  hasMoreExercises calculation: currentExerciseIndex=${_currentExerciseIndex.value}, exercises.size=${it.exercises.size}, result=$result")
                result
            } ?: false

            // 2. Single Exercise mode (not Just Lift, includes temp routines from SingleExerciseScreen)
            val isSingleExercise = isSingleExerciseMode() && !isJustLift
            // Only show rest timer if there are actually more sets/exercises remaining
            val shouldShowRestTimer = (hasMoreSets || hasMoreExercises) && !isJustLift

            Timber.d("Decision:")
            Timber.d("  hasMoreSets = $hasMoreSets")
            Timber.d("  hasMoreExercises = $hasMoreExercises")
            Timber.d("  isSingleExercise = $isSingleExercise")
            Timber.d("  shouldShowRestTimer = $shouldShowRestTimer")
            Timber.d("???????????????????????????????????????????????????")

            // Show rest timer if there are more sets/exercises OR in single exercise mode
            // (regardless of autoplay preference)
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
            val routine = _loadedRoutine.value
            val currentExercise = routine?.exercises?.getOrNull(_currentExerciseIndex.value)

            // Determine rest duration and autoplay
            // Use per-set rest time for the set that was just completed
            val completedSetIndex = _currentSetIndex.value
            val restDuration = currentExercise?.getRestForSet(completedSetIndex)?.takeIf { it > 0 } ?: 90
            val autoplay = userPreferences.value.autoplayEnabled

            // For single exercise mode (no routine or temp routine), we always "continue" the same exercise
            val isSingleExercise = isSingleExerciseMode()

            Timber.d("???????????????????????????????????????????????????")
            Timber.d("REST TIMER STARTING")
            if (isSingleExercise) {
                Timber.d("  Mode: Single Exercise")
            } else {
                Timber.d("  Exercise: ${currentExercise?.exercise?.displayName}")
                Timber.d("  Current set: ${_currentSetIndex.value + 1}/${currentExercise?.setReps?.size}")
            }
            Timber.d("  Rest duration: ${restDuration}s")
            Timber.d("  Autoplay enabled: $autoplay")
            Timber.d("???????????????????????????????????????????????????")

            for (i in restDuration downTo 1) {
                val nextName = if (isSingleExercise) {
                    "Next Set"
                } else {
                    val isLastSet = _currentSetIndex.value >= (currentExercise?.setReps?.size ?: 0) - 1
                    val nextExercise = routine?.exercises?.getOrNull(_currentExerciseIndex.value + 1)
                    if (isLastSet) {
                        nextExercise?.exercise?.name ?: "Workout Complete"
                    } else {
                        "Set ${_currentSetIndex.value + 2} of ${currentExercise?.exercise?.name}"
                    }
                }

                _workoutState.value = if (isSingleExercise) {
                    // For single exercise temp routines, show proper set count
                    WorkoutState.Resting(
                        restSecondsRemaining = i,
                        nextExerciseName = nextName,
                        isLastExercise = false,
                        currentSet = _currentSetIndex.value + 1,
                        totalSets = currentExercise?.setReps?.size ?: 0
                    )
                } else {
                    val isLastSet = _currentSetIndex.value >= (currentExercise?.setReps?.size ?: 0) - 1
                    val nextExercise = routine?.exercises?.getOrNull(_currentExerciseIndex.value + 1)
                    WorkoutState.Resting(
                        restSecondsRemaining = i,
                        nextExerciseName = nextName,
                        isLastExercise = isLastSet && nextExercise == null,
                        currentSet = _currentSetIndex.value + 1,
                        totalSets = currentExercise?.setReps?.size ?: 0
                    )
                }

                // Play "rest ending" sound at 5 seconds remaining
                if (i == 5) {
                    _hapticEvents.emit(HapticEvent.REST_ENDING)
                }

                delay(1000)
            }

            // Only auto-advance if autoplay is enabled
            // If autoplay is disabled, user must manually start next set via skipRest()
            if (autoplay) {
                Timber.d("Autoplay enabled - starting next set/exercise")
                if (isSingleExercise) {
                    // For single exercise, only restart if there are more sets remaining
                    // Otherwise this creates an infinite loop
                    val currentExerciseSets = routine?.exercises?.getOrNull(_currentExerciseIndex.value)
                    if (currentExerciseSets != null && _currentSetIndex.value < currentExerciseSets.setReps.size - 1) {
                        _currentSetIndex.value++
                        // CRITICAL: Update workout parameters for the new set (Issue #147 fix)
                        val targetReps = currentExerciseSets.setReps[_currentSetIndex.value]

                        // Check if user modified parameters during rest
                        val userModified = _parametersModifiedDuringRest.value
                        if (userModified) {
                            Timber.d("Autoplay: User modified parameters during rest - preserving their changes")
                        }

                        val setWeight = if (userModified) {
                            workoutParameters.value.weightPerCableKg
                        } else {
                            currentExerciseSets.setWeightsPerCableKg.getOrNull(_currentSetIndex.value)
                                ?: currentExerciseSets.weightPerCableKg
                        }
                        val finalReps = if (userModified) {
                            workoutParameters.value.reps
                        } else {
                            targetReps ?: 0
                        }

                        Timber.d("Autoplay: Advancing to set ${_currentSetIndex.value + 1}, targetReps=$finalReps, isAMRAP=${!userModified && targetReps == null}")
                        _workoutParameters.value = workoutParameters.value.copy(
                            reps = finalReps,
                            weightPerCableKg = setWeight,
                            isAMRAP = if (userModified) workoutParameters.value.isAMRAP else (targetReps == null)
                        )
                        // Reset the flag after applying user changes
                        _parametersModifiedDuringRest.value = false
                        startWorkout(skipCountdown = true)
                    } else {
                        Timber.d("Single exercise complete - no more sets remaining")
                        _workoutState.value = WorkoutState.Completed
                    }
                } else {
                    startNextSetOrExercise()
                }
            } else {
                // Stay in resting state with 0 seconds remaining
                // User will see "Start Next Set" button in UI
                Timber.d("Autoplay disabled - staying in resting state")

                // Recalculate next exercise info after loop ends
                val nextNameFinal = if (isSingleExercise) {
                    "Next Set"
                } else {
                    val isLastSet = _currentSetIndex.value >= (currentExercise?.setReps?.size ?: 0) - 1
                    val nextExercise = routine?.exercises?.getOrNull(_currentExerciseIndex.value + 1)
                    if (isLastSet) {
                        nextExercise?.exercise?.name ?: "Workout Complete"
                    } else {
                        "Set ${_currentSetIndex.value + 2} of ${currentExercise?.exercise?.name}"
                    }
                }

                _workoutState.value = if (isSingleExercise) {
                    WorkoutState.Resting(
                        restSecondsRemaining = 0,
                        nextExerciseName = nextNameFinal,
                        isLastExercise = false,
                        currentSet = 0,
                        totalSets = 0
                    )
                } else {
                    val isLastSet = _currentSetIndex.value >= (currentExercise?.setReps?.size ?: 0) - 1
                    val nextExercise = routine?.exercises?.getOrNull(_currentExerciseIndex.value + 1)
                    WorkoutState.Resting(
                        restSecondsRemaining = 0,
                        nextExerciseName = nextNameFinal,
                        isLastExercise = isLastSet && nextExercise == null,
                        currentSet = _currentSetIndex.value + 1,
                        totalSets = currentExercise?.setReps?.size ?: 0
                    )
                }
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

            // Check if user modified parameters during rest - if so, use their values
            val userModified = _parametersModifiedDuringRest.value
            if (userModified) {
                Timber.d("  User modified parameters during rest - preserving their changes")
                Timber.d("  Using user weight: ${workoutParameters.value.weightPerCableKg} kg")
                Timber.d("  Using user reps: ${workoutParameters.value.reps}")
            }

            // Get per-set weight, falling back to exercise default (Issue #147)
            val setWeight = if (userModified) {
                workoutParameters.value.weightPerCableKg
            } else {
                currentExercise.setWeightsPerCableKg.getOrNull(_currentSetIndex.value)
                    ?: currentExercise.weightPerCableKg
            }
            val finalReps = if (userModified) {
                workoutParameters.value.reps
            } else {
                targetReps ?: 0
            }

            Timber.d("  New set index: ${_currentSetIndex.value}")
            Timber.d("  Target reps: $finalReps")
            Timber.d("  Set weight: $setWeight kg")
            _workoutParameters.value = workoutParameters.value.copy(
                reps = finalReps,
                weightPerCableKg = setWeight,
                progressionRegressionKg = workoutParameters.value.progressionRegressionKg,
                workoutType = workoutParameters.value.workoutType,
                selectedExerciseId = workoutParameters.value.selectedExerciseId,
                isAMRAP = if (userModified) workoutParameters.value.isAMRAP else (targetReps == null)
            )
            // Reset the flag after applying user changes
            _parametersModifiedDuringRest.value = false
            Timber.d("  AFTER UPDATE - isAMRAP set to: ${_workoutParameters.value.isAMRAP} (targetReps was: $targetReps)")
            Timber.d("  AFTER UPDATE - reps set to: ${_workoutParameters.value.reps}")
            Timber.d("???????????????????????????????????????????????????")
            startWorkout(skipCountdown = true)
        } else {
            // Move to next exercise
            Timber.d("  No more sets in current exercise")
            if (_currentExerciseIndex.value < routine.exercises.size - 1) {
                Timber.d("  ? Moving to next exercise")
                _currentExerciseIndex.value++
                _currentSetIndex.value = 0
                // Reset user modification flag - new exercise uses its own parameters
                _parametersModifiedDuringRest.value = false
                // Update workout parameters for new exercise
                val nextExercise = routine.exercises[_currentExerciseIndex.value]
                val nextSetReps = nextExercise.setReps.getOrNull(0)
                // Get per-set weight for first set, falling back to exercise default (Issue #147)
                val nextSetWeight = nextExercise.setWeightsPerCableKg.getOrNull(0)
                    ?: nextExercise.weightPerCableKg
                Timber.d("  New exercise index: ${_currentExerciseIndex.value}")
                Timber.d("  Next exercise: ${nextExercise.exercise.displayName}")
                Timber.d("  First set weight: $nextSetWeight kg")
                _workoutParameters.value = workoutParameters.value.copy(
                    weightPerCableKg = nextSetWeight, // Use per-set weight (Issue #147)
                    reps = nextSetReps ?: 0, // AMRAP sets have null reps
                    workoutType = nextExercise.workoutType,
                    progressionRegressionKg = nextExercise.progressionKg,
                    selectedExerciseId = nextExercise.exercise.id,
                    isAMRAP = nextSetReps == null // First SET is AMRAP if its reps is null
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
                currentRoutineSessionId = null
                currentRoutineName = null
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
        Timber.d("  Loaded routine: ${_loadedRoutine.value?.name ?: "None (single exercise mode)"}")
        Timber.d("???????????????????????????????????????????????????")

        if (_workoutState.value is WorkoutState.Resting) {
            // Cancel the rest timer
            restTimerJob?.cancel()
            restTimerJob = null

            Timber.d("Rest timer cancelled, starting next set/exercise")

            // Check if this is single exercise mode (no routine or temp routine from SingleExerciseScreen)
            val isSingleExercise = isSingleExerciseMode() && !_workoutParameters.value.isJustLift
            if (isSingleExercise) {
                // For single exercise, advance to next set with proper parameter updates
                val routine = _loadedRoutine.value
                val currentExercise = routine?.exercises?.getOrNull(_currentExerciseIndex.value)
                if (currentExercise != null && _currentSetIndex.value < currentExercise.setReps.size - 1) {
                    _currentSetIndex.value++
                    val targetReps = currentExercise.setReps[_currentSetIndex.value]

                    // Check if user modified parameters during rest
                    val userModified = _parametersModifiedDuringRest.value
                    if (userModified) {
                        Timber.d("skipRest: User modified parameters during rest - preserving their changes")
                    }

                    val setWeight = if (userModified) {
                        workoutParameters.value.weightPerCableKg
                    } else {
                        currentExercise.setWeightsPerCableKg.getOrNull(_currentSetIndex.value)
                            ?: currentExercise.weightPerCableKg
                    }
                    val finalReps = if (userModified) {
                        workoutParameters.value.reps
                    } else {
                        targetReps ?: 0
                    }

                    Timber.d("skipRest: Advancing to set ${_currentSetIndex.value + 1}, targetReps=$finalReps, isAMRAP=${!userModified && targetReps == null}")
                    _workoutParameters.value = workoutParameters.value.copy(
                        reps = finalReps,
                        weightPerCableKg = setWeight,
                        isAMRAP = if (userModified) workoutParameters.value.isAMRAP else (targetReps == null)
                    )
                    // Reset the flag after applying user changes
                    _parametersModifiedDuringRest.value = false
                    startWorkout(skipCountdown = true)
                } else {
                    Timber.d("skipRest: Single exercise complete - no more sets remaining")
                    _workoutState.value = WorkoutState.Completed
                }
            } else {
                startNextSetOrExercise()
            }
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
            val nextSetReps = nextExercise.setReps.getOrNull(0)
            // Get per-set weight for first set, falling back to exercise default (Issue #147)
            val nextSetWeight = nextExercise.setWeightsPerCableKg.getOrNull(0)
                ?: nextExercise.weightPerCableKg
            _workoutParameters.value = workoutParameters.value.copy(
                weightPerCableKg = nextSetWeight, // Use per-set weight (Issue #147)
                reps = nextSetReps ?: 0, // AMRAP sets have null reps
                workoutType = nextExercise.workoutType,
                progressionRegressionKg = nextExercise.progressionKg,
                selectedExerciseId = nextExercise.exercise.id,
                isAMRAP = nextSetReps == null // First SET is AMRAP if its reps is null
            )

            // Start the next exercise
            startWorkout(skipCountdown = true)
        }
    }

    private fun resetAutoStopState() {
        autoStopStartTime = null
        stallDetectionStartTime = null  // Reset stall detection timer (Issue #204)
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

        // Store per-cable weight for history/PRs.
        // For Echo mode: use heuristic data (kgMax) which is the actual measured force
        // For other modes: use totalLoad / 2 from workout metrics
        val isEchoMode = params.workoutType is WorkoutType.Echo
        val measuredPerCableKg = if (isEchoMode && maxHeuristicKgMax > 0f) {
            // Echo mode: kgMax from heuristic data is already per-cable force in kg
            Timber.d("Echo mode: using heuristic kgMax=$maxHeuristicKgMax kg for measured weight")
            maxHeuristicKgMax
        } else if (collectedMetrics.isNotEmpty()) {
            // Other modes: derive from device output (totalLoad / 2)
            collectedMetrics.maxOf { it.totalLoad } / 2f
        } else {
            params.weightPerCableKg // Fallback to configured if no metrics
        }

        // Use measuredPerCableKg for history display so it shows actual weight lifted,
        // not the configured weight (which may differ from what the device measured)

        val (eccentricLoad, echoLevel) = when (val wt = params.workoutType) {
            is WorkoutType.Echo -> wt.eccentricLoad.percentage to wt.level.levelValue
            is WorkoutType.Program -> 100 to 2 // Defaults for program modes
        }

        // Get exercise name for history display (avoids DB lookups later)
        val exerciseName = if (params.isJustLift) {
            null // Just Lift mode doesn't have an exercise name
        } else {
            // Try to get from loaded routine first
            _loadedRoutine.value?.exercises?.getOrNull(_currentExerciseIndex.value)?.exercise?.name
                // Fallback: lookup by ID from exercise repository
                ?: params.selectedExerciseId?.let { exerciseRepository.getExerciseById(it)?.name }
        }

        // For Single Exercise mode, don't save routineSessionId - these should appear as single sessions in history
        // Regular routines (Daily Routines) should be grouped together
        val isSingleExercise = isSingleExerciseMode() && !params.isJustLift
        val savedRoutineSessionId = if (isSingleExercise) null else currentRoutineSessionId
        val savedRoutineName = if (isSingleExercise) null else currentRoutineName

        val session = WorkoutSession(
            id = sessionId,
            timestamp = workoutStartTime,
            mode = params.workoutType.displayName,
            reps = params.reps,
            weightPerCableKg = measuredPerCableKg, // Store actual measured weight for history
            progressionKg = params.progressionRegressionKg,
            duration = duration,
            totalReps = working,  // Exclude warm-up reps from total count
            warmupReps = warmup,
            workingReps = working,
            isJustLift = params.isJustLift,
            stopAtTop = params.stopAtTop,
            eccentricLoad = eccentricLoad,
            echoLevel = echoLevel,
            exerciseId = params.selectedExerciseId,
            exerciseName = exerciseName,
            routineSessionId = savedRoutineSessionId,
            routineName = savedRoutineName
        )

        // Check if this is a bodyweight exercise (for logging purposes)
        val currentExercise = _loadedRoutine.value?.exercises?.getOrNull(_currentExerciseIndex.value)
        val isBodyweight = isBodyweightExercise(currentExercise)

        Timber.d("💾 SAVING WORKOUT SESSION:")
        Timber.d("  sessionId: $sessionId")
        Timber.d("  mode: ${params.workoutType.displayName}")
        Timber.d("  reps (target): ${params.reps}")
        Timber.d("  totalReps (actual): $working")
        Timber.d("  isAMRAP: ${params.isAMRAP}")
        Timber.d("  isBodyweight: $isBodyweight")
        Timber.d("  exerciseId: ${params.selectedExerciseId}")
        Timber.d("  isSingleExercise: $isSingleExercise (routineSessionId will be null for history)")
        Timber.d("  savedRoutineSessionId: $savedRoutineSessionId")
        Timber.d("  savedRoutineName: $savedRoutineName")
        Timber.d("  _loadedRoutine.value: ${_loadedRoutine.value?.name}")
        Timber.d("  _loadedRoutine.value.id: ${_loadedRoutine.value?.id}")
        Timber.d("  maxConcentricPerCableKgThisSession=$maxConcentricPerCableKgThisSession, maxEccentricPerCableKgThisSession=$maxEccentricPerCableKgThisSession")
        if (isBodyweight) {
            Timber.d("  Note: Bodyweight exercise - working reps is 0 (expected), duration tracked instead")
        }

        workoutRepository.saveSession(session)

        if (collectedMetrics.isNotEmpty()) {
            workoutRepository.saveMetrics(sessionId, collectedMetrics)
        }

        // Save exercise defaults for next time (only for Just Lift and Single Exercise modes)
        // Routines have their own saved configuration and should not interfere with these defaults
        if (params.isJustLift) {
            saveJustLiftDefaultsFromWorkout()
        } else if (isSingleExerciseMode()) {
            saveSingleExerciseDefaultsFromWorkout()
        }

        // Track personal record if exercise is selected
        params.selectedExerciseId?.let { exerciseId ->
            // Only track PRs for completed working sets (not warmups, not just lift, not echo mode)
            // Echo mode is excluded because it uses adaptive weight (machine calculates)
            if (working > 0 && !params.isJustLift && !isEchoMode) {
                val brokenPRs = workoutRepository.updatePersonalRecordsIfNeeded(
                    exerciseId = exerciseId,
                    weightPerCableKg = measuredPerCableKg,
                    reps = working,
                    workoutMode = params.workoutType.displayName
                )
                if (brokenPRs.isNotEmpty()) {
                    Timber.d("NEW PERSONAL RECORD(S)! Exercise: $exerciseId, Weight: ${measuredPerCableKg}kg, Reps: $working, Types: $brokenPRs")
                    // Trigger PR celebration
                    viewModelScope.launch {
                        try {
                            val exercise = exerciseRepository.getExerciseById(exerciseId)
                            _prCelebrationEvent.emit(
                                PRCelebrationEvent(
                                    exerciseName = exercise?.name ?: "Unknown Exercise",
                                    weightPerCableKg = measuredPerCableKg,
                                    reps = working,
                                    workoutMode = params.workoutType.displayName,
                                    brokenPRTypes = brokenPRs
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

    // Data Export/Import Functions
    fun exportAllData() {
        viewModelScope.launch {
            _isExporting.value = true
            try {
                val backup = dataBackupManager.exportAllData()
                val uriResult = dataBackupManager.saveToCache(backup)

                uriResult.onSuccess { uri ->
                    // Create share intent
                    val context = getContext()
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                    val chooserIntent = Intent.createChooser(shareIntent, "Export Workout Data")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooserIntent)

                    Timber.d("Export successful: ${backup.data.workoutSessions.size} sessions, ${backup.data.routines.size} routines")
                }.onFailure { e ->
                    Timber.e(e, "Failed to save backup file")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to export data")
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun importFromUri(uri: Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            try {
                val result = dataBackupManager.importFromUri(uri)

                result.onSuccess { importResult ->
                    _importResult.value = importResult
                    _showImportResultDialog.value = true

                    Timber.d("Import successful: ${importResult.sessionsImported} sessions, ${importResult.routinesImported} routines")

                    // Workout history will automatically refresh via Flow collection
                }.onFailure { e ->
                    Timber.e(e, "Failed to import data")
                    _importResult.value = ImportResult(
                        sessionsImported = 0,
                        sessionsSkipped = 0,
                        metricsImported = 0,
                        routinesImported = 0,
                        routinesSkipped = 0,
                        routineExercisesImported = 0,
                        programsImported = 0,
                        programsSkipped = 0,
                        programDaysImported = 0,
                        personalRecordsImported = 0,
                        personalRecordsSkipped = 0
                    )
                    _showImportResultDialog.value = true
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to import data")
                _importResult.value = ImportResult(
                    sessionsImported = 0,
                    sessionsSkipped = 0,
                    metricsImported = 0,
                    routinesImported = 0,
                    routinesSkipped = 0,
                    routineExercisesImported = 0,
                    programsImported = 0,
                    programsSkipped = 0,
                    programDaysImported = 0,
                    personalRecordsImported = 0,
                    personalRecordsSkipped = 0
                )
                _showImportResultDialog.value = true
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun dismissImportResult() {
        _showImportResultDialog.value = false
        _importResult.value = null
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

    fun setEnableVideoPlayback(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setEnableVideoPlayback(enabled)
        }
    }

    fun setBeepsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setBeepsEnabled(enabled)
        }
    }

    // ========== Just Lift & Single Exercise Defaults ==========

    /**
     * Get Just Lift defaults from preferences
     * Returns null if no defaults have been saved yet
     */
    suspend fun getJustLiftDefaults(): com.example.vitruvianredux.data.preferences.JustLiftDefaults? {
        return preferencesManager.getJustLiftDefaults()
    }

    /**
     * Get Single Exercise defaults for a specific exercise+cableConfig combination
     * Returns null if no defaults have been saved for this combination
     */
    suspend fun getSingleExerciseDefaults(
        exerciseId: String,
        cableConfig: String
    ): com.example.vitruvianredux.data.preferences.SingleExerciseDefaults? {
        return preferencesManager.getSingleExerciseDefaults(exerciseId, cableConfig)
    }

    /**
     * Save Just Lift defaults after completing a Just Lift workout
     * Called from saveWorkoutSession when isJustLift is true
     */
    private suspend fun saveJustLiftDefaultsFromWorkout() {
        val params = _workoutParameters.value
        if (!params.isJustLift) return

        val (eccentricLoad, echoLevel) = when (val wt = params.workoutType) {
            is WorkoutType.Echo -> wt.eccentricLoad.percentage to wt.level.levelValue
            is WorkoutType.Program -> 100 to 1
        }

        try {
            val defaults = com.example.vitruvianredux.data.preferences.JustLiftDefaults(
                workoutModeId = com.example.vitruvianredux.data.preferences.WorkoutModeId.fromWorkoutType(params.workoutType),
                weightPerCableKg = params.weightPerCableKg.coerceAtLeast(0.1f),
                // Use roundToInt() to minimize accumulating rounding errors when converting between units
                weightChangePerRep = kotlin.math.round(params.progressionRegressionKg).toInt(),
                eccentricLoadPercentage = eccentricLoad,
                echoLevelValue = echoLevel
            )
            preferencesManager.saveJustLiftDefaults(defaults)
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "Failed to save Just Lift defaults - validation error")
        }
    }

    /**
     * Save Single Exercise defaults after completing a single exercise workout
     * Called from saveWorkoutSession when in Single Exercise mode (temp routine)
     */
    private suspend fun saveSingleExerciseDefaultsFromWorkout() {
        val routine = _loadedRoutine.value ?: return

        // Only save for temp single exercise routines, not for regular routines
        if (!routine.id.startsWith(TEMP_SINGLE_EXERCISE_PREFIX)) return

        val currentExercise = routine.exercises.getOrNull(_currentExerciseIndex.value) ?: return
        val exerciseId = currentExercise.exercise.id ?: return

        val (eccentricLoad, echoLevel) = when (val wt = currentExercise.workoutType) {
            is WorkoutType.Echo -> wt.eccentricLoad.percentage to wt.level.levelValue
            is WorkoutType.Program -> 100 to 1
        }

        try {
            val setReps = currentExercise.setReps.ifEmpty { listOf(10) }
            val numSets = setReps.size

            // Normalize setWeightsPerCableKg to match setReps size (or be empty)
            val normalizedSetWeights = when {
                currentExercise.setWeightsPerCableKg.isEmpty() -> emptyList()
                currentExercise.setWeightsPerCableKg.size == numSets -> currentExercise.setWeightsPerCableKg
                else -> emptyList() // Reset if invalid size
            }

            // Normalize setRestSeconds to match setReps size (or be empty)
            val normalizedSetRest = when {
                currentExercise.setRestSeconds.isEmpty() -> emptyList()
                currentExercise.setRestSeconds.size == numSets -> currentExercise.setRestSeconds
                else -> emptyList() // Reset if invalid size
            }

            val defaults = com.example.vitruvianredux.data.preferences.SingleExerciseDefaults(
                exerciseId = exerciseId,
                cableConfig = currentExercise.cableConfig.name,
                workoutModeId = com.example.vitruvianredux.data.preferences.WorkoutModeId.fromWorkoutType(currentExercise.workoutType),
                setReps = setReps,
                weightPerCableKg = currentExercise.weightPerCableKg.coerceAtLeast(0f),
                setWeightsPerCableKg = normalizedSetWeights,
                progressionKg = currentExercise.progressionKg.coerceIn(-50f, 50f),
                setRestSeconds = normalizedSetRest,
                perSetRestTime = currentExercise.perSetRestTime,
                eccentricLoadPercentage = eccentricLoad,
                echoLevelValue = echoLevel,
                duration = currentExercise.duration?.takeIf { it > 0 },
                isAMRAP = currentExercise.isAMRAP
            )
            preferencesManager.saveSingleExerciseDefaults(defaults)
            Timber.d("Saved Single Exercise defaults for ${currentExercise.exercise.name} (${currentExercise.cableConfig})")
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "Failed to save Single Exercise defaults - validation error")
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
        _repRanges.value = null
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

        // Generate a new routine session ID for grouping all sets from this routine
        currentRoutineSessionId = java.util.UUID.randomUUID().toString()
        currentRoutineName = routine.name
        Timber.d("✅ ROUTINE LOADED: Set currentRoutineSessionId = $currentRoutineSessionId, routineName = $currentRoutineName")

        // Load parameters from first exercise
        val firstExercise = routine.exercises[0]
        val firstSetReps = firstExercise.setReps.firstOrNull() // Can be null for AMRAP sets
        // Get per-set weight for first set, falling back to exercise default (Issue #147)
        val firstSetWeight = firstExercise.setWeightsPerCableKg.getOrNull(0)
            ?: firstExercise.weightPerCableKg

        Timber.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Timber.d("LOADING ROUTINE: ${routine.name}")
        Timber.d("  ID: ${routine.id}")
        Timber.d("  Total exercises: ${routine.exercises.size}")
        Timber.d("  First exercise: ${firstExercise.exercise.displayName}")
        Timber.d("  Sets: ${firstExercise.setReps.size} (${firstExercise.setReps})")
        Timber.d("  Weight (exercise default): ${firstExercise.weightPerCableKg}kg")
        Timber.d("  First set weight: ${firstSetWeight}kg")
        Timber.d("  First set reps: $firstSetReps")
        Timber.d("  Setting isJustLift = false")
        Timber.d("")
        Timber.d("WORKOUT TYPE DETAILS:")
        Timber.d("  Type: ${firstExercise.workoutType.displayName}")
        when (val wt = firstExercise.workoutType) {
            is com.example.vitruvianredux.domain.model.WorkoutType.Echo -> {
                Timber.d("  ✓ Echo mode detected!")
                Timber.d("    Level: ${wt.level.displayName} (levelValue=${wt.level.levelValue})")
                Timber.d("    Eccentric Load: ${wt.eccentricLoad.displayName} (${wt.eccentricLoad.percentage}%)")
            }
            is com.example.vitruvianredux.domain.model.WorkoutType.Program -> {
                Timber.d("  Program mode: ${wt.mode.displayName}")
            }
        }
        Timber.d("  First exercise isAMRAP: ${firstExercise.isAMRAP}")
        Timber.d("  First set reps: $firstSetReps")
        Timber.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        val params = WorkoutParameters(
            workoutType = firstExercise.workoutType, // Use workout type from the exercise
            reps = firstSetReps ?: 0, // AMRAP sets have null reps, use 0 as placeholder
            weightPerCableKg = firstSetWeight, // Use per-set weight (Issue #147)
            progressionRegressionKg = firstExercise.progressionKg,
            isJustLift = false,  // CRITICAL: Routines are NOT just lift mode (enables autoplay)
            stopAtTop = stopAtTop.value,   // Use user preference from settings
            warmupReps = _workoutParameters.value.warmupReps,
            isAMRAP = firstSetReps == null, // This SET is AMRAP if its reps is null
            selectedExerciseId = firstExercise.exercise.id // Set exercise ID for history tracking
        )

        Timber.d("Created WorkoutParameters:")
        Timber.d("  isAMRAP: ${params.isAMRAP}")
        Timber.d("  workoutType.displayName: ${params.workoutType.displayName}")
        when (val wt = params.workoutType) {
            is com.example.vitruvianredux.domain.model.WorkoutType.Echo -> {
                Timber.d("  Echo level: ${wt.level.displayName}")
                Timber.d("  Eccentric load: ${wt.eccentricLoad.displayName}")
            }
            else -> {}
        }

        updateWorkoutParameters(params)

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
        currentRoutineSessionId = null
        currentRoutineName = null
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
        private const val AUTO_STOP_DURATION_SECONDS = 2.5f  // User observation: ~2.5 seconds (snappier than 5s)

        // Velocity-based stall detection constants (Issue #204)
        // Two-tier hysteresis matching official app (<2.5 stalled, >10 moving):
        // - Below LOW (<2.5): start/continue stall timer (user is stopped)
        // - Above HIGH (>10): reset stall timer (user is clearly moving)
        // - Between LOW and HIGH (≥2.5 and ≤10): maintain current state (prevents toggling)
        private const val STALL_VELOCITY_LOW = 2.5    // Below this = definitely stalled (mm/s)
        private const val STALL_VELOCITY_HIGH = 10.0  // Above this = definitely moving (mm/s)
        private const val STALL_DURATION_SECONDS = 5.0f    // How long to stall before auto-stop triggers
        // Min position to consider handles "in use" (Issue #204 fix)
        // Prevents false auto-stop when handles are at rest position near 0
        private const val STALL_MIN_POSITION = 10.0        // Position > this = handles are being used (mm)

        /** Prefix for temporary routines created in Single Exercise mode */
        const val TEMP_SINGLE_EXERCISE_PREFIX = "temp_single_exercise_"
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

