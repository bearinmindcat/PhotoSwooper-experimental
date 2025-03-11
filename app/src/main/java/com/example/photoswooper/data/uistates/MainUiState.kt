package com.example.photoswooper.data.uistates

import com.example.photoswooper.data.models.Photo
import com.example.photoswooper.data.photoLimit

data class MainUiState(
    val photos: List<Photo> = listOf<Photo>(),
    val currentPhotoIndex: Int = 0,
    val numUnset: Int = photoLimit,
    val showReviewDialog: Boolean = false,
    val showInfo: Boolean = false
)