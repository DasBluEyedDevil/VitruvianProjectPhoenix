package com.example.vitruvianredux.domain.model

sealed class ProgramMode(
    val modeValue: Int,
    val displayName: String
) {
    data object OldSchool : ProgramMode(0, "Old School")
    data object Pump : ProgramMode(2, "Pump")
    data object TUT : ProgramMode(3, "TUT")
    data object TUTBeast : ProgramMode(4, "TUT Beast")
    data object EccentricOnly : ProgramMode(6, "Eccentric Only")

    companion object {
        fun fromValue(value: Int): ProgramMode = when (value) {
            0 -> OldSchool
            2 -> Pump
            3 -> TUT
            4 -> TUTBeast
            6 -> EccentricOnly
            else -> OldSchool
        }
    }
}
