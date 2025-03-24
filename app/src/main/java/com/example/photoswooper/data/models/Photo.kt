package com.example.photoswooper.data.models

import android.net.Uri
import android.os.Build
import com.example.photoswooper.data.database.MediaStatus
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

enum class PhotoStatus() {
    UNSET,
    DELETE,
    KEEP,
}

data class Photo (
    val id: Long,
    val uri: Uri,
    val fileHash: String,
    val dateTaken: Long?,
    val size: Long,
    val location: DoubleArray?,
    val album: String?,
    val description: String?,
    val title: String?,
    val resolution: String?,
    var status: PhotoStatus
) {
    fun getFormattedDate(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(dateTaken?: 0), ZoneId.systemDefault()).toString().substringBefore("T")
        }
        else {
            // TODO("Format date for Android version < O")
            return "1970-01-20"
        }
    }

    fun getMediaStatusEntity(): MediaStatus {
        var currentDate: Long = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            currentDate = Date().toInstant().toEpochMilli()
        else
            null// TODO("Get current date in epoch milli for Android version < O")
        return MediaStatus(
            fileHash = fileHash,
            mediaStoreId = id,
            status = status,
            size = size,
            dateModified = currentDate
        )
    }
}