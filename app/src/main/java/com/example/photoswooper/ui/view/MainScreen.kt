package com.example.photoswooper.ui.view

import android.Manifest.permission.READ_MEDIA_IMAGES
import android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.content.res.Resources
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.checkSelfPermission
import coil3.ImageLoader
import com.example.photoswooper.R
import com.example.photoswooper.checkPermissionsAndGetPhotos
import com.example.photoswooper.data.models.PhotoStatus
import com.example.photoswooper.data.uistates.BooleanPreference
import com.example.photoswooper.dataStore
import com.example.photoswooper.ui.components.ActionBar
import com.example.photoswooper.ui.components.InfoRow
import com.example.photoswooper.ui.components.ReviewDeletedButton
import com.example.photoswooper.ui.components.ReviewDialog
import com.example.photoswooper.ui.components.SwipeableAsyncImageWithIndicatorIcons
import com.example.photoswooper.ui.viewmodels.MainViewModel
import com.example.photoswooper.ui.viewmodels.StatsViewModel
import com.example.photoswooper.utils.DataStoreInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class DragAnchors {
    Left,
    Center,
    Right,
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
        .getBooleanSettingValue(BooleanPreference.reduce_animations.toString()).collectAsState(false)
    val limitedPhotoAccess = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            && (checkSelfPermission(context, READ_MEDIA_VISUAL_USER_SELECTED) == PERMISSION_GRANTED)
            && (checkSelfPermission(context, READ_MEDIA_IMAGES) == PERMISSION_DENIED)


    val uiState by mainViewModel.uiState.collectAsState()
    val numToDelete = uiState.photos.count { it.status == PhotoStatus.DELETE }
    val currentPhoto =
        try {
            uiState.photos[uiState.currentPhotoIndex]
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

    /* For anchored draggable (photo swiping left/right) */

    val anchoredDraggableState = remember {
        AnchoredDraggableState(
            initialValue = DragAnchors.Center,
            anchors = DraggableAnchors {
                DragAnchors.Left at (-Resources.getSystem().displayMetrics.widthPixels.toFloat() / 2)
                DragAnchors.Center at 0f
                DragAnchors.Right at (Resources.getSystem().displayMetrics.widthPixels.toFloat() / 2)
            },
        )
    }

    /* When user drags to one of the anchors, without releasing yet */
    LaunchedEffect(anchoredDraggableState) {
        snapshotFlow { anchoredDraggableState.targetValue }
            .collectLatest { position ->
                when (position) {
                    DragAnchors.Left -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE)
                    }

                    DragAnchors.Center -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && anchoredDraggableState.settledValue == DragAnchors.Center)
                            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE)
                    }

                    DragAnchors.Right -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE)
                    }
                }
            }
    }

    /* When user releases drag motion */
    LaunchedEffect(anchoredDraggableState) {
        snapshotFlow { anchoredDraggableState.settledValue }
            .collectLatest { position ->
                when (position) {
                    DragAnchors.Left -> {
                        mainViewModel.markPhoto(PhotoStatus.DELETE)
                        mainViewModel.nextPhoto()
                        anchoredDraggableState.animateTo(
                            DragAnchors.Center,
                            animationSpec = spring(stiffness = 0f)
                        )
                    }
                    DragAnchors.Right -> {
                        mainViewModel.markPhoto(PhotoStatus.KEEP)
                        mainViewModel.nextPhoto()
                        anchoredDraggableState.animateTo(
                            DragAnchors.Center,
                            animationSpec = spring(stiffness = 0f)
                        )
                    }

                    else -> { /* Maybe add a markPhotoUnset() function if necessary? */
                        anchoredDraggableState.animateTo(
                            DragAnchors.Center,
                            animationSpec = spring(stiffness = 0f)
                        )
                    }
                }
            }
    }

    if (uiState.showReviewDialog) {
        ReviewDialog(
            photosToDelete = mainViewModel.getPhotosToDelete(),
            onDismissRequest = { mainViewModel.dismissReviewDialog() },
            onCancellation = {
                for (photo in mainViewModel.getPhotosToDelete()) {
                    mainViewModel.markPhoto(PhotoStatus.UNSET, uiState.photos.indexOf(photo))
                }
            },
            onUnsetPhoto = { mainViewModel.markPhoto(PhotoStatus.UNSET, uiState.photos.indexOf(it)) },
            onConfirmation = { CoroutineScope(Dispatchers.Main).launch { mainViewModel.deletePhotos() } },
            onDisableReviewDialog = { mainViewModel.disableReviewDialog() },
        )
    }

    BottomSheetScaffold(
        content = { paddingValues ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
            ) {
                when {
                    /* When loading new photos */
                    (uiState.isLoading) -> {
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

                    (currentPhoto != null && currentPhoto.status == PhotoStatus.UNSET) -> {
                        SwipeableAsyncImageWithIndicatorIcons(
                            currentPhoto,
                            mainViewModel,
                            imageLoader,
                            anchoredDraggableState,
                        )
                    }

                    (uiState.photos.isEmpty()) -> {// TODO("Check if this is the last round of photos then show this, instead of checking of scanning list is empty. Can add another UiState edited in [MainViewModel.getNewPhotos()]")
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
                                "You have swiped on all of your photos, congrats! :D \uD83C\uDF89",
                                style = MaterialTheme.typography.titleLarge,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(bottom = dimensionResource(R.dimen.padding_small))
                            )
                                Button(onClick = {
                                    checkPermissionsAndGetPhotos(
                                        context = context,
                                        onPermissionsGranted = { mainViewModel.getNewPhotos() }
                                    )
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                }) {
                                    Text(if (limitedPhotoAccess) "Select more photos" else "Scan again")
                                }
                        }
                    }

                    (!mainViewModel.seekToUnsetPhotoOrFalse()) -> { // If there are no unset photos in the list, ask the user to delete the photos selected
                        if (numToDelete > 0)
                            ReviewDeletedButton(view, mainViewModel, numToDelete, uiState.reviewDialogEnabled)
                        else // If there aren't any photos to delete, ask the user if they want to swipe more photos
                            Button(onClick = {
                                checkPermissionsAndGetPhotos(
                                    context = context,
                                    onPermissionsGranted = { mainViewModel.getNewPhotos() }
                                )
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            }) {
                                Text("Fetch more photos")
                            }
                    }
                }
                AnimatedVisibility(
                    visible = uiState.showInfo && currentPhoto != null,
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
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    InfoRow(
                        mainViewModel,
                        currentPhoto,
                        Modifier
                            .background(
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
                                MaterialTheme.shapes.medium
                            )
                            .widthIn(max = 380.dp)
                    )
                }
            }
        },
        sheetContent = {
            ActionBar(
                currentPhoto = currentPhoto,
                numToDelete = numToDelete,
                uiState,
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
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
            )
        },
        sheetPeekHeight = animatedActionBarHeight.value + WindowInsets.navigationBars.getBottom(density).dp,
    )
}