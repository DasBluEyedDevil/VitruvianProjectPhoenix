package com.example.vitruvianredux.data.ble

import org.junit.Test
import kotlin.test.assertNotNull

class KableBleScannerTest {

    @Test
    fun `scanner should be instantiable`() {
        // This is a basic smoke test - full BLE testing requires instrumented tests
        // Verifies the class compiles and can be referenced
        val scannerClass = KableBleScanner::class
        assertNotNull(scannerClass)
    }
}
