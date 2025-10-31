package com.example.vitruvianredux.presentation.components

import android.widget.NumberPicker
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    label: String = "",
    suffix: String = "",
    modifier: Modifier = Modifier
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

                    // Increase fling velocity/duration for faster scrolling
                    try {
                        val flingScroller = NumberPicker::class.java.getDeclaredField("mFlingScroller")
                        flingScroller.isAccessible = true
                        val scroller = flingScroller.get(this) as? android.widget.Scroller

                        scroller?.let {
                            val frictionField = android.widget.Scroller::class.java.getDeclaredField("mFriction")
                            frictionField.isAccessible = true
                            frictionField.setFloat(it, 0.005f)  // Lower = longer fling (default ~0.015)
                        }
                    } catch (e: Exception) {
                        // If reflection fails, just use default behavior
                        android.util.Log.w("CompactNumberPicker", "Could not customize fling behavior", e)
                    }
                }
            },
            update = { picker ->
                // Update picker when value changes externally (convert to picker value with offset)
                val pickerValue = value + offset
                if (picker.value != pickerValue) {
                    picker.value = pickerValue.coerceIn(pickerRange)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        )
    }
}
