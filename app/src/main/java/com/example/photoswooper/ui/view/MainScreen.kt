/*
 * SPDX-FileCopyrightText: 2025 Loowiz <loowiz@envs.net>
 *
 *  SPDX-License-Identifier: AGPL-3.0-only
 */

package com.example.photoswooper.ui.view

import android.Manifest.permission.READ_MEDIA_IMAGES
import android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.content.res.Resources
import android.os.Build
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.checkSelfPermission
import coil3.ImageLoader
import com.example.photoswooper.R
import com.example.photoswooper.checkPermissions
import com.example.photoswooper.data.models.MediaSortField
import com.example.photoswooper.data.models.MediaStatus
import com.example.photoswooper.data.models.MediaType
import com.example.photoswooper.data.uistates.BooleanPreference
import com.example.photoswooper.data.uistates.IntPreference
import com.example.photoswooper.data.uistates.StatsData
import com.example.photoswooper.data.uistates.TimeFrame
import com.example.photoswooper.dataStore
import com.example.photoswooper.ui.components.ActionBar
import com.example.photoswooper.ui.components.FilterDialog
import com.example.photoswooper.ui.components.FloatingActionsRow
import com.example.photoswooper.ui.components.InfoRow
import com.example.photoswooper.ui.components.ReviewDeletedButton
import com.example.photoswooper.ui.components.SwipeableMediaWithIndicatorIcons
import com.example.photoswooper.ui.viewmodels.FilterDialogViewModel
import com.example.photoswooper.ui.viewmodels.MainViewModel
import com.example.photoswooper.ui.viewmodels.StatsViewModel
import com.example.photoswooper.utils.DataStoreInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class DragAnchors(val offset: Float) {
    Left(-Resources.getSystem().displayMetrics.widthPixels.toFloat() / 2),
    Center(0f),
    Right(Resources.getSystem().displayMetrics.widthPixels.toFloat() / 2)
}

/**
 * A composable containing the main UI of the app, within a BottomSheetScaffold composable
 *
 * @param skipReview Whether the user has chosen to skip navigating to the review screen before deleting swiped items
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalAnimationApi::class)
fun MainScreen(
    statsViewModel: StatsViewModel,
    mainViewModel: MainViewModel,
    imageLoader: ImageLoader,
    skipReview: Boolean
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val dataStoreInterface = DataStoreInterface(context.dataStore)
    val reduceAnimations by dataStoreInterface
        .getBooleanSettingValue(BooleanPreference.REDUCE_ANIMATIONS.setting).collectAsState(false)
    val tutorialIndex by dataStoreInterface.getIntSettingValue(IntPreference.TUTORIAL_INDEX.setting)
        .collectAsState(1)

    fun updateTutorialIndex(newIndex: Int) {
        if (newIndex > 0) {
            coroutineScope.launch {
                dataStoreInterface.setIntSettingValue(
                    newIndex,
                    IntPreference.TUTORIAL_INDEX.setting
                )
            }
        }
    }


    val uiState by mainViewModel.uiState.collectAsState()
    val numToDelete = uiState.mediaItems.count { it.status == MediaStatus.DELETE }
    val currentMediaItem =
        try {
            uiState.mediaItems[uiState.currentIndex]
        } catch (_: IndexOutOfBoundsException) {
            null
        }
    val actionBarHeight = remember { Animatable(0f) }
    fun animateActionBarHeightTo(newHeight: Float) {
        coroutineScope.launch {
            actionBarHeight.animateTo(
                newHeight,
                spring(
                    stiffness = if (reduceAnimations) 0f else Spring.StiffnessMediumLow,
                    dampingRatio = Spring.DampingRatioLowBouncy,
                )
            )
        }
    }

    var sheetContentTabIndex by rememberSaveable { mutableIntStateOf(TabIndex.REVIEW.ordinal) }
    fun navigateToReviewScreen() {
        coroutineScope.launch {
            if (mainViewModel.bottomSheetScaffoldState.bottomSheetState.targetValue == SheetValue.Expanded)
                sheetContentTabIndex = TabIndex.REVIEW.ordinal
            else {
                mainViewModel.expandBottomSheet(coroutineScope)
                delay(250)
                sheetContentTabIndex = TabIndex.REVIEW.ordinal
            }
        }
    }

    /* For anchored draggable (dragging photo/video left/right) */

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
                performDragHapticFeedback(position, view, anchoredDraggableState)
            }
    }

    /* When user releases drag motion */
    LaunchedEffect(anchoredDraggableState) {
        snapshotFlow { anchoredDraggableState.settledValue }
            .collectLatest { position ->
                when (position) {
                    DragAnchors.Left -> {
                        mainViewModel.markItem(MediaStatus.DELETE)
                        mainViewModel.animatedImageScaleEntry.snapTo(0f)
                        mainViewModel.next()
                        anchoredDraggableState.snapTo(
                            DragAnchors.Center,
                        )
                    }

                    DragAnchors.Right -> {
                        mainViewModel.markItem(MediaStatus.KEEP)
                        mainViewModel.animatedImageScaleEntry.snapTo(0f)
                        mainViewModel.next()
                        anchoredDraggableState.snapTo(
                            DragAnchors.Center
                        )
                    }

                    else -> { /* Maybe add a markPhotoUnset() function if necessary? */
                    }
                }
            }
    }

    /* LaunchedEffect to make tutorial interactive */
    if (uiState.tutorialMode) {
        val tertiaryColor = MaterialTheme.colorScheme.tertiary
        fun onStepComplete(showToast: Boolean = true) {
            if (showToast)
                Toast.makeText(
                    context,
                    "Nice! :)",
                    Toast.LENGTH_SHORT
                ).show()
            updateTutorialIndex(tutorialIndex + 1)
        }
        LaunchedEffect(tutorialIndex) {
            when (tutorialIndex) {
                // Swipe right to keep
                1 -> {
                    mainViewModel.updateTutorialCardContent(
                        iconDrawableId = R.drawable.bookmark_simple,
                        title = context.getString(R.string.tutorial_swipe_right_title),
                        body = buildAnnotatedString {
                            append(context.getString(R.string.tutorial_swipe_right_desc) + "\n\n")
                            pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                            append(context.getString(R.string.tutorial_swipe_right_try))
                            pop()
                        }
                    )
                    while ((uiState.mediaItems.getOrElse(uiState.currentIndex - 1, { currentMediaItem })?.status
                            ?: MediaStatus.DELETE) != MediaStatus.KEEP
                    ) {
                        delay(500)
                    }
                    onStepComplete()
                }
                // Swipe left to mark item to be deleted
                2 -> {
                    mainViewModel.updateTutorialCardContent(
                        iconDrawableId = R.drawable.trash,
                        title = context.getString(R.string.tutorial_swipe_left_title),
                        body = buildAnnotatedString {
                            append(context.getString(R.string.tutorial_swipe_left_desc) + "\n\n")
                            pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                            append(context.getString(R.string.try_it))
                            pop()
                        }
                    )
                    while ((uiState.mediaItems.getOrElse(uiState.currentIndex - 1, { currentMediaItem })?.status
                            ?: MediaStatus.KEEP) != MediaStatus.DELETE
                    ) {
                        delay(500)
                    }
                    onStepComplete()
                }
                // Tap on the photo/video to view more actions. See the wiki for more info on each of these.
                3 -> {
                    mainViewModel.updateTutorialCardContent(
                        iconDrawableId = R.drawable.info,
                        title = context.getString(R.string.tutorial_tap_title),
                        body = buildAnnotatedString {
                            append(context.getString(R.string.see) + " ")
                            pushLink(LinkAnnotation.Url("https://codeberg.org/Loowiz/PhotoSwooper/wiki/Extra-actions"))
                            pushStyle(SpanStyle(color = tertiaryColor, textDecoration = TextDecoration.Underline))
                            append(context.getString(R.string.the_wiki))
                            pop()
                            pop()
                            append(" " + context.getString(R.string.for_info_about_these_actions) + "\n\n")
                            pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                            append(context.getString(R.string.try_it))
                            pop()
                        },
                    )
                    delay(500)
                    mainViewModel.toggleInfoAndFloatingActionsRow(false)
                    delay(500)
                    while (!uiState.showInfoAndFloatingActionsRow) {
                        delay(500)
                    }
                    delay(1000) // Delay so user doesn't think that tapping brings up bottom sheet (seeking to next step does this)
                    onStepComplete()
                }
                // Undo button to undo the previous swipe (bring the swiped photo/video back)
                4 -> {
                    // If first start-up, wait for media items to be fetched
                    while (uiState.mediaItems.size < 3) {
                        delay(200)
                    }
                    if (uiState.currentIndex == 0) {
                        mainViewModel.markItem(MediaStatus.DELETE)
                        mainViewModel.next()
                    }
                    val initialIndex = uiState.currentIndex
                    mainViewModel.updateTutorialCardContent(
                        iconDrawableId = R.drawable.undo,
                        title = context.getString(R.string.tutorial_bottom_sheet_title),
                        body = buildAnnotatedString {
                            append(
                                context.getString(R.string.tutorial_bottom_sheet_desc) +
                                        "\n\n"
                            )
                            pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                            append(context.getString(R.string.tutorial_bottom_sheet_try))
                            pop()
                        }
                    )
                    while (initialIndex <= uiState.currentIndex) {
                        delay(500)
                    }
                    onStepComplete()
                }
                // Filter button shows a dialog to choose what you media want to see (e.g. swipe only videos)
                5 -> {
                    delay(500)
                    mainViewModel.bottomSheetScaffoldState.bottomSheetState.partialExpand()
                    mainViewModel.updateTutorialCardContent(
                        iconDrawableId = R.drawable.funnel,
                        title = context.getString(R.string.tutorial_filter_sort_title),
                        body = buildAnnotatedString {
                            append(context.getString(R.string.tutorial_filter_sort_desc) + "\n\n")
                            pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                            append(context.getString(R.string.tutorial_filter_sort_try))
                            pop()
                        }
                    )
                    while (
                        mainViewModel.mediaFilter.value.sortAscending
                        || mainViewModel.mediaFilter.value.mediaTypes != setOf(MediaType.PHOTO)
                        || mainViewModel.mediaFilter.value.sortField != MediaSortField.DATE
                    ) {
                        delay(500)
                    }
                    onStepComplete()
                }
                // Review screen shows media marked as deleted. To actually delete media, click the delete button in the bottom right
                6 -> {
                    // If first start-up, wait for media items to be fetcheds
                    while (uiState.mediaItems.size < 3) {
                        delay(200)
                    }
                    if (mainViewModel.getMediaToDelete().isEmpty()) {
                        mainViewModel.markItem(MediaStatus.DELETE)
                        mainViewModel.next()
                        mainViewModel.markItem(MediaStatus.DELETE)
                        mainViewModel.next()
                    }
                    delay(500)
                    val initialNumToDelete = mainViewModel.getMediaToDelete().size
                    mainViewModel.expandBottomSheet(coroutineScope)
                    sheetContentTabIndex = TabIndex.REVIEW.ordinal
                    mainViewModel.updateTutorialCardContent(
                        iconDrawableId = R.drawable.check,
                        title = context.getString(R.string.review_screen),
                        body = buildAnnotatedString {
                            append(
                                context.getString(R.string.tutorial_review_desc) + "\n\n"
                            )
                            pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                            append(context.getString(R.string.tutorial_review_try))
                            pop()
                        }
                    )
                    while (mainViewModel.getMediaToDelete().size >= initialNumToDelete) {
                        delay(500)
                    }
                    onStepComplete()
                }
                // Stats screen shows you how much you have swiped in the past
                7 -> {
                    mainViewModel.expandBottomSheet(coroutineScope)
                    sheetContentTabIndex = TabIndex.STATS.ordinal
                    mainViewModel.updateTutorialCardContent(
                        iconDrawableId = R.drawable.chart,
                        title = context.getString(R.string.statistics_screen),
                        body = buildAnnotatedString {
                            append(context.getString(R.string.tutorial_statistics_desc) + "\n\n")
                            pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                            append(context.getString(R.string.tutorial_statistics_try))
                            pop()
                        }
                    )
                    while (
                        statsViewModel.uiState.value.dataType != StatsData.SPACE_SAVED
                        || statsViewModel.uiState.value.timeFrame != TimeFrame.DAY
                    ) {
                        delay(500)
                    }
                    onStepComplete()
                }
                // Settings screen lets you customise the app
                8 -> {
                    mainViewModel.expandBottomSheet(coroutineScope)
                    sheetContentTabIndex = TabIndex.SETTINGS.ordinal
                    mainViewModel.updateTutorialCardContent(
                        iconDrawableId = R.drawable.gear,
                        title = context.getString(R.string.settings_screen),
                        body = buildAnnotatedString {
                            append(context.getString(R.string.settings_desc))
                        }
                    )
                    delay(5000)
                    onStepComplete(showToast = false)
                }

                9 -> {
                    mainViewModel.updateTutorialCardContent(
                        iconDrawableId = R.drawable.check,
                        title = context.getString(R.string.tutorial_end_title),
                        body = buildAnnotatedString {
                            append(context.getString(R.string.tutorial_end_desc))
                        }
                    )
                    delay(5000)
                    mainViewModel.onEndTutorial()
                }
            }
        }
    }
    if (uiState.showFilterDialog)
        FilterDialog(
            onDismiss = {
                mainViewModel.toggleFilterDialog(false)
                mainViewModel.revertIsPlayingToBeforeTempPause()
            },
            onConfirm = { newFilter, setFilterAsDefault ->
                mainViewModel.toggleFilterDialog(false)
                mainViewModel.updateMediaFilter(newFilter, setFilterAsDefault)
            },
            filterDialogViewModel = FilterDialogViewModel(mainViewModel.mediaFilter.collectAsState().value),
        )

    Box(Modifier.fillMaxSize()) {
        BottomSheetScaffold(
            content = { paddingValues ->
                Log.d("UI", "Loading MainScreen")
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize()
                ) {
                    when {
                        /* When permissions not granted */
                        (uiState.permissionsGranted == false) -> {
                            RequestPermissionsScreen(mainViewModel, context, view)
                        }
                        /* When loading new photos */
                        (uiState.fetchingMedia) -> {
                            if (reduceAnimations) Text(
                                text = "Loading...",
                                style = MaterialTheme.typography.titleLarge,
                                textAlign = TextAlign.Center,
                            )
                            else
                                CircularProgressIndicator(
                                    modifier = Modifier.width(64.dp),
                                    color = MaterialTheme.colorScheme.secondary,
                                    trackColor = MaterialTheme.colorScheme.surfaceContainer,
                                )
                        }

                        (currentMediaItem != null && currentMediaItem.status == MediaStatus.UNSET) -> {
                            SwipeableMediaWithIndicatorIcons(
                                currentMediaItem,
                                mainViewModel,
                                imageLoader,
                                anchoredDraggableState,
                            )
                        }

                        (uiState.mediaItems.isEmpty()) -> {// TODO("Check if this is the last round of photos then show this, instead of checking of scanning list is empty. Can add another UiState edited in [MainViewModel.getNewPhotos()]")
                            EndOfPhotosScreen(context, mainViewModel, view)
                        }

                        (!mainViewModel.seekToUnsetItemOrFalse()) -> { // If there are no unset photos in the list, ask the user to delete the photos selected
                            if (numToDelete > 0)
                                ReviewDeletedButton(
                                    numToDelete = numToDelete,
                                    skipReview = skipReview,
                                    navigateToReviewScreen = { navigateToReviewScreen() },
                                    deleteMedia = {
                                        mainViewModel.confirmDeletion()
                                    }
                                )
                            else // If there aren't any photos to delete, ask the user if they want to swipe more photos
                                Button(onClick = {
                                    checkPermissions(
                                        context = context,
                                        onPermissionsGranted = { mainViewModel.resetAndGetNewMediaItems() }
                                    )
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                }) {
                                    Text("Fetch more photos/videos")
                                }
                        }
                    }
                    // Column of floating actions & info drawer
                    AnimatedVisibility(
                        visible = uiState.showInfoAndFloatingActionsRow && currentMediaItem != null,
                        enter =
                            if (reduceAnimations) fadeIn()
                            else fadeIn() + slideInVertically(
                                animationSpec = spring(
                                    stiffness = Spring.StiffnessMediumLow,
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                ),
                                initialOffsetY = { it }
                            ),
                        exit =
                            if (reduceAnimations) fadeOut()
                            else fadeOut() + slideOutVertically(
                                animationSpec = spring(
                                    stiffness = Spring.StiffnessMediumLow,
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                ),
                                targetOffsetY = { it }
                            ),
                        modifier = Modifier
                            .fillMaxSize()
                            .align(Alignment.BottomCenter)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            FloatingActionsRow(
                                currentMedia = currentMediaItem,
                                viewModel = mainViewModel,
                                modifier = Modifier.widthIn(max = 640.dp)
                            )
                            AnimatedVisibility(
                                uiState.showInfo,
                                enter = if (reduceAnimations) fadeIn()
                                else fadeIn() + expandVertically(
                                    animationSpec = spring(
                                        stiffness = Spring.StiffnessMediumLow,
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                    ),
                                ),
                                exit = if (reduceAnimations) fadeOut()
                                else fadeOut() + shrinkVertically(
                                    animationSpec = spring(
                                        stiffness = Spring.StiffnessMediumLow,
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                    ),
                                ),
                            ) {
                                InfoRow(
                                    viewModel = mainViewModel,
                                    currentMedia = currentMediaItem,
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                                            MaterialTheme.shapes.medium
                                        )
                                        .widthIn(max = 640.dp)
                                )
                            }
                        }
                    }
                }
            },
            topBar = {},
            sheetContent = {
                ActionBar(
                    numToDelete = numToDelete,
                    mainViewModel,
                    navigateToReviewScreen = { navigateToReviewScreen() },
                    skipReview = skipReview,
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        with(density) {
                            animateActionBarHeightTo(coordinates.size.height.toDp().value)
                        }
                    }
                )
                TabbedSheetContent(
                    tabIndex = sheetContentTabIndex,
                    updateTabIndex = { sheetContentTabIndex = it },
                    mainViewModel = mainViewModel,
                    statsViewModel = statsViewModel,
                    expandBottomSheet = { mainViewModel.expandBottomSheet(it) },
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars),
                )
            },
            sheetPeekHeight = if (tutorialIndex > 3) actionBarHeight.value.dp + WindowInsets.navigationBars.getBottom(
                density
            ).dp * 3 / 4 else 0.dp,
            scaffoldState = mainViewModel.bottomSheetScaffoldState,
        )
        val tutorialVerticalAlignmentBias by animateFloatAsState(
            if (tutorialIndex < 6) -1f else 0.7f,
            animationSpec = mainViewModel.defaultEntryAnimationSpec
        )
        if (uiState.tutorialMode)
            TutorialCard(
                uiState.tutorialCardIconDrawableId,
                uiState.tutorialCardTitle,
                uiState.tutorialCardBody,
                onSkip = {
                    updateTutorialIndex(tutorialIndex + 1)
                    if (tutorialIndex + 1 > 0) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                },
                modifier = Modifier
                    .padding(dimensionResource(R.dimen.padding_medium))
                    .safeDrawingPadding()
                    .align(BiasAlignment(0f, tutorialVerticalAlignmentBias))
            )
    }
}

@Composable
fun TutorialCard(
    iconDrawableId: Int?,
    title: String,
    body: AnnotatedString,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f)
        ),
        modifier = modifier
    ) {
        Column(
            Modifier
                .padding(dimensionResource(R.dimen.padding_small))
                .animateContentSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimensionResource(R.dimen.padding_small))
            ) {
                if (iconDrawableId != null)
                    Icon(
                        painterResource(iconDrawableId),
                        contentDescription = null,
                        modifier = Modifier
                            .size(dimensionResource(R.dimen.medium_icon))
                    )
                Column(
                    Modifier.padding(
                        horizontal = dimensionResource(R.dimen.padding_small),
//                        vertical = dimensionResource(R.dimen.padding_xsmall)
                    )
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = dimensionResource(R.dimen.padding_xsmall))
                    )
                    Text(
                        body,
                        style = MaterialTheme.typography.bodyMedium,
//                        modifier = Modifier.padding(dimensionResource(R.dimen.padding_xsmall))
                    )
                }
            }
            TextButton(
                onClick = {
                    onSkip()
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    text = stringResource(R.string.skip),
//                        color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(end = dimensionResource(R.dimen.padding_xsmall))
                )
            }
        }
    }

}

@Composable
private fun EndOfPhotosScreen(
    context: Context,
    mainViewModel: MainViewModel,
    view: View,
) {
    val limitedPhotoAccess = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            && (checkSelfPermission(context, READ_MEDIA_VISUAL_USER_SELECTED) == PERMISSION_GRANTED)
            && (checkSelfPermission(context, READ_MEDIA_IMAGES) == PERMISSION_DENIED)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(dimensionResource(R.dimen.padding_medium))
    ) {
        Icon(
            painter = painterResource(R.drawable.check),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier
                .size(64.dp)
                .padding(bottom = dimensionResource(R.dimen.padding_medium))
        )
        Text(
            "You have swiped on all of your photos & videos, congrats! :D \uD83C\uDF89",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(bottom = dimensionResource(R.dimen.padding_small))
        )
        Button(onClick = {
            CoroutineScope(Dispatchers.IO).launch {
                checkPermissions(
                    context = context,
                    onPermissionsGranted = { mainViewModel.resetAndGetNewMediaItems() }
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }) {
            Text(if (limitedPhotoAccess) "Select more photos & videos" else "Scan again")
        }
    }
}

@Composable
private fun RequestPermissionsScreen(
    mainViewModel: MainViewModel,
    context: Context,
    view: View
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(dimensionResource(R.dimen.padding_medium))
    ) {
        Icon(
            painter = painterResource(R.drawable.x),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .size(64.dp)
                .padding(bottom = dimensionResource(R.dimen.padding_medium))
        )
        Text(
            stringResource(R.string.media_permission_request),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = dimensionResource(R.dimen.padding_small))
        )
        Row {
            Button(
                onClick = {
                    mainViewModel.navigateToAppSettingsForPermissions(context)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) view.performHapticFeedback(
                        HapticFeedbackConstants.CONFIRM
                    )
                },
                shape = MaterialTheme.shapes.extraLarge.copy(
                    topEnd = CornerSize(4.dp),
                    bottomEnd = CornerSize(4.dp)
                )
            ) {
                Text("Go to app settings")
            }
            Spacer(Modifier.width(2.dp))
            OutlinedButton(
                onClick = {
                    checkPermissions(
                        context,
                        onPermissionsGranted = {
                            CoroutineScope(Dispatchers.Main).launch {
                                mainViewModel.updatePermissionsGranted(true)
                                mainViewModel.resetAndGetNewMediaItems()
                            }
                        }
                    )
                },
                shape = MaterialTheme.shapes.extraLarge.copy(
                    topStart = CornerSize(4.dp),
                    bottomStart = CornerSize(4.dp)
                ),
            ) {
                Icon( // TODO("Add spinning animation when clicked")
                    painterResource(R.drawable.arrows_clockwise),
                    null,
                    modifier = Modifier.size(dimensionResource(R.dimen.small_icon))
                )
            }
        }
    }
}

private fun performDragHapticFeedback(
    position: DragAnchors,
    view: View,
    anchoredDraggableState: AnchoredDraggableState<DragAnchors>
) {
    when (position) {
        DragAnchors.Left -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                view.performHapticFeedback(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE)
        }

        DragAnchors.Center -> {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                && anchoredDraggableState.settledValue == DragAnchors.Center
            )
                view.performHapticFeedback(HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE)
        }

        DragAnchors.Right -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                view.performHapticFeedback(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE)
        }
    }
}