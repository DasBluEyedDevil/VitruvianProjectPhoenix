package com.example.vitruvianredux.presentation.screen

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.vitruvianredux.presentation.viewmodel.ProtocolTesterViewModel
import com.example.vitruvianredux.presentation.viewmodel.ProtocolTesterViewModel.TestMode
import com.example.vitruvianredux.presentation.viewmodel.ProtocolTesterViewModel.TestState
import com.example.vitruvianredux.ui.theme.Spacing
import com.example.vitruvianredux.util.ProtocolTester.TestResult
import com.example.vitruvianredux.util.ProtocolTester.ExerciseCyclePhaseResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtocolTesterScreen(
    onNavigateBack: () -> Unit,
    viewModel: ProtocolTesterViewModel = hiltViewModel()
) {
    val testState by viewModel.testState.collectAsState()
    val results by viewModel.results.collectAsState()
    val currentDevice by viewModel.currentDeviceName.collectAsState()
    val testMode by viewModel.testMode.collectAsState()
    val exerciseCycleResults by viewModel.exerciseCycleResults.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Protocol Tester",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        currentDevice?.let {
                            Text(
                                "Device: $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (testState is TestState.Completed || testState is TestState.ExerciseCycleCompleted) {
                        IconButton(
                            onClick = {
                                val report = if (testState is TestState.ExerciseCycleCompleted) {
                                    viewModel.generateExerciseCycleReport()
                                } else {
                                    viewModel.generateReport()
                                }
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, report)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share Protocol Test Report"))
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share Report")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium)
        ) {
            // Info card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.medium)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .shadow(8.dp, RoundedCornerShape(20.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFF8B5CF6), Color(0xFF9333EA))
                                    ),
                                    RoundedCornerShape(20.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Science,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(Spacing.medium))
                        Column {
                            Text(
                                "BLE Protocol Diagnostics",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Find the best connection protocol for your device",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Test mode selector (only show when idle)
            AnimatedVisibility(visible = testState is TestState.Idle) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.medium)
                    ) {
                        Text(
                            "Test Mode",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(Spacing.small))

                        TestMode.values().forEach { mode ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = testMode == mode,
                                    onClick = { viewModel.setTestMode(mode) }
                                )
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    Text(
                                        mode.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        mode.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // State-dependent content
            when (val state = testState) {
                is TestState.Idle -> {
                    IdleContent(onStartTesting = { viewModel.startTesting() })
                }

                is TestState.Scanning -> {
                    ScanningContent(onCancel = { viewModel.cancelTesting() })
                }

                is TestState.Testing -> {
                    TestingContent(
                        currentConfig = state.currentConfig,
                        progress = state.progress,
                        total = state.total,
                        results = results,
                        onCancel = { viewModel.cancelTesting() }
                    )
                }

                is TestState.Completed -> {
                    CompletedContent(
                        results = state.results,
                        onReset = { viewModel.reset() }
                    )
                }

                is TestState.ExerciseCycleTesting -> {
                    ExerciseCycleTestingContent(
                        currentPhase = state.currentPhase,
                        phaseIndex = state.phaseIndex,
                        totalPhases = state.totalPhases,
                        elapsedWaitSeconds = state.elapsedWaitSeconds,
                        phaseResults = exerciseCycleResults,
                        onCancel = { viewModel.cancelTesting() }
                    )
                }

                is TestState.ExerciseCycleCompleted -> {
                    ExerciseCycleCompletedContent(
                        phaseResults = state.phaseResults,
                        onReset = { viewModel.reset() }
                    )
                }

                is TestState.Error -> {
                    ErrorContent(
                        message = state.message,
                        onRetry = { viewModel.reset() }
                    )
                }
            }
        }
    }
}

@Composable
private fun IdleContent(onStartTesting: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Text(
            "Ready to Test",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Spacing.small))

        Text(
            "This will test different BLE connection protocols to find\nwhich works best with your Vitruvian trainer.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Spacing.large))

        Button(
            onClick = onStartTesting,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Start Testing", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ScanningContent(onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
            strokeWidth = 4.dp
        )

        Spacer(modifier = Modifier.height(Spacing.medium))

        Text(
            "Scanning for Vitruvian...",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(Spacing.small))

        Text(
            "Make sure your trainer is powered on",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(Spacing.large))

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.height(48.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Cancel")
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TestingContent(
    currentConfig: com.example.vitruvianredux.util.ProtocolTester.TestConfig,
    progress: Int,
    total: Int,
    results: List<TestResult>,
    onCancel: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Progress header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.medium),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Testing Protocol $progress of $total",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(Spacing.small))

                LinearProgressIndicator(
                    progress = { progress.toFloat() / total },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    trackColor = MaterialTheme.colorScheme.primaryContainer
                )

                Spacer(modifier = Modifier.height(Spacing.medium))

                Text(
                    "Current: ${currentConfig.protocol.displayName}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Delay: ${currentConfig.delay.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.medium))

        // Results list
        Text(
            "Results So Far",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(Spacing.small))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(results) { result ->
                ResultCard(result = result)
            }
        }

        Spacer(modifier = Modifier.height(Spacing.medium))

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Default.Close, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Cancel Testing")
        }
    }
}

@Composable
private fun ExerciseCycleTestingContent(
    currentPhase: com.example.vitruvianredux.util.ProtocolTester.ExerciseCyclePhase,
    phaseIndex: Int,
    totalPhases: Int,
    elapsedWaitSeconds: Int,
    phaseResults: List<ExerciseCyclePhaseResult>,
    onCancel: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Progress header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.medium),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Phase ${phaseIndex + 1} of $totalPhases",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(Spacing.small))

                LinearProgressIndicator(
                    progress = { (phaseIndex + 1).toFloat() / totalPhases },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    trackColor = MaterialTheme.colorScheme.primaryContainer
                )

                Spacer(modifier = Modifier.height(Spacing.medium))

                Text(
                    currentPhase.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    currentPhase.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Show countdown during wait phase
                if (currentPhase == com.example.vitruvianredux.util.ProtocolTester.ExerciseCyclePhase.WAIT) {
                    Spacer(modifier = Modifier.height(Spacing.medium))
                    Text(
                        "${15 - elapsedWaitSeconds}s remaining",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.medium))

        // Results list
        Text(
            "Phase Results",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(Spacing.small))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(phaseResults) { result ->
                ExerciseCyclePhaseCard(result = result)
            }
        }

        Spacer(modifier = Modifier.height(Spacing.medium))

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Default.Close, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Cancel Testing")
        }
    }
}

@Composable
private fun CompletedContent(
    results: List<TestResult>,
    onReset: () -> Unit
) {
    val successCount = results.count { it.success }
    val fastest = results.filter { it.success }.minByOrNull { it.totalTimeMs }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Summary card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (successCount > 0)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.errorContainer
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.medium),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    if (successCount > 0) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = if (successCount > 0)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )

                Spacer(modifier = Modifier.height(Spacing.small))

                Text(
                    "Testing Complete",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "$successCount of ${results.size} protocols worked",
                    style = MaterialTheme.typography.bodyMedium
                )

                if (fastest != null) {
                    Spacer(modifier = Modifier.height(Spacing.medium))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(Spacing.medium))

                    Text(
                        "Recommended Protocol",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${fastest.protocol.displayName} + ${fastest.delay.displayName}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "Connection time: ${fastest.totalTimeMs}ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.medium))

        Text(
            "All Results",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(Spacing.small))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(results.sortedBy { !it.success }) { result ->
                ResultCard(result = result)
            }
        }

        Spacer(modifier = Modifier.height(Spacing.medium))

        Button(
            onClick = onReset,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Run Again")
        }
    }
}

@Composable
private fun ExerciseCycleCompletedContent(
    phaseResults: List<ExerciseCyclePhaseResult>,
    onReset: () -> Unit
) {
    val successCount = phaseResults.count { it.success }
    val totalDuration = phaseResults.sumOf { it.durationMs }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Summary card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (successCount == phaseResults.size)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.errorContainer
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.medium),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    if (successCount == phaseResults.size) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = if (successCount == phaseResults.size)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )

                Spacer(modifier = Modifier.height(Spacing.small))

                Text(
                    "Exercise Cycle Test Complete",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "$successCount of ${phaseResults.size} phases passed",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(Spacing.small))

                Text(
                    "Total duration: ${totalDuration}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.medium))

        Text(
            "Phase Results",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(Spacing.small))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(phaseResults) { result ->
                ExerciseCyclePhaseCard(result = result)
            }
        }

        Spacer(modifier = Modifier.height(Spacing.medium))

        Button(
            onClick = onReset,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Run Again")
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Icon(
            Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(Spacing.medium))

        Text(
            "Testing Failed",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(Spacing.small))

        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Spacing.large))

        Button(
            onClick = onRetry,
            modifier = Modifier.height(48.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Try Again")
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ResultCard(result: TestResult) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        colors = CardDefaults.cardColors(
            containerColor = if (result.success)
                MaterialTheme.colorScheme.surfaceContainerHigh
            else
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (result.success) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                tint = if (result.success)
                    Color(0xFF22C55E)
                else
                    MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(Spacing.small))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    result.protocol.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Delay: ${result.delay.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                result.errorMessage?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (result.success) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${result.totalTimeMs}ms",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "total",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ExerciseCyclePhaseCard(result: ExerciseCyclePhaseResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (result.success)
                MaterialTheme.colorScheme.surfaceContainerHigh
            else
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.small)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (result.success) Icons.Default.CheckCircle else Icons.Default.Cancel,
                    contentDescription = null,
                    tint = if (result.success)
                        Color(0xFF22C55E)
                    else
                        MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(Spacing.small))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        result.phase.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    result.notes?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    result.errorMessage?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${result.durationMs}ms",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (result.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }

            // Show command hex if present
            result.commandSent?.let { cmd ->
                Spacer(modifier = Modifier.height(4.dp))
                val hexStr = cmd.take(16).joinToString(" ") { "%02X".format(it) }
                val displayStr = if (cmd.size > 16) "$hexStr... (${cmd.size} bytes)" else hexStr
                Text(
                    "Sent: $displayStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
    }
}
