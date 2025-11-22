package com.example.vitruvianredux.presentation.viewmodel

import com.google.common.truth.Truth.assertThat
import org.junit.Test

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
}
