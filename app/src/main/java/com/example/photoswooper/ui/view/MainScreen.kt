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
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.checkSelfPermission
import coil3.ImageLoader
import com.example.photoswooper.R
import com.example.photoswooper.checkPermissions
import com.example.photoswooper.data.BooleanPreference
import com.example.photoswooper.data.IntPreference
import com.example.photoswooper.data.models.MediaStatus
import com.example.photoswooper.dataStore
import com.example.photoswooper.ui.components.ActionBar
import com.example.photoswooper.ui.components.FilterDialog
import com.example.photoswooper.ui.components.FloatingActionsRow
import com.example.photoswooper.ui.components.InfoRow
import com.example.photoswooper.ui.components.ReviewDeletedButton
import com.example.photoswooper.ui.components.SwipeableMediaWithIndicatorIcons
import com.example.photoswooper.experimental.data.SwipeableItem
import com.example.photoswooper.experimental.ui.DocumentFloatingActionsRow
import com.example.photoswooper.experimental.ui.DocumentInfoRow
import com.example.photoswooper.ui.components.tiny.AnimatedExpandCollapseIcon
import com.example.photoswooper.ui.viewmodels.FilterDialogViewModel
import com.example.photoswooper.ui.viewmodels.MainViewModel
import com.example.photoswooper.ui.viewmodels.StatsViewModel
import com.example.photoswooper.ui.viewmodels.TutorialController
import com.example.photoswooper.ui.viewmodels.defaultEntryAnimationSpec
import com.example.photoswooper.utils.DataStoreInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    documentSwipeViewModel: com.example.photoswooper.experimental.viewmodel.DocumentSwipeViewModel,
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

    val tutorialController = remember { TutorialController(dataStoreInterface) }
    val tutorialUiState by tutorialController.uiState.collectAsState()
    val tutorialIndex by dataStoreInterface.getIntSettingValue(IntPreference.TUTORIAL_INDEX.setting)
        .collectAsState(1)

    val uiState by mainViewModel.uiState.collectAsState()
    val docUiState by documentSwipeViewModel.uiState.collectAsState()
    val numToDelete = if (docUiState.isSwipeMode)
        documentSwipeViewModel.getDocumentsToDelete().size
    else
        uiState.mediaItems.count { it.status == MediaStatus.DELETE }
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

    /* LaunchedEffect to make tutorial interactive */
    if (uiState.tutorialMode) {
        val tertiaryColor = MaterialTheme.colorScheme.tertiary
        LaunchedEffect(tutorialIndex) {
            tutorialController.awaitUserInteractionAndControlTutorial(
                tutorialIndex,
                mainViewModel,
                context,
                currentMediaItem,
                tertiaryColor,
                coroutineScope,
                { sheetContentTabIndex = it },
                statsViewModel
            )
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

    // Show warning before exiting the app by system back gesture/button
    var backToExitWarningEnabled by remember { mutableStateOf(true) }
    BackHandler(enabled = backToExitWarningEnabled) {
        backToExitWarningEnabled = false
        Toast.makeText(
            context,
            R.string.press_back_again_to_exit,
            Toast.LENGTH_SHORT
        ).show()
        // Enable warning again after 3 seconds
        CoroutineScope(Dispatchers.Main).launch {
            delay(3000)
            backToExitWarningEnabled = true
        }
    }

    // Back handler to exit document swipe mode
    BackHandler(enabled = docUiState.isSwipeMode) {
        documentSwipeViewModel.exitSwipeMode()
    }

    Box(Modifier.fillMaxSize()) {
        BottomSheetScaffold(
            content = { paddingValues ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize()
                        .then(
                            if (tutorialIndex < 4)
                                Modifier.navigationBarsPadding()
                            else Modifier
                        )
                ) {
                    when {
                        /* When document swiping mode is active */
                        docUiState.isSwipeMode -> {
                            val currentDoc = documentSwipeViewModel.getCurrentDocument()
                            when {
                                docUiState.fetchingDocuments -> {
                                    CircularProgressIndicator()
                                }
                                currentDoc != null && currentDoc.status == MediaStatus.UNSET -> {
                                    SwipeableMediaWithIndicatorIcons(
                                        item = SwipeableItem.DocumentItem(currentDoc),
                                        controller = documentSwipeViewModel,
                                        imageLoader = imageLoader,
                                        isReady = docUiState.documentReady,
                                        docRenderMethod = docUiState.docRenderMethod,
                                    )
                                }
                                docUiState.documents.isEmpty() -> {
                                    // No swipes available (no documents found or all swiped)
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_folder_match),
                                            contentDescription = null,
                                            modifier = Modifier.size(64.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "No swipes available",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 8.dp)
                                        )
                                    }
                                }
                                else -> {
                                    // All documents swiped
                                    val docDeleteCount = documentSwipeViewModel.getDocumentsToDelete().size
                                    if (docDeleteCount > 0) {
                                        ReviewDeletedButton(
                                            numToDelete = docDeleteCount,
                                            skipReview = skipReview,
                                            navigateToReviewScreen = { navigateToReviewScreen() },
                                            deleteMedia = {
                                                documentSwipeViewModel.deleteMarkedDocuments()
                                            }
                                        )
                                    } else {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_folder_match),
                                                contentDescription = null,
                                                modifier = Modifier.size(64.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "No swipes available",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
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
                                CircularProgressIndicator()
                        }

                        (currentMediaItem != null && currentMediaItem.status == MediaStatus.UNSET) -> {
                            SwipeableMediaWithIndicatorIcons(
                                item = SwipeableItem.MediaItem(currentMediaItem),
                                controller = mainViewModel,
                                imageLoader = imageLoader,
                                isReady = uiState.mediaReady,
                                mediaAspectRatio = uiState.mediaAspectRatio,
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
                                        mainViewModel.deleteMarkedMedia()
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
                        visible = uiState.showInfoAndFloatingActionsRow && currentMediaItem != null && !docUiState.isSwipeMode,
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
                    // Document floating actions & info row
                    val docShowFloatingActions by documentSwipeViewModel.showFloatingActions.collectAsState()
                    val docShowInfo by documentSwipeViewModel.showDocumentInfo.collectAsState()
                    val currentDoc = if (docUiState.isSwipeMode) documentSwipeViewModel.getCurrentDocument() else null
                    AnimatedVisibility(
                        visible = docShowFloatingActions && currentDoc != null && docUiState.isSwipeMode,
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
                            DocumentFloatingActionsRow(
                                viewModel = documentSwipeViewModel,
                                currentDocumentType = currentDoc?.documentType,
                                modifier = Modifier.widthIn(max = 640.dp)
                            )
                            AnimatedVisibility(
                                docShowInfo,
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
                                DocumentInfoRow(
                                    document = currentDoc,
                                    modifier = Modifier.widthIn(max = 640.dp)
                                )
                            }
                        }
                    }
                }
            },
            sheetContent = {
                ActionBar(
                    numToDelete = numToDelete,
                    mainViewModel,
                    navigateToReviewScreen = { navigateToReviewScreen() },
                    skipReview = skipReview,
                    deleteMedia = {
                        if (docUiState.isSwipeMode) documentSwipeViewModel.deleteMarkedDocuments()
                        else mainViewModel.deleteMarkedMedia()
                    },
                    onUndo = {
                        if (docUiState.isSwipeMode) {
                            documentSwipeViewModel.undoLastSwipe()
                            true
                        } else {
                            mainViewModel.undo()
                        }
                    },
                    experimentalSpaceSaved = docUiState.spaceSaved,
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
                    documentSwipeViewModel = documentSwipeViewModel,
                    imageLoader = imageLoader,
                    expandBottomSheet = { mainViewModel.expandBottomSheet(it) },
                    collapseBottomSheet = {
                        coroutineScope.launch {
                            mainViewModel.bottomSheetScaffoldState.bottomSheetState.partialExpand()
                        }
                    },
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
            animationSpec = defaultEntryAnimationSpec
        )
        if (uiState.tutorialMode)
            TutorialCard(
                tutorialUiState.tutorialCardIconDrawableId,
                tutorialUiState.tutorialCardTitle,
                tutorialUiState.tutorialCardBody,
                tutorialIndex,
                onSkip = {
                    tutorialController.updateTutorialIndex(tutorialIndex + 1)
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
    tutorialIndex: Int,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    var bodyTextVisible by rememberSaveable { mutableStateOf(true) }
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
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium
                        )
                        AnimatedExpandCollapseIcon(
                            expanded = if (tutorialIndex >= 6) !bodyTextVisible else bodyTextVisible,
                            onClick = { bodyTextVisible = !bodyTextVisible },
                            contentDescription = "tutorial text"
                        )
                    }
                    AnimatedVisibility(bodyTextVisible){
                        Text(
                            body,
                            style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = dimensionResource(R.dimen.padding_xsmall))
                        )
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimensionResource(R.dimen.padding_small))
            ) {
                Text(
                    text = "Swipe actions are temporary for the tutorial",
                    style = MaterialTheme.typography.labelSmall,
                )
                TextButton(onClick = { onSkip() }) { Text(stringResource(R.string.skip)) }
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
            modifier = Modifier
                .size(64.dp)
                .padding(bottom = dimensionResource(R.dimen.padding_medium))
        )
        Text(
            "You have swiped on all of your photos & videos, congrats! :D \uD83C\uDF89",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
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
                Text(stringResource(R.string.go_to_permission_settings))
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