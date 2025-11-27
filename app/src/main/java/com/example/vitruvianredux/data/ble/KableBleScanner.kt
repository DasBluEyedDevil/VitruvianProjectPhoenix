package com.example.vitruvianredux.data.ble

import com.juul.kable.Advertisement
import com.juul.kable.Scanner
import com.juul.kable.logs.Logging
import com.juul.kable.logs.SystemLogEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data class representing a discovered Vitruvian device.
 */
data class DiscoveredDevice(
    val name: String?,
    val address: String,
    val rssi: Int,
    val advertisement: Advertisement
)

/**
 * Kable-based BLE scanner for discovering Vitruvian Trainer devices.
 */
@Singleton
class KableBleScanner @Inject constructor() {

    companion object {
        // Vitruvian devices advertise with names starting with "V-Trainer" or "Vitruvian"
        private val VITRUVIAN_NAME_PREFIXES = listOf("V-Trainer", "Vitruvian", "VTrain")
    }

    private val scanner = Scanner {
        logging {
            engine = SystemLogEngine
            level = Logging.Level.Warnings
        }
    }

    /**
     * Flow of discovered Vitruvian devices.
     * Filters for devices with Vitruvian name prefixes.
     */
    val advertisements: Flow<DiscoveredDevice> = scanner.advertisements
        .filter { advertisement ->
            val name = advertisement.name ?: return@filter false
            VITRUVIAN_NAME_PREFIXES.any { prefix ->
                name.startsWith(prefix, ignoreCase = true)
            }
        }
        .map { advertisement ->
            Timber.d("Discovered Vitruvian device: ${advertisement.name} (${advertisement.identifier})")
            DiscoveredDevice(
                name = advertisement.name,
                address = advertisement.identifier.toString(),
                rssi = advertisement.rssi,
                advertisement = advertisement
            )
        }

    /**
     * Flow of all BLE advertisements (unfiltered).
     * Use for debugging or manual device selection.
     */
    val allAdvertisements: Flow<DiscoveredDevice> = scanner.advertisements
        .map { advertisement ->
            DiscoveredDevice(
                name = advertisement.name,
                address = advertisement.identifier.toString(),
                rssi = advertisement.rssi,
                advertisement = advertisement
            )
        }
}
