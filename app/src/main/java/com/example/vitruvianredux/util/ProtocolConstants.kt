package com.example.vitruvianredux.util

object ProtocolConstants {
    const val CMD_INIT = 0x00.toByte()
    const val CMD_WORKOUT_START = 0x01.toByte()
    const val CMD_WORKOUT_STOP = 0x02.toByte()
    const val CMD_SET_COLOR = 0x03.toByte()
    const val CMD_SET_WEIGHT = 0x04.toByte()
    const val CMD_CALIBRATE = 0x05.toByte()

    const val FRAME_HEADER = 0xAA.toByte()
    const val FRAME_FOOTER = 0x55.toByte()

    const val MONITOR_DATA_LENGTH = 20
    const val HEURISTIC_DATA_LENGTH = 28
    const val DIAGNOSTIC_DATA_LENGTH = 16
}
