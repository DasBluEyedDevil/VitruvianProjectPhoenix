package com.example.vitruvianredux.presentation.screen

import android.Manifest
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.example.vitruvianredux.domain.model.ConnectionState
import com.example.vitruvianredux.presentation.navigation.NavGraph
import com.example.vitruvianredux.presentation.navigation.NavigationRoutes
import com.example.vitruvianredux.presentation.viewmodel.MainViewModel
import com.example.vitruvianredux.presentation.viewmodel.ScannedDevice
import com.example.vitruvianredux.ui.theme.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.example.vitruvianredux.presentation.viewmodel.ThemeViewModel

import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.ui.input.nestedscroll.nestedScroll

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EnhancedMainScreen(
    viewModel: MainViewModel = hiltViewModel(),
    exerciseRepository: com.example.vitruvianredux.data.repository.ExerciseRepository = hiltViewModel<MainViewModel>().exerciseRepository
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val connectionLostDuringWorkout by viewModel.connectionLostDuringWorkout.collectAsState()

    val themeViewModel: ThemeViewModel = hiltViewModel()
    val themeMode by themeViewModel.themeMode.collectAsState()

    // Determine if we're in dark mode for TopAppBar color
    val isDarkMode = when (themeMode) {
        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val navController = rememberNavController()
    var currentRoute by remember { mutableStateOf(NavigationRoutes.Home.route) }

    // Track navigation changes
    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { backStackEntry ->
            currentRoute = backStackEntry.destination.route ?: NavigationRoutes.Home.route
        }
    }
    
    // Helper function to determine if current route is a "Workouts" route
    val isWorkoutsRoute = remember(currentRoute) {
        currentRoute == NavigationRoutes.Home.route ||
        currentRoute == NavigationRoutes.JustLift.route ||
        currentRoute == NavigationRoutes.SingleExercise.route ||
        currentRoute == NavigationRoutes.DailyRoutines.route ||
        currentRoute == NavigationRoutes.ActiveWorkout.route ||
        currentRoute == NavigationRoutes.WeeklyPrograms.route ||
        currentRoute.startsWith(NavigationRoutes.ProgramBuilder.route.replace("/{programId}", ""))
    }

    // Request BLE permissions (REMOVED POST_NOTIFICATIONS - not needed and causes Android to forward notifications to BLE device!)
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        listOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    val permissionState = rememberMultiplePermissionsState(permissions)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0), // Let components handle their own insets
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        Text(
                            text = "Vitruvian",
                            style = MaterialTheme.typography.headlineMedium.copy( // Larger for LargeTopAppBar
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFFEAB308), // Bright Gold
                                        Color(0xFFF59E0B)  // Amber
                                    )
                                ),
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "Project Phoenix",
                            style = MaterialTheme.typography.titleMedium.copy( // Larger subtitle
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFFF97316), // Orange
                                        Color(0xFFEF4444)  // Red
                                    )
                                ),
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDarkMode) TopAppBarDark else TopAppBarLight,
                    titleContentColor = TextPrimary,
                    actionIconContentColor = TextPrimary
                ),
                actions = {
                    // Connection status icon (Bluetooth) with text label
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp) // Ensure 48dp touch target
                            .clickable(
                                onClick = {
                                    if (connectionState is ConnectionState.Connected) {
                                        viewModel.disconnect()
                                    } else {
                                        viewModel.ensureConnection(
                                            onConnected = {},
                                            onFailed = {}
                                        )
                                    }
                                },
                                role = androidx.compose.ui.semantics.Role.Button
                            )
                    ) {
                        Icon(
                            imageVector = when (connectionState) {
                                is ConnectionState.Connected -> Icons.Default.Bluetooth
                                is ConnectionState.Connecting -> Icons.AutoMirrored.Filled.BluetoothSearching
                                is ConnectionState.Disconnecting -> Icons.AutoMirrored.Filled.BluetoothSearching
                                is ConnectionState.Disconnected -> Icons.Default.BluetoothDisabled
                                is ConnectionState.Scanning -> Icons.AutoMirrored.Filled.BluetoothSearching
                                is ConnectionState.Error -> Icons.Default.BluetoothDisabled
                            },
                            contentDescription = when (connectionState) {
                                is ConnectionState.Connected -> "Connected to machine. Tap to disconnect"
                                is ConnectionState.Connecting -> "Connecting to machine"
                                is ConnectionState.Disconnecting -> "Disconnecting from machine"
                                is ConnectionState.Disconnected -> "Disconnected. Tap to connect"
                                is ConnectionState.Scanning -> "Scanning for machine"
                                is ConnectionState.Error -> "Connection error. Tap to retry"
                            },
                            tint = when (connectionState) {
                                is ConnectionState.Connected -> Color(0xFF22C55E) // green-500
                                is ConnectionState.Connecting -> Color(0xFFFBBF24) // yellow-400
                                is ConnectionState.Disconnecting -> Color(0xFFFBBF24) // yellow-400
                                is ConnectionState.Disconnected -> Color(0xFFEF4444) // red-500
                                is ConnectionState.Scanning -> Color(0xFF3B82F6) // blue-500
                                is ConnectionState.Error -> Color(0xFFEF4444) // red-500
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = when (connectionState) {
                                is ConnectionState.Connected -> "Connected"
                                is ConnectionState.Connecting -> "Connecting"
                                is ConnectionState.Disconnecting -> "Disconnecting"
                                is ConnectionState.Disconnected -> "Disconnected"
                                is ConnectionState.Scanning -> "Scanning"
                                is ConnectionState.Error -> "Error"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = when (connectionState) {
                                is ConnectionState.Connected -> Color(0xFF22C55E)
                                is ConnectionState.Connecting -> Color(0xFFFBBF24)
                                is ConnectionState.Disconnecting -> Color(0xFFFBBF24)
                                is ConnectionState.Disconnected -> Color(0xFFEF4444)
                                is ConnectionState.Scanning -> Color(0xFF3B82F6)
                                is ConnectionState.Error -> Color(0xFFEF4444)
                            },
                            maxLines = 1
                        )
                    }

                    // Theme toggle
                    com.example.vitruvianredux.presentation.components.ThemeToggle(
                        mode = themeMode,
                        onModeChange = { themeViewModel.setThemeMode(it) }
                    )
                }
            )
        },
        bottomBar = {
            NavigationBar(
                tonalElevation = 8.dp
            ) {
                // Analytics
                NavigationBarItem(
                    selected = currentRoute == NavigationRoutes.Analytics.route,
                    onClick = {
                        navController.navigate(NavigationRoutes.Analytics.route) {
                            popUpTo(NavigationRoutes.Home.route)
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = if (currentRoute == NavigationRoutes.Analytics.route)
                                Icons.Filled.BarChart
                            else
                                Icons.Outlined.BarChart,
                            contentDescription = "Analytics"
                        )
                    },
                    label = { Text("Analytics") },
                    alwaysShowLabel = false
                )

                // Workouts
                NavigationBarItem(
                    selected = isWorkoutsRoute,
                    onClick = {
                        navController.navigate(NavigationRoutes.Home.route) {
                            popUpTo(NavigationRoutes.Home.route)
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = if (isWorkoutsRoute)
                                Icons.Filled.Home
                            else
                                Icons.Outlined.Home,
                            contentDescription = "Workouts"
                        )
                    },
                    label = { Text("Workouts") },
                    alwaysShowLabel = false
                )

                // Settings
                NavigationBarItem(
                    selected = currentRoute == NavigationRoutes.Settings.route,
                    onClick = {
                        navController.navigate(NavigationRoutes.Settings.route) {
                            popUpTo(NavigationRoutes.Home.route)
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = if (currentRoute == NavigationRoutes.Settings.route)
                                Icons.Filled.Settings
                            else
                                Icons.Outlined.Settings,
                            contentDescription = "Settings"
                        )
                    },
                    label = { Text("Settings") },
                    alwaysShowLabel = false
                )
            }
        }
    ) { padding ->
        // Use proper padding to account for TopAppBar and system bars (status bar, notch, etc.)
        val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
        val adjustedPadding = PaddingValues(
            start = padding.calculateLeftPadding(layoutDirection),
            end = padding.calculateRightPadding(layoutDirection),
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding()
        )

        if (!permissionState.allPermissionsGranted) {
            PermissionRequestScreen(
                permissionState = permissionState,
                modifier = Modifier.padding(adjustedPadding)
            )
        } else {
            NavGraph(
                navController = navController,
                viewModel = viewModel,
                exerciseRepository = exerciseRepository,
                themeMode = themeMode,
                onThemeModeChange = { mode -> themeViewModel.setThemeMode(mode) },
                modifier = Modifier.padding(adjustedPadding)
            )
        }
    }

    // Show connection lost alert during workout (Issue #43)
    if (connectionLostDuringWorkout) {
        com.example.vitruvianredux.presentation.components.ConnectionLostDialog(
            onReconnect = {
                viewModel.dismissConnectionLostAlert()
                viewModel.ensureConnection(
                    onConnected = {},
                    onFailed = {}
                )
            },
            onDismiss = {
                viewModel.dismissConnectionLostAlert()
            }
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionRequestScreen(
    permissionState: MultiplePermissionsState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "Connection information",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(Spacing.medium))
        Text(
            "Bluetooth permissions required",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Spacing.small))
        Text(
            buildString {
                append("This app needs Bluetooth and Location permissions to connect to your Vitruvian machine.")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    append(" Notification permission is needed to keep workouts running in the background.")
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Spacing.large))
        Button(onClick = { permissionState.launchMultiplePermissionRequest() }) {
            Icon(Icons.Default.Check, contentDescription = "Confirm")
            Spacer(modifier = Modifier.width(Spacing.small))
            Text("Grant permissions")
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
            Column(modifier = Modifier.fillMaxWidth()) {
                if (devices.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.medium),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isScanning) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(Spacing.small))
                                Text("Scanning...")
                            }
                        } else {
                            Text(
                                "No devices found. Try scanning again.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                if (devices.isNotEmpty()) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
                        items(devices, key = { it.address }) { device ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onDeviceSelected(device) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(20.dp) // Material 3 Expressive: More rounded (was 16dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(Spacing.medium),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            device.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            device.address,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "Navigate to device",
                                        tint = MaterialTheme.colorScheme.primary
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
                    Icon(Icons.Default.Refresh, contentDescription = "Rescan for devices", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(Spacing.extraSmall))
                    Text("Rescan", color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
