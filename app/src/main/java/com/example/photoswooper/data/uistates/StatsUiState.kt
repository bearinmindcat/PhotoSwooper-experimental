package com.example.photoswooper.data.uistates

import java.util.Calendar

/**
 * The uiState used by the StatsCard function in [com.example.photoswooper.ui.components.TabbedPreferencesAndStatsPage]
 *
 * @property dateToFetchFromMillis The date chosen by the user to fetch data for. Data will be fetched for the whole
 * [timeFrame] e.g. If the [dateToFetchFromMillis] is a tuesday, all data for monday-sunday will be fetched
 * @property latestData The latest statistics data. keys: x-axis data (time), values: y-axis data  (no. of swipes)
 * @property currentDateShown A boolean for whether the data shown is of the current date & time
 */
data class StatsUiState (
    val dateToFetchFromMillis: Long = Calendar.getInstance().timeInMillis,
    val timeFrame: TimeFrame = TimeFrame.WEEK,
    val latestData: List<Int> = listOf(),
    val currentDateShown: Boolean = true
)
