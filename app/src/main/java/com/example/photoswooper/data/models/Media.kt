/*
 * SPDX-FileCopyrightText: 2025 Loowiz <loowiz@envs.net>
 *
 *  SPDX-License-Identifier: AGPL-3.0-only
 */

package com.example.photoswooper.data.models

import android.net.Uri
import android.os.Build
import android.os.Parcelable
import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import com.example.photoswooper.R
import com.example.photoswooper.data.database.MediaEntity
import kotlinx.parcelize.Parcelize
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

enum class MediaType {
    PHOTO,
    VIDEO
}

enum class MediaStatus(@param:DrawableRes val iconDrawableId: Int) {
    UNSET(R.drawable.x),
    DELETE(R.drawable.trash),
    KEEP(R.drawable.bookmark_simple),
    SNOOZE(R.drawable.hourglass_high),
    HIDE(R.drawable.check) // Used when an item is deleted so shouldnt be attempted to be fetched
}

@Parcelize
data class Media(
    val id: Long,
    val uri: Uri,
    val fileHash: String,
    val dateTaken: Long?,
    val size: Long,
    val type: MediaType,
    val location: DoubleArray?,
    val album: String?,
    val description: String?,
    val title: String?,
    val resolution: String?,
    var status: MediaStatus
) : Parcelable {
    fun getFormattedDate(): String {
        return if (dateTaken != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                LocalDateTime.ofInstant(Instant.ofEpochMilli(dateTaken), ZoneId.systemDefault()).toString()
                    .substringBefore("T")
            } else {
                DateFormat.format("yyyy-MM-dd", dateTaken).toString()
            }
        } else {
            ""
        }
    }

    fun getFormattedLocation() = location?.joinToString(", ") { it.toString().substring(0, 8) }

    fun getMediaStatusEntity(statisticsEnabled: Boolean): MediaEntity {
        return MediaEntity(
            fileHash = fileHash,
            mediaStoreId = id,
            status = status,
            type = type,
            size = size,
            dateModified = if (statisticsEnabled) System.currentTimeMillis() else 0,
        )
    }

    /* Recommended override: location is an Array so contentEquals should be used for that, not just equals */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Media

        if (id != other.id) return false
        if (dateTaken != other.dateTaken) return false
        if (size != other.size) return false
        if (uri != other.uri) return false
        if (fileHash != other.fileHash) return false
        if (!location.contentEquals(other.location)) return false
        if (album != other.album) return false
        if (description != other.description) return false
        if (title != other.title) return false
        if (resolution != other.resolution) return false
        if (status != other.status) return false

        return true
    }

    /* Recommended override: location is an Array so contentEquals should be used for that, not just equals */
    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (dateTaken?.hashCode() ?: 0)
        result = 31 * result + size.hashCode()
        result = 31 * result + uri.hashCode()
        result = 31 * result + fileHash.hashCode()
        result = 31 * result + (location?.contentHashCode() ?: 0)
        result = 31 * result + (album?.hashCode() ?: 0)
        result = 31 * result + (description?.hashCode() ?: 0)
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + (resolution?.hashCode() ?: 0)
        result = 31 * result + status.hashCode()
        return result
    }
}