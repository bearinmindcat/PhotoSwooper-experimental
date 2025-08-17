package com.example.photoswooper.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.annotation.DrawableRes
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.photoswooper.R
import com.example.photoswooper.data.models.Photo
import com.example.photoswooper.data.models.PhotoStatus
import com.example.photoswooper.ui.viewmodels.MainViewModel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatingActionsRow(
    currentPhoto: Photo?,
    viewModel: MainViewModel
) {
    val view = LocalView.current
    var showChangeSnoozeLengthDialog by remember {mutableStateOf(false)}
    if (showChangeSnoozeLengthDialog)
        ChangeSnoozeLengthDialog(
            currentSnoozeLength = viewModel.snoozeLengthMillis.collectAsState(0).value,
            onDismissRequest = { showChangeSnoozeLengthDialog = false },
            onConfirmation = {
                viewModel.updateSnoozeLengthMillis(it)
            }
        )
    
    Row(
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.dropShadow(
            shape = MaterialTheme.shapes.medium,
            shadow = Shadow(96.dp)
        )
    ) {
        FloatingAction(
            drawableIconId = R.drawable.hourglass_high_bold,
            actionTitle = "Snooze",
            actionDescription = "Snooze the photo",
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                viewModel.markPhoto(PhotoStatus.SNOOZE)
                viewModel.nextPhoto()
                // TODO("Show a tip dialog on first click, showing how a tap + hold lets the user change the snooze length")
            },
            onLongPress = {
                showChangeSnoozeLengthDialog = true
            }
        )
        // TODO("If current mediaToSwipe is video, show play/pause, rewind & skip")
        FloatingAction(
            drawableIconId = R.drawable.share_network,
            actionTitle = "Share",
            actionDescription = "Share photo",
            onClick = { viewModel.sharePhoto() }
        )
        FloatingAction(
            drawableIconId = R.drawable.arrow_square_out_bold,
            actionTitle = "Open",
            actionDescription = "Open externally",
            onClick = {
                viewModel.openPhotoInGalleryApp()
            }
        )
    }
}

@Composable
private fun FloatingAction(
    @DrawableRes drawableIconId: Int,
    actionTitle: String,
    actionDescription: String?,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var showTextHint by remember { mutableStateOf(true) }
    LaunchedEffect(null) {
        delay(1000)
        showTextHint = false
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .padding(dimensionResource(R.dimen.padding_small))
            .combinedClickable(
                onClick = { onClick() },
                onLongClick = { onLongPress?.invoke() },
                hapticFeedbackEnabled = true,
            )
    ) {
        Icon(
            painter = painterResource(drawableIconId),
            contentDescription = actionDescription,
            modifier = Modifier
                .size(dimensionResource(R.dimen.small_icon))
        )
        Text(actionTitle, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ChangeSnoozeLengthDialog(
    currentSnoozeLength: Long,
    onDismissRequest: () -> Unit,
    onConfirmation: (Long) -> Unit
) {
    val view = LocalView.current
    Dialog(
        onDismissRequest = { onDismissRequest() }
    ) {
        var displayedSnoozeLength by remember {
            mutableStateOf(currentSnoozeLength.milliseconds.inWholeDays.toString())
        }
        Card(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(dimensionResource(R.dimen.padding_medium))
            ) {
                OutlinedTextField(
                    value = displayedSnoozeLength,
                    onValueChange = { input ->
                        if (input.toLongOrNull() != null || input == "")
                            displayedSnoozeLength = input
                    },
                    label = { Text("Snooze photos for:") },
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
                TextButton(
                    onClick = { onDismissRequest() },
                ) {
                    Text(
                        "Cancel",
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                TextButton(
                    onClick = {
                        onConfirmation(displayedSnoozeLength.toLong())
                    },
                ) {
                    Text(
                        stringResource(R.string.confirm),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}