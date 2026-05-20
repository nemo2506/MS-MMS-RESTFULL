package com.miseservice.msmms.data.local

import android.content.Context
import androidx.room.Room
import com.miseservice.msmms.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        val builder = Room.databaseBuilder(context, AppDatabase::class.java, "app_db")

        // En prod, on refuse la suppression silencieuse de données.
        // En debug, on garde la souplesse pour accélérer les itérations de schéma.
        if (BuildConfig.DEBUG) {
            builder
                .fallbackToDestructiveMigration()
                .fallbackToDestructiveMigrationOnDowngrade()
        }

        return builder.build()
    }

    @Provides
    fun provideSettingsDao(db: AppDatabase): SettingsDao = db.settingsDao()

    @Provides
    fun provideLogDao(db: AppDatabase): LogDao = db.logDao()
}
