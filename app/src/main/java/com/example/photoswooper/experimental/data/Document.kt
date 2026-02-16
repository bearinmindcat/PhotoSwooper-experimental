package com.example.photoswooper.experimental.data

import android.net.Uri
import android.os.Build
import android.os.Parcelable
import android.text.format.DateFormat
import com.example.photoswooper.data.models.MediaStatus
import com.example.photoswooper.experimental.data.database.DocumentEntity
import kotlinx.parcelize.Parcelize
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@Parcelize
data class Document(
    val uri: Uri,
    val name: String,
    val size: Long,
    val dateModified: Long?,
    val extension: String,
    val documentType: DocumentType,
    val status: MediaStatus,
    val mimeType: String = "",
    val fileHash: String = "-1",
) : Parcelable {

    fun getFormattedDate(): String {
        return if (dateModified != null && dateModified > 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                LocalDateTime.ofInstant(Instant.ofEpochMilli(dateModified), ZoneId.systemDefault())
                    .toString().substringBefore("T")
            } else {
                DateFormat.format("yyyy-MM-dd", dateModified).toString()
            }
        } else {
            ""
        }
    }

    fun getFormattedSize(): String {
        val kb = size / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> "%.1f GB".format(gb)
            mb >= 1.0 -> "%.1f MB".format(mb)
            kb >= 1.0 -> "%.0f KB".format(kb)
            else -> "$size B"
        }
    }

    fun toDocumentEntity(statisticsEnabled: Boolean): DocumentEntity {
        return DocumentEntity(
            uriString = uri.toString(),
            fileHash = fileHash,
            status = status,
            documentType = documentType.name,
            size = size,
            dateModified = if (statisticsEnabled) System.currentTimeMillis() else 0,
            fileName = name,
            fileExtension = extension,
        )
    }
}
