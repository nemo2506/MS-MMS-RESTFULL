package com.miseservice.msmms.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Migration consolidée de v3 à v6.
 * Consolide les trois migrations antérieures (3→4, 4→5, 5→6) en une seule.
 * Cela simplifie la maintenance et accélère la migration.
 */
private val MIGRATION_3_6 = object : Migration(3, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Ajouter les colonnes BLE introduites en v4
        db.execSQL("ALTER TABLE app_settings ADD COLUMN blePin TEXT")

        // Ajouter les seuils de batterie BLE introduits en v5
        db.execSQL("ALTER TABLE app_settings ADD COLUMN bleMinBattery INTEGER NOT NULL DEFAULT 20")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN bleMaxBattery INTEGER NOT NULL DEFAULT 80")

        // Ajouter le statut de connexion BLE introduit en v6
        db.execSQL("ALTER TABLE app_settings ADD COLUMN bleConnectionActive INTEGER NOT NULL DEFAULT 0")
    }
}

// Migrations individuelles conservées pour compatibilité ascendante
// (Si des bases de données sont en v4 ou v5 lors de la migration)
private val MIGRATION_4_6 = object : Migration(4, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE app_settings ADD COLUMN bleMinBattery INTEGER NOT NULL DEFAULT 20")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN bleMaxBattery INTEGER NOT NULL DEFAULT 80")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN bleConnectionActive INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE app_settings ADD COLUMN bleConnectionActive INTEGER NOT NULL DEFAULT 0")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "app_db")
            // Migrations pour toutes les versions
            .addMigrations(MIGRATION_3_6, MIGRATION_4_6, MIGRATION_5_6)
            // Destruction destructive uniquement pour les très anciennes versions (v1, v2)
            .fallbackToDestructiveMigrationFrom(1, 2)
            .build()

    @Provides
    fun provideSettingsDao(db: AppDatabase): SettingsDao = db.settingsDao()

    @Provides
    fun provideLogDao(db: AppDatabase): LogDao = db.logDao()
}


