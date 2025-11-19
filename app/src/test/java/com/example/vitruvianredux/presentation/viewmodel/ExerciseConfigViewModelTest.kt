package com.example.vitruvianredux.presentation.viewmodel

import com.example.vitruvianredux.domain.model.*
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.UUID

class ExerciseConfigViewModelTest {

    @Test
    fun `SetConfiguration should include per-set rest time`() {
        val setConfig = SetConfiguration(
            setNumber = 1,
            reps = 10,
            weightPerCable = 15.0f,
            duration = 30,
            restSeconds = 90 // Add this field
        )

        assertThat(setConfig.restSeconds).isEqualTo(90)
    }

    @Test
    fun `initialize should extract echo level from WorkoutType Echo when exercise is in Echo mode`() {
        val viewModel = ExerciseConfigViewModel()
        
        // Create an exercise with Echo mode set to "Hard"
        val exercise = Exercise(
            name = "Bench Press",
            muscleGroup = "Chest",
            defaultCableConfig = CableConfiguration.DOUBLE
        )
        
        val routineExercise = RoutineExercise(
            id = UUID.randomUUID().toString(),
            exercise = exercise,
            cableConfig = CableConfiguration.DOUBLE,
            orderIndex = 0,
            setReps = listOf(10, 10, 10),
            weightPerCableKg = 20f,
            workoutType = WorkoutType.Echo(
                level = EchoLevel.HARD,  // Setting to HARD (not the default HARDER)
                eccentricLoad = EccentricLoad.LOAD_75  // Setting to 75% (not the default 100%)
            ),
            eccentricLoad = EccentricLoad.LOAD_100,  // Default value in standalone field
            echoLevel = EchoLevel.HARDER  // Default value in standalone field
        )
        
        // Initialize the view model
        viewModel.initialize(
            exercise = routineExercise,
            unit = WeightUnit.KG,
            toDisplay = { kg, _ -> kg },
            toKg = { display, _ -> display }
        )
        
        // Verify that echo level is extracted from WorkoutType.Echo, not from standalone field
        assertThat(viewModel.echoLevel.value).isEqualTo(EchoLevel.HARD)
        assertThat(viewModel.eccentricLoad.value).isEqualTo(EccentricLoad.LOAD_75)
    }

    @Test
    fun `initialize should use standalone echo level field when exercise is not in Echo mode`() {
        val viewModel = ExerciseConfigViewModel()
        
        // Create an exercise with OldSchool mode
        val exercise = Exercise(
            name = "Bench Press",
            muscleGroup = "Chest",
            defaultCableConfig = CableConfiguration.DOUBLE
        )
        
        val routineExercise = RoutineExercise(
            id = UUID.randomUUID().toString(),
            exercise = exercise,
            cableConfig = CableConfiguration.DOUBLE,
            orderIndex = 0,
            setReps = listOf(10, 10, 10),
            weightPerCableKg = 20f,
            workoutType = WorkoutType.Program(ProgramMode.OldSchool),
            eccentricLoad = EccentricLoad.LOAD_125,  // Standalone field value
            echoLevel = EchoLevel.EPIC  // Standalone field value
        )
        
        // Initialize the view model
        viewModel.initialize(
            exercise = routineExercise,
            unit = WeightUnit.KG,
            toDisplay = { kg, _ -> kg },
            toKg = { display, _ -> display }
        )
        
        // Verify that echo level is taken from standalone field for non-Echo modes
        assertThat(viewModel.echoLevel.value).isEqualTo(EchoLevel.EPIC)
        assertThat(viewModel.eccentricLoad.value).isEqualTo(EccentricLoad.LOAD_125)
    }
}
