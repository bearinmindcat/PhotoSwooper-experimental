package com.example.photoswooper.ui.components

import android.content.res.Resources
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.util.fastRoundToInt
import coil3.ImageLoader
import coil3.compose.AsyncImage
import com.example.photoswooper.data.models.Photo
import com.example.photoswooper.ui.view.DragAnchors
import kotlin.math.roundToInt


@Composable
fun ZoomableAsyncImage(
    currentPhoto: Photo,
    imageLoader: ImageLoader,
    anchoredDraggableState: AnchoredDraggableState<DragAnchors>
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Animatable for smooth transitions

    val animatableOffsetX = animateFloatAsState(targetValue = offset.x)
    val animatableOffsetY = animateFloatAsState(targetValue = offset.y)
    val animatableScale = animateFloatAsState(targetValue = scale)

    LaunchedEffect(scale) {
        if (scale == 1f) offset = Offset.Zero
    }


    AsyncImage(
        model = currentPhoto.uri,
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
                                y = -it.y + (currentPhoto.resolution?.substringAfterLast("×")?.toFloat() ?: 0f) / 25,
                            )
                        }
                    }
                )
            }
            .then(
                // Only enable swiping when not zoomed
                if (scale == 1f) {
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
