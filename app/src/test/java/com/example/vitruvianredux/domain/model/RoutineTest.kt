package com.example.vitruvianredux.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RoutineTest {

    @Test
    fun `RoutineExercise should support per-set rest times`() {
        val exercise = RoutineExercise(
            id = "test-id",
            exercise = Exercise(
                name = "Bench Press",
                muscleGroup = "Chest",
                equipment = "Barbell",
                defaultCableConfig = CableConfiguration.DOUBLE
            ),
            cableConfig = CableConfiguration.DOUBLE,
            orderIndex = 0,
            setReps = listOf(10, 8, 6),
            weightPerCableKg = 15.0f,
            setWeightsPerCableKg = listOf(15.0f, 20.0f, 25.0f),
            setRestSeconds = listOf(60, 90, 120) // Shorter → longer rest
        )

        assertThat(exercise.setRestSeconds).hasSize(3)
        assertThat(exercise.setRestSeconds[0]).isEqualTo(60)
        assertThat(exercise.setRestSeconds[1]).isEqualTo(90)
        assertThat(exercise.setRestSeconds[2]).isEqualTo(120)
    }

    @Test
    fun `RoutineExercise should default to 60s rest for all sets when not specified`() {
        val exercise = RoutineExercise(
            id = "test-id",
            exercise = Exercise(
                name = "Bench Press",
                muscleGroup = "Chest",
                equipment = "Barbell",
                defaultCableConfig = CableConfiguration.DOUBLE
            ),
            cableConfig = CableConfiguration.DOUBLE,
            orderIndex = 0,
            setReps = listOf(10, 10, 10),
            weightPerCableKg = 15.0f,
            setWeightsPerCableKg = listOf(15.0f, 15.0f, 15.0f)
            // setRestSeconds not specified
        ).withNormalizedRestTimes()

        // Should default to [60, 60, 60]
        assertThat(exercise.setRestSeconds).isEqualTo(listOf(60, 60, 60))
    }
}
