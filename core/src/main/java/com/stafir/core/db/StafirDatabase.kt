package com.stafir.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MediaEntity::class, MediaLabelEntity::class, MediaFtsEntity::class, UiVersionEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class StafirDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao

    companion object {
        fun create(context: Context): StafirDatabase =
            Room.databaseBuilder(context.applicationContext, StafirDatabase::class.java, "stafir.db")
                // Fase 1: il database contiene solo dati ricalcolabili dall'archivio.
                // Dalla fase con nomi/categorie personali useremo migrazioni vere.
                .fallbackToDestructiveMigration()
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
