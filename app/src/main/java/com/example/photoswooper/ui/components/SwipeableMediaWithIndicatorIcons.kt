/*
 * SPDX-FileCopyrightText: 2025 Loowiz <loowiz@envs.net>
 *
 *  SPDX-License-Identifier: AGPL-3.0-only
 */

package com.example.photoswooper.ui.components

import android.content.res.Resources
import android.view.HapticFeedbackConstants
import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import coil3.ImageLoader
import coil3.compose.AsyncImage
import com.example.photoswooper.R
import com.example.photoswooper.data.BooleanPreference
import com.example.photoswooper.data.models.Media
import com.example.photoswooper.data.models.MediaStatus
import com.example.photoswooper.data.models.MediaType
import com.example.photoswooper.dataStore
import com.example.photoswooper.player
import com.example.photoswooper.ui.view.DragAnchors
import com.example.photoswooper.ui.viewmodels.MainViewModel
import com.example.photoswooper.utils.DataStoreInterface
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt


/**
 * Composable function containing a swipeable & zoomable image, with icons behind it showing what each swipe does
 *
 * @param media The [Media] to be displayed.
 * @param anchoredDraggableState The [AnchoredDraggableState] object for handling swipe gestures.
 */
@Composable
fun SwipeableMediaWithIndicatorIcons(
    media: Media,
    viewModel: MainViewModel,
    imageLoader: ImageLoader,
    anchoredDraggableState: AnchoredDraggableState<DragAnchors>,
    displayDeleteHint: Boolean,
    displayKeepHint: Boolean,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val reduceAnimations by DataStoreInterface(LocalContext.current.dataStore)
        .getBooleanSettingValue(BooleanPreference.REDUCE_ANIMATIONS.setting).collectAsState(false)
    val uiState by viewModel.uiState.collectAsState()
    val currentMediaItem =
        try {
            uiState.mediaItems[uiState.currentIndex]
        } catch (_: IndexOutOfBoundsException) {
            null
        }

    /** The id of the most recently loaded image
     *
     * Used to ensure the image that is loaded is actually a new one crazy haptic feedback on configuration change */
    var cachedMediaId by rememberSaveable { mutableLongStateOf(0) }
    /** Alpha used to give the illusion of a photo swiping away and disappearing*/
    var alphaValue by remember { mutableFloatStateOf(1f) }
    var indicatorIconsAlpha by remember { mutableFloatStateOf(0f) }

    // Variables used for zooming
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var zoomOffset by remember { mutableStateOf(Offset.Zero) }
    val animatableOffsetX = animateFloatAsState(targetValue = zoomOffset.x)
    val animatableOffsetY = animateFloatAsState(targetValue = zoomOffset.y)
    val animatableScale = animateFloatAsState(targetValue = zoomScale)
    val swipingEnabled = zoomScale == 1f

    // Perform haptic feedback based on whether a new media item has been loaded (i.e. not a reload from config change)
    LaunchedEffect(uiState.mediaReady) {
        if (uiState.mediaReady) {
            coroutineScope.launch {
                if (cachedMediaId != media.id) {
                    cachedMediaId = media.id
                    view.performHapticFeedback(
                        HapticFeedbackConstants.CLOCK_TICK
                    )
                }
            }
        }
    }

    /* Animate image size & alpha depending on how far the user has swiped */
    LaunchedEffect(anchoredDraggableState.requireOffset()) {
        if (!reduceAnimations) {
            if (uiState.mediaReady) // This prevents interference with entry animation
                viewModel.animatedImageScale.snapTo(
                    ((1.25f - (anchoredDraggableState.requireOffset().absoluteValue) / DragAnchors.Right.offset / 2f))
                        .coerceIn(0.8f, 1f),
                )
            alphaValue = 3 * (1f - anchoredDraggableState.requireOffset().absoluteValue / DragAnchors.Right.offset)
                .coerceIn(0f, 1f)
        }
        indicatorIconsAlpha = 2 * (anchoredDraggableState.requireOffset().absoluteValue / DragAnchors.Right.offset)
            .coerceIn(0f, 1f)
    }

    LaunchedEffect(zoomScale) {
        if (swipingEnabled) zoomOffset = Offset.Zero
    }

    Box(contentAlignment = Alignment.Center) {
        if (swipingEnabled)
            IndicatorIconRow(
                anchoredDraggableState.targetValue,
                displayKeepHint = displayKeepHint,
                displayDeleteHint = displayDeleteHint,
                modifier = Modifier.alpha(indicatorIconsAlpha)
            )
        if (!uiState.mediaReady)
            CircularProgressIndicator( )
        /* Swipeable box containing video or image */
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .fillMaxSize() // Expands bounds of swiping outside actual media
                .alpha(alphaValue)
                // Allow panning to be visible outside original bounds
                .clipToBounds()
                // Double-tap to toggle zoom
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { clickOffset ->
                            if (zoomScale > 1f) {
                                zoomScale = 1f
                            } else {
                                zoomScale = 2f
                                zoomOffset = clickOffset.copy(
                                    x = -clickOffset.x - Resources.getSystem().displayMetrics.widthPixels.toFloat() / 25,
                                    y = -clickOffset.y + (media.resolution?.substringAfterLast("×")?.toFloat()
                                        ?: 0f) / 25,
                                )
                            }
                        },
                        onTap = { viewModel.toggleInfoAndFloatingActionsRow() }
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
                    } else {
                        Modifier
                            // Detect zoom & pan using transformable
                            .pointerInput(Unit) {
                                detectTransformGestures(
                                    onGesture = { centroid, pan, gestureZoom, _ ->
                                        val oldScale = zoomScale
                                        val newScale = (zoomScale * gestureZoom).coerceIn(1f, 5f)
                                        zoomOffset += (pan + centroid * (oldScale - newScale))
                                        zoomScale = newScale
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

        ) {
            // Container setting scale of either the photo or video
                Box (Modifier.scale(viewModel.animatedImageScale.value)) {
                    if (currentMediaItem?.decodingError == null) {
                        when (media.type) {
                            MediaType.PHOTO -> {
                                AsyncImage(
                                    model = media.uri,
                                    imageLoader = imageLoader,
                                    contentDescription = null,
                                    onSuccess = {
                                        /* TODO("Adjust animation when undoing - enter from side they were swiped to") */
                                        viewModel.onMediaLoaded()
                                    },
                                    onError = { error ->
                                        viewModel.onMediaError(
                                            error.result.throwable.localizedMessage
                                                ?: error.result.throwable.message
                                        )
                                    },
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                )
                            }

                            MediaType.VIDEO -> {
                                PlayerSurface(
                                    player = player,
                                    surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                                    modifier = Modifier
                                        .aspectRatio(uiState.mediaAspectRatio)
                                        .fillMaxSize()
                                )
                            }
                        }
                    }
                    // If there is an error loading the media, display the error message
                    else {
                        Column(
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                text = buildAnnotatedString {
                                    append("PhotoSwooper was unable to load this file with error:\n")
                                    pushStyle(SpanStyle(fontFamily = FontFamily.Monospace))
                                    append(currentMediaItem.decodingError)
                                    pop()
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                            )
                            if (viewModel.getCurrentMedia()?.size == 0L)
                                Text(
                                    text = "This was likely because the file is empty",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = dimensionResource(R.dimen.padding_large))
                                )
                        }
                    }
                }
        }
    }
}

/** Row of two icons intended to be shown behind the photo/video.
 * These icons show the user if they are marking the item as "delete" or "keep"
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
                painter = painterResource(MediaStatus.KEEP.iconDrawableId),
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
                    text = "Release to keep",
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
                painter = painterResource(MediaStatus.DELETE.iconDrawableId),
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
                    text = "Release to mark for deletion",
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

enum class MediaError(@param:StringRes val reason: Int) {
    FileEmpty(R.string.the_file_is_empty),
}