package com.miseservice.msmms.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [AppSettingsEntity::class, LogEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun logDao(): LogDao
}
