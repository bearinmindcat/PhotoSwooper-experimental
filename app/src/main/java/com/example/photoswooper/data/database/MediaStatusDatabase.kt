package com.example.photoswooper.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Database class with a singleton Instance object.
 */
@Database(entities = [MediaStatus::class], version = 1)
abstract class MediaStatusDatabase : RoomDatabase() {
    abstract fun mediaStatusDao(): MediaStatusDao

    companion object {
        @Volatile
        private var Instance: MediaStatusDatabase? = null

        /* Implement the singleton pattern to ensure only one instance of the database is created */
        fun getDatabase(context: Context): MediaStatusDatabase {
            // if the Instance is not null, return it, otherwise create a new database instance.
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(context, MediaStatusDatabase::class.java, "mediaStatusDatabase")
                    .build()
                    .also { Instance = it }
            }
        }
    }

}