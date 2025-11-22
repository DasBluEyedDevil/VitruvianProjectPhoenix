package com.example.vitruvianredux.data.ble

import java.util.Arrays

/**
 * Data class representing a rep notification from the BLE device.
 * Contains information about the current rep state during exercise.
 *
 * @property up The up count value
 * @property down The down count value
 * @property rangeTop The top of the range of motion
 * @property rangeBottom The bottom of the range of motion
 * @property repsRomCount Current rep count within range of motion
 * @property repsRomTotal Total reps within range of motion
 * @property repsSetCount Current rep count in the set
 * @property repsSetTotal Total reps in the set
 * @property rawData The raw byte data from the notification
 * @property timestamp The timestamp of the notification
 */
data class RepNotification(
    val up: Int,
    val down: Int,
    val rangeTop: Float,
    val rangeBottom: Float,
    val repsRomCount: Short?,
    val repsRomTotal: Short?,
    val repsSetCount: Short?,
    val repsSetTotal: Short?,
    val rawData: ByteArray,
    val timestamp: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RepNotification

        if (up != other.up) return false
        if (down != other.down) return false
        if (rangeTop != other.rangeTop) return false
        if (rangeBottom != other.rangeBottom) return false
        if (repsRomCount != other.repsRomCount) return false
        if (repsRomTotal != other.repsRomTotal) return false
        if (repsSetCount != other.repsSetCount) return false
        if (repsSetTotal != other.repsSetTotal) return false
        if (!rawData.contentEquals(other.rawData)) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = up
        result = 31 * result + down
        result = 31 * result + rangeTop.hashCode()
        result = 31 * result + rangeBottom.hashCode()
        result = 31 * result + (repsRomCount?.hashCode() ?: 0)
        result = 31 * result + (repsRomTotal?.hashCode() ?: 0)
        result = 31 * result + (repsSetCount?.hashCode() ?: 0)
        result = 31 * result + (repsSetTotal?.hashCode() ?: 0)
        result = 31 * result + rawData.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }

    override fun toString(): String {
        return "RepNotification(up=$up, down=$down, rangeTop=$rangeTop, rangeBottom=$rangeBottom, " +
                "repsRomCount=$repsRomCount, repsRomTotal=$repsRomTotal, repsSetCount=$repsSetCount, " +
                "repsSetTotal=$repsSetTotal, rawData=${Arrays.toString(rawData)}, timestamp=$timestamp)"
    }
}
