package com.example.waterwatchlaredo

import android.content.Context
import androidx.room.Room
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Report::class], version = 2, exportSchema = false)
abstract class WaterWatchDatabase : RoomDatabase() {
    abstract fun reportDao(): ReportDao

    companion object {
        private var instance: WaterWatchDatabase? = null

        private val migration1To2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Existing resident reports stay intact and are not marked as samples.
                database.execSQL("ALTER TABLE reports ADD COLUMN isSample INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Reuse one database for the app, including after screen rotation.
        @Synchronized
        fun getInstance(context: Context): WaterWatchDatabase {
            val existingDatabase = instance
            if (existingDatabase != null) return existingDatabase

            val database = Room.databaseBuilder(
                context.applicationContext,
                WaterWatchDatabase::class.java,
                "waterwatch.db"
            ).addMigrations(migration1To2).build()
            instance = database
            return database
        }
    }
}
