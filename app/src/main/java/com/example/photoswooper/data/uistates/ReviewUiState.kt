/*
 * SPDX-FileCopyrightText: 2025 Loowiz <loowiz@envs.net>
 *
 *  SPDX-License-Identifier: AGPL-3.0-only
 */

package com.example.photoswooper.data.uistates

import com.example.photoswooper.data.models.Media
import com.example.photoswooper.data.models.MediaStatus

data class ReviewUiState (
    val currentStatusFilter: MediaStatus = MediaStatus.DELETE,
    val mediaSelectionEnabled: Boolean = false,
    val selectedMedia: List<Media> = listOf(),
)