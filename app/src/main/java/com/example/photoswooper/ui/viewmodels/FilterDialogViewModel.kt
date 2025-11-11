/*
 * SPDX-FileCopyrightText: 2025 Loowiz <loowiz@envs.net>
 *
 *  SPDX-License-Identifier: AGPL-3.0-only
 */

package com.example.photoswooper.ui.viewmodels

import com.example.photoswooper.data.models.MediaFilter
import com.example.photoswooper.data.models.MediaSortField
import com.example.photoswooper.data.models.MediaType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class FileSize(val multiplier: Float) {
    KB(1000f),
    MB(1000000f),
    GB(1000000000f)
}

/**
 * View model used in [com.example.photoswooper.ui.view.ReviewScreen]
 *
 * Stores a private set mediaFilter than can be edited via the provided functions.
 *
 * When the user confirms the desired filter, the UI passes this into [MainViewModel.updateMediaFilter] to apply it
 */
class FilterDialogViewModel(initialFilter: MediaFilter) {

    // Private state holder for the filter criteria
    private val _newFilters = MutableStateFlow(initialFilter)

    // State flow for accessing the current filter criteria
    val newFilters = _newFilters.asStateFlow()

    /**
     * Toggles the selection of a media type.
     * @param type The media type in the filter to toggle whether it is filtered out.
     * @param onError A callback function to execute if the filter is invalid.
     */
    fun toggleMediaType(type: MediaType, onError: () -> Unit) {
        val newMediaTypes = if (newFilters.value.mediaTypes.contains(type))
            newFilters.value.mediaTypes.minus(type)
        else
            newFilters.value.mediaTypes.plus(type)

        // Validate that at least one media type is selected
        if (newMediaTypes.isNotEmpty()) {
            _newFilters.update { currentFilters ->
                currentFilters.copy(
                    mediaTypes = newMediaTypes
                )
            }
        } else
            onError()
    }

    fun updateSortField(newSortField: MediaSortField) {
        _newFilters.update { currentFilters ->
            currentFilters.copy(
                sortField = newSortField
            )
        }
    }

    /**
     * Toggles the sort ascending flag.
     * @param newValue Whether to sort in ascending order. Defaults to inverting the current.
     */
    fun toggleSortAscending(newValue: Boolean = !_newFilters.value.sortAscending) {
        _newFilters.update { currentFilters ->
            currentFilters.copy(
                sortAscending = newValue
            )
        }
    }

    fun updateContainsText(input: String) {
        _newFilters.update { currentFilters ->
            currentFilters.copy(
                containsText = input
            )
        }
    }

    fun updateDirectory(newDirectory: String) {
        _newFilters.update { currentFilters ->
            currentFilters.copy(
                directory = newDirectory
            )
        }
    }
}