/*
 * SPDX-FileCopyrightText: 2025 Loowiz <loowiz@envs.net>
 *
 *  SPDX-License-Identifier: AGPL-3.0-only
 */

package com.example.photoswooper.data.uistates

import com.example.photoswooper.data.IntPreference

data class PrefsUiState(
    /** Text input from user to be validated before committed to preferences */
    val numPhotosPerStackTextInput: String = IntPreference.NUM_PHOTOS_PER_STACK.default.toString()
)