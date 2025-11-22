package com.example.vitruvianredux.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Entity representing a weekly workout program.
 */
@Entity(tableName = "weekly_programs")
data class WeeklyProgramEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val title: String,

    val notes: String? = null,

    val isActive: Boolean = false,

    val lastUsed: Long? = null,

    val createdAt: Long = System.currentTimeMillis()
)
