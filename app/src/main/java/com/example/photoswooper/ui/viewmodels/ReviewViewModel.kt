/*
 * SPDX-FileCopyrightText: 2025 Loowiz <loowiz@envs.net>
 *
 *  SPDX-License-Identifier: AGPL-3.0-only
 */

package com.example.photoswooper.ui.viewmodels

import androidx.lifecycle.ViewModel
import com.example.photoswooper.data.models.Media
import com.example.photoswooper.data.models.MediaStatus
import com.example.photoswooper.data.uistates.ReviewUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The viewModel used by the `ReviewScreen` function in [com.example.photoswooper.ui.view.TabbedSheetContent]
 */
class ReviewViewModel() : ViewModel() {
    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState = _uiState.asStateFlow()

    fun updateCurrentStatusFilter(newStatusFilter: MediaStatus) {
        _uiState.update { currentState ->
            currentState.copy(currentStatusFilter = newStatusFilter)
        }
    }

    fun toggleMediaItemSelected(mediaItem: Media, newState: Boolean? = null) {
        _uiState.update { currentState ->
            currentState.copy(
                selectedMedia =
                    if (currentState.selectedMedia.contains(mediaItem)) {
                        if (newState != true)
                            currentState.selectedMedia.minus(mediaItem)
                        else currentState.selectedMedia
                    } else if (newState != false) currentState.selectedMedia.plus(mediaItem)
                    else currentState.selectedMedia,
            )
        }
        /* If no media are selected, disable selection */
        _uiState.update { currentState ->
            currentState.copy(
                mediaSelectionEnabled = currentState.selectedMedia.isNotEmpty()
            )
        }
    }

    fun cancelSelection() {
        _uiState.update { currentState ->
            currentState.copy(selectedMedia = listOf(), mediaSelectionEnabled = false)
        }
    }

    fun toggleMediaSelectionEnabled() {
        _uiState.update { currentState ->
            currentState.copy(mediaSelectionEnabled = !currentState.mediaSelectionEnabled)
        }
    }

    fun markSelectedItemsAsUnset(markAsUnset: (Media) -> Unit) {
        uiState.value.selectedMedia.forEach { markAsUnset(it) }
        _uiState.update { currentState ->
            currentState.copy(
                selectedMedia = listOf(),
                mediaSelectionEnabled = false
            )
        }
    }
}