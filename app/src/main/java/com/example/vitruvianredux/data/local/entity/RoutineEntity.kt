package com.example.vitruvianredux.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a workout routine.
 */
@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey
    val id: String,

    val name: String,

    val description: String = "",

    val createdAt: Long = System.currentTimeMillis(),

    val lastUsed: Long? = null,

    val useCount: Int = 0
)
