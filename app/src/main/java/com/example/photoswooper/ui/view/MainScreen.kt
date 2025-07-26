package com.example.photoswooper.ui.view

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import com.example.photoswooper.R
import com.example.photoswooper.checkPermissionsAndGetPhotos
import com.example.photoswooper.data.database.MediaStatusDatabase
import com.example.photoswooper.data.models.PhotoStatus
import com.example.photoswooper.ui.components.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class DragAnchors {
    Left,
    Center,
    Right,
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
fun MainScreen(
    viewModel: MainViewModel,
    imageLoader: ImageLoader
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current

    val uiState by viewModel.uiState.collectAsState()
    val numToDelete = uiState.photos.count { it.status == PhotoStatus.DELETE }
    val currentPhoto =
        try {
            uiState.photos[uiState.currentPhotoIndex]
        } catch (_: IndexOutOfBoundsException) {
            null
        }

    /* For anchored draggable (photo swiping left/right) */
    val decayAnimationSpec = rememberSplineBasedDecay<Float>()
    val anchoredDraggableState = remember {
        AnchoredDraggableState(
            initialValue = DragAnchors.Center,
            positionalThreshold = { distance: Float -> distance * 0.5f },
            velocityThreshold = { with(density) { 100.dp.toPx() } },
            anchors = DraggableAnchors {
                DragAnchors.Left at with(density) { -200.dp.toPx() }
                DragAnchors.Center at 0f
                DragAnchors.Right at with(density) { 200.dp.toPx() }
            },
            snapAnimationSpec = tween(),
            decayAnimationSpec = decayAnimationSpec
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
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
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
                        viewModel.markPhoto(PhotoStatus.DELETE)
                        viewModel.nextPhoto()
                        anchoredDraggableState.animateTo(DragAnchors.Center)
                    }

                    DragAnchors.Right -> {
                        viewModel.markPhoto(PhotoStatus.KEEP)
                        viewModel.nextPhoto()
                        anchoredDraggableState.animateTo(DragAnchors.Center)
                    }

                    else -> { /* Maybe add a markPhotoUnset() function if necessary? */
                    }
                }
            }
    }

    if (uiState.showReviewDialog == true) {
        ReviewDialog(
            photosToDelete = viewModel.getPhotosToDelete(),
            onDismissRequest = { viewModel.dismissReviewDialog() },
            onCancellation = {
                for (photo in viewModel.getPhotosToDelete()) {
                    viewModel.markPhoto(PhotoStatus.UNSET, uiState.photos.indexOf(photo))
                }
            },
            onUnsetPhoto = { viewModel.markPhoto(PhotoStatus.UNSET, uiState.photos.indexOf(it)) },
            onConfirmation = { CoroutineScope(Dispatchers.Main).launch { viewModel.deletePhotos() } },
            onDisableReviewDialog = { viewModel.disableReviewDialog() },
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
                if (uiState.numUnset > 0) // First check if there are unset photos in the list
                {
                    if (currentPhoto?.status == PhotoStatus.UNSET) // Then check if the current photo is unset
                        AsyncImage(
                            model = currentPhoto.uri,
                            imageLoader = imageLoader,
                            contentDescription = null,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxSize()
                                .anchoredDraggable(
                                    state = anchoredDraggableState,
                                    orientation = Orientation.Horizontal
                                )
                                .offset {
                                    IntOffset(
                                        x = anchoredDraggableState.requireOffset().roundToInt(),
                                        y = 0
                                    )
                                }
                        )
                    else
                        viewModel.findUnsetPhoto()
                } // if the current photo is not unset, find the next one in the list
                else { // If there are no unset photos in the list, ask the user to delete the photos selected
                    if (numToDelete > 0)
                        ReviewDeletedButton(view, viewModel, numToDelete, uiState.reviewDialogEnabled)
                    else // If there aren't any photos to delete, ask the user if they want to swipe more photos
                        Button(onClick = {
                            checkPermissionsAndGetPhotos(
                                context = context,
                                onPermissionsGranted = { viewModel.getPhotos() }
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        }) {
                            Text("Fetch more photos")
                        }
                }
                Column(
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxSize()
                        .padding(dimensionResource(R.dimen.padding_medium))
                ) {

                    AnimatedVisibility(
                        visible = uiState.showInfo && currentPhoto != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        InfoRow(
                            viewModel,
                            currentPhoto,
                            Modifier
                                .padding(horizontal = dimensionResource(R.dimen.padding_medium))
                                .background(
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
                                    MaterialTheme.shapes.medium
                                )
                        )
                    }
                }
            }
        },
        sheetContent = { // TODO("Fix offset from bottom")
            ActionBar(
                currentPhoto = currentPhoto,
                numToDelete = numToDelete,
                uiState,
                viewModel,
            )
            val statsViewModel = StatsViewModel(
                mediaStatusDao = MediaStatusDatabase.getDatabase(context.applicationContext).mediaStatusDao() // TODO("Could cause issues as not same dao variable as in MainActivity?")
            )
            TabbedPreferencesAndStatsPage(
                modifier = Modifier,
                statsViewModel = statsViewModel
            )
        },
        sheetPeekHeight = 144.dp
    )
}