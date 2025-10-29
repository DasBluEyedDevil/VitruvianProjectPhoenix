package com.example.vitruvianredux.data.local

import androidx.room.TypeConverter

/**
 * Room type converters for custom data types
 */
class Converters {

    @TypeConverter
    fun fromIntList(value: List<Int>): String {
        return value.joinToString(",")
    }

    @TypeConverter
    fun toIntList(value: String): List<Int> {
        if (value.isEmpty()) return emptyList()
        return value.split(",").mapNotNull { it.toIntOrNull() }
    }
}
