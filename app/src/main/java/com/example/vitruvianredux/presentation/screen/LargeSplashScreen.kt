package com.example.vitruvianredux.presentation.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.vitruvianredux.R

@Composable
fun LargeSplashScreen(visible: Boolean) {
    val config = LocalConfiguration.current
    // Scale logo to ~55% of the smallest screen dimension for prominent display
    @Suppress("DEPRECATION")
    val minDp = minOf(config.screenWidthDp, config.screenHeightDp)
    val logoSize = (minDp * 0.55f).dp

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(250)),
        exit = fadeOut(animationSpec = tween(250))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorResource(id = R.color.ic_launcher_background)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.vitphoe_logo_foreground),
                contentDescription = "Vitruvian Phoenix Logo",
                modifier = Modifier.size(logoSize)
            )
        }
    }
}

