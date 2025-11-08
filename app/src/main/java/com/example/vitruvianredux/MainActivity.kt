package com.example.vitruvianredux

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.vitruvianredux.domain.model.WorkoutState
import com.example.vitruvianredux.presentation.screen.EnhancedMainScreen
import com.example.vitruvianredux.presentation.screen.LargeSplashScreen
import com.example.vitruvianredux.presentation.viewmodel.MainViewModel
import com.example.vitruvianredux.presentation.viewmodel.ThemeViewModel
import com.example.vitruvianredux.ui.theme.VitruvianProjectPhoenixTheme
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen BEFORE super.onCreate() to prevent black screen
        // This keeps the splash visible until the first frame is drawn
        installSplashScreen()

        super.onCreate(savedInstanceState)

        // Ensure system windows are not drawn behind content to avoid black overlay issues
        WindowCompat.setDecorFitsSystemWindows(window, true)

        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val themeMode = themeViewModel.themeMode.collectAsState().value
            val workoutState by mainViewModel.workoutState.collectAsState()
            var showLargeSplash by remember { mutableStateOf(true) }

            // Keep screen on during active workouts (Issue #42)
            // This prevents screen lock from disconnecting BLE (Issue #43)
            DisposableEffect(workoutState) {
                val shouldKeepScreenOn = when (workoutState) {
                    is WorkoutState.Active,
                    is WorkoutState.Countdown,
                    is WorkoutState.Resting,
                    is WorkoutState.Initializing -> true
                    else -> false
                }

                if (shouldKeepScreenOn) {
                    Timber.d("Keeping screen on during workout state: $workoutState")
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    Timber.d("Releasing screen keep-on for state: $workoutState")
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }

                onDispose {
                    // Always clear flag when this effect is disposed
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            // Hide large splash after a short delay
            LaunchedEffect(Unit) {
                // 900ms feels snappy; adjust if you want shorter/longer
                kotlinx.coroutines.delay(900)
                showLargeSplash = false
            }

            VitruvianProjectPhoenixTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showLargeSplash) {
                        LargeSplashScreen(visible = true)
                    } else {
                        EnhancedMainScreen()
                    }
                }
            }
        }
    }
}
