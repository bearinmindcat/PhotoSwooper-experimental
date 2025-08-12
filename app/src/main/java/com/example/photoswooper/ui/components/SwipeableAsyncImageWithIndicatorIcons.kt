package com.example.photoswooper.ui.components

import android.content.res.Resources
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import com.example.photoswooper.R
import com.example.photoswooper.data.models.Photo
import com.example.photoswooper.data.uistates.BooleanPreference
import com.example.photoswooper.dataStore
import com.example.photoswooper.ui.view.DragAnchors
import com.example.photoswooper.ui.viewmodels.MainViewModel
import com.example.photoswooper.utils.DataStoreInterface
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt


/**
 * Composable function containing a swipeable & zoomable image, with icons behind it showing what each swipe does
 *
 * @param photo The [Photo] to be displayed.
 * @param anchoredDraggableState The [AnchoredDraggableState] object for handling swipe gestures.
 */
@Composable
fun SwipeableAsyncImageWithIndicatorIcons(
    photo: Photo,
    viewModel: MainViewModel,
    imageLoader: ImageLoader,
    anchoredDraggableState: AnchoredDraggableState<DragAnchors>,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val reduceAnimations = DataStoreInterface(LocalContext.current.dataStore)
        .getBooleanSettingValue(BooleanPreference.reduce_animations.toString()).collectAsState(false)

    var imageAlpha by remember { mutableStateOf(1f) }
    var indicatorIconsAlpha by remember { mutableStateOf(0f) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val swipingEnabled = scale == 1f
    var displayDeleteHint by remember { mutableStateOf(false) }
    var displayKeepHint by remember { mutableStateOf(false) }

    // Animated for smooth transitions
    val animatableOffsetX = animateFloatAsState(targetValue = offset.x)
    val animatableOffsetY = animateFloatAsState(targetValue = offset.y)
    val animatableScale = animateFloatAsState(targetValue = scale)

    /* Animate image depending on how far the user has swiped */
    LaunchedEffect(anchoredDraggableState.requireOffset()) {
        if (!reduceAnimations.value) {
            viewModel.animatedImageScaleEntry.snapTo(
                ((1.25f - (anchoredDraggableState.requireOffset().absoluteValue) / DragAnchors.Right.offset / 2f))
                    .coerceIn(0.8f, 1f),
            )
            imageAlpha = 3 * (1f - anchoredDraggableState.requireOffset().absoluteValue / DragAnchors.Right.offset)
                .coerceIn(0f, 1f)
        }
            indicatorIconsAlpha = 2*(anchoredDraggableState.requireOffset().absoluteValue / DragAnchors.Right.offset)
                    .coerceIn(0f, 1f)
    }

    LaunchedEffect(scale) {
        if (swipingEnabled) offset = Offset.Zero
    }
    /* Show usage hints after a delay */
    LaunchedEffect(anchoredDraggableState.targetValue) {
        when (anchoredDraggableState.targetValue) {
            DragAnchors.Left -> {
                delay(1000)
                displayDeleteHint = true
            }
            DragAnchors.Right -> {
                delay(1000)
                displayKeepHint = true
            }
            DragAnchors.Center -> {
                displayKeepHint = false
                displayDeleteHint = false
            }

        }
    }

    Box(contentAlignment = Alignment.Center) {
        if (swipingEnabled) {
            IndicatorIconRow(
                anchoredDraggableState.targetValue,
                displayKeepHint = displayKeepHint,
                displayDeleteHint = displayDeleteHint,
                modifier = Modifier.alpha(indicatorIconsAlpha)
            )
        }
        AsyncImage(
            model = photo.uri,
            imageLoader = imageLoader,
            contentDescription = null,
            onSuccess = {
                /* TODO("Adjust animation when undoing - enter from side they were swiped to") */
                coroutineScope.launch {
                    anchoredDraggableState.animateTo(
                        DragAnchors.Center,
                        tween(0)
                    )
                    viewModel.enterImage(coroutineScope)
                    view.performHapticFeedback(
                        HapticFeedbackConstants.CLOCK_TICK
                    )
                }
            },
            contentScale = ContentScale.FillWidth,
            modifier = modifier
                .fillMaxSize()
                .alpha(imageAlpha)
                // Allow panning to be visible outside original bounds
                .clipToBounds()
                // Double-tap to toggle zoom
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                            } else {
                                scale = 2f
                                offset = it.copy(
                                    x = -it.x - Resources.getSystem().displayMetrics.widthPixels.toFloat() / 25,
                                    y = -it.y + (photo.resolution?.substringAfterLast("×")?.toFloat()
                                        ?: 0f) / 25,
                                )
                            }
                        }
                    )
                }
                .then(
                    // Only enable swiping when not zoomed
                    if (swipingEnabled) {
                        Modifier
                            .anchoredDraggable(
                                state = anchoredDraggableState,
                                orientation = Orientation.Horizontal,
                                flingBehavior = AnchoredDraggableDefaults.flingBehavior(
                                    anchoredDraggableState,
                                    animationSpec = tween(100),
                                )
                            )
                            .offset {
                                IntOffset(
                                    x = anchoredDraggableState.requireOffset().roundToInt(),
                                    y = 0
                                )
                            }
                            .scale(viewModel.animatedImageScaleEntry.value)
                    } else {
                        Modifier
                            // Detect zoom & pan using transformable
                            .pointerInput(Unit) {
                                detectTransformGestures(
                                    onGesture = { centroid, pan, gestureZoom, _ ->
                                        val oldScale = scale
                                        val newScale = (scale * gestureZoom).coerceIn(1f, 5f)
                                        offset += (pan + centroid * (oldScale - newScale))
                                        scale = newScale
                                    }
                                )
                            }
                            // Apply zoom + pan
                            .graphicsLayer {
                                scaleX = animatableScale.value
                                scaleY = animatableScale.value
                                translationX = animatableOffsetX.value
                                translationY = animatableOffsetY.value
                                transformOrigin = TransformOrigin(0f, 0f)
                            }
                    }
                )
        )
    }
}

/** Row of two icons intended to be shown behind the photo.
 * These icons show the user if they are marking the photo as "delete" or "keep"
 *
 * @param currentAnchor The anchor the user is currently dragging to (left to delete, right to keep)
 */
@Composable
private fun IndicatorIconRow(
    currentAnchor: DragAnchors,
    displayKeepHint: Boolean,
    displayDeleteHint: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(min = 96.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.bookmark_simple),
                contentDescription = null,
                tint = animateColorAsState(
                    when (currentAnchor) {
                        DragAnchors.Right -> MaterialTheme.colorScheme.primary
                        DragAnchors.Left -> MaterialTheme.colorScheme.surface
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    label = "keepIconColour"
                ).value,
                modifier = Modifier
                    .size(dimensionResource(R.dimen.medium_icon))
                    .animateContentSize()
            )
            AnimatedVisibility(
                visible = displayKeepHint,
                enter = expandVertically(
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = Spring.DampingRatioLowBouncy,
                    ),
                ),
                exit = shrinkVertically(
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = Spring.DampingRatioLowBouncy,
                    ),
                ),
                label = "keepHint"
            ) {
                Text(
                    text = "Release to keep this photo",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .widthIn(max = 96.dp)
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(min = 96.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.trash),
                contentDescription = null,
                tint = animateColorAsState(
                    when (currentAnchor) {
                        DragAnchors.Left -> MaterialTheme.colorScheme.error
                        DragAnchors.Right -> MaterialTheme.colorScheme.surface
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    label = "deleteIconColour"
                ).value,
                modifier = Modifier
                    .size(dimensionResource(R.dimen.medium_icon))
            )
            AnimatedVisibility(
                visible = displayDeleteHint,
                enter = expandVertically(
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = Spring.DampingRatioLowBouncy,
                    ),
                ),
                exit = shrinkVertically(
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = Spring.DampingRatioLowBouncy,
                    ),
                ),
                label = "deleteHint"
            ) {
                Text(
                    text = "Release to delete this photo",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .widthIn(max = 96.dp)
                )
            }
        }
    }
}