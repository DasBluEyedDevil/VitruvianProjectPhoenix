package com.example.vitruvianredux.presentation.screen

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.vitruvianredux.ui.theme.*

@Composable
fun CountdownCard(secondsRemaining: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, PrimaryPurple, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.extraLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.medium)
        ) {
            Text(
                text = "Get Ready!",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "$secondsRemaining",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = PrimaryPurple
            )
            Text(
                text = "Starting in...",
                style = MaterialTheme.typography.titleLarge,
                color = TextSecondary
            )
        }
    }
}
