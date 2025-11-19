package com.example.vitruvianredux.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.example.vitruvianredux.domain.model.EccentricLoad
import com.example.vitruvianredux.domain.model.EchoLevel
import com.example.vitruvianredux.domain.model.RoutineExercise
import com.example.vitruvianredux.domain.model.WeightUnit
import com.example.vitruvianredux.domain.model.WorkoutMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject


// These are tightly coupled with the ExerciseEditDialog, so keeping them here is reasonable.
// They could be moved to a dedicated file in the `presentation.screen` package if used elsewhere.
enum class ExerciseType {
    BODYWEIGHT,
    STANDARD
}

enum class SetMode {
    REPS,
    DURATION
}

data class SetConfiguration(
    val id: String = UUID.randomUUID().toString(), // Stable ID for Compose keys
    val setNumber: Int,
    val reps: Int? = 10,  // Nullable to support AMRAP (null = AMRAP)
    val weightPerCable: Float = 15.0f,
    val duration: Int = 30,
    val restSeconds: Int = 60 // Add this
)

@HiltViewModel
class ExerciseConfigViewModel @Inject constructor() : ViewModel() {

    private val _initialized = MutableStateFlow(false)

    // Dependencies that need to be passed in
    private lateinit var originalExercise: RoutineExercise
    private lateinit var weightUnit: WeightUnit
    private lateinit var kgToDisplay: (Float, WeightUnit) -> Float
    private lateinit var displayToKg: (Float, WeightUnit) -> Float

    private val _exerciseType = MutableStateFlow(ExerciseType.STANDARD)
    val exerciseType: StateFlow<ExerciseType> = _exerciseType.asStateFlow()

    private val _setMode = MutableStateFlow(SetMode.REPS)
    val setMode: StateFlow<SetMode> = _setMode.asStateFlow()

    private val _sets = MutableStateFlow<List<SetConfiguration>>(emptyList())
    val sets: StateFlow<List<SetConfiguration>> = _sets.asStateFlow()

    private val _selectedMode = MutableStateFlow<WorkoutMode>(WorkoutMode.OldSchool)
    val selectedMode: StateFlow<WorkoutMode> = _selectedMode.asStateFlow()

    private val _weightChange = MutableStateFlow(0)
    val weightChange: StateFlow<Int> = _weightChange.asStateFlow()

    private val _rest = MutableStateFlow(60)
    val rest: StateFlow<Int> = _rest.asStateFlow()

    private val _perSetRestTime = MutableStateFlow(false)
    val perSetRestTime: StateFlow<Boolean> = _perSetRestTime.asStateFlow()

    private val _eccentricLoad = MutableStateFlow(EccentricLoad.LOAD_100)
    val eccentricLoad: StateFlow<EccentricLoad> = _eccentricLoad.asStateFlow()

    private val _echoLevel = MutableStateFlow(EchoLevel.HARDER)
    val echoLevel: StateFlow<EchoLevel> = _echoLevel.asStateFlow()

    init {

    }

    fun initialize(
        exercise: RoutineExercise,
        unit: WeightUnit,
        toDisplay: (Float, WeightUnit) -> Float,
        toKg: (Float, WeightUnit) -> Float,
        prWeightKg: Float? = null  // Optional PR weight to use as default
    ) {
        if (_initialized.value && originalExercise.id == exercise.id) {
            return
        }

        originalExercise = exercise
        weightUnit = unit
        kgToDisplay = toDisplay
        displayToKg = toKg

        _exerciseType.value = if (exercise.exercise.equipment.isEmpty() ||
            exercise.exercise.equipment.equals("bodyweight", ignoreCase = true)) {
            ExerciseType.BODYWEIGHT
        } else {
            ExerciseType.STANDARD
        }

        // Force DURATION mode for bodyweight exercises, otherwise use existing duration setting
        _setMode.value = if (_exerciseType.value == ExerciseType.BODYWEIGHT) {
            SetMode.DURATION
        } else if (exercise.duration != null) {
            SetMode.DURATION
        } else {
            SetMode.REPS
        }

        // Use PR weight as default if available, otherwise use 15kg
        val defaultWeightKg = prWeightKg ?: 15f

        // Determine default duration and log if defaulting for bodyweight exercises
        val defaultDuration = if (exercise.duration != null) {
            exercise.duration
        } else {
            if (_exerciseType.value == ExerciseType.BODYWEIGHT) {
                Timber.w("Bodyweight exercise '${exercise.exercise.name}' missing duration - defaulting to 30s")
            }
            30
        }

        val initialSets = exercise.setReps.mapIndexed { index, reps ->
            val perSetWeightKg = exercise.setWeightsPerCableKg.getOrNull(index) ?: exercise.weightPerCableKg
            val perSetRest = exercise.setRestSeconds.getOrNull(index) ?: 60
            SetConfiguration(
                id = UUID.randomUUID().toString(),
                setNumber = index + 1,
                reps = reps, // Preserve null for AMRAP sets
                weightPerCable = kgToDisplay(perSetWeightKg, weightUnit),
                duration = defaultDuration,
                restSeconds = perSetRest
            )
        }.ifEmpty {
            listOf(
                SetConfiguration(id = UUID.randomUUID().toString(), setNumber = 1, reps = 10, weightPerCable = kgToDisplay(defaultWeightKg, weightUnit), restSeconds = 60),
                SetConfiguration(id = UUID.randomUUID().toString(), setNumber = 2, reps = 10, weightPerCable = kgToDisplay(defaultWeightKg, weightUnit), restSeconds = 60),
                SetConfiguration(id = UUID.randomUUID().toString(), setNumber = 3, reps = 10, weightPerCable = kgToDisplay(defaultWeightKg, weightUnit), restSeconds = 60)
            )
        }

        // Debug logging for AMRAP exercise data loading
        Timber.d("━━━━━ ExerciseConfigViewModel.initialize() ━━━━━")
        Timber.d("Exercise: ${exercise.exercise.name}")
        Timber.d("isAMRAP flag: ${exercise.isAMRAP}")
        Timber.d("perSetRestTime flag: ${exercise.perSetRestTime}")
        Timber.d("setReps: ${exercise.setReps}")
        Timber.d("setWeightsPerCableKg: ${exercise.setWeightsPerCableKg}")
        Timber.d("weightPerCableKg: ${exercise.weightPerCableKg}")
        Timber.d("setRestSeconds: ${exercise.setRestSeconds}")
        Timber.d("Loaded sets:")
        initialSets.forEach { set ->
            Timber.d("  Set ${set.setNumber}: reps=${set.reps}, weight=${set.weightPerCable}, rest=${set.restSeconds}")
        }
        Timber.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        _sets.value = initialSets

        _selectedMode.value = exercise.workoutType.toWorkoutMode()
        _weightChange.value = kgToDisplay(exercise.progressionKg, weightUnit).toInt()
        _rest.value = exercise.setRestSeconds.firstOrNull()?.coerceIn(0, 300) ?: 60 // Use first rest time or default
        _perSetRestTime.value = exercise.perSetRestTime
        
        // Extract echo level and eccentric load from WorkoutType.Echo when in Echo mode
        // This ensures the previously saved values are loaded correctly (Issue #108, persisting issue)
        when (val workoutType = exercise.workoutType) {
            is com.example.vitruvianredux.domain.model.WorkoutType.Echo -> {
                _eccentricLoad.value = workoutType.eccentricLoad
                _echoLevel.value = workoutType.level
            }
            else -> {
                _eccentricLoad.value = exercise.eccentricLoad
                _echoLevel.value = exercise.echoLevel
            }
        }

        _initialized.value = true
    }

    fun onSetModeChange(mode: SetMode) {
        // Bodyweight exercises must always use DURATION mode (cannot count reps)
        if (_exerciseType.value == ExerciseType.BODYWEIGHT && mode == SetMode.REPS) {
            Timber.w("Cannot switch to REPS mode for bodyweight exercises - staying in DURATION mode")
            return
        }
        _setMode.value = mode
    }

    fun onSelectedModeChange(mode: WorkoutMode) {
        _selectedMode.value = mode
    }

    fun onWeightChange(change: Int) {
        _weightChange.value = change
    }

    fun onRestChange(newRest: Int) {
        _rest.value = newRest
    }

    fun onPerSetRestTimeChange(enabled: Boolean) {
        _perSetRestTime.value = enabled
        // When switching to single rest time, update all sets to use the current rest value
        if (!enabled) {
            _sets.value = _sets.value.map { it.copy(restSeconds = _rest.value) }
        }
    }

    fun onEccentricLoadChange(load: EccentricLoad) {
        _eccentricLoad.value = load
    }

    fun onEchoLevelChange(level: EchoLevel) {
        _echoLevel.value = level
    }

    fun updateReps(setId: String, reps: Int?) {
        _sets.value = _sets.value.map { set ->
            if (set.id == setId) set.copy(reps = reps) else set
        }
    }

    fun updateWeight(setId: String, weight: Float) {
        _sets.value = _sets.value.map { set ->
            if (set.id == setId) set.copy(weightPerCable = weight) else set
        }
    }

    fun updateDuration(setId: String, duration: Int) {
        _sets.value = _sets.value.map { set ->
            if (set.id == setId) set.copy(duration = duration) else set
        }
    }

    fun updateRestTime(setId: String, restSeconds: Int) {
        _sets.value = _sets.value.map { set ->
            if (set.id == setId) set.copy(restSeconds = restSeconds) else set
        }
    }

    fun addSet() {
        val lastSet = _sets.value.lastOrNull()
        val newSet = SetConfiguration(
            setNumber = _sets.value.size + 1,
            reps = lastSet?.reps ?: 10,
            weightPerCable = lastSet?.weightPerCable ?: kgToDisplay(15f, weightUnit),
            duration = lastSet?.duration ?: 30,
            restSeconds = lastSet?.restSeconds ?: 60
        )
        _sets.value = _sets.value + newSet
    }

    fun deleteSet(index: Int) {
        val newSets = _sets.value.filterIndexed { i, _ -> i != index }
            .mapIndexed { i, set -> set.copy(setNumber = i + 1) }
        _sets.value = newSets
    }

    fun onSave(onSaveCallback: (RoutineExercise) -> Unit) {
        if (_sets.value.isEmpty()) return

        // Determine rest times based on perSetRestTime toggle
        val restTimes = if (_perSetRestTime.value) {
            // Per-set rest times: use each set's rest time
            _sets.value.map { it.restSeconds }
        } else {
            // Single rest time: use the bottom rest time picker value for all sets
            List(_sets.value.size) { _rest.value }
        }

        // Determine if exercise is AMRAP (all sets have null reps)
        val isAMRAP = _sets.value.all { it.reps == null }

        // Debug logging for AMRAP exercise data saving
        Timber.d("━━━━━ ExerciseConfigViewModel.onSave() ━━━━━")
        Timber.d("Exercise: ${originalExercise.exercise.name}")
        Timber.d("isAMRAP computed: $isAMRAP")
        Timber.d("perSetRestTime toggle: ${_perSetRestTime.value}")
        Timber.d("Current sets before save:")
        _sets.value.forEach { set ->
            Timber.d("  Set ${set.setNumber}: reps=${set.reps}, weight=${set.weightPerCable}, rest=${set.restSeconds}")
        }
        Timber.d("Rest times to save: $restTimes")
        Timber.d("Weights to save: ${_sets.value.map { displayToKg(it.weightPerCable, weightUnit) }}")

        val updatedExercise = originalExercise.copy(
            setReps = _sets.value.map { it.reps },
            weightPerCableKg = displayToKg(_sets.value.first().weightPerCable, weightUnit),
            setWeightsPerCableKg = _sets.value.map { displayToKg(it.weightPerCable, weightUnit) },
            workoutType = _selectedMode.value.toWorkoutType(
                eccentricLoad = if (_selectedMode.value is WorkoutMode.Echo) _eccentricLoad.value else EccentricLoad.LOAD_100
            ),
            eccentricLoad = _eccentricLoad.value,
            echoLevel = _echoLevel.value,
            progressionKg = displayToKg(_weightChange.value.toFloat(), weightUnit),
            setRestSeconds = restTimes,
            duration = if (_setMode.value == SetMode.DURATION) {
                _sets.value.firstOrNull()?.duration ?: 30 // Default to 30 seconds if not set
            } else null,
            perSetRestTime = _perSetRestTime.value,
            isAMRAP = isAMRAP
        )

        Timber.d("Updated exercise to save:")
        Timber.d("  setReps: ${updatedExercise.setReps}")
        Timber.d("  setWeightsPerCableKg: ${updatedExercise.setWeightsPerCableKg}")
        Timber.d("  weightPerCableKg: ${updatedExercise.weightPerCableKg}")
        Timber.d("  setRestSeconds: ${updatedExercise.setRestSeconds}")
        Timber.d("  perSetRestTime: ${updatedExercise.perSetRestTime}")
        Timber.d("  isAMRAP: ${updatedExercise.isAMRAP}")
        Timber.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        onSaveCallback(updatedExercise)
        _initialized.value = false // Reset for next use
    }

    fun onDismiss() {
        _initialized.value = false // Reset for next use
    }

    override fun onCleared() {
        super.onCleared()
    }
}
