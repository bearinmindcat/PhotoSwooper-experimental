/*
 * SPDX-FileCopyrightText: 2025 Loowiz <loowiz@envs.net>
 *
 *  SPDX-License-Identifier: AGPL-3.0-only
 */

package com.example.photoswooper.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import com.example.photoswooper.R

@Composable
fun ReviewDeletedButton(
    navigateToReviewScreen: () -> Unit,
    deleteMedia: () -> Unit,
    numToDelete: Int,
    skipReview: Boolean,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    ElevatedButton(
        onClick = {
            if(numToDelete > 0) {
                if (skipReview)
                    deleteMedia()
                else
                    navigateToReviewScreen()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            }
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        },
        modifier = modifier
//                                .height(92.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(if (skipReview) R.drawable.trash else R.drawable.check),
                contentDescription = null,
                modifier = Modifier.padding(dimensionResource(R.dimen.padding_small))
            )
            Text(
                text = "${if (skipReview) "Delete" else "Review"} $numToDelete items",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(dimensionResource(R.dimen.padding_small))
            )
        }
    }
}