package com.stafir.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MediaEntity::class, MediaLabelEntity::class, MediaFtsEntity::class, UiVersionEntity::class, FaceEntity::class, PersonEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class StafirDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun faceDao(): FaceDao

    companion object {
        fun create(context: Context): StafirDatabase =
            Room.databaseBuilder(context.applicationContext, StafirDatabase::class.java, "stafir.db")
                .addMigrations(MIGRATION_2_3)
                // Da qui in avanti ogni cambio di struttura ha una migrazione vera (nomi delle persone,
                // correzioni e analisi non si perdono). Il ripiego distruttivo resta solo per versioni di prova molto vecchie.
                .fallbackToDestructiveMigrationFrom(1)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}

/** Fase 2: tabelle dei volti e delle persone; i file già analizzati restano tali. */
val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `face` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `mediaId` INTEGER NOT NULL, `frameMs` INTEGER NOT NULL, `boxLeft` REAL NOT NULL, `boxTop` REAL NOT NULL, `boxRight` REAL NOT NULL, `boxBottom` REAL NOT NULL, `embedding` BLOB NOT NULL, `quality` REAL NOT NULL, `personId` INTEGER, `confirmed` INTEGER NOT NULL, `rejectedPersonId` INTEGER)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_face_mediaId` ON `face` (`mediaId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_face_personId` ON `face` (`personId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `person` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT, `centroid` BLOB NOT NULL, `faceCount` INTEGER NOT NULL, `coverFaceId` INTEGER, `hidden` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_person_name` ON `person` (`name`)")
        db.execSQL("ALTER TABLE `media` ADD COLUMN `facesVersion` INTEGER NOT NULL DEFAULT 0")
    }
}
