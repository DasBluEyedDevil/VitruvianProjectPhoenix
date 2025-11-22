package com.example.vitruvianredux.domain.model

sealed class WorkoutType {

    val displayName: String
        get() = when (this) {
            is Program -> mode.displayName
            is Echo -> "Echo"
        }

    val modeValue: Int
        get() = when (this) {
            is Program -> mode.modeValue
            is Echo -> 10
        }

    fun toWorkoutMode(): WorkoutMode = when (this) {
        is Program -> when (mode) {
            ProgramMode.OldSchool -> WorkoutMode.OldSchool
            ProgramMode.Pump -> WorkoutMode.Pump
            ProgramMode.TUT -> WorkoutMode.TUT
            ProgramMode.TUTBeast -> WorkoutMode.TUTBeast
            ProgramMode.EccentricOnly -> WorkoutMode.EccentricOnly
        }
        is Echo -> WorkoutMode.Echo(level)
    }

    data class Program(val mode: ProgramMode) : WorkoutType()

    data class Echo(
        val level: EchoLevel,
        val eccentricLoad: EccentricLoad
    ) : WorkoutType()
}
