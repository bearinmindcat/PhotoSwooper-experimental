/*
 * SPDX-FileCopyrightText: 2025 Loowiz <loowiz@envs.net>
 *
 *  SPDX-License-Identifier: AGPL-3.0-only
 */

package com.example.photoswooper.data.uistates

import androidx.annotation.DrawableRes
import androidx.compose.ui.text.AnnotatedString
import com.example.photoswooper.R
import com.example.photoswooper.data.models.Media

enum class TimeFrame(val milliseconds: Long, val iconDrawableId: Int) {
    DAY(86400000, R.drawable.calendar_dot),
    WEEK(604800000, R.drawable.calendar_dots),
    MONTH(2629746000, R.drawable.calendar_dots),
    YEAR(31556952000, R.drawable.calendar),
//    ALL(Calendar.getInstance().timeInMillis) // this millisecond value is the number of miliseconds since the epoch
// TODO("Add support for an 'all' time frame")
}

data class MainUiState(
    val permissionsGranted: Boolean? = null,
    val fetchingMedia: Boolean = true,
    val mediaBuffering: Boolean = true,
    val tutorialMode: Boolean = false,

    val isPlaying: Boolean,
    val previousIsPlaying: Boolean = false,
    val mediaItems: MutableList<Media> = mutableListOf(),
    val fetchIteration: Int = 0, // Incremented every time new media items are fetched, so that background process for previous fetching can be cancelled
    val currentIndex: Int = 0,
    val numUnset: Int = 0,
    val tutorialCardTitle: String = "",
    val tutorialCardBody: AnnotatedString = AnnotatedString(""),
    @param:DrawableRes val tutorialCardIconDrawableId: Int? = null,

    val showInfoAndFloatingActionsRow: Boolean = false,
    val previousShowInfoAndFloatingActionsRow: Boolean = false, // Used for showing/hiding floating actions for videos
    val showInfo: Boolean = false,
    val showFilterDialog: Boolean = false,

    val currentStorageStatsTimeFrame: TimeFrame = TimeFrame.WEEK,
    val spaceSavedInTimeFrame: Long = 0,
)