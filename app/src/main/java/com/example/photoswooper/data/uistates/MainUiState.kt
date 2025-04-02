package com.example.photoswooper.data.uistates

import com.example.photoswooper.data.models.Photo

enum class TimeFrame(val milliseconds: Long) {
    DAY(86400000),
    WEEK(604800000),
    MONTH(2629746000),
    YEAR(31556952000)
}

data class MainUiState(
    val photos: MutableList<Photo> = mutableListOf<Photo>(),
    val currentPhotoIndex: Int = 0,
    val numUnset: Int = 0,
    val showReviewDialog: Boolean = false,
    val reviewDialogEnabled: Boolean = true, // Whether to show review dialog, or just delete photos
    val showInfo: Boolean = false,

    val currentStatsTimeFrame: TimeFrame = TimeFrame.WEEK,
    val spaceSavedInTimeFrame: Long = 0
)