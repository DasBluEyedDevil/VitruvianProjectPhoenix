package com.example.vitruvianredux.util

import timber.log.Timber

/**
 * Protocol Tester - Diagnostic tool for testing different BLE initialization protocols
 *
 * This helps diagnose connection issues on specific device/firmware combinations by
 * cycling through different initialization sequences and delays to find what works.
 */
object ProtocolTester {

    /**
     * Different initialization protocol variants to test
     */
    enum class InitProtocol(val displayName: String, val description: String) {
        NO_INIT(
            "No Init (Default)",
            "Skip initialization - just connect and start workout directly"
        ),
        INIT_0x0A_NO_WAIT(
            "Init 0x0A (No Wait)",
            "Send INIT command (0x0A) but don't wait for response"
        ),
        INIT_0x0A_WAIT_0x0B(
            "Init 0x0A + Wait 0x0B",
            "Send INIT (0x0A) and wait up to 5 seconds for 0x0B response"
        ),
        INIT_0x0A_PLUS_PRESET(
            "Init 0x0A + Preset",
            "Legacy web app protocol: Send 0x0A then 0x11 preset frame"
        ),
        INIT_DOUBLE_0x0A(
            "Double Init 0x0A",
            "Send INIT command twice with delay between"
        )
    }

    /**
     * Different delay configurations to test after connection
     */
    enum class ConnectionDelay(val displayName: String, val delayMs: Long) {
        NONE("No Delay", 0L),
        DELAY_50MS("50ms", 50L),
        DELAY_100MS("100ms", 100L),
        DELAY_250MS("250ms", 250L),
        DELAY_500MS("500ms", 500L),
        DELAY_1000MS("1 second", 1000L),
        DELAY_2000MS("2 seconds", 2000L)
    }

    /**
     * Result of a single protocol test
     */
    data class TestResult(
        val protocol: InitProtocol,
        val delay: ConnectionDelay,
        val success: Boolean,
        val connectionTimeMs: Long,
        val initTimeMs: Long,
        val errorMessage: String? = null,
        val notes: String? = null
    ) {
        val totalTimeMs: Long get() = connectionTimeMs + initTimeMs

        fun toFormattedString(): String = buildString {
            append("${protocol.displayName} + ${delay.displayName}: ")
            if (success) {
                append("✅ SUCCESS (${totalTimeMs}ms)")
            } else {
                append("❌ FAILED")
                errorMessage?.let { append(" - $it") }
            }
        }
    }

    /**
     * Protocol test configuration
     */
    data class TestConfig(
        val protocol: InitProtocol,
        val delay: ConnectionDelay,
        val timeout: Long = 10000L
    )

    /**
     * Generate all test configurations to try
     */
    fun generateAllTestConfigs(): List<TestConfig> {
        val configs = mutableListOf<TestConfig>()

        // For each protocol, test with different delays
        InitProtocol.values().forEach { protocol ->
            ConnectionDelay.values().forEach { delay ->
                configs.add(TestConfig(protocol, delay))
            }
        }

        return configs
    }

    /**
     * Generate a recommended subset of test configurations (faster testing)
     */
    fun generateRecommendedTestConfigs(): List<TestConfig> {
        return listOf(
            // Most likely to work - current default
            TestConfig(InitProtocol.NO_INIT, ConnectionDelay.DELAY_2000MS),
            TestConfig(InitProtocol.NO_INIT, ConnectionDelay.DELAY_500MS),
            TestConfig(InitProtocol.NO_INIT, ConnectionDelay.NONE),

            // Legacy protocols that might work on older firmware
            TestConfig(InitProtocol.INIT_0x0A_NO_WAIT, ConnectionDelay.DELAY_50MS),
            TestConfig(InitProtocol.INIT_0x0A_WAIT_0x0B, ConnectionDelay.DELAY_2000MS),
            TestConfig(InitProtocol.INIT_0x0A_PLUS_PRESET, ConnectionDelay.DELAY_100MS),

            // Double init might help with flaky connections
            TestConfig(InitProtocol.DOUBLE_0x0A, ConnectionDelay.DELAY_500MS)
        )
    }

    /**
     * Build the init command bytes based on protocol
     */
    fun buildInitCommandForProtocol(protocol: InitProtocol): ByteArray? {
        return when (protocol) {
            InitProtocol.NO_INIT -> null
            InitProtocol.INIT_0x0A_NO_WAIT,
            InitProtocol.INIT_0x0A_WAIT_0x0B,
            InitProtocol.DOUBLE_0x0A -> ProtocolBuilder.buildInitCommand()
            InitProtocol.INIT_0x0A_PLUS_PRESET -> ProtocolBuilder.buildInitCommand()
        }
    }

    /**
     * Build the secondary command (if any) based on protocol
     */
    fun buildSecondaryCommandForProtocol(protocol: InitProtocol): ByteArray? {
        return when (protocol) {
            InitProtocol.INIT_0x0A_PLUS_PRESET -> ProtocolBuilder.buildInitPreset()
            InitProtocol.DOUBLE_0x0A -> ProtocolBuilder.buildInitCommand()
            else -> null
        }
    }

    /**
     * Check if protocol requires waiting for response
     */
    fun requiresResponseWait(protocol: InitProtocol): Boolean {
        return protocol == InitProtocol.INIT_0x0A_WAIT_0x0B
    }

    /**
     * Get expected response opcode (if any)
     */
    fun getExpectedResponseOpcode(protocol: InitProtocol): UByte? {
        return when (protocol) {
            InitProtocol.INIT_0x0A_WAIT_0x0B -> 0x0Bu
            else -> null
        }
    }

    /**
     * Format test results as a shareable report
     */
    fun formatTestReport(
        results: List<TestResult>,
        deviceName: String,
        androidVersion: String,
        appVersion: String
    ): String = buildString {
        appendLine("═══════════════════════════════════════════════════════")
        appendLine("       VITRUVIAN PROTOCOL TESTER REPORT")
        appendLine("═══════════════════════════════════════════════════════")
        appendLine()
        appendLine("Generated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
        appendLine("Device: $deviceName")
        appendLine("Android: $androidVersion")
        appendLine("App Version: $appVersion")
        appendLine()
        appendLine("─── TEST RESULTS ───")
        appendLine()

        val successfulResults = results.filter { it.success }
        val failedResults = results.filter { !it.success }

        appendLine("✅ Successful configurations: ${successfulResults.size}")
        successfulResults.forEach { result ->
            appendLine("  • ${result.protocol.displayName} + ${result.delay.displayName}")
            appendLine("    Time: ${result.totalTimeMs}ms (connect: ${result.connectionTimeMs}ms, init: ${result.initTimeMs}ms)")
            result.notes?.let { appendLine("    Notes: $it") }
        }

        appendLine()
        appendLine("❌ Failed configurations: ${failedResults.size}")
        failedResults.forEach { result ->
            appendLine("  • ${result.protocol.displayName} + ${result.delay.displayName}")
            appendLine("    Error: ${result.errorMessage ?: "Unknown error"}")
        }

        appendLine()
        appendLine("─── RECOMMENDATION ───")
        appendLine()

        if (successfulResults.isNotEmpty()) {
            val fastest = successfulResults.minByOrNull { it.totalTimeMs }
            appendLine("Recommended protocol: ${fastest?.protocol?.displayName} + ${fastest?.delay?.displayName}")
            appendLine("This configuration connected fastest at ${fastest?.totalTimeMs}ms")
        } else {
            appendLine("No successful configurations found.")
            appendLine("Please share this report for further analysis.")
        }

        appendLine()
        appendLine("═══════════════════════════════════════════════════════")
    }
}
