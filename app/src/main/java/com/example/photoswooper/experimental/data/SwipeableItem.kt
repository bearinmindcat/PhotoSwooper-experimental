package com.example.photoswooper.experimental.data

import android.net.Uri
import com.example.photoswooper.data.models.Media
import com.example.photoswooper.data.models.MediaType

sealed class SwipeableItem {
    abstract val uri: Uri
    abstract val size: Long
    abstract val decodingError: String?

    data class MediaItem(val media: Media) : SwipeableItem() {
        override val uri: Uri get() = media.uri
        override val size: Long get() = media.size
        override val decodingError: String? get() = media.decodingError
        val id: Long get() = media.id
        val type: MediaType get() = media.type
        val resolution: String? get() = media.resolution
    }

    data class DocumentItem(val document: Document) : SwipeableItem() {
        override val uri: Uri get() = document.uri
        override val size: Long get() = document.size
        override val decodingError: String? get() = null
        val documentType: DocumentType get() = document.documentType
        val name: String get() = document.name
    }
}
