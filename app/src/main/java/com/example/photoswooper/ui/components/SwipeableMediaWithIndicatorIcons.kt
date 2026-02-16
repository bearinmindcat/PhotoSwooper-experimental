/*
 * SPDX-FileCopyrightText: 2025 Loowiz <loowiz@envs.net>
 *
 *  SPDX-License-Identifier: AGPL-3.0-only
 */

package com.example.photoswooper.ui.components

import android.content.res.Resources
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import coil3.ImageLoader
import coil3.compose.AsyncImage
import com.example.photoswooper.R
import com.example.photoswooper.data.BooleanPreference
import com.example.photoswooper.data.models.MediaStatus
import com.example.photoswooper.data.models.MediaType
import com.example.photoswooper.dataStore
import com.example.photoswooper.experimental.SwipeController
import com.example.photoswooper.experimental.data.SwipeableItem
import com.example.photoswooper.experimental.ui.DocumentPreviewCard
import com.example.photoswooper.player
import com.example.photoswooper.ui.viewmodels.defaultEntryAnimationSpec
import com.example.photoswooper.utils.DataStoreInterface
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue


/**
 * Composable function containing a swipeable & zoomable item, with icons behind it showing what each swipe does.
 * Supports both photos/videos (via [SwipeableItem.MediaItem]) and documents (via [SwipeableItem.DocumentItem]).
 *
 * @param item The [SwipeableItem] to be displayed (photo, video, or document).
 * @param controller The [SwipeController] for handling swipe actions (mark, next, etc.).
 * @param imageLoader The Coil [ImageLoader] for photo rendering (null for documents).
 * @param isReady Whether the current item is loaded and ready to display.
 * @param mediaAspectRatio Aspect ratio for video display (default 1f).
 */
@Composable
fun SwipeableMediaWithIndicatorIcons(
    item: SwipeableItem,
    controller: SwipeController,
    imageLoader: ImageLoader?,
    isReady: Boolean,
    mediaAspectRatio: Float = 1f,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val reduceAnimations by DataStoreInterface(LocalContext.current.dataStore)
        .getBooleanSettingValue(BooleanPreference.REDUCE_ANIMATIONS.setting).collectAsState(false)

    /** The id of the most recently loaded item
     *
     * Used to ensure the item that is loaded is actually a new one — prevents crazy haptic feedback on configuration change */
    val itemId = when (item) {
        is SwipeableItem.MediaItem -> item.id
        is SwipeableItem.DocumentItem -> item.uri.hashCode().toLong()
    }
    var cachedMediaId by rememberSaveable { mutableLongStateOf(0) }
    /** Alpha used to give the illusion of an item swiping away and disappearing*/
    var alphaValue by remember { mutableFloatStateOf(1f) }
    var indicatorIconsAlpha by remember { mutableFloatStateOf(0f) }

    /** Whether to display a hint to "release to delete" */
    var displayDeleteHint by remember { mutableStateOf(false) }
    /** Whether to display a hint to "release to keep" */
    var displayKeepHint by remember { mutableStateOf(false) }

    // Variables used for zooming
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var zoomOffset by remember { mutableStateOf(Offset.Zero) }
    val animatedOffset = animateOffsetAsState(zoomOffset)
    val animatedScale = animateFloatAsState(targetValue = zoomScale)
    val swipingEnabled = zoomScale == 1f

    // Show "reset zoom" button if currently zooming
    val resetZoomButtonScale: Float by animateFloatAsState(
        if (zoomScale != 1f) 1f else 0f,
        animationSpec = defaultEntryAnimationSpec
    )

    // Perform haptic feedback based on whether a new item has been loaded (i.e. not a reload from config change)
    LaunchedEffect(isReady) {
        if (isReady) {
            coroutineScope.launch {
                if (cachedMediaId != itemId) {
                    cachedMediaId = itemId
                    view.performHapticFeedback(
                        HapticFeedbackConstants.CLOCK_TICK
                    )
                }
            }
        }
    }

    /* State of item's dragged position (dragging left/right) */
    val anchoredDraggableState = remember {
        AnchoredDraggableState(
            initialValue = DragAnchors.Center,
            anchors = DraggableAnchors {
                DragAnchors.Left at DragAnchors.Left.offset
                DragAnchors.Center at DragAnchors.Center.offset
                DragAnchors.Right at DragAnchors.Right.offset
            },
        )
    }

    /* When user drags to one of the anchors, without releasing yet */
    LaunchedEffect(anchoredDraggableState) {
        snapshotFlow { anchoredDraggableState.targetValue }
            .collectLatest { position ->
                // Only perform haptic feedback if item is visible
                if (controller.animatedMediaScale.value != 0f) {
                    when (position) {
                        DragAnchors.Left -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                                view.performHapticFeedback(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE)
                            delay(1000)
                            displayDeleteHint = true
                        }

                        DragAnchors.Center -> {
                            if (
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                                && anchoredDraggableState.settledValue == DragAnchors.Center
                            )
                                view.performHapticFeedback(HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE)
                            displayKeepHint = false
                            displayDeleteHint = false
                        }

                        DragAnchors.Right -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                                view.performHapticFeedback(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE)
                            delay(1000)
                            displayKeepHint = true
                        }
                    }
                }            }
    }

    /* When user releases drag motion */
    LaunchedEffect(anchoredDraggableState) {
        snapshotFlow { anchoredDraggableState.settledValue }
            .collectLatest { position ->
                when (position) {
                    DragAnchors.Left -> {
                        controller.markItem(MediaStatus.DELETE)
                        controller.animatedMediaScale.snapTo(0f)
                        controller.next()
                        anchoredDraggableState.snapTo(
                            DragAnchors.Center,
                        )
                    }

                    DragAnchors.Right -> {
                        controller.markItem(MediaStatus.KEEP)
                        controller.animatedMediaScale.snapTo(0f)
                        controller.next()
                        anchoredDraggableState.snapTo(
                            DragAnchors.Center
                        )
                    }

                    else -> { /* Maybe add a markPhotoUnset() function if necessary? */
                    }
                }
            }
    }

    /* Animate item size & alpha depending on how far the user has swiped */
    LaunchedEffect(anchoredDraggableState.requireOffset()) {
        if (!reduceAnimations) {
            if (isReady) // This prevents interference with entry animation
                controller.animatedMediaScale.snapTo(
                    ((1.25f - (anchoredDraggableState.requireOffset().absoluteValue) / DragAnchors.Right.offset / 2f))
                        .coerceIn(0.8f, 1f),
                )
            alphaValue = 3 * (1f - anchoredDraggableState.requireOffset().absoluteValue / DragAnchors.Right.offset)
                .coerceIn(0f, 1f)
        }
        indicatorIconsAlpha = 2 * (anchoredDraggableState.requireOffset().absoluteValue / DragAnchors.Right.offset)
            .coerceIn(0f, 1f)
    }

    // Resolution for zoom offset (only for media items)
    val itemResolution = (item as? SwipeableItem.MediaItem)?.resolution

    Box(contentAlignment = Alignment.Center) {
        // If swiping, show indicators in the background
        if (swipingEnabled)
            IndicatorIconRow(
                anchoredDraggableState.targetValue,
                displayKeepHint = displayKeepHint,
                displayDeleteHint = displayDeleteHint,
                modifier = Modifier.alpha(indicatorIconsAlpha)
            )
        if (!isReady)
            CircularProgressIndicator()
        /* Swipeable box containing video, image, or document */
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .fillMaxSize() // Expands bounds of swiping outside actual content
                .alpha(alphaValue)
                // Allow panning to be visible outside original bounds
                .clipToBounds()
                // Detect zoom & pan using transformable
                .pointerInput(Unit) {
                    detectTransformGestures(
                        onGesture = { centroid, pan, gestureZoom, _ ->
                            val oldScale = zoomScale
                            val newScale = (zoomScale * gestureZoom).coerceIn(1f, 5f)
                            zoomOffset += (pan + centroid * (oldScale - newScale))
                            zoomScale = newScale
                        },
                    )
                }
                // Apply zoom on double tap
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { clickOffset ->
                            if (zoomScale == 1f) {
                                zoomScale = 2f
                                zoomOffset = clickOffset / zoomScale
                            } else {
                                zoomScale = 1f
                            }
                        },
                    )
                }
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
                                    y = -clickOffset.y + (itemResolution?.substringAfterLast("×")?.toFloat()
                                        ?: 0f) / 25,
                                )
                            }
                        },
                        onTap = { controller.toggleInfoAndFloatingActionsRow() }
                    )
                }
                // Apply effects of zoom
                .graphicsLayer {
                    scaleX = animatedScale.value
                    scaleY = animatedScale.value
                    translationX = animatedOffset.value.x + anchoredDraggableState.requireOffset()
                    translationY = animatedOffset.value.y
                    transformOrigin = TransformOrigin(0f, 0f)
                }
                .then(
                    // Only enable swiping when not zoomed
                    if (swipingEnabled) {
                        zoomOffset = Offset.Zero
                        Modifier
                            .anchoredDraggable(
                                state = anchoredDraggableState,
                                orientation = Orientation.Horizontal,
                                flingBehavior = AnchoredDraggableDefaults.flingBehavior(
                                    anchoredDraggableState,
                                    animationSpec = tween(100),
                                )
                            )
                    } else {
                        Modifier
                    }
                )

        ) {
            // Container setting scale of the content
                Box (Modifier.scale(controller.animatedMediaScale.value)) {
                    if (item.decodingError == null) {
                        when (item) {
                            is SwipeableItem.MediaItem -> {
                                when (item.type) {
                                    MediaType.PHOTO -> {
                                        AsyncImage(
                                            model = item.uri,
                                            imageLoader = imageLoader!!,
                                            contentDescription = null,
                                            onSuccess = {
                                                /* TODO("Adjust animation when undoing - enter from side they were swiped to") */
                                                controller.onMediaLoaded()
                                            },
                                            onError = { error ->
                                                controller.onMediaError(
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
                                                .aspectRatio(mediaAspectRatio)
                                                .fillMaxSize()
                                        )
                                    }
                                }
                            }

                            is SwipeableItem.DocumentItem -> {
                                DocumentPreviewCard(
                                    document = item.document,
                                    imageLoader = imageLoader,
                                    exoPlayer = player,
                                    modifier = Modifier.fillMaxSize()
                                )
                                LaunchedEffect(item.uri) {
                                    controller.onMediaLoaded()
                                }
                            }
                        }
                    }
                    // If there is an error loading the item, display the error message
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
                                    append(item.decodingError)
                                    pop()
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                            )
                            if (controller.getCurrentItemSize() == 0L)
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
        // If zooming, show reset zoom button
            FloatingActionButton(
                onClick = { zoomScale = 1f },
                modifier = Modifier
                    .scale(resetZoomButtonScale)
                    .padding(dimensionResource(R.dimen.padding_medium))
                    .align(Alignment.BottomEnd)
            ) {
                Icon(
                    painterResource(R.drawable.magnifying_glass_minus),
                    contentDescription = if (!swipingEnabled) stringResource(R.string.reset_zoom) else null,
                )
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

enum class DragAnchors(val offset: Float) {
    Left(-Resources.getSystem().displayMetrics.widthPixels.toFloat() / 2),
    Center(0f),
    Right(Resources.getSystem().displayMetrics.widthPixels.toFloat() / 2)
}
