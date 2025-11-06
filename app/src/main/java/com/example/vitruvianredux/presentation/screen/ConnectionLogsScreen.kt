package com.example.vitruvianredux.presentation.screen

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.vitruvianredux.data.local.ConnectionLogEntity
import com.example.vitruvianredux.presentation.viewmodel.ConnectionLogsViewModel
import com.example.vitruvianredux.presentation.viewmodel.LogStats
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Screen for viewing and managing connection logs
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionLogsScreen(
    onNavigateBack: () -> Unit,
    mainViewModel: com.example.vitruvianredux.presentation.viewmodel.MainViewModel,
    viewModel: ConnectionLogsViewModel = hiltViewModel()
) {
    val filteredLogs by viewModel.filteredLogs.collectAsState()
    val logStats by viewModel.logStats.collectAsState()
    val selectedLevelFilter by viewModel.selectedLevelFilter.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isAutoConnecting by mainViewModel.isAutoConnecting.collectAsState()
    val connectionError by mainViewModel.connectionError.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showClearDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connection Logs") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Export button
                    IconButton(onClick = { showExportDialog = true }) {
                        Icon(Icons.Default.Share, "Export logs")
                    }
                    // Clear logs button
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Default.Delete, "Clear all logs")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Stats card
            LogStatsCard(stats = logStats)

            Spacer(modifier = Modifier.height(16.dp))

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search logs...") },
                leadingIcon = { Icon(Icons.Default.Search, "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, "Clear search")
                        }
                    }
                },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Filter chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedLevelFilter == "ERROR",
                    onClick = {
                        viewModel.setLevelFilter(if (selectedLevelFilter == "ERROR") null else "ERROR")
                    },
                    label = { Text("Errors") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )

                FilterChip(
                    selected = selectedLevelFilter == "WARNING",
                    onClick = {
                        viewModel.setLevelFilter(if (selectedLevelFilter == "WARNING") null else "WARNING")
                    },
                    label = { Text("Warnings") }
                )

                FilterChip(
                    selected = selectedLevelFilter == "INFO",
                    onClick = {
                        viewModel.setLevelFilter(if (selectedLevelFilter == "INFO") null else "INFO")
                    },
                    label = { Text("Info") }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Logs list
            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No logs found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredLogs, key = { it.id }) { log ->
                        LogEntryCard(log = log)
                    }
                }
            }
        }
    }

    // Clear dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Logs?") },
            text = { Text("This will permanently delete all connection logs. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllLogs()
                        showClearDialog = false
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Export dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Logs") },
            text = { Text("Export all logs as a text file?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val logsText = viewModel.exportLogsAsText()

                            // Create a temporary file
                            val file = File(context.cacheDir, "vitruvian_connection_logs_${System.currentTimeMillis()}.txt")
                            file.writeText(logsText)

                            // Share the file
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )

                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }

                            context.startActivity(Intent.createChooser(shareIntent, "Export Logs"))
                        }
                        showExportDialog = false
                    }
                ) {
                    Text("Export")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Auto-connect UI overlays (same as other screens)
    if (isAutoConnecting) {
        com.example.vitruvianredux.presentation.components.ConnectingOverlay()
    }

    connectionError?.let { error ->
        com.example.vitruvianredux.presentation.components.ConnectionErrorDialog(
            message = error,
            onDismiss = { mainViewModel.clearConnectionError() }
        )
    }
}

@Composable
private fun LogStatsCard(stats: LogStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(label = "Total", value = stats.total.toString())
            StatItem(label = "Errors", value = stats.errors.toString(), color = MaterialTheme.colorScheme.error)
            StatItem(label = "Warnings", value = stats.warnings.toString(), color = Color(0xFFFF9800))
            StatItem(label = "Info", value = stats.info.toString())
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSecondaryContainer) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun LogEntryCard(log: ConnectionLogEntity) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }
    val timeString = remember(log.timestamp) { dateFormat.format(Date(log.timestamp)) }

    val levelColor = when (log.level) {
        "ERROR" -> MaterialTheme.colorScheme.error
        "WARNING" -> Color(0xFFFF9800)
        "INFO" -> MaterialTheme.colorScheme.primary
        "DEBUG" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = levelColor.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = timeString,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = levelColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = log.level,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = levelColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Event type
            Text(
                text = log.eventType,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = levelColor
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Message
            Text(
                text = log.message,
                style = MaterialTheme.typography.bodyMedium
            )

            // Device info
            if (log.deviceName != null || log.deviceAddress != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Device: ${log.deviceName ?: "Unknown"} (${log.deviceAddress ?: "N/A"})",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Details (if present)
            if (!log.details.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = log.details,
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
