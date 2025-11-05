/*
 * SPDX-FileCopyrightText: 2025 Loowiz <loowiz@envs.net>
 *
 *  SPDX-License-Identifier: AGPL-3.0-only
 */

package com.example.photoswooper.ui.components

import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.view.HapticFeedbackConstants
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.photoswooper.R
import com.example.photoswooper.data.models.Media
import com.example.photoswooper.data.models.MediaStatus
import com.example.photoswooper.data.models.MediaType
import com.example.photoswooper.data.uistates.LongPreference
import com.example.photoswooper.data.uistates.TimeFrame
import com.example.photoswooper.dataStore
import com.example.photoswooper.ui.viewmodels.MainViewModel
import com.example.photoswooper.ui.viewmodels.defaultEntryAnimationSpec
import com.example.photoswooper.utils.DataStoreInterface
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatingActionsRow(
    currentMedia: Media?,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val uiState by viewModel.uiState.collectAsState()

    // player position used in bottom slider
    var displayedPlayerPosition by remember { mutableFloatStateOf(viewModel.player.currentPosition.toFloat()) }
    fun endOfVideo() = viewModel.player.currentPosition >= viewModel.player.duration
    var playPauseIcon by remember { mutableStateOf(
        when {
            (uiState.isPlaying) -> R.drawable.pause
            (endOfVideo()) -> R.drawable.arrow_counter_clockwise
            else -> R.drawable.play
        }
    ) }

    var showChangeSnoozeLengthDialog by remember { mutableStateOf(false) }
    if (showChangeSnoozeLengthDialog)
        ChangeSnoozeLengthDialog(
            onDismissRequest = {
                showChangeSnoozeLengthDialog = false
                               },
            onConfirmation = {
                viewModel.updateSnoozeLengthMillis(it)
                showChangeSnoozeLengthDialog = false
            }
        )

    val showVideoPlaybackControls = currentMedia?.type == MediaType.VIDEO

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = dimensionResource(R.dimen.padding_medium))
            .pointerInput(null) {} // Prevents accidental swipes/taps
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .dropShadow(
                    shape = MaterialTheme.shapes.medium,
                    shadow = Shadow(
                        radius = 128.dp,
                        alpha = 0.9f
                    )
                )
                .padding(horizontal = dimensionResource(R.dimen.padding_small))
        ) {

            Row {
                FloatingAction(
                    drawableIconId = R.drawable.hourglass_high,
                    actionTitle = stringResource(R.string.snooze),
                    actionDescription = stringResource(R.string.snooze_desc),
                    onClick = {
                        if (SDK_INT >= Build.VERSION_CODES.R)
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        viewModel.markItem(MediaStatus.SNOOZE)
                        viewModel.next()
                        // TODO("Show a tip dialog on first click, showing how a tap + hold lets the user change the snooze length")
                    },
                    onLongPress = {
                        showChangeSnoozeLengthDialog = true
                    }
                )
                FloatingAction(
                    drawableIconId = R.drawable.info,
                    actionTitle = stringResource(R.string.info),
                    checked = uiState.showInfo,
                    actionDescription = stringResource(R.string.show_info),
                    onClick = {
                        if (SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) view.performHapticFeedback(
                            if (uiState.showInfo) HapticFeedbackConstants.TOGGLE_OFF
                            else HapticFeedbackConstants.TOGGLE_ON
                        )
                        viewModel.toggleInfo()
                    },
                )
            }
            AnimatedVisibility(
                visible = showVideoPlaybackControls,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
            ) {
                Row {
                    FloatingAction(
                        drawableIconId = R.drawable.rewind,
                        actionTitle = null,
                        actionDescription = stringResource(R.string.rewind),
                        onClick = {
                            viewModel.player.seekBack()
                        },
//                            onLongPress = { TODO("Configurable rewind amount") }
                    )
                        FloatingAction(
                            drawableIconId = playPauseIcon,
                            actionTitle = null,
                            actionDescription = stringResource(R.string.pause_current_video),
                            onClick = {
                                when {
                                    (viewModel.player.isPlaying) -> viewModel.safePause()
                                    (endOfVideo()) -> {
                                        viewModel.player.seekTo(0)
                                        viewModel.safePlay()
                                    }

                                    else -> viewModel.safePlay()
                                }
                                if (SDK_INT >= Build.VERSION_CODES.R)
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            },
                        )
                    FloatingAction(
                        drawableIconId = R.drawable.fast_forward,
                        actionTitle = null,
                        actionDescription = stringResource(R.string.fast_forward),
                        onClick = {
                            viewModel.player.seekForward()
                        },
//                            onLongPress = { TODO("Configurable fast forward amount") }
                    )
                }
            }
            Row {
                FloatingAction(
                    drawableIconId = R.drawable.share_network,
                    actionTitle = stringResource(R.string.share),
                    actionDescription = stringResource(R.string.share_desc),
                    onClick = { viewModel.share() }
                )
                FloatingAction(
                    drawableIconId = R.drawable.arrow_square_out,
                    actionTitle = stringResource(R.string.open_externally_title),
                    actionDescription = stringResource(R.string.open_externally_desc),
                    onClick = {
                        viewModel.openInGalleryApp()
                    }
                )
            }
        }
        AnimatedVisibility(showVideoPlaybackControls) {
            var userDragging by remember { mutableStateOf(false) }
            val animatedDisplayedPlayerPosition by animateFloatAsState(displayedPlayerPosition,
                defaultEntryAnimationSpec)
            LaunchedEffect(null) { // When either of these two values (keys) change:
                while (true) {
                    delay(150) // Only update every 150 milliseconds
                    if (!userDragging) {
                        displayedPlayerPosition = viewModel.player.currentPosition.toFloat()
                        viewModel.updateVideoPosition()
                        playPauseIcon = when {
                            (uiState.isPlaying) -> R.drawable.pause
                            (endOfVideo()) -> R.drawable.arrow_counter_clockwise
                            else -> R.drawable.play
                        }
                    }
                    else
                        viewModel.player.seekTo(displayedPlayerPosition.toLong())
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .padding(horizontal = dimensionResource(R.dimen.padding_small))
                    .fillMaxWidth()
            ) {
                DurationText(
                    displayedPlayerPosition.toLong(),
                    Color.White,
                    Modifier
                        .width(dimensionResource(R.dimen.duration_text_width))
                )
                androidx.compose.material.Slider(
                    value = if (userDragging) displayedPlayerPosition else animatedDisplayedPlayerPosition,
                    valueRange = 0f..if (viewModel.player.duration > 0L) viewModel.player.duration.toFloat() else Float.MAX_VALUE,
                    onValueChange = {
                        if (!userDragging) // Only run when user first starts dragging
                            viewModel.tempPause()
                        userDragging = true
                        displayedPlayerPosition = it
                    },
                    onValueChangeFinished = {
                        viewModel.player.seekTo(displayedPlayerPosition.toLong())
                        if (!endOfVideo()) // Prevents play/pause button going wack as play -> immediate pause as end of video
                            viewModel.revertIsPlayingToBeforeTempPause()
                        if (SDK_INT >= Build.VERSION_CODES.R)
                            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
                        userDragging = false
                    },
                    colors = androidx.compose.material.SliderDefaults.colors(
                        activeTrackColor = SliderDefaults.colors().activeTrackColor,
                        thumbColor = SliderDefaults.colors().thumbColor,
                        disabledThumbColor = SliderDefaults.colors().disabledThumbColor,
                        inactiveTrackColor = SliderDefaults.colors().inactiveTrackColor,
                        disabledActiveTrackColor = SliderDefaults.colors().disabledActiveTrackColor,
                        disabledInactiveTrackColor = SliderDefaults.colors().disabledInactiveTrackColor,
                        activeTickColor = SliderDefaults.colors().activeTickColor,
                        inactiveTickColor = SliderDefaults.colors().inactiveTickColor,
                        disabledActiveTickColor = SliderDefaults.colors().disabledActiveTickColor,
                        disabledInactiveTickColor = SliderDefaults.colors().disabledInactiveTickColor
                    ),
                    modifier = Modifier.weight(0.7f)
                )
                DurationText(
                    if (viewModel.player.duration > 0L) viewModel.player.duration else 0,
                    Color.White,
                    Modifier
                        .width(dimensionResource(R.dimen.duration_text_width))
                )
            }
        }
    }
}

@Composable
fun FloatingAction(
    modifier: Modifier = Modifier,
    @DrawableRes drawableIconId: Int,
    actionTitle: String?,
    actionDescription: String?,
    checked: Boolean? = null,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
) {
    val contentColor =
        if (checked ?: false) MaterialTheme.colorScheme.primary
        else Color.White

    Box(
        modifier
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(
                onClick = { onClick() },
                onLongClick = { onLongPress?.invoke() },
                hapticFeedbackEnabled = true,
            )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier
                .padding(dimensionResource(R.dimen.padding_small))
        ) {
            Icon(
                painter = painterResource(drawableIconId),
                contentDescription = actionDescription,
                tint = contentColor,
                modifier = Modifier
                    .size(dimensionResource(R.dimen.small_icon))
            )
            if (actionTitle != null)
                Text(
                    actionTitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor
//                    textDecoration = if (onLongPress != null) TextDecoration.Underline else TextDecoration.None
                )
        }
    }
}

@Composable
private fun ChangeSnoozeLengthDialog(
    onDismissRequest: () -> Unit,
    onConfirmation: (Long) -> Unit
) {
    val view = LocalView.current
    val context = LocalContext.current


    val currentSnoozeLength by DataStoreInterface(context.dataStore).getLongSettingValue(LongPreference.SNOOZE_LENGTH.setting).collectAsState(LongPreference.SNOOZE_LENGTH.default)

    Dialog(
        onDismissRequest = { onDismissRequest() }
    ) {
        var displayedSnoozeLength by remember {
            mutableStateOf(currentSnoozeLength.milliseconds.inWholeDays.toString())
        }
        Card(shape = MaterialTheme.shapes.large) {
            Column(
                Modifier.padding(dimensionResource(R.dimen.padding_medium))
            ) {
                OutlinedTextField(
                    value = displayedSnoozeLength,
                    onValueChange = { input ->
                        if (input.toLongOrNull() != null || input == "")
                            displayedSnoozeLength = input
                    },
                    label = { Text("Snooze photos/videos for:") },
                    singleLine = true,
                    suffix = { Text("Days") },
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Number
                    ),
                    modifier = Modifier
                        .padding(bottom = dimensionResource(R.dimen.padding_small))
                )
                Slider(
                    value = displayedSnoozeLength.toFloatOrNull()
                        ?: currentSnoozeLength.milliseconds.inWholeDays.toFloat(),
                    onValueChange = {
                        if (it.roundToInt() != displayedSnoozeLength.toIntOrNull()) {
                            displayedSnoozeLength = it.roundToLong().toString()
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                    },
                    valueRange = 1f..365f,
                    steps = 26,
                )
            }
            Row(
                modifier = Modifier
                    .padding(bottom = dimensionResource(R.dimen.padding_small))
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                OutlinedButton (
                    onClick = { onDismissRequest() },
                ) {
                    Text(
                        stringResource(R.string.cancel),
                    )
                }
                Button(
                    onClick = {
                        onConfirmation(TimeFrame.DAY.milliseconds * displayedSnoozeLength.toLong())
                    },
                ) {
                    Text(
                        stringResource(R.string.confirm),
                    )
                }
            }
        }
    }
}

@Composable
private fun DurationText(
    duration: Long,
    color: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Text(
        text = formatDuration(duration),
        color = color,
        style = MaterialTheme.typography.labelMedium,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}

private fun formatDuration(numToConvert: Long): String { // Parameter is the minutes in milliseconds
    numToConvert.milliseconds.toComponents { days, hours, minutes, seconds, _ ->
        var formattedDuration = ""

        if (days > 0) formattedDuration += days.toString()
        if (hours > 0) {
            if (formattedDuration.isNotEmpty())
                formattedDuration += ":"
            formattedDuration += hours.toString()
        }
        // Minutes
        if (formattedDuration.isNotEmpty())
            formattedDuration += ":"
        formattedDuration += minutes.toString()
        // Seconds
        if (formattedDuration.isNotEmpty())
            formattedDuration += ":"
        if (seconds < 10)
            formattedDuration += "0" // Add 0 so minimum 2 digits displayed
        formattedDuration += seconds.toString()
        return formattedDuration
    }
}