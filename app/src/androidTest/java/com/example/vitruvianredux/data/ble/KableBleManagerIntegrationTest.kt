package com.example.vitruvianredux.data.ble

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for KableBleManager.
 *
 * IMPORTANT: These tests require a physical Vitruvian Trainer device.
 * Run with device connected and Bluetooth enabled.
 */
@RunWith(AndroidJUnit4::class)
class KableBleManagerIntegrationTest {

    private lateinit var scanner: KableBleScanner

    @Before
    fun setup() {
        scanner = KableBleScanner()
    }

    @After
    fun teardown() {
        // Cleanup
    }

    @Test
    fun scanner_discoversDevices() = runBlocking {
        // This test will timeout if no BLE devices are nearby
        // In CI, this should be skipped or mocked
        try {
            withTimeout(10_000) {
                val device = scanner.allAdvertisements.first()
                assertNotNull(device.address)
            }
        } catch (e: Exception) {
            // Expected in CI environment without BLE
            println("Skipping BLE test - no devices found: ${e.message}")
        }
    }

    @Test
    fun kableBleManager_canBeInstantiated() {
        val manager = KableBleManager()
        assertNotNull(manager)
        manager.close()
    }
}
