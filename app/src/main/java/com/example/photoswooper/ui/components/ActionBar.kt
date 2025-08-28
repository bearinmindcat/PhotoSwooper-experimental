/*
 * SPDX-FileCopyrightText: 2025 Loowiz <loowiz@envs.net>
 *
 *  SPDX-License-Identifier: AGPL-3.0-only
 */

package com.example.photoswooper.ui.components

import android.os.Build
import android.text.format.Formatter.formatShortFileSize
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import com.example.photoswooper.R
import com.example.photoswooper.data.uistates.BooleanPreference
import com.example.photoswooper.dataStore
import com.example.photoswooper.ui.viewmodels.MainViewModel
import com.example.photoswooper.utils.DataStoreInterface
import io.github.koalaplot.core.util.ExperimentalKoalaPlotApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@Suppress("AssignedValueIsNeverRead")
@OptIn(ExperimentalFoundationApi::class, ExperimentalKoalaPlotApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ActionBar(
    numToDelete: Int,
    viewModel: MainViewModel,
    skipReview: Boolean,
    navigateToReviewScreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val context = LocalContext.current
    val reduceAnimations by DataStoreInterface(context.dataStore)
        .getBooleanSettingValue(BooleanPreference.REDUCE_ANIMATIONS.setting).collectAsState(false)
    val uiState by viewModel.uiState.collectAsState()

    val extraSmallIconSize = dimensionResource(R.dimen.xsmall_icon)
    val expandedIconSize = extraSmallIconSize * 1.25f
    var shuffleIconSize by remember { mutableStateOf(extraSmallIconSize) }
    val animatedShuffleIconSize = animateDpAsState(
        targetValue = shuffleIconSize,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioLowBouncy,
        )
    )
    var shuffleIconRotation by remember { mutableFloatStateOf(0f) }
    val animatedShuffleIconRotation by animateFloatAsState(
        targetValue = shuffleIconRotation,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioLowBouncy,
        )
    )

    /* Bouncy effect when the time frame is changed */
    @Suppress("AssignedValueIsNeverRead")
    LaunchedEffect(uiState.currentStorageStatsTimeFrame) {
        if (!reduceAnimations) {
            shuffleIconSize = expandedIconSize
            shuffleIconRotation = 10f
            delay(200)
            shuffleIconRotation = -10f
            shuffleIconSize = extraSmallIconSize
            delay(200)
            shuffleIconRotation = 0f
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = dimensionResource(R.dimen.padding_medium))
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                /* Undo button TODO("Add rotation animation when clicked")*/
                FilledIconButton(
                    onClick = {
                        val undoResult = viewModel.undo()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            if (undoResult)
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            else
                                view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                        }
                    },
                    modifier = Modifier
                        .padding(
                            horizontal = dimensionResource(R.dimen.padding_small),
                        )
                        .weight(0.2f)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.undo),
                        contentDescription = "Undo deletion",
                        modifier = Modifier.padding(dimensionResource(R.dimen.padding_small))
                    )
                }
                ReviewDeletedButton(
                    numToDelete = numToDelete,
                    skipReview = skipReview,
                    navigateToReviewScreen = { navigateToReviewScreen() },
                    deleteMedia = {
                        viewModel.confirmDeletion()
                    }
                )
                /* Filter button */
                FilledTonalIconButton(
                    onClick = {
                        if (viewModel.getMediaToDelete().isEmpty()) {
                            viewModel.tempPause()
                            viewModel.toggleFilterDialog()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        } else {
                            Toast.makeText(
                                context,
                                "Review media marked for deletion first",
                                Toast.LENGTH_LONG
                            ).show()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                                view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                        }
                    },
                    modifier = Modifier
                        .padding(
                            horizontal = dimensionResource(R.dimen.padding_small),
                        )
                        .weight(0.2f)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.funnel),
                        contentDescription = "Filter/sort the photos & videos",
                        modifier = Modifier.padding(dimensionResource(R.dimen.padding_small))
                    )
                }
            }
        }

        /* Statistics row */
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .clickable(onClickLabel = "Click to change time frame") {
                    CoroutineScope(Dispatchers.Main).launch {
                        viewModel.cycleStorageStatsTimeFrame()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    }
                }
                .fillMaxWidth()
                .padding(dimensionResource(R.dimen.padding_small))
                .clip(MaterialTheme.shapes.medium)
        ) {
            val statsTextStyle = MaterialTheme.typography.bodyLarge
            Text(
                text = "Space saved this ",
                style = statsTextStyle,
                modifier = Modifier
                    .padding(
                        start = dimensionResource(R.dimen.padding_small),
                        top = dimensionResource(R.dimen.padding_small),
                        bottom = dimensionResource(R.dimen.padding_small)
                    )
            )
            Icon(
                painter = painterResource(R.drawable.shuffle),
                contentDescription = null,
                modifier = Modifier
                    .size(animatedShuffleIconSize.value)
                    .rotate(animatedShuffleIconRotation)
            )
            Text(
                text = buildAnnotatedString {
                    append(" ")
                    pushStyle(SpanStyle(textDecoration = TextDecoration.Underline))
                    append(uiState.currentStorageStatsTimeFrame.name.lowercase())
                    pop()
                    append(": ${formatShortFileSize(LocalContext.current, uiState.spaceSavedInTimeFrame)}")
                },
                style = statsTextStyle,
                modifier = Modifier
                    .padding(
                        end = dimensionResource(R.dimen.padding_small),
                        top = dimensionResource(R.dimen.padding_small),
                        bottom = dimensionResource(R.dimen.padding_small)
                    )
            )
        }
    }
}