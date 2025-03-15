package com.example.photoswooper.data.models

import android.net.Uri

enum class PhotoStatus() {
    UNSET,
    DELETE,
    KEEP,
}

data class Photo (
    val id: Long,
    val uri: Uri,
    val dateTaken: String?,
    val size: Long,
    val location: DoubleArray?,
    val album: String?,
    val description: String?,
    val title: String?,
    val resolution: String?,
    var status: PhotoStatus
)