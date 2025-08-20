package com.example.photoswooper.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.photoswooper.data.models.MediaStatus
import com.example.photoswooper.data.models.MediaType

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
val MIGRATION_2_3 = object : Migration(2, 3){
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create new table
        db.execSQL("CREATE TABLE `media_status_ver_3`(`fileHash` TEXT NOT NULL," +
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
        db.execSQL("INSERT INTO media_status_ver_3(fileHash," +
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
