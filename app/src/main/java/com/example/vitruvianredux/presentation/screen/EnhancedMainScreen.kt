package com.example.vitruvianredux.presentation.screen

import android.Manifest
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.vitruvianredux.domain.model.ConnectionState
import com.example.vitruvianredux.presentation.viewmodel.MainViewModel
import com.example.vitruvianredux.presentation.viewmodel.ScannedDevice
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EnhancedMainScreen(
    viewModel: MainViewModel = hiltViewModel()
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val workoutState by viewModel.workoutState.collectAsState()
    val currentMetric by viewModel.currentMetric.collectAsState()
    val workoutParameters by viewModel.workoutParameters.collectAsState()
    val repCount by viewModel.repCount.collectAsState()
    val scannedDevices by viewModel.scannedDevices.collectAsState()
    val workoutHistory by viewModel.workoutHistory.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    var showDeviceSelector by remember { mutableStateOf(false) }

    // Request BLE permissions
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        buildList {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            // Add notification permission for Android 13+ (required for foreground service)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    } else {
        listOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    val permissionState = rememberMultiplePermissionsState(permissions)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vitruvian Control") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    // Connection status indicator
                    when (connectionState) {
                        is ConnectionState.Connected -> {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Connected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        is ConnectionState.Scanning -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp).padding(end = 8.dp)
                            )
                        }
                        else -> {}
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Workout") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text("History") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
            }
        }
    ) { padding ->
        // Check permissions first
        if (!permissionState.allPermissionsGranted) {
            PermissionRequestScreen(
                permissionState = permissionState,
                modifier = Modifier.padding(padding)
            )
        } else {
            when (selectedTab) {
                0 -> WorkoutTab(
                    connectionState = connectionState,
                    workoutState = workoutState,
                    currentMetric = currentMetric,
                    workoutParameters = workoutParameters,
                    repCount = repCount,
                    onScan = { 
                        viewModel.startScanning()
                        showDeviceSelector = true
                    },
                    onDisconnect = { viewModel.disconnect() },
                    onStartWorkout = { viewModel.startWorkout() },
                    onStopWorkout = { viewModel.stopWorkout() },
                    onUpdateParameters = { viewModel.updateWorkoutParameters(it) },
                    modifier = Modifier.padding(padding)
                )
                1 -> HistoryTab(
                    workoutHistory = workoutHistory,
                    onDeleteWorkout = { viewModel.deleteWorkout(it) },
                    modifier = Modifier.padding(padding)
                )
                2 -> SettingsTab(
                    onColorSchemeChange = { viewModel.setColorScheme(it) },
                    onDeleteAllWorkouts = { viewModel.deleteAllWorkouts() },
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }

    // Device selector dialog
    if (showDeviceSelector) {
        DeviceSelectorDialog(
            devices = scannedDevices,
            isScanning = connectionState is ConnectionState.Scanning,
            onDeviceSelected = { device ->
                viewModel.connectToDevice(device.address)
                viewModel.stopScanning()
                showDeviceSelector = false
            },
            onRescan = {
                viewModel.startScanning()
            },
            onDismiss = {
                viewModel.stopScanning()
                showDeviceSelector = false
            }
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionRequestScreen(
    permissionState: com.google.accompanist.permissions.MultiplePermissionsState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Bluetooth Permissions Required",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "This app needs Bluetooth and Location permissions to connect to your Vitruvian machine. " +
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        "Notification permission is needed to keep workouts running in the background."
                    } else {
                        ""
                    },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { permissionState.launchMultiplePermissionRequest() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Grant Permissions")
        }
    }
}

@Composable
fun DeviceSelectorDialog(
    devices: List<ScannedDevice>,
    isScanning: Boolean,
    onDeviceSelected: (ScannedDevice) -> Unit,
    onRescan: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Vitruvian Device") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isScanning) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scanning...")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (devices.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            if (isScanning) {
                                "Scanning for Vitruvian devices...\nMake sure your trainer is powered on."
                            } else {
                                "No devices found.\nTry scanning again."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
                    ) {
                        items(devices) { device ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { onDeviceSelected(device) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            device.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            device.address,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!isScanning) {
                TextButton(onClick = onRescan) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Rescan")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
