/*
 * SPDX-FileCopyrightText: 2025 Loowiz <loowiz@envs.net>
 *
 *  SPDX-License-Identifier: AGPL-3.0-only
 */

package com.example.photoswooper.data.database

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.photoswooper.data.models.MediaStatus
import com.example.photoswooper.data.models.MediaType
import com.example.photoswooper.utils.calculateMediaHash
import java.io.FileNotFoundException

/* This represents a row in the mediaStatus table in Room */
@Entity(
    tableName = "mediaStatus",
    indices = [Index(value = ["fileHash"], unique = false)]
)
data class MediaEntity(
    val fileHash: String,
    @PrimaryKey val mediaStoreId: Long, // This can be used to fetch images from contentResolver
    val status: MediaStatus,
    val type: MediaType,
    val size: Long,
    val dateModified: Long,
    val snoozedUntil: Long? = null
)

/* This migration changes primary key from fileHash -> mediaStoreId and changes index from mediaStoreId -> fileHash */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create new table
        db.execSQL(
            "CREATE TABLE `media_status_ver_3`(`fileHash` TEXT NOT NULL," +
                    " `mediaStoreId` INTEGER NOT NULL," +
                    " `status` TEXT NOT NULL," +
                    " `type` TEXT NOT NULL," +
                    " `size` INTEGER NOT NULL," +
                    " `dateModified` INTEGER NOT NULL," +
                    " `snoozedUntil` INTEGER," +
                    " PRIMARY KEY(`mediaStoreId`) )"
        )
        // Add fileHash as an index
        db.execSQL("CREATE INDEX index_mediaStatus_fileHash ON  media_status_ver_3 (fileHash)")

        //insert data from old table into new table
        db.execSQL(
            "REPLACE INTO media_status_ver_3(fileHash," +
                    " mediaStoreId," +
                    " status," +
                    " type," +
                    " size," +
                    " dateModified," +
                    " snoozedUntil)" +
                    " SELECT fileHash, mediaStoreId, status, 'PHOTO', size, dateModified, snoozedUntil  FROM mediaStatus"
        )
        //drop old table
        db.execSQL("DROP TABLE mediaStatus")

        //rename new table to the old table name
        db.execSQL("ALTER TABLE media_status_ver_3 RENAME TO mediaStatus")

    }
}

/** Update previous media hashes to use the new lower number of input bytes to avoid memory usage issues */
class MIGRATION_3_4(val contentResolver: ContentResolver) : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.query("SELECT mediaStoreId, type FROM mediaStatus").use { cursor ->
            val mediaStoreIdIndex = cursor.getColumnIndexOrThrow("mediaStoreId")
            val typeIndex = cursor.getColumnIndexOrThrow("type")
            while (cursor.moveToNext()) {
                val currentMediaStoreId = cursor.getInt(mediaStoreIdIndex)
                val typeString = cursor.getString(typeIndex)
                val uri = when (typeString) {
                    "PHOTO" -> ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        currentMediaStoreId.toLong()
                    )

                    "VIDEO" -> ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        currentMediaStoreId.toLong()
                    )

                    else -> {
                        "".toUri()
                    }
                }
                val fileHash: String
                try {
                    contentResolver.openInputStream(uri).use { fileInputStream ->
                        fileHash = calculateMediaHash(fileInputStream)
                    }
                    val contentValues = ContentValues().apply {
                        put("fileHash", fileHash)
                    }
                    db.update(
                        "mediaStatus",
                        SQLiteDatabase.CONFLICT_REPLACE,
                        contentValues,
                        "mediaStoreId = ?",
                        arrayOf(currentMediaStoreId.toString())
                    )
                } catch (_: FileNotFoundException) {
                    Log.e("DatabaseMigration 3-4", "Unable to access file - skipping.")
                }
            }
        }
    }
}