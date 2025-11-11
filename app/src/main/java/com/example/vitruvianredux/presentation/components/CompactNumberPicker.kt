package com.example.vitruvianredux.presentation.components

import android.os.Build
import android.widget.NumberPicker
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
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

        // Row with -/+ buttons and number picker
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Decrease button
            IconButton(
                onClick = {
                    val newValue = (value - 1).coerceIn(range)
                    onValueChange(newValue)
                },
                enabled = value > range.first,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Decrease $label",
                    tint = if (value > range.first)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }

            // Native Android NumberPicker wrapped in AndroidView
            // IMPORTANT: NumberPicker only supports non-negative minValue, so we use offset approach for negative ranges
            val offset = if (range.first < 0) -range.first else 0
            val pickerRange = (range.first + offset)..(range.last + offset)

            // Get the theme-aware text color
            val textColor = MaterialTheme.colorScheme.onSurface

            // Don't use key() wrapper - let update block handle all text color changes
            // This ensures update runs on every recomposition, catching new EditText children during scroll
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

                        // Set text color for all Android versions
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            // API 29+: Use official setTextColor() method
                            setTextColor(textColor.toArgb())
                        } else {
                            // API 28 and below (including Fire OS): Aggressively style ALL text-related children
                            // Use post() to ensure styling is applied after view is fully created
                            post {
                                try {
                                    // Style all TextView-based children (EditText, TextView, etc.)
                                    val count = childCount
                                    for (i in 0 until count) {
                                        val child = getChildAt(i)
                                        when (child) {
                                            is android.widget.EditText -> {
                                                child.setTextColor(textColor.toArgb())
                                                child.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                            }
                                            is android.widget.TextView -> {
                                                child.setTextColor(textColor.toArgb())
                                                child.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                            }
                                        }
                                    }

                                    // Try to access and modify the Paint object used for selector rendering
                                    try {
                                        val paintField = NumberPicker::class.java.getDeclaredField("mSelectorWheelPaint")
                                        paintField.isAccessible = true
                                        val paint = paintField.get(this) as? android.graphics.Paint
                                        paint?.color = textColor.toArgb()
                                    } catch (e: Exception) {
                                        // Paint field not found or access denied
                                        android.util.Log.d("CompactNumberPicker", "Could not access Paint: ${e.message}")
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.w("CompactNumberPicker", "Failed to set text color via reflection", e)
                                }
                            }
                        }
                    }
                },
                update = { picker ->
                    // Update picker when value changes externally (convert to picker value with offset)
                    val pickerValue = value + offset
                    if (picker.value != pickerValue) {
                        picker.value = pickerValue.coerceIn(pickerRange)
                    }

                    // Update text color on every recomposition (including after scroll events)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // API 29+: Use official setTextColor() method
                        picker.setTextColor(textColor.toArgb())
                    } else {
                        // API 28 and below (including Fire OS): Aggressively apply text color to ALL text children
                        // This runs on every recomposition to catch dynamically created children during scroll
                        // Use post() to ensure this runs after any layout/draw updates
                        picker.post {
                            try {
                                // Style all TextView-based children (EditText, TextView, etc.)
                                val count = picker.childCount
                                for (i in 0 until count) {
                                    val child = picker.getChildAt(i)
                                    when (child) {
                                        is android.widget.EditText -> {
                                            child.setTextColor(textColor.toArgb())
                                            child.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                        }
                                        is android.widget.TextView -> {
                                            child.setTextColor(textColor.toArgb())
                                            child.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                        }
                                    }
                                }

                                // Try to access and modify the Paint object used for selector rendering
                                try {
                                    val paintField = NumberPicker::class.java.getDeclaredField("mSelectorWheelPaint")
                                    paintField.isAccessible = true
                                    val paint = paintField.get(picker) as? android.graphics.Paint
                                    paint?.color = textColor.toArgb()
                                } catch (e: Exception) {
                                    // Paint field not found or access denied
                                    android.util.Log.d("CompactNumberPicker", "Could not access Paint in update: ${e.message}")
                                }
                            } catch (e: Exception) {
                                android.util.Log.w("CompactNumberPicker", "Failed to set text color in update block", e)
                            }
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(120.dp)
                )

            // Increase button
            IconButton(
                onClick = {
                    val newValue = (value + 1).coerceIn(range)
                    onValueChange(newValue)
                },
                enabled = value < range.last,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Increase $label",
                    tint = if (value < range.last)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
        }
    }
}
