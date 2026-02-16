/*
 * SPDX-FileCopyrightText: 2025 Loowiz <loowiz@envs.net>
 *
 *  SPDX-License-Identifier: AGPL-3.0-only
 */

package com.example.photoswooper.data.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.photoswooper.experimental.data.database.DocumentEntity
import com.example.photoswooper.experimental.data.database.DocumentStatusDao
import com.example.photoswooper.experimental.data.database.MIGRATION_4_5

/**
 * Database class with a singleton Instance object.
 */
@Database(
    entities = [MediaEntity::class, DocumentEntity::class],
    version = 5,
    autoMigrations = [
        AutoMigration(from = 1, to = 2)
    ]
)
abstract class MediaStatusDatabase : RoomDatabase() {
    abstract fun mediaStatusDao(): MediaStatusDao
    abstract fun documentStatusDao(): DocumentStatusDao

    companion object {
        @Volatile
        private var Instance: MediaStatusDatabase? = null

        /* Implement the singleton pattern to ensure only one instance of the database is created */
        fun getDatabase(context: Context): MediaStatusDatabase {
            // if the Instance is not null, return it, otherwise create a new database instance.
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(context, MediaStatusDatabase::class.java, "mediaStatusDatabase")
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4(context.contentResolver), MIGRATION_4_5)
                    .build()
                    .also { Instance = it }
            }
        }
    }
}