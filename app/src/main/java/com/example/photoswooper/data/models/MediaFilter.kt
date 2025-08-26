/*
 * SPDX-FileCopyrightText: 2025 Loowiz <loowiz@envs.net>
 *
 *  SPDX-License-Identifier: AGPL-3.0-only
 */

package com.example.photoswooper.data.models

import android.provider.MediaStore
import androidx.annotation.DrawableRes
import com.example.photoswooper.R

/**
 * Enum class representing the possible fields to sort by
 * @param sortOrderString a string to be used in contentResolver.query() sortOrder parameter
 */
enum class MediaSortField(val sortOrderString: String, @param:DrawableRes val iconDrawableId: Int) {
    RANDOM("RANDOM()", R.drawable.shuffle),
    DATE(MediaStore.MediaColumns.DATE_TAKEN, R.drawable.calendar),
    SIZE(MediaStore.MediaColumns.SIZE, R.drawable.hard_drives),
}

data class MediaFilter(
    val sizeRange: LongRange,
    val mediaTypes: Set<MediaType>,
    val directory: String,
    val sortField: MediaSortField,
    val sortAscending: Boolean,
    val containsText: String,

    // Advanced filters
//    val location: DoubleArray?, // TODO("Implement filtering by location")
)