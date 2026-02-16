package com.example.photoswooper.experimental.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.photoswooper.data.models.MediaStatus

@Entity(
    tableName = "documentStatus",
    indices = [Index(value = ["fileHash"], unique = false)]
)
data class DocumentEntity(
    @PrimaryKey val uriString: String,
    val fileHash: String,
    val status: MediaStatus,
    val documentType: String,
    val size: Long,
    val dateModified: Long,
    val fileName: String,
    val fileExtension: String,
    val snoozedUntil: Long? = null
)

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `documentStatus` (" +
                    "`uriString` TEXT NOT NULL, " +
                    "`fileHash` TEXT NOT NULL, " +
                    "`status` TEXT NOT NULL, " +
                    "`documentType` TEXT NOT NULL, " +
                    "`size` INTEGER NOT NULL, " +
                    "`dateModified` INTEGER NOT NULL, " +
                    "`fileName` TEXT NOT NULL, " +
                    "`fileExtension` TEXT NOT NULL, " +
                    "`snoozedUntil` INTEGER, " +
                    "PRIMARY KEY(`uriString`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_documentStatus_fileHash ON documentStatus (fileHash)")
    }
}
