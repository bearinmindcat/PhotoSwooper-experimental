/*
 * SPDX-FileCopyrightText: 2025 Loowiz <loowiz@envs.net>
 *
 *  SPDX-License-Identifier: AGPL-3.0-only
 */

package com.example.photoswooper.ui.view

import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import coil3.compose.AsyncImage
import com.example.photoswooper.R
import com.example.photoswooper.data.models.Media
import com.example.photoswooper.data.models.MediaStatus
import com.example.photoswooper.data.models.MediaType
import com.example.photoswooper.data.uistates.BooleanPreference
import com.example.photoswooper.dataStore
import com.example.photoswooper.ui.components.DropdownFilterChip
import com.example.photoswooper.ui.components.FloatingAction
import com.example.photoswooper.ui.viewmodels.MainViewModel
import com.example.photoswooper.ui.viewmodels.ReviewViewModel
import com.example.photoswooper.utils.DataStoreInterface
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    mainViewModel: MainViewModel,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val reduceAnimations by DataStoreInterface(context.dataStore)
        .getBooleanSettingValue(BooleanPreference.REDUCE_ANIMATIONS.setting).collectAsState(false)

    val reviewViewModel = remember { ReviewViewModel() }
    val reviewUiState by reviewViewModel.uiState.collectAsState()
    val mainUiState by mainViewModel.uiState.collectAsState()

    var floatingActionButtonSize by remember { mutableStateOf(DpSize(0.dp, 0.dp)) }

    fun performSelectItemHapticFeedback(mediaItem: Media) {
        if (SDK_INT >= 34) {
            if (reviewUiState.selectedMedia.contains(mediaItem))
                view.performHapticFeedback(HapticFeedbackConstants.TOGGLE_OFF)
            else
                view.performHapticFeedback(HapticFeedbackConstants.TOGGLE_ON)
        }
        else
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    if (mainUiState.mediaItems.firstOrNull { it.status != MediaStatus.UNSET } != null)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(top = dimensionResource(R.dimen.padding_small))
                .fillMaxSize()
        ) {
            Text("Status", style = MaterialTheme.typography.labelLarge)
            val listOfMediaStatusToFilter = MediaStatus.entries.minus(setOf(MediaStatus.UNSET, MediaStatus.HIDE))
            // TODO("Add colours to delete, keep & snooze icons")
            DropdownFilterChip(
                leadingIconPainter = painterResource(reviewUiState.currentStatusFilter.iconDrawableId),
                selectedMenuItem = reviewUiState.currentStatusFilter.toString().lowercase(),
                menuItemsDescription = "Statuses of the media to show",
                menuItems = listOfMediaStatusToFilter.map { it.toString().lowercase() }.toTypedArray(),
                menuItemIcons = listOfMediaStatusToFilter.map { painterResource(it.iconDrawableId) }.toTypedArray(),
                onSelectionChange = {
                    if (SDK_INT >= Build.VERSION_CODES.R)
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    reviewViewModel.updateCurrentStatusFilter(MediaStatus.valueOf(it.uppercase()))
                },
                modifier = Modifier.padding(bottom = dimensionResource(R.dimen.padding_small))
            )

            Box(Modifier.fillMaxSize()) {
                key(mainUiState.mediaItems) {
                    LazyVerticalStaggeredGrid(columns = StaggeredGridCells.Fixed(3)) {
                        items(mainUiState.mediaItems.filter { it.status == reviewUiState.currentStatusFilter }) { mediaItem ->
                            val coroutineScope = rememberCoroutineScope()
                            val imageScale = remember { Animatable(1f) }
                            fun animateImageSelect() {
                                coroutineScope.launch {
                                    val newTargetValue: Float =
                                        if (reviewUiState.selectedMedia.contains(mediaItem)) 0.9f
                                        else 1f
                                    imageScale.animateTo(
                                        newTargetValue,
                                        spring(
                                            stiffness = Spring.StiffnessMedium,
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                        )
                                    )
                                }
                            }
                            LaunchedEffect(reviewUiState.selectedMedia) {
                                animateImageSelect()
                            }
                            // Box containing image, type icon & selected checkbox
                            Box(
                                Modifier
                                    .combinedClickable(
                                        onClick = {
                                            if (reviewUiState.mediaSelectionEnabled) {
                                                performSelectItemHapticFeedback(mediaItem)
                                                reviewViewModel.toggleMediaItemSelected(mediaItem)
                                            } else
                                                mainViewModel.openInGalleryApp(mediaItem)
                                        },
                                        onClickLabel =
                                            if (reviewUiState.mediaSelectionEnabled) stringResource(R.string.select_item)
                                            else stringResource(R.string.open_externally_desc),
                                        onLongClick = {
                                            if (reviewUiState.mediaSelectionEnabled) {
                                                performSelectItemHapticFeedback(mediaItem)
                                                reviewViewModel.toggleMediaItemSelected(mediaItem)
                                            } else {
                                                reviewViewModel.toggleMediaSelectionEnabled()
                                                reviewViewModel.toggleMediaItemSelected(mediaItem)
                                            }
                                        },
                                        onLongClickLabel =
                                            if (reviewUiState.mediaSelectionEnabled) stringResource(R.string.select_item)
                                            else stringResource(R.string.enable_media_selection)
                                    )
                            ) {
                                // Box containing type icon & image
                                Box(
                                    modifier = Modifier
                                        .scale(imageScale.value)
                                ) {
                                    AsyncImage(
                                        model = mediaItem.uri,
                                        contentDescription = null,
                                        alignment = Alignment.Center,
                                        modifier = Modifier
                                            .padding(dimensionResource(R.dimen.padding_small))

                                    )
                                    Icon(
                                        painter = painterResource(if (mediaItem.type == MediaType.PHOTO) R.drawable.image else R.drawable.video),
                                        contentDescription = "This item is a ${mediaItem.type.toString().lowercase()}",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .padding(dimensionResource(R.dimen.padding_medium))
                                            .align(Alignment.BottomStart)
                                            .size(dimensionResource(R.dimen.xsmall_icon))
                                            .dropShadow(
                                                MaterialTheme.shapes.medium,
                                                Shadow(dimensionResource(R.dimen.xsmall_icon).div(1.5f))
                                            )
                                    )
                                }
                                androidx.compose.animation.AnimatedVisibility(
                                    reviewUiState.selectedMedia.contains(
                                        mediaItem
                                    ),
                                    enter = expandIn(
                                        spring(
                                            stiffness = Spring.StiffnessMediumLow,
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                        ),
                                        expandFrom = Alignment.Center,
                                    ),
                                    exit = shrinkOut(
                                        spring(
                                            stiffness = Spring.StiffnessMediumLow,
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                        ),
                                        shrinkTowards = Alignment.Center
                                    ),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.check),
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        contentDescription = stringResource(R.string.selected),
                                        modifier = Modifier
                                            .background(
                                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                                                shape = CircleShape
                                            )
                                            .padding(dimensionResource(R.dimen.padding_xsmall))
                                            .size(dimensionResource(R.dimen.small_icon))
                                    )
                                }
                            }
                        }
                        item(span = StaggeredGridItemSpan.FullLine) {
                            Spacer(
                                Modifier
                                    .height(floatingActionButtonSize.height + 32.dp/*(padding)*/)
                            )
                        }
                    }
                }
                if (mainUiState.mediaItems.none { it.status == reviewUiState.currentStatusFilter })
                    Text(
                        text = when (reviewUiState.currentStatusFilter) {
                            MediaStatus.DELETE -> stringResource(R.string.no_deleted_photos)
                            MediaStatus.KEEP -> stringResource(R.string.no_kept_photos)
                            else -> stringResource(R.string.no_snoozed_photos)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(dimensionResource(R.dimen.padding_medium))
                            .fillMaxSize()
                    )
                // Floating actions
                androidx.compose.animation.AnimatedVisibility(
                    visible = reviewUiState.mediaSelectionEnabled,
                    enter =
                        if (reduceAnimations) fadeIn()
                        else slideInVertically(
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMediumLow,
                                dampingRatio = Spring.DampingRatioLowBouncy,
                            ),
                            initialOffsetY = { it * 2 }
                        ),
                    exit =
                        if (reduceAnimations) fadeOut()
                        else slideOutVertically(
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMediumLow,
                                dampingRatio = Spring.DampingRatioLowBouncy,
                            ),
                            targetOffsetY = { it * 2 }
                        ),
                    label = "Review screen actions",
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.BottomStart)
                ) {
                    Box(contentAlignment = Alignment.BottomStart) {
                        Row(
                            modifier = Modifier
                                .dropShadow(
                                    shape = MaterialTheme.shapes.medium,
                                    shadow = Shadow(
                                        radius = 128.dp,
                                        alpha = 0.6f
                                    )
                                )
                                .padding(dimensionResource(R.dimen.padding_medium))
                        ) {
                            FloatingAction(
                                drawableIconId = R.drawable.x,
                                actionTitle = stringResource(R.string.cancel),
                                actionDescription = stringResource(R.string.cancel_selection),
                                onClick = {
                                    if (SDK_INT >= Build.VERSION_CODES.R)
                                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    reviewViewModel.cancelSelection()
                                },
                            )
                            FloatingAction(
                                drawableIconId = R.drawable.selection_all,
                                actionTitle = stringResource(R.string.select_all),
                                actionDescription = null,
                                onClick = {
                                    if (SDK_INT >= Build.VERSION_CODES.R)
                                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    mainUiState.mediaItems.forEach {
                                        reviewViewModel.toggleMediaItemSelected(
                                            it,
                                            true
                                        )
                                    }
                                },
                            )
                            FloatingAction(
                                drawableIconId = R.drawable.undo,
                                actionTitle = stringResource(R.string.unswipe),
                                actionDescription = stringResource(R.string.unswipe_selected_photos),
                                onClick = {
                                    if (SDK_INT >= Build.VERSION_CODES.R)
                                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    reviewViewModel.markSelectedItemsAsUnset {
                                        mainViewModel.markItem(
                                            status = MediaStatus.UNSET,
                                            index = mainUiState.mediaItems.indexOf(it)
                                        )
                                    }
                                },
                            )

                        }
                    }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = mainViewModel.getMediaToDelete().isNotEmpty()
                            && !reviewUiState.mediaSelectionEnabled
                            && reviewUiState.currentStatusFilter == MediaStatus.DELETE,
                    enter =
                        if (reduceAnimations) fadeIn()
                        else slideInHorizontally(
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMediumLow,
                                dampingRatio = Spring.DampingRatioLowBouncy,
                            ),
                            initialOffsetX = { it * 2 }
                        ),
                    exit =
                        if (reduceAnimations) fadeOut()
                        else slideOutHorizontally(
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMediumLow,
                                dampingRatio = Spring.DampingRatioLowBouncy,
                            ),
                            targetOffsetX = { it * 2 }
                        ),
                    modifier = Modifier
                        .padding(dimensionResource(R.dimen.padding_medium))
                        .fillMaxSize()
                        .align(Alignment.BottomEnd)

                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        ExtendedFloatingActionButton(
                            onClick = {
                                if (SDK_INT >= Build.VERSION_CODES.R)
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                mainViewModel.deleteMarkedMedia()
                                      },
                            modifier = Modifier
                                .onGloballyPositioned {
                                    with(density) {
                                        floatingActionButtonSize = it.size.toSize().toDpSize()
                                    }
                                }
                        ) {
                            Icon(painterResource(MediaStatus.DELETE.iconDrawableId), null)
                            Spacer(Modifier.width(dimensionResource(R.dimen.padding_small)))
                            Text("Delete ${mainViewModel.getMediaToDelete().size} items")
                        }
                    }
                }
            }
        }
    // Show screen instructing the user to swipe on photos/videos to see items here
    else
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(dimensionResource(R.dimen.padding_medium))
        ) {
            Icon(
                painter = painterResource(R.drawable.info),
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .padding(bottom = dimensionResource(R.dimen.padding_medium))
            )
            Text(
                stringResource(R.string.no_photos_to_review),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = dimensionResource(R.dimen.padding_small))
            )
            Text(
                stringResource(R.string.swiped_photos_will_show_here),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = dimensionResource(R.dimen.padding_small))
            )
        }
}