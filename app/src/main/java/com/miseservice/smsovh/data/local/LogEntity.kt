package com.miseservice.smsovh.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "logs")
data class LogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

