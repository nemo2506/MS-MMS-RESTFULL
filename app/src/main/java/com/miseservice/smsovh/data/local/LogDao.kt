package com.miseservice.smsovh.data.local

import androidx.room.*

@Dao
interface LogDao {
    @Insert
    suspend fun insertLog(log: LogEntity)

    @Query("SELECT * FROM logs ORDER BY timestamp DESC LIMIT 5")
    suspend fun getLast5Logs(): List<LogEntity>

    @Query("DELETE FROM logs WHERE id NOT IN (SELECT id FROM logs ORDER BY timestamp DESC LIMIT 5)")
    suspend fun deleteOldLogs()
}

