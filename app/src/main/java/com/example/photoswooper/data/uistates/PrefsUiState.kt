/*
 * SPDX-FileCopyrightText: 2025 Loowiz <loowiz@envs.net>
 *
 *  SPDX-License-Identifier: AGPL-3.0-only
 */

package com.example.photoswooper.data.uistates

import android.os.Build
import android.os.Build.VERSION.SDK_INT
import com.example.photoswooper.data.models.MediaSortField

enum class BooleanPreference(val default: Boolean, val setting: String) {
    // Functional
    PERMANENTLY_DELETE(SDK_INT < Build.VERSION_CODES.R, "permanently_delete"),

    // UI
    SYSTEM_FONT(false, "system_font"),
    DYNAMIC_THEME(SDK_INT >= Build.VERSION_CODES.S, "dynamic_theme"),
    REDUCE_ANIMATIONS(false, "reduce_animations"),
    /** Whether the user last set the info row to be in the expanded view, or single row */
    INFO_ROW_EXPANDED(false, "info_row_expanded"),
    /** false means navigate to review screen before deletion, true means immediately delete files */
    SKIP_REVIEW(false, "skip_review"),

    // Filtering
    FILTER_SHOW_ADVANCED(false, "filter_show_advanced"),
    FILTER_INCLUDE_PHOTOS(true, "filter_include_photos"),
    FILTER_INCLUDE_VIDEOS(true, "filter_include_videos"),
    FILTER_SORT_ASCENDING(false, "filter_sort_ascending") // When false, sort descending
}

enum class IntPreference(val default: Int, val setting: String) {
    /** Number of media items per stack (poorly named due to being created before videos) */
    NUM_PHOTOS_PER_STACK(30, "num_photos_per_stack"),

    /** The version code the app was updated from (0 for first install) */
    TUTORIAL_INDEX(0, "tutorial_index")
}

enum class LongPreference(val default: Long, val setting: String) {
    // Functional
    SNOOZE_LENGTH(2 * TimeFrame.WEEK.milliseconds, "snooze_length"),
    TUTORIAL_START_TIME(Long.MAX_VALUE, "tutorial_start_time"),

    // Filtering
    FILTER_MIN_FILE_SIZE(0, "filter_min_file_size"),
    FILTER_MAX_FILE_SIZE(Long.MAX_VALUE, "filter_max_file_size"),
}

enum class StringPreference(val default: String, val setting: String) {
    // Filtering
    FILTER_DIRECTORIES("", "filter_directories"),
    FILTER_SORT_FIELD(MediaSortField.RANDOM.sortOrderString, "filter_sort_field"),
    FILTER_CONTAINS_TEXT("", "filter_contains_text")
}

data class PrefsUiState(
    /** Text input from user to be validated before committed to preferences */
    val numPhotosPerStackTextInput: String = IntPreference.NUM_PHOTOS_PER_STACK.default.toString()
)