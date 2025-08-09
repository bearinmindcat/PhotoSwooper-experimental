package com.example.photoswooper.data.uistates

import com.example.photoswooper.data.models.Photo

enum class TimeFrame(val milliseconds: Long) {
    DAY(86400000),
    WEEK(604800000),
    MONTH(2629746000),
    YEAR(31556952000),
//    ALL(Calendar.getInstance().timeInMillis) // this millisecond value is the number of miliseconds since the epoch
// TODO("Add support for an 'all' time frame")
}

data class MainUiState(
    val photos: MutableList<Photo> = mutableListOf<Photo>(),
    val isLoading: Boolean = true,
    val currentPhotoIndex: Int = 0,
    val numUnset: Int = 0,
    val showReviewDialog: Boolean = false,
    val reviewDialogEnabled: Boolean = true, // Whether to show review dialog, or just delete photos
    val showInfo: Boolean = false,

    val currentStorageStatsTimeFrame: TimeFrame = TimeFrame.WEEK,
    val spaceSavedInTimeFrame: Long = 0
)