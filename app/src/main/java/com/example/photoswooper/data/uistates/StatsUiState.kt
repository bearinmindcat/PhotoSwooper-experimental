package com.example.photoswooper.data.uistates

import java.util.*

data class StatsUiState (
    val dateToFetchFromMillis: Long = Calendar.getInstance().timeInMillis,
    val timeFrame: TimeFrame = TimeFrame.WEEK,
    val latestData: Map<Int, Int> = mapOf(),
    val currentDateShown: Boolean = true
)
