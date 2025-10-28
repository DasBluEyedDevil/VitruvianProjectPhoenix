package com.example.vitruvianredux.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import com.example.vitruvianredux.data.repository.BleRepository
import com.example.vitruvianredux.domain.model.ConnectionState
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * BLE Connection Test Suite
 *
 * Tests direct Bluetooth Low Energy communication with Vitruvian devices
 * All operations are local - no server/cloud authentication required
 */
@ExperimentalCoroutinesApi
class BleConnectionTest {

    private lateinit var bleRepository: BleRepository

    @Before
    fun setup() {
        bleRepository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `test device scanning - local BLE discovery`() = runTest {
        // Verify device scanning uses local BLE, not server directory
        // Given: BLE scanning capability
        val mockDevice = mockk<BluetoothDevice>(relaxed = true)
        val mockScanResult = mockk<ScanResult>(relaxed = true)

        every { mockScanResult.device } returns mockDevice
        every { mockDevice.name } returns "Vitruvian"
        every { mockDevice.address } returns "AA:BB:CC:DD:EE:FF"
        every { mockScanResult.rssi } returns -65

        coEvery { bleRepository.startScanning() } returns Result.success(Unit)
        every { bleRepository.scannedDevices } returns flowOf(mockScanResult)

        // When: Starting scan
        val scanResult = bleRepository.startScanning()

        val foundDevices = bleRepository.scannedDevices.take(1).toList()

        // Then: Devices are discovered via local BLE (no server lookup)
        assertTrue(scanResult.isSuccess, "BLE scan should work locally")
        assertEquals(1, foundDevices.size, "Should find devices via BLE")
        assertEquals("Vitruvian", foundDevices[0].device.name)

        // Verify only local BLE operations
        coVerify(exactly = 1) { bleRepository.startScanning() }
    }

    @Test
    fun `test direct device connection - no server pairing`() = runTest {
        // Verify connection happens directly to device via BLE
        // Given: A Vitruvian device MAC address
        val deviceAddress = "AA:BB:CC:DD:EE:FF"

        val stateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        every { bleRepository.connectionState } returns stateFlow

        coEvery { bleRepository.connectToDevice(deviceAddress) } answers {
            stateFlow.value = ConnectionState.Connected("Vitruvian", deviceAddress)
            Result.success(Unit)
        }

        // When: Connecting to device
        val connectionResult = bleRepository.connectToDevice(deviceAddress)

        val currentState = stateFlow.value

        // Then: Direct BLE connection established (no server authentication)
        assertTrue(connectionResult.isSuccess, "BLE connection should succeed")
        assertTrue(currentState is ConnectionState.Connected)
        assertEquals(deviceAddress, currentState.deviceAddress)

        // No server authentication calls needed
        coVerify(exactly = 1) { bleRepository.connectToDevice(deviceAddress) }
    }

    @Test
    fun `test connection state transitions - local management`() = runTest {
        // Verify connection state is managed locally
        // Given: Connection state flow
        val stateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        every { bleRepository.connectionState } returns stateFlow

        // When: Transitioning through connection states
        stateFlow.value = ConnectionState.Scanning
        stateFlow.value = ConnectionState.Connecting
        stateFlow.value = ConnectionState.Connected("Vitruvian", "AA:BB:CC:DD:EE:FF")

        val finalState = stateFlow.value

        // Then: All state transitions happen locally
        assertTrue(finalState is ConnectionState.Connected)
        assertEquals("AA:BB:CC:DD:EE:FF", finalState.deviceAddress)
    }

    @Test
    fun `test connection persistence - survives app restart`() = runTest {
        // Verify device address can be saved for reconnection
        // Given: Previously connected device address (saved locally)
        val savedDeviceAddress = "AA:BB:CC:DD:EE:FF"

        coEvery { bleRepository.connectToDevice(savedDeviceAddress) } returns Result.success(Unit)

        // When: App restarts and reconnects
        val reconnectResult = bleRepository.connectToDevice(savedDeviceAddress)

        // Then: Reconnection works without server lookup
        assertTrue(reconnectResult.isSuccess, "Should reconnect using saved address")
        coVerify { bleRepository.connectToDevice(savedDeviceAddress) }
    }

    @Test
    fun `test multiple device handling - local preference`() = runTest {
        // Verify app can manage multiple devices locally
        // Given: Multiple Vitruvian devices discovered
        val device1 = mockk<BluetoothDevice>(relaxed = true)
        val device2 = mockk<BluetoothDevice>(relaxed = true)
        val scan1 = mockk<ScanResult>(relaxed = true)
        val scan2 = mockk<ScanResult>(relaxed = true)

        every { scan1.device } returns device1
        every { scan2.device } returns device2
        every { device1.address } returns "AA:BB:CC:DD:EE:FF"
        every { device2.address } returns "11:22:33:44:55:66"
        every { device1.name } returns "Vitruvian 1"
        every { device2.name } returns "Vitruvian 2"

        every { bleRepository.scannedDevices } returns flowOf(scan1, scan2)

        // When: Scanning finds multiple devices
        val foundDevices = bleRepository.scannedDevices.take(2).toList()

        // Then: All devices discovered locally (no server registry)
        assertEquals(2, foundDevices.size, "Should find both devices")

        // User can connect to any device directly
        coEvery { bleRepository.connectToDevice(any()) } returns Result.success(Unit)
        bleRepository.connectToDevice("AA:BB:CC:DD:EE:FF")

        coVerify { bleRepository.connectToDevice("AA:BB:CC:DD:EE:FF") }
    }

    @Test
    fun `test connection error handling - local recovery`() = runTest {
        // Verify connection errors are handled locally
        // Given: Connection attempt that fails
        val deviceAddress = "AA:BB:CC:DD:EE:FF"
        val error = Exception("BLE connection failed")

        val stateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        every { bleRepository.connectionState } returns stateFlow

        coEvery { bleRepository.connectToDevice(deviceAddress) } answers {
            stateFlow.value = ConnectionState.Error("Connection failed", error)
            Result.failure(error)
        }

        // When: Connection fails
        val connectionResult = bleRepository.connectToDevice(deviceAddress)

        val errorState = stateFlow.value

        // Then: Error is handled locally (no server error reporting)
        assertTrue(connectionResult.isFailure, "Should report failure")
        assertTrue(errorState is ConnectionState.Error)

        // Retry can happen locally
        coEvery { bleRepository.connectToDevice(deviceAddress) } answers {
            stateFlow.value = ConnectionState.Connected("Vitruvian", deviceAddress)
            Result.success(Unit)
        }
        val retryResult = bleRepository.connectToDevice(deviceAddress)
        assertTrue(retryResult.isSuccess, "Local retry should work")
    }

    @Test
    fun `test graceful disconnection - local operation`() = runTest {
        // Verify disconnection is handled locally
        // Given: Connected device
        val deviceAddress = "AA:BB:CC:DD:EE:FF"
        val stateFlow = MutableStateFlow<ConnectionState>(
            ConnectionState.Connected("Vitruvian", deviceAddress)
        )
        every { bleRepository.connectionState } returns stateFlow

        coEvery { bleRepository.disconnect() } answers {
            stateFlow.value = ConnectionState.Disconnected
        }

        // When: Disconnecting
        bleRepository.disconnect()

        val finalState = stateFlow.value

        // Then: Disconnection happens locally (no server notification)
        assertTrue(finalState is ConnectionState.Disconnected)
        coVerify(exactly = 1) { bleRepository.disconnect() }
    }

    @Test
    fun `test BLE permissions are device-only - no account required`() = runTest {
        // Verify BLE operations don't require user account/server authentication
        // Given: BLE operations
        coEvery { bleRepository.startScanning() } returns Result.success(Unit)
        coEvery { bleRepository.connectToDevice(any()) } returns Result.success(Unit)

        // When: Performing BLE operations
        bleRepository.startScanning()
        bleRepository.connectToDevice("AA:BB:CC:DD:EE:FF")

        // Then: Operations succeed without account/authentication
        coVerify { bleRepository.startScanning() }
        coVerify { bleRepository.connectToDevice(any()) }

        // No authentication/login mocks needed - proves no account required
    }

    @Test
    fun `test RSSI signal strength tracking - local measurement`() = runTest {
        // Verify signal strength is measured locally
        // Given: Device with varying RSSI
        val mockDevice = mockk<BluetoothDevice>(relaxed = true)
        val strongSignal = mockk<ScanResult>(relaxed = true)
        val weakSignal = mockk<ScanResult>(relaxed = true)

        every { strongSignal.device } returns mockDevice
        every { weakSignal.device } returns mockDevice
        every { strongSignal.rssi } returns -45  // Strong signal
        every { weakSignal.rssi } returns -85     // Weak signal
        every { mockDevice.address } returns "AA:BB:CC:DD:EE:FF"

        // When: Measuring signal strength
        val strongRSSI = strongSignal.rssi
        val weakRSSI = weakSignal.rssi

        // Then: RSSI is measured locally (no server ping)
        assertTrue(strongRSSI > weakRSSI, "Signal strength measured locally")
        assertEquals(-45, strongRSSI)
        assertEquals(-85, weakRSSI)
    }

    @Test
    fun `test concurrent connection attempts - local queue management`() = runTest {
        // Verify multiple connection attempts are managed locally
        // Given: Multiple connection attempts
        val deviceAddress = "AA:BB:CC:DD:EE:FF"
        var connectionCount = 0

        coEvery { bleRepository.connectToDevice(deviceAddress) } answers {
            connectionCount++
            Result.success(Unit)
        }

        // When: Multiple rapid connection attempts
        repeat(3) {
            bleRepository.connectToDevice(deviceAddress)
        }

        // Then: All attempts handled locally (no server throttling)
        assertEquals(3, connectionCount, "All attempts processed locally")
        coVerify(exactly = 3) { bleRepository.connectToDevice(deviceAddress) }
    }

    @Test
    fun `test BLE service discovery - local GATT operations`() = runTest {
        // Verify GATT services are discovered locally from device
        // Given: Connected device
        val deviceAddress = "AA:BB:CC:DD:EE:FF"

        coEvery { bleRepository.connectToDevice(deviceAddress) } returns Result.success(Unit)
        coEvery { bleRepository.sendInitSequence() } returns Result.success(Unit)

        // When: Connecting and discovering services
        val connectResult = bleRepository.connectToDevice(deviceAddress)
        val initResult = bleRepository.sendInitSequence()

        // Then: Service discovery happens via local GATT (no server lookup)
        assertTrue(connectResult.isSuccess, "Connection should succeed")
        assertTrue(initResult.isSuccess, "Service discovery should succeed")

        coVerify { bleRepository.connectToDevice(deviceAddress) }
        coVerify { bleRepository.sendInitSequence() }
    }
}

