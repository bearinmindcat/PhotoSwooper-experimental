package com.example.photoswooper.data.uistates

import com.example.photoswooper.data.models.Photo
import com.example.photoswooper.data.photoLimit

data class MainUiState(
    val photos: MutableList<Photo> = mutableListOf<Photo>(),
    val currentPhotoIndex: Int = 0,
    val numUnset: Int = photoLimit,
    val showReviewDialog: Boolean = false,
    val reviewDialogEnabled: Boolean = true, // Whether to show review dialog, or just delete photos
    val showInfo: Boolean = false
)