/*
 * SPDX-FileCopyrightText: 2025 Loowiz <loowiz@envs.net>
 *
 *  SPDX-License-Identifier: AGPL-3.0-only
 */

package com.example.photoswooper.data.uistates

import com.example.photoswooper.R
import java.util.Calendar

/** Enum class of the types of data that can be shown in the stats graph */
enum class StatsData(val extraInfo: String = "", val iconDrawableId: Int) {
    SWIPE_COUNT(iconDrawableId = R.drawable.images),
    DELETED_COUNT(iconDrawableId = R.drawable.trash),
    SPACE_SAVED(extraInfo = "(MB)", iconDrawableId = R.drawable.hard_drives),
}

/**
 * The uiState used by the StatsCard function in [com.example.photoswooper.ui.view.TabbedSheetContent]
 *
 * @property dateToFetchFromMillis The date chosen by the user to fetch data for. Data will be fetched for the whole
 * [timeFrame] e.g. If the [dateToFetchFromMillis] is a tuesday, all data for monday-sunday will be fetched
 * @property latestData The latest statistics data. keys: x-axis data (time), values: y-axis data  (no. of swipes)
 * @property currentDateShown A boolean for whether the data shown is of the current date & time
 */
data class StatsUiState(
    val dateToFetchFromMillis: Long = Calendar.getInstance().timeInMillis,
    val timeFrame: TimeFrame = TimeFrame.WEEK,
    val dataType: StatsData = StatsData.SWIPE_COUNT,
    val latestData: List<Float> = listOf(),
    val currentDateShown: Boolean = true
)