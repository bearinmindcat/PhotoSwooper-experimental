package com.example.photoswooper.data.models

import android.net.Uri
import java.util.*

data class Photo (
    val id: Long,
    val dateTaken: Date,
    val uri: Uri
)