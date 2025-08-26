package com.example.photoswooper.ui.view

import android.Manifest.permission.READ_MEDIA_IMAGES
import android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.content.res.Resources
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.checkSelfPermission
import coil3.ImageLoader
import com.example.photoswooper.R
import com.example.photoswooper.checkPermissions
import com.example.photoswooper.data.models.MediaStatus
import com.example.photoswooper.data.uistates.BooleanPreference
import com.example.photoswooper.dataStore
import com.example.photoswooper.ui.components.ActionBar
import com.example.photoswooper.ui.components.FilterDialog
import com.example.photoswooper.ui.components.FloatingActionsRow
import com.example.photoswooper.ui.components.InfoRow
import com.example.photoswooper.ui.components.ReviewDeletedButton
import com.example.photoswooper.ui.components.ReviewDialog
import com.example.photoswooper.ui.components.SwipeableMediaWithIndicatorIcons
import com.example.photoswooper.ui.viewmodels.FilterDialogViewModel
import com.example.photoswooper.ui.viewmodels.MainViewModel
import com.example.photoswooper.ui.viewmodels.StatsViewModel
import com.example.photoswooper.utils.DataStoreInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class DragAnchors(val offset: Float) {
    Left(-Resources.getSystem().displayMetrics.widthPixels.toFloat() / 2),
    Center(0f),
    Right(Resources.getSystem().displayMetrics.widthPixels.toFloat() / 2)
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalAnimationApi::class)
fun MainScreen(
    statsViewModel: StatsViewModel,
    mainViewModel: MainViewModel,
    imageLoader: ImageLoader
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current

    val reduceAnimations = DataStoreInterface(context.dataStore)
        .getBooleanSettingValue(BooleanPreference.REDUCE_ANIMATIONS.setting).collectAsState(false)
    val limitedPhotoAccess = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            && (checkSelfPermission(context, READ_MEDIA_VISUAL_USER_SELECTED) == PERMISSION_GRANTED)
            && (checkSelfPermission(context, READ_MEDIA_IMAGES) == PERMISSION_DENIED)


    val uiState by mainViewModel.uiState.collectAsState()
    val numToDelete = uiState.mediaItems.count { it.status == MediaStatus.DELETE }
    val currentMediaItem =
        try {
            uiState.mediaItems[uiState.currentIndex]
        } catch (_: IndexOutOfBoundsException) {
            null
        }
    var actionBarHeight by remember { mutableStateOf(0.dp) }
    val animatedActionBarHeight = animateDpAsState(
        actionBarHeight,
        spring(
            stiffness = if (reduceAnimations.value) 0f else Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioLowBouncy,
        )
    )

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

    if (uiState.showReviewDialog) {
        ReviewDialog(
            mediaItemsToDelete = mainViewModel.getMediaToDelete(),
            onDismissRequest = { mainViewModel.dismissReviewDialog() },
            onCancellation = {
                for (photo in mainViewModel.getMediaToDelete()) {
                    mainViewModel.markItem(MediaStatus.UNSET, uiState.mediaItems.indexOf(photo))
                }
            },
            onUnsetMediaItem = { mainViewModel.markItem(MediaStatus.UNSET, uiState.mediaItems.indexOf(it)) },
            onConfirmation = { CoroutineScope(Dispatchers.Main).launch { mainViewModel.confirmDeletion() } },
            onDisableReviewDialog = { mainViewModel.disableReviewDialog() },
        )
    }
    else if (uiState.showFilterDialog)
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

    BottomSheetScaffold(
        content = { paddingValues ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
            ) {
                when {
                    /* When permissions not granted */
                    (uiState.permissionsGranted == false) -> {
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
                                        modifier = Modifier.size(dimensionResource(R.dimen.small_icon)))
                                }
                            }}
                    }
                    /* When loading new photos */
                    (uiState.fetchingMedia) -> {
                        if (reduceAnimations.value) Text(
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

                    (!mainViewModel.seekToUnsetItemOrFalse()) -> { // If there are no unset photos in the list, ask the user to delete the photos selected
                        if (numToDelete > 0)
                            ReviewDeletedButton(view, mainViewModel, numToDelete, uiState.reviewDialogEnabled)
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
                /* Column of floating actions & info drawer */
                AnimatedVisibility(
                    visible = uiState.showInfoAndFloatingActionsRow && currentMediaItem != null,
                    enter =
                        if (reduceAnimations.value) fadeIn()
                        else slideInVertically(
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMediumLow,
                                dampingRatio = Spring.DampingRatioLowBouncy,
                            ),
                            initialOffsetY = { it }
                        ),
                    exit =
                        if (reduceAnimations.value) fadeOut()
                        else slideOutVertically(
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
                        )
                        AnimatedVisibility(
                            uiState.showInfo,
                            enter = if (reduceAnimations.value) fadeIn()
                                    else fadeIn() + expandVertically(
                                animationSpec = spring(
                                    stiffness = Spring.StiffnessMediumLow,
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                ),
                            ),
                            exit = if (reduceAnimations.value) fadeOut()
                            else fadeOut() +  shrinkVertically(
                                animationSpec = spring(
                                    stiffness = Spring.StiffnessMediumLow,
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                ),
                            ),
                        ){
                            InfoRow(
                                viewModel = mainViewModel,
                                currentMedia = currentMediaItem,
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                                        MaterialTheme.shapes.medium
                                    )
                                    .sizeIn(maxWidth = 380.dp)
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
                modifier = Modifier.onGloballyPositioned { coordinates ->
                    with (density) {
                        actionBarHeight = coordinates.size.height.toDp()
                    }
                }
            )
            TabbedPreferencesAndStatsPage(
                statsViewModel = statsViewModel,
                numPhotosUnset = uiState.numUnset,
                expandBottomSheet = { mainViewModel.expandBottomSheet(it) },
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
            )
        },
        sheetPeekHeight = animatedActionBarHeight.value + WindowInsets.navigationBars.getBottom(density).dp *3/4,
        scaffoldState = mainViewModel.bottomSheetScaffoldState,
    )
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