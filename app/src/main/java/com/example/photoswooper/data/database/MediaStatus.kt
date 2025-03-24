package com.example.photoswooper.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.photoswooper.data.models.PhotoStatus

/* This represents a row in the mediaStatus table in Room */
@Entity(
    tableName = "mediaStatus",
    indices = [Index(value = ["mediaStoreId"], unique = false)]
)
data class MediaStatus(
    @PrimaryKey val fileHash: String,
    val mediaStoreId: Long, // This can be used to fetch images from contentResolver
    val status: PhotoStatus,
    val size: Long,
    val dateModified: Long,
)