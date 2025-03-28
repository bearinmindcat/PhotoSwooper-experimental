package com.example.photoswooper.ui.components

import android.os.Build
import android.text.format.Formatter.formatShortFileSize
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.photoswooper.R
import com.example.photoswooper.data.models.Photo
import com.example.photoswooper.data.uistates.MainUiState
import com.example.photoswooper.ui.view.MainViewModel
import com.example.photoswooper.ui.view.ReviewDeletedButton
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import io.github.koalaplot.core.util.ExperimentalKoalaPlotApi
import kotlin.math.roundToInt

enum class ActionBarDragAnchors {
    Expanded,
    Collapsed
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalKoalaPlotApi::class)
@Composable
fun ActionBar(
    currentPhoto: Photo?,
    blurState: HazeState,
    numToDelete: Int,
    uiState: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val view = LocalView.current

    /* State for dragging/expanding the action bar */
    val decayAnimationSpec = rememberSplineBasedDecay<Float>()
    val anchoredDraggableState = remember {
        AnchoredDraggableState(
            initialValue = ActionBarDragAnchors.Collapsed,
            positionalThreshold = { distance: Float -> distance * 0.5f },
            velocityThreshold = { with(density) { 100.dp.toPx() } },
            anchors = DraggableAnchors {
                ActionBarDragAnchors.Collapsed at with(density) { (300.dp).toPx() }
                ActionBarDragAnchors.Expanded at with(density) { -view.height.dp.toPx() / 2 }
                // TODO("Change these anchors to be top and bottom of the display, rather than fixed values")
            },
            snapAnimationSpec = tween(),
            decayAnimationSpec = decayAnimationSpec,
        )
    }

    /* Expand to full width when expanding the action bar */
    val animatedPaddingValue: Dp by animateDpAsState(
        if (anchoredDraggableState.targetValue == ActionBarDragAnchors.Collapsed ) // if collapsed
            dimensionResource(R.dimen.padding_medium)
        else // if expanded, remove padding
            0.dp
    )

    var yOffset = anchoredDraggableState.requireOffset().roundToInt()

    /* Adjust offset for size of other components */
    var infoHeight by remember { mutableStateOf(0) }
    var statsPageHeight by remember { mutableStateOf(0) }
//    if (uiState.showInfo)
//        yOffset -= infoHeight / 2
//    if (anchoredDraggableState.offset != with(density) { (300.dp).toPx() })
//        yOffset -= statsPageHeight / 2


    Column(
        modifier
            .padding(animatedPaddingValue)
            .offset {
                IntOffset(
                    x = 0,
                    y = yOffset
                )
            }
            .anchoredDraggable(
                state = anchoredDraggableState,
                orientation = Orientation.Vertical
            )
    ) {
        AnimatedVisibility(
            visible = uiState.showInfo && currentPhoto != null,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            InfoRow(
                viewModel,
                currentPhoto,
                Modifier
                    .onGloballyPositioned { coordinates ->
                        infoHeight = coordinates.size.height
                    }
                    .padding(horizontal = animatedPaddingValue)
            )
        }
        /* Bottom blurred-background bar */
        val barTopCornerSize = if (uiState.showInfo) CornerSize(0) else MaterialTheme.shapes.medium.topEnd
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = animatedPaddingValue)
                .hazeChild(
                    state = blurState,
                    shape = MaterialTheme.shapes.medium.copy(
                        topEnd = barTopCornerSize,
                        topStart = barTopCornerSize
                    )
                )
        ) {
            HorizontalDivider(
                thickness = 4.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .alpha(0.4f)
                    .width(32.dp)
                    .padding(top = dimensionResource(R.dimen.padding_small))
            )
            Row(
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                    /* Undo button */
                    FilledIconButton(
                        onClick = {
                            viewModel.undo()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            }
                        },
                        modifier = Modifier.padding(
                            horizontal = dimensionResource(R.dimen.padding_small),
                            vertical = dimensionResource(R.dimen.padding_medium)
                        )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.undo),
                            contentDescription = "Undo deletion",
                            modifier = Modifier.padding(dimensionResource(R.dimen.padding_small))
                        )
                    }
                    /* Review deleted photos button */
                    ReviewDeletedButton(view, viewModel, numToDelete, uiState.reviewDialogEnabled)
                    /* Info button */
                    FilledTonalIconButton(
                        onClick = {
                            viewModel.toggleInfo()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            }
                        },
                        modifier = Modifier.padding(
                            horizontal = dimensionResource(R.dimen.padding_small),
                            vertical = dimensionResource(R.dimen.padding_medium)
                        )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.info_bold),
                            contentDescription = "Show more image information",
                            modifier = Modifier.padding(dimensionResource(R.dimen.padding_small))
                        )
                    }
                }
                }
        AnimatedVisibility(
            visible = anchoredDraggableState.offset != with(density) { (300.dp).toPx() },
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            StatsCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = animatedPaddingValue)
                    .onGloballyPositioned { coordinates ->
                        statsPageHeight = coordinates.size.height
                    }
            )
        }
    }
}

@Composable
fun InfoRow(
    viewModel: MainViewModel,
    currentPhoto: Photo?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium.copy(
                    bottomEnd = CornerSize(0.dp),
                    bottomStart = CornerSize(0.dp)
                )
            )
    ) {
        Text(
            text = currentPhoto?.title?: "Title",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .padding(
                    start = dimensionResource(R.dimen.padding_medium),
                    end =  dimensionResource(R.dimen.padding_medium),
                    top = dimensionResource(R.dimen.padding_medium),
                    bottom = dimensionResource(R.dimen.padding_small))
//                .align(Alignment.Start)
        )
        if (currentPhoto?.description != null)
            Text(
                text = currentPhoto.description,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(horizontal = dimensionResource(R.dimen.padding_medium))
                //                .align(Alignment.Start)
            )
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Info(
                title = "Date",
                icon = painterResource(R.drawable.calendar),
                value = {
                    Text(currentPhoto?.getFormattedDate() ?: "", style = MaterialTheme.typography.bodyMedium)
                }
            )
            Info(
                title = "Size",
                icon = painterResource(R.drawable.hard_drives),
                value = {
                    Text(
                        formatShortFileSize(context, currentPhoto?.size ?: 0),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            )
            Info(
                title = "Location",
                icon = painterResource(R.drawable.map),
                value = {
                    Text(
                        currentPhoto?.location?.toString() ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        textDecoration = TextDecoration.Underline,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            viewModel.openLocationInMapsApp(currentPhoto)
                        }
                    )
                }
            )
            Info(
                title = "Album",
                icon = painterResource(R.drawable.books),
                value = {
                    Text(currentPhoto?.album ?: "", style = MaterialTheme.typography.bodyMedium)
                }
            )
        }
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth()
        ) {
            Info(
                title = "Resolution",
                icon = painterResource(R.drawable.frame_corners),
                value = {
                    Text(currentPhoto?.resolution ?: "", style = MaterialTheme.typography.bodyMedium)
                }
            )
            OutlinedIconButton(
                onClick = {
                    viewModel.sharePhoto()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                }
            ) { Icon(
                painterResource(R.drawable.share_network),
                contentDescription = "Share photo",
                modifier = Modifier.padding(dimensionResource(R.dimen.padding_small))
            ) }
        }
    }
}

@Composable
fun Info(
    title: String,
    icon: Painter,
    value: @Composable () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(dimensionResource(R.dimen.padding_small))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                null,
                Modifier
                    .padding(end = dimensionResource(R.dimen.padding_xsmall))
                    .size(16.dp)
            )
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        value()
    }
}