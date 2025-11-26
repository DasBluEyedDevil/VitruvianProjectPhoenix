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
        val notes: String? = null,
        // Enhanced diagnostics
        val diagnostics: TestDiagnostics? = null
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
     * Enhanced diagnostic data collected during test
     */
    data class TestDiagnostics(
        val firmwareVersion: String? = null,
        val mtuSize: Int? = null,
        val monitorPollsReceived: Int = 0,
        val monitorPollsFailed: Int = 0,
        val disconnectTimeSeconds: Int? = null,  // If disconnected, when did it happen?
        val lastDisconnectReason: String? = null,
        val rssiAtDisconnect: Int? = null,
        val workoutSimulationDurationMs: Long = 0
    )

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
            TestConfig(InitProtocol.INIT_DOUBLE_0x0A, ConnectionDelay.DELAY_500MS)
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
            InitProtocol.INIT_DOUBLE_0x0A -> ProtocolBuilder.buildInitCommand()
            InitProtocol.INIT_0x0A_PLUS_PRESET -> ProtocolBuilder.buildInitCommand()
        }
    }

    /**
     * Build the secondary command (if any) based on protocol
     */
    fun buildSecondaryCommandForProtocol(protocol: InitProtocol): ByteArray? {
        return when (protocol) {
            InitProtocol.INIT_0x0A_PLUS_PRESET -> ProtocolBuilder.buildInitPreset()
            InitProtocol.INIT_DOUBLE_0x0A -> ProtocolBuilder.buildInitCommand()
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

        // Show firmware version from first result that has it
        val firmwareVersion = results.firstNotNullOfOrNull { it.diagnostics?.firmwareVersion }
        firmwareVersion?.let { appendLine("Firmware: $it") }

        // Show MTU from first result that has it
        val mtuSize = results.firstNotNullOfOrNull { it.diagnostics?.mtuSize }
        mtuSize?.let { appendLine("MTU Size: $it bytes") }

        appendLine()
        appendLine("─── TEST RESULTS ───")
        appendLine()

        val successfulResults = results.filter { it.success }
        val failedResults = results.filter { !it.success }

        appendLine("✅ Successful configurations: ${successfulResults.size}")
        successfulResults.forEach { result ->
            appendLine("  • ${result.protocol.displayName} + ${result.delay.displayName}")
            appendLine("    Time: ${result.totalTimeMs}ms (connect: ${result.connectionTimeMs}ms, init: ${result.initTimeMs}ms)")
            result.diagnostics?.let { diag ->
                if (diag.monitorPollsReceived > 0) {
                    appendLine("    Monitor polls: ${diag.monitorPollsReceived} received, ${diag.monitorPollsFailed} failed")
                }
                if (diag.workoutSimulationDurationMs > 0) {
                    appendLine("    Workout simulation: ${diag.workoutSimulationDurationMs}ms stable")
                }
            }
            result.notes?.let { appendLine("    Notes: $it") }
        }

        appendLine()
        appendLine("❌ Failed configurations: ${failedResults.size}")
        failedResults.forEach { result ->
            appendLine("  • ${result.protocol.displayName} + ${result.delay.displayName}")
            appendLine("    Error: ${result.errorMessage ?: "Unknown error"}")
            result.diagnostics?.let { diag ->
                diag.disconnectTimeSeconds?.let { sec ->
                    appendLine("    ⚠️ DISCONNECTED at ${sec}s into workout simulation")
                }
                diag.lastDisconnectReason?.let { reason ->
                    appendLine("    Disconnect reason: $reason")
                }
                if (diag.monitorPollsReceived > 0 || diag.monitorPollsFailed > 0) {
                    appendLine("    Monitor polls before failure: ${diag.monitorPollsReceived} received, ${diag.monitorPollsFailed} failed")
                }
            }
        }

        // Analyze disconnect patterns
        val disconnectTimes = failedResults.mapNotNull { it.diagnostics?.disconnectTimeSeconds }
        if (disconnectTimes.isNotEmpty()) {
            appendLine()
            appendLine("─── DISCONNECT PATTERN ANALYSIS ───")
            appendLine()
            val avgDisconnect = disconnectTimes.average()
            val minDisconnect = disconnectTimes.minOrNull() ?: 0
            val maxDisconnect = disconnectTimes.maxOrNull() ?: 0
            appendLine("Disconnect times: ${disconnectTimes.joinToString(", ")}s")
            appendLine("Average disconnect time: %.1fs".format(avgDisconnect))
            appendLine("Range: ${minDisconnect}s - ${maxDisconnect}s")

            if (avgDisconnect in 4.0..6.0) {
                appendLine()
                appendLine("⚠️ PATTERN DETECTED: ~5 second disconnects")
                appendLine("This suggests a connection supervision timeout issue.")
                appendLine("The device may be timing out due to:")
                appendLine("  - Missing keep-alive packets")
                appendLine("  - BLE connection parameter mismatch")
                appendLine("  - Android BLE stack issues")
            }
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

    /**
     * Phases of the exercise cycle test
     */
    enum class ExerciseCyclePhase(val displayName: String, val description: String) {
        SCAN("Scan", "Scanning for Vitruvian device"),
        CONNECT("Connect", "Establishing BLE connection"),
        INITIALIZE("Initialize", "Sending INIT command"),
        CONFIGURE("Configure", "Sending workout configuration"),
        START("Start", "Sending START command"),
        WAIT("Wait", "Holding active for 15 seconds"),
        STOP_PRIMARY("Stop (0x05)", "Sending primary STOP command"),
        STOP_OFFICIAL("Stop (0x50)", "Sending official stop packet"),
        CLEANUP("Cleanup", "Disconnecting and cleaning up")
    }

    /**
     * Result of a single phase in the exercise cycle test
     */
    data class ExerciseCyclePhaseResult(
        val phase: ExerciseCyclePhase,
        val success: Boolean,
        val durationMs: Long,
        val commandSent: ByteArray? = null,
        val errorMessage: String? = null,
        val notes: String? = null
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as ExerciseCyclePhaseResult
            return phase == other.phase && success == other.success && durationMs == other.durationMs
        }

        override fun hashCode(): Int {
            var result = phase.hashCode()
            result = 31 * result + success.hashCode()
            result = 31 * result + durationMs.hashCode()
            return result
        }
    }

    /**
     * Format exercise cycle test results as a shareable report
     */
    fun formatExerciseCycleReport(
        phaseResults: List<ExerciseCyclePhaseResult>,
        deviceName: String,
        androidVersion: String,
        appVersion: String
    ): String = buildString {
        appendLine("═══════════════════════════════════════════════════════")
        appendLine("       VITRUVIAN EXERCISE CYCLE TEST REPORT")
        appendLine("═══════════════════════════════════════════════════════")
        appendLine()
        appendLine("Generated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
        appendLine("Device: $deviceName")
        appendLine("Android: $androidVersion")
        appendLine("App Version: $appVersion")
        appendLine()
        appendLine("─── PHASE RESULTS ───")
        appendLine()

        phaseResults.forEachIndexed { index, result ->
            val status = if (result.success) "✅ SUCCESS" else "❌ FAILED"
            appendLine("${index + 1}. ${result.phase.displayName}: $status (${result.durationMs}ms)")

            result.commandSent?.let { cmd ->
                val hexStr = cmd.joinToString(" ") { "%02X".format(it) }
                val truncated = if (cmd.size > 16) "$hexStr... (${cmd.size} bytes)" else hexStr
                appendLine("   Sent: $truncated")
            }

            result.notes?.let { appendLine("   Notes: $it") }
            result.errorMessage?.let { appendLine("   Error: $it") }
            appendLine()
        }

        appendLine("─── SUMMARY ───")
        appendLine()

        val totalDuration = phaseResults.sumOf { it.durationMs }
        val failedPhases = phaseResults.filter { !it.success }

        if (failedPhases.isEmpty()) {
            appendLine("Result: ALL PHASES PASSED")
        } else {
            appendLine("Result: ${failedPhases.size} PHASE(S) FAILED")
            failedPhases.forEach { appendLine("  - ${it.phase.displayName}: ${it.errorMessage ?: "Unknown error"}") }
        }
        appendLine("Total duration: ${totalDuration}ms")
        appendLine()
        appendLine("═══════════════════════════════════════════════════════")
    }
}
