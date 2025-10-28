package com.example.vitruvianredux

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.vitruvianredux.presentation.screen.EnhancedMainScreen
import com.example.vitruvianredux.ui.theme.VitruvianReduxTheme
import dagger.hilt.android.AndroidEntryPoint

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
            VitruvianReduxTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    EnhancedMainScreen()
                }
            }
        }
    }
}

