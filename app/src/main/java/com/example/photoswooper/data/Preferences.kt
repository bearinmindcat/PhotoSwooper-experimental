/*
 * SPDX-FileCopyrightText: 2025 Loowiz <loowiz@envs.net>
 *
 *  SPDX-License-Identifier: AGPL-3.0-only
 */

package com.example.photoswooper.data

import android.icu.util.Calendar
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.photoswooper.R
import com.example.photoswooper.data.models.MediaSortField
import com.example.photoswooper.data.uistates.TimeFrame

/** Preferences with boolean values used within the app
 *
 * @param default The default value of the preference
 * @param setting The preference key used in the dataStore
 * [title], [description] and [icon] are resource ids of descriptors and icons of the preference
 * [com.example.photoswooper.ui.view.BooleanPreferenceEditor]
 */
enum class BooleanPreference(
    val default: Boolean,
    val setting: String,
    @param:StringRes val title: Int,
    @param:StringRes val description: Int?,
    @param:DrawableRes val icon: Int
) {
    // Functional
    PERMANENTLY_DELETE(
        default = SDK_INT < Build.VERSION_CODES.R,
        setting = "permanently_delete",
        title = R.string.permanently_delete,
        description = R.string.permanently_delete_desc,
        icon = R.drawable.trash
    ),

    // UI
    SYSTEM_FONT(
        default = false,
        setting = "system_font",
        title = R.string.system_font,
        description = R.string.system_font_desc,
        icon = R.drawable.text_aa
    ),
    DYNAMIC_THEME(
        default = SDK_INT >= Build.VERSION_CODES.S,
        setting = "dynamic_theme",
        title = R.string.dynamic_theme,
        description = R.string.dynamic_theme_desc,
        icon = R.drawable.palette
    ),
    REDUCE_ANIMATIONS(
        default = false,
        setting = "reduce_animations",
        title = R.string.reduce_animations,
        description = R.string.reduce_animations_desc,
        icon = R.drawable.film_strip
    ),
    /** false means navigate to review screen before deletion, true means immediately delete files */
    SKIP_REVIEW(
        default = false,
        setting = "skip_review",
        title = R.string.skip_review,
        description = R.string.skip_review_desc,
        icon = R.drawable.check
    ),
    PAUSE_BACKGROUND_MEDIA(
        default = true,
        setting = "pause_background_media",
        title = R.string.pause_background_media,
        description = R.string.pause_background_media_desc,
        icon = R.drawable.pause
    ),
    LOOP_VIDEOS(
        default = false,
        setting = "loop_videos",
        title = R.string.loop_videos,
        description = R.string.loop_videos_desc,
        icon = R.drawable.repeat
    ),

    STATISTICS_ENABLED(
        default = true,
        setting = "statistics_enabled",
        title = R.string.enable_statistics,
        description = R.string.enable_statistics_desc,
        icon = R.drawable.chart_line_up
    ),
    START_WEEK_ON_MONDAY(
        default = Calendar.getInstance().firstDayOfWeek == Calendar.MONDAY,
        setting = "start_week_on_monday",
        title = R.string.start_week_on_monday,
        description = R.string.start_week_on_monday_desc,
        icon = R.drawable.calendar_dot
    ),

    // Filtering
    FILTER_SHOW_ADVANCED(
        default = false,
        setting = "filter_show_advanced",
        title = R.string.app_name, // Not used in settings screen so placeholder used
        description = R.string.app_name, // Not used in settings screen so placeholder used
        icon = R.drawable.funnel
    ),
    FILTER_INCLUDE_PHOTOS(
        default = true,
        setting = "filter_include_photos",
        title = R.string.app_name, // Not used in settings screen so placeholder used
        description = R.string.app_name, // Not used in settings screen so placeholder used
        icon = R.drawable.funnel
    ),
    FILTER_INCLUDE_VIDEOS(
        default = true,
        setting = "filter_include_videos",
        title = R.string.app_name, // Not used in settings screen so placeholder used
        description = R.string.app_name, // Not used in settings screen so placeholder used
        icon = R.drawable.funnel
    ),
    FILTER_SORT_ASCENDING(
        default = false,
        setting = "filter_sort_ascending",
        title = R.string.app_name, // Not used in settings screen so placeholder used
        description = R.string.app_name, // Not used in settings screen so placeholder used
        icon = R.drawable.funnel
    ), // When false, sort descending

    // Not displayed in PreferencesScreen
    /** Whether the user last set the info row to be in the expanded view, or single row */
    INFO_ROW_EXPANDED(
        default = false,
        setting = "info_row_expanded",
        title = R.string.app_name, // Not used in settings screen so placeholder used
        description = R.string.app_name, // Not used in settings screen so placeholder used
        icon = R.drawable.info
    ),
}

enum class IntPreference(
    val default: Int, val setting: String,
    @param:StringRes val title: Int,
    @param:DrawableRes val icon: Int
) {
    /** Number of media items per stack (poorly named due to being created before videos) */
    NUM_PHOTOS_PER_STACK(
        default = 30,
        setting = "num_photos_per_stack",
        title = R.string.num_photos_per_stack,
        icon = R.drawable.images
    ),
    /** Number of days to remember swipes for */
    NO_DAYS_TO_REMEMBER_SWIPES(
        default = 0,
        setting = "no_days_to_remember_swipes",
        title = R.string.swipe_retention_time,
        icon = R.drawable.timer
    ),

    /** The version code the app was updated from (0 for first install) */
    TUTORIAL_INDEX(
        default = 0,
        setting = "tutorial_index",
        title = R.string.app_name, // placeholder as not used
        icon = R.drawable.images // placeholder as not used
    ),
}

enum class LongPreference(val default: Long, val setting: String) {
    // Functional
    SNOOZE_LENGTH(2 * TimeFrame.WEEK.milliseconds, "snooze_length"),
    TUTORIAL_START_TIME(Long.MAX_VALUE, "tutorial_start_time"),

    // Filtering
    FILTER_MIN_FILE_SIZE(0, "filter_min_file_size"),
    FILTER_MAX_FILE_SIZE(Long.MAX_VALUE, "filter_max_file_size"),

    // Experimental
    DOCUMENT_SPACE_SAVED(0L, "document_space_saved"),
}

enum class StringPreference(val default: String, val setting: String) {
    // Filtering
    FILTER_DIRECTORIES("", "filter_directories"),
    FILTER_SORT_FIELD(MediaSortField.RANDOM.sortOrderString, "filter_sort_field"),
    FILTER_CONTAINS_TEXT("", "filter_contains_text"),

    /**Most recent crash log*/
    CRASH_LOG("", "crash_log"),

    // Experimental: document swiping
    DOCUMENT_SCAN_FOLDERS("", "document_scan_folders"),
    DOCUMENT_EXCLUDED_EXTENSIONS("", "document_excluded_extensions"),
}