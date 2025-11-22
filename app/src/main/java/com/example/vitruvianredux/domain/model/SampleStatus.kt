package com.example.vitruvianredux.domain.model

/**
 * Status flags from the Vitruvian FormTrainer device.
 * These are bit-masked flags matching the official app protocol.
 *
 * BLE Format: 16-bit short (transmitted in sample data)
 */
enum class SampleStatus(val mask: Int) {
    REP_TOP_READY(0x0001),      // Bit 0: Reached top position (peak contraction)
    REP_BOTTOM_READY(0x0002),   // Bit 1: Reached bottom position (full extension)
    ROM_OUTSIDE_HIGH(0x0004),   // Bit 2: Exceeded max ROM range
    ROM_OUTSIDE_LOW(0x0008),    // Bit 3: Below min ROM range
    ROM_UNLOAD_ACTIVE(0x0010),  // Bit 4: Unloading phase active
    SPOTTER_ACTIVE(0x0020),     // Bit 5: Machine assist (spotter) engaged
    DELOAD_WARN(0x0040),        // Bit 6: Warning - force cap approaching
    DELOAD_OCCURRED(0x8000);    // Bit 15: Critical - force capped/unloaded

    companion object {
        fun fromBitfield(flags: Int): Set<SampleStatus> {
            return entries.filter { (flags and it.mask) != 0 }.toSet()
        }

        fun hasFlag(flags: Int, status: SampleStatus): Boolean {
            return (flags and status.mask) != 0
        }
    }
}
