package com.example.photoswooper.ui.components

import android.content.res.Resources
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastRoundToInt
import coil3.ImageLoader
import coil3.compose.AsyncImage
import com.example.photoswooper.R
import com.example.photoswooper.data.models.Photo
import com.example.photoswooper.ui.view.DragAnchors
import kotlinx.coroutines.delay
import kotlin.math.roundToInt


/**
 * Composable function containing a swipeable & zoomable image, with icons behind it showing what each swipe does
 *
 * @param image The [Photo] to be displayed.
 * @param anchoredDraggableState The [AnchoredDraggableState] object for handling swipe gestures.
 */
@Composable
fun SwipeableAsyncImageWithIndicatorIcons(
    image: Photo,
    imageLoader: ImageLoader,
    anchoredDraggableState: AnchoredDraggableState<DragAnchors>
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val swipingEnabled = scale == 1f
    var displayDeleteHint by remember { mutableStateOf(false) }
    var displayKeepHint by remember { mutableStateOf(false) }

    // Animated for smooth transitions
    val animatableOffsetX = animateFloatAsState(targetValue = offset.x)
    val animatableOffsetY = animateFloatAsState(targetValue = offset.y)
    val animatableScale = animateFloatAsState(targetValue = scale)

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
            )
        }
        AsyncImage(
            model = image.uri,
            imageLoader = imageLoader,
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxSize()
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
                                    y = -it.y + (image.resolution?.substringAfterLast("×")?.toFloat()
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
                                    animationSpec = tween(
                                        anchoredDraggableState.lastVelocity.times(100000).fastRoundToInt()
                                    ),
                                )
                            )
                            .offset {
                                IntOffset(
                                    x = anchoredDraggableState.requireOffset().roundToInt(),
                                    y = 0
                                )
                            }
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
private fun IndicatorIconRow(currentAnchor: DragAnchors, displayKeepHint: Boolean, displayDeleteHint: Boolean) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
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
                    if (currentAnchor == DragAnchors.Right) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
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
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                    ),
                ),
                exit = shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
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
                    if (currentAnchor == DragAnchors.Left) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.surfaceVariant,
                    label = "deleteIconColour"
                ).value,
                modifier = Modifier
                    .size(dimensionResource(R.dimen.medium_icon))
            )
            AnimatedVisibility(
                visible = displayDeleteHint,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                    ),
                ),
                exit = shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
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