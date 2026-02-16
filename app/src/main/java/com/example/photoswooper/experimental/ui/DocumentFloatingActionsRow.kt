package com.example.photoswooper.experimental.ui

import android.content.Intent
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.util.Log
import android.view.HapticFeedbackConstants
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.photoswooper.R
import com.example.photoswooper.experimental.data.DocumentType
import com.example.photoswooper.experimental.viewmodel.DocumentSwipeViewModel
import com.example.photoswooper.player
import com.example.photoswooper.ui.components.FloatingAction
import com.example.photoswooper.ui.viewmodels.defaultEntryAnimationSpec
import kotlinx.coroutines.delay
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun DocumentFloatingActionsRow(
    viewModel: DocumentSwipeViewModel,
    currentDocumentType: DocumentType?,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val context = LocalContext.current
    val showInfo by viewModel.showDocumentInfo.collectAsState()

    val showAudioControls = currentDocumentType == DocumentType.AUDIO
            || currentDocumentType == DocumentType.VIDEO

    // Audio player state
    var displayedPlayerPosition by remember { mutableFloatStateOf(player.currentPosition.toFloat()) }
    fun endOfAudio() = player.currentPosition >= player.duration
    var playPauseIcon by remember { mutableStateOf(R.drawable.play) }

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
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        viewModel.snoozeItem()
                        viewModel.next()
                    },
                )
                FloatingAction(
                    drawableIconId = R.drawable.info,
                    actionTitle = stringResource(R.string.info),
                    checked = showInfo,
                    actionDescription = stringResource(R.string.show_info),
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) view.performHapticFeedback(
                            if (showInfo) HapticFeedbackConstants.TOGGLE_OFF
                            else HapticFeedbackConstants.TOGGLE_ON
                        )
                        viewModel.toggleDocumentInfo()
                    },
                )
            }
            // Audio playback controls (rewind, play/pause, fast forward)
            AnimatedVisibility(
                visible = showAudioControls,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
            ) {
                Row {
                    FloatingAction(
                        drawableIconId = R.drawable.rewind,
                        actionTitle = null,
                        actionDescription = stringResource(R.string.rewind),
                        onClick = {
                            player.seekBack()
                        },
                    )
                    FloatingAction(
                        drawableIconId = playPauseIcon,
                        actionTitle = null,
                        actionDescription = stringResource(R.string.pause_current_video),
                        onClick = {
                            when {
                                player.isPlaying -> player.pause()
                                endOfAudio() -> {
                                    player.seekTo(0)
                                    player.play()
                                }
                                else -> player.play()
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
                            player.seekForward()
                        },
                    )
                }
            }
            Row {
                FloatingAction(
                    drawableIconId = R.drawable.share_network,
                    actionTitle = stringResource(R.string.share),
                    actionDescription = stringResource(R.string.share_desc),
                    onClick = {
                        val doc = viewModel.getCurrentDocument() ?: return@FloatingAction
                        try {
                            val mimeType = doc.mimeType.ifEmpty {
                                MimeTypeMap.getSingleton()
                                    .getMimeTypeFromExtension(doc.extension) ?: "*/*"
                            }
                            val shareUri = if (doc.uri.scheme == "file") {
                                FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    File(doc.uri.path!!)
                                )
                            } else {
                                doc.uri
                            }
                            val shareIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_STREAM, shareUri)
                                type = mimeType
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, null))
                        } catch (e: Exception) {
                            Log.w("DocFloatingActions", "Share failed: ${e.message}")
                            Toast.makeText(context, "Unable to share file", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
                FloatingAction(
                    drawableIconId = R.drawable.arrow_square_out,
                    actionTitle = stringResource(R.string.open_externally_title),
                    actionDescription = stringResource(R.string.open_externally_desc),
                    onClick = {
                        val doc = viewModel.getCurrentDocument() ?: return@FloatingAction
                        try {
                            val mimeType = doc.mimeType.ifEmpty {
                                MimeTypeMap.getSingleton()
                                    .getMimeTypeFromExtension(doc.extension) ?: "*/*"
                            }
                            val openUri = if (doc.uri.scheme == "file") {
                                FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    File(doc.uri.path!!)
                                )
                            } else {
                                doc.uri
                            }
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(openUri, mimeType)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Log.w("DocFloatingActions", "Open failed: ${e.message}")
                            Toast.makeText(context, "No suitable app found", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
        }
        // Audio seekbar (matches regular FloatingActionsRow video seekbar)
        AnimatedVisibility(showAudioControls) {
            var userDragging by remember { mutableStateOf(false) }
            val animatedDisplayedPlayerPosition by animateFloatAsState(
                displayedPlayerPosition,
                defaultEntryAnimationSpec
            )
            LaunchedEffect(null) {
                while (true) {
                    delay(150)
                    if (!userDragging) {
                        displayedPlayerPosition = player.currentPosition.toFloat()
                        playPauseIcon = when {
                            player.isPlaying -> R.drawable.pause
                            endOfAudio() -> R.drawable.arrow_counter_clockwise
                            else -> R.drawable.play
                        }
                    } else {
                        player.seekTo(displayedPlayerPosition.toLong())
                    }
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
                    Modifier.width(dimensionResource(R.dimen.duration_text_width))
                )
                androidx.compose.material.Slider(
                    value = if (userDragging) displayedPlayerPosition else animatedDisplayedPlayerPosition,
                    valueRange = 0f..if (player.duration > 0L) player.duration.toFloat() else Float.MAX_VALUE,
                    onValueChange = {
                        if (!userDragging)
                            player.pause()
                        userDragging = true
                        displayedPlayerPosition = it
                    },
                    onValueChangeFinished = {
                        player.seekTo(displayedPlayerPosition.toLong())
                        if (!endOfAudio())
                            player.play()
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
                    if (player.duration > 0L) player.duration else 0,
                    Color.White,
                    Modifier.width(dimensionResource(R.dimen.duration_text_width))
                )
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

private fun formatDuration(numToConvert: Long): String {
    numToConvert.milliseconds.toComponents { days, hours, minutes, seconds, _ ->
        var formattedDuration = ""

        if (days > 0) formattedDuration += days.toString()
        if (hours > 0) {
            if (formattedDuration.isNotEmpty())
                formattedDuration += ":"
            formattedDuration += hours.toString()
        }
        if (formattedDuration.isNotEmpty())
            formattedDuration += ":"
        formattedDuration += minutes.toString()
        if (formattedDuration.isNotEmpty())
            formattedDuration += ":"
        if (seconds < 10)
            formattedDuration += "0"
        formattedDuration += seconds.toString()
        return formattedDuration
    }
}
