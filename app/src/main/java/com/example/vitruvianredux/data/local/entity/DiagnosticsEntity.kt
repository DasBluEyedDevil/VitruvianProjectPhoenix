package com.example.vitruvianredux.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "diagnostics_history")
data class DiagnosticsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runtimeSeconds: Int,
    val faultMask: Long,     // Compressed faults bitmask
    val temp1: Byte,
    val temp2: Byte,
    val temp3: Byte,
    val temp4: Byte,
    val temp5: Byte,
    val temp6: Byte,
    val temp7: Byte,
    val temp8: Byte,
    val containsFaults: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
