/*
 * SPDX-FileCopyrightText: 2025 Loowiz <loowiz@envs.net>
 *
 *  SPDX-License-Identifier: AGPL-3.0-only
 */

package com.example.photoswooper.data.uistates

import android.os.Parcelable
import com.example.photoswooper.R
import com.example.photoswooper.data.models.Media
import kotlinx.parcelize.Parcelize

enum class TimeFrame(val milliseconds: Long, val iconDrawableId: Int) {
    DAY(86400000, R.drawable.calendar_dot),
    WEEK(604800000, R.drawable.calendar_dots),
    MONTH(2629746000, R.drawable.calendar_dots),
    YEAR(31556952000, R.drawable.calendar),
//    ALL(Calendar.getInstance().timeInMillis) // this millisecond value is the number of miliseconds since the epoch
// TODO("Add support for an 'all' time frame")
}


@Parcelize
data class MainUiState(
    /** Whether the user has granted permissions to access photos/videos. Value is null when the state is unknown */
    val permissionsGranted: Boolean? = null,
    /** Boolean for whether media to swipe on is currently being fetched from the device (UI shows a loading indicator
     * when true) */
    val fetchingMedia: Boolean = true,
    /** Whether the current media has been decoded/loaded */
    val mediaReady: Boolean = false,
    /** The aspect ratio is used by the PlayerSurface composable to ensure the video isn't stretched/squished */
    val mediaAspectRatio: Float = 1f,
    val tutorialMode: Boolean = false,

    /** Whether a video is playing (not paused) */
    val isPlaying: Boolean,
    /** Cached isPlaying value used to temporarily pause a video when e.g. switching apps */
    val previousIsPlaying: Boolean = false,
    val videoPosition: Long = 0,
    /** List of mediaItems iterated through by [com.example.photoswooper.ui.viewmodels.MainViewModel] from 0 */
    val mediaItems: MutableList<Media> = mutableListOf(),
    /** Index of the current media item being shown in [mediaItems] */
    val currentIndex: Int = 0,
    /** Incremented every time new media items are fetched, so that background process for previous fetching can be
     * cancelled.
     *
     * This is useful e.g. when a user confirms a filter while the app is still fetching media.
     * */
    val fetchIteration: Int = 0,
    /** Number of photos marked as unset. TODO: Is this needed? Can we not use a mediaItems.filter()? */
    val numUnset: Int = 0,

    val showInfoAndFloatingActionsRow: Boolean = false,
    val previousShowInfoAndFloatingActionsRow: Boolean = false, // Used for showing/hiding floating actions for videos
    val showInfo: Boolean = false,
    val showFilterDialog: Boolean = false,

    val currentStorageStatsTimeFrame: TimeFrame = TimeFrame.WEEK,
    val spaceSavedInTimeFrame: Long = 0,
) : Parcelable