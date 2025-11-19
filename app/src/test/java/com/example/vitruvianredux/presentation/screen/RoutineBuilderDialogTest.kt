package com.example.vitruvianredux.presentation.screen

import com.example.vitruvianredux.domain.model.CableConfiguration
import com.example.vitruvianredux.domain.model.Exercise
import com.example.vitruvianredux.domain.model.RoutineExercise
import com.example.vitruvianredux.domain.model.WorkoutType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RoutineBuilderDialogTest {

    private fun createExercise(
        duration: Int? = null,
        setReps: List<Int?> = listOf(10, 10, 10),
        equipment: String = "",
        workoutType: WorkoutType = WorkoutType.Program(com.example.vitruvianredux.domain.model.ProgramMode.OldSchool)
    ): RoutineExercise {
        val baseExercise = Exercise(
            id = "ex1",
            name = "Push-up",
            muscleGroup = "Chest",
            equipment = equipment,
            defaultCableConfig = CableConfiguration.SINGLE
        )
        return RoutineExercise(
            id = "re1",
            exercise = baseExercise,
            cableConfig = CableConfiguration.SINGLE,
            orderIndex = 0,
            setReps = setReps,
            weightPerCableKg = 0f,
            workoutType = workoutType,
            duration = duration
        )
    }

    @Test
    fun `formatReps handles uniform reps`() {
        val result = formatReps(listOf(8, 8, 8))
        assertThat(result).isEqualTo("3 x 8 reps")
    }

    @Test
    fun `formatReps handles AMRAP reps`() {
        val result = formatReps(listOf(null, null))
        assertThat(result).isEqualTo("2 x AMRAP")
    }

    @Test
    fun `formatSetTarget uses duration for bodyweight`() {
        val exercise = createExercise(duration = 30, setReps = listOf(10, 10), equipment = "")

        val result = formatSetTarget(exercise)

        assertThat(result).isEqualTo("2 x 30 sec")
    }

    @Test
    fun `formatSetTarget falls back to reps when no duration`() {
        val exercise = createExercise(duration = null, setReps = listOf(5, 5, 5), equipment = "Cable")

        val result = formatSetTarget(exercise)

        assertThat(result).isEqualTo("3 x 5 reps")
    }
}

