package com.example.vitruvianredux.presentation.components

import android.os.Build
import android.widget.NumberPicker
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Compact Number Picker using native Android NumberPicker
 * Provides reliable wheel-based number selection with proper physics
 */
@Composable
fun CompactNumberPicker(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
    label: String = "",
    suffix: String = ""
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Native Android NumberPicker wrapped in AndroidView
        // IMPORTANT: NumberPicker only supports non-negative minValue, so we use offset approach for negative ranges
        val offset = if (range.first < 0) -range.first else 0
        val pickerRange = (range.first + offset)..(range.last + offset)

        // Get the theme-aware text color
        val textColor = MaterialTheme.colorScheme.onSurface

        // Use isDarkTheme as key for API 28 and below to force recreation on theme change
        val isDarkTheme = isSystemInDarkTheme()

        // For API 28 and below, use key() to force recreation on theme change
        // For API 29+, use setTextColor() in update block
        key(if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) isDarkTheme else null) {
            AndroidView(
                factory = { context ->
                NumberPicker(context).apply {
                    // Use offset range that starts at 0 or positive number
                    minValue = pickerRange.first
                    maxValue = pickerRange.last
                    this.value = (value + offset).coerceIn(pickerRange)
                    wrapSelectorWheel = false // Prevents wrapping around

                    // Always use displayedValues to show actual range values with suffix
                    val displayValues = (range.first..range.last).map {
                        if (suffix.isNotEmpty()) "$it $suffix" else "$it"
                    }.toTypedArray()
                    this.displayedValues = displayValues

                    setOnValueChangedListener { _, _, newPickerVal ->
                        // Convert picker value back to actual value by removing offset
                        val actualValue = newPickerVal - offset
                        onValueChange(actualValue)
                    }

                    // Set text color for API 29+ (Android Q and above)
                    // NumberPicker gained setTextColor() method in API 29
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        setTextColor(textColor.toArgb())
                    }
                }
            },
            update = { picker ->
                // Update picker when value changes externally (convert to picker value with offset)
                val pickerValue = value + offset
                if (picker.value != pickerValue) {
                    picker.value = pickerValue.coerceIn(pickerRange)
                }

                // Update text color for API 29+ on theme changes
                // For API 28 and below, key() forces recreation so this isn't needed
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    picker.setTextColor(textColor.toArgb())
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
            )
        }
    }
}
