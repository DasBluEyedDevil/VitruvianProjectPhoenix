package com.example.vitruvianredux.domain.model

sealed class WorkoutMode(val displayName: String) {

    data object OldSchool : WorkoutMode("Old School")
    data object Pump : WorkoutMode("Pump")
    data object TUT : WorkoutMode("TUT")
    data object TUTBeast : WorkoutMode("TUT Beast")
    data object EccentricOnly : WorkoutMode("Eccentric Only")
    data class Echo(val level: EchoLevel) : WorkoutMode("Echo")

    fun toWorkoutType(eccentricLoad: EccentricLoad = EccentricLoad.LOAD_100): WorkoutType = when (this) {
        is OldSchool -> WorkoutType.Program(ProgramMode.OldSchool)
        is Pump -> WorkoutType.Program(ProgramMode.Pump)
        is TUT -> WorkoutType.Program(ProgramMode.TUT)
        is TUTBeast -> WorkoutType.Program(ProgramMode.TUTBeast)
        is EccentricOnly -> WorkoutType.Program(ProgramMode.EccentricOnly)
        is Echo -> WorkoutType.Echo(level, eccentricLoad)
    }
}
