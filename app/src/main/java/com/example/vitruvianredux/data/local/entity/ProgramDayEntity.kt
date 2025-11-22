package com.example.vitruvianredux.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing a day within a weekly program.
 */
@Entity(
    tableName = "program_days",
    indices = [Index(value = ["programId"])]
)
data class ProgramDayEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val programId: String,

    val dayOfWeek: Int,

    val routineId: String
)
