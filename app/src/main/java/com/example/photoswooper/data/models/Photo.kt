package com.example.photoswooper.data.models

import android.net.Uri

enum class PhotoStatus() {
    UNSET,
    DELETE,
    KEEP,
}

data class Photo (
    val id: Long,
    val dateTaken: String?,
    val uri: Uri,
    var status: PhotoStatus
)