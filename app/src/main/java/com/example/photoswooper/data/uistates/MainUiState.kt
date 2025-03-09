package com.example.photoswooper.data.uistates

import com.example.photoswooper.data.models.Photo

data class MainUiState(
    val photos: List<Photo> = listOf<Photo>(),
    val currentPhotoIndex: Int = 0
)