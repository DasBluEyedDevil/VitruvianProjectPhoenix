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
    val reps: Int = 10,
    val weightPerCable: Float = 15.0f,
    val duration: Int = 30
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

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

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

        _setMode.value = if (exercise.duration != null) SetMode.DURATION else SetMode.REPS

        // Use PR weight as default if available, otherwise use 15kg
        val defaultWeightKg = prWeightKg ?: 15f

        val initialSets = exercise.setReps.mapIndexed { index, reps ->
            val perSetWeightKg = exercise.setWeightsPerCableKg.getOrNull(index) ?: exercise.weightPerCableKg
            SetConfiguration(
                id = UUID.randomUUID().toString(),
                setNumber = index + 1,
                reps = reps,
                weightPerCable = kgToDisplay(perSetWeightKg, weightUnit),
                duration = exercise.duration ?: 30
            )
        }.ifEmpty {
            listOf(
                SetConfiguration(id = UUID.randomUUID().toString(), setNumber = 1, reps = 10, weightPerCable = kgToDisplay(defaultWeightKg, weightUnit)),
                SetConfiguration(id = UUID.randomUUID().toString(), setNumber = 2, reps = 10, weightPerCable = kgToDisplay(defaultWeightKg, weightUnit)),
                SetConfiguration(id = UUID.randomUUID().toString(), setNumber = 3, reps = 10, weightPerCable = kgToDisplay(defaultWeightKg, weightUnit))
            )
        }
        _sets.value = initialSets

        _selectedMode.value = exercise.workoutType.toWorkoutMode()
        _weightChange.value = kgToDisplay(exercise.progressionKg, weightUnit).toInt()
        _rest.value = exercise.restSeconds.coerceIn(0, 300)
        _notes.value = exercise.notes
        _eccentricLoad.value = exercise.eccentricLoad
        _echoLevel.value = exercise.echoLevel

        _initialized.value = true
    }

    fun onSetModeChange(mode: SetMode) {
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

    fun onNotesChange(newNotes: String) {
        _notes.value = newNotes
    }

    fun onEccentricLoadChange(load: EccentricLoad) {
        _eccentricLoad.value = load
    }

    fun onEchoLevelChange(level: EchoLevel) {
        _echoLevel.value = level
    }

    fun updateReps(index: Int, reps: Int) {
        val currentSets = _sets.value.toMutableList()
        if (index < currentSets.size) {
            currentSets[index] = currentSets[index].copy(reps = reps)
            _sets.value = currentSets
        }
    }

    fun updateWeight(index: Int, weight: Float) {
        val currentSets = _sets.value.toMutableList()
        if (index < currentSets.size) {
            currentSets[index] = currentSets[index].copy(weightPerCable = weight)
            _sets.value = currentSets
        }
    }

    fun updateDuration(index: Int, duration: Int) {
        val currentSets = _sets.value.toMutableList()
        if (index < currentSets.size) {
            currentSets[index] = currentSets[index].copy(duration = duration)
            _sets.value = currentSets
        }
    }

    fun addSet() {
        val lastSet = _sets.value.lastOrNull()
        val newSet = SetConfiguration(
            setNumber = _sets.value.size + 1,
            reps = lastSet?.reps ?: 10,
            weightPerCable = lastSet?.weightPerCable ?: kgToDisplay(15f, weightUnit),
            duration = lastSet?.duration ?: 30
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
            restSeconds = _rest.value,
            notes = _notes.value.trim(),
            duration = if (_setMode.value == SetMode.DURATION) _sets.value.firstOrNull()?.duration else null
        )
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
