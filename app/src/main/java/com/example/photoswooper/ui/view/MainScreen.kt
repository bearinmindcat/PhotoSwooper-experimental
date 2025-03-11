package com.example.photoswooper.ui.view

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.tween
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.photoswooper.R
import com.example.photoswooper.data.models.PhotoStatus
import com.example.photoswooper.ui.components.ReviewDialog
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

enum class DragAnchors {
    Left,
    Center,
    Right,
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val numToDelete = uiState.photos.count { it.status == PhotoStatus.DELETE }

    val blurState = remember { HazeState() } // For bottom bar
    val density = LocalDensity.current
    val decayAnimationSpec = rememberSplineBasedDecay<Float>()
//    val anchors = remember { DraggableAnchors {  } }
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
//    SideEffect {
//        state.updateAnchors(Dragg)
//    }
    val view = LocalView.current

    /* LaunchedEffect for the functions to be called when the photo is dragged to each anchor */
    LaunchedEffect(anchoredDraggableState) {
        snapshotFlow { anchoredDraggableState.settledValue }
            .collectLatest { position ->
                when (position) {
//                    delay(300)
                    DragAnchors.Left -> {
                        viewModel.markPhoto(PhotoStatus.DELETE)
                        viewModel.nextPhoto()
                        anchoredDraggableState.animateTo(DragAnchors.Center)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
                    }
                    DragAnchors.Right -> {
                        viewModel.markPhoto(PhotoStatus.KEEP)
                        viewModel.nextPhoto()
                        anchoredDraggableState.animateTo(DragAnchors.Center)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
                    }
                    else -> { /* Maybe add a markPhotoUnset() function if necessary? */ }
                }
            }
    }

    if(uiState.showReviewDialog == true) {
        ReviewDialog(
            photosToDelete = viewModel.getPhotosToDelete(),
            onDismissRequest = { viewModel.dismissReviewDialog() },
            onCancellation = { viewModel.getPhotos() },
            onUnsetPhoto = { viewModel.markPhoto(PhotoStatus.UNSET, uiState.photos.indexOf(it)) },
            onConfirmation = { viewModel.deletePhotos() },
            contentResolverInterface = viewModel.contentResolverInterface
        )
    }

    Scaffold {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            if (uiState.numUnset > 0) // First check if there are unset photos in the list
                if (uiState.photos[uiState.currentPhotoIndex].status == PhotoStatus.UNSET) // Then check if the current photo is unset
                    Image(
                        bitmap = viewModel.getPhotoBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(dimensionResource(R.dimen.padding_medium))
                            .haze(
                                blurState,
                                backgroundColor = MaterialTheme.colorScheme.background,
                                tint = Color.Black.copy(alpha = .2f),
                                blurRadius = 30.dp,
                            )
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
                    viewModel.findUnsetPhoto() // if the current photo is not unset, find the next one in the list
            else { // If there are no unset photos in the list, ask the user to delete the photos selected
                if (numToDelete > 0)
                    ReviewDeletedButton(view, viewModel, numToDelete)
                else // If there aren't any photos to delete, ask the user if they want to swipe more photos
                    Button(onClick = {
                        viewModel.getPhotos()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    }) {
                        Text("Fetch more photos")
                    }
            }
            Column(
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(dimensionResource(R.dimen.padding_medium))
            ) {
                /* Bottom blurred-background bar */
                Row(
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(dimensionResource(R.dimen.padding_medium))
                        .hazeChild(state = blurState, shape = MaterialTheme.shapes.large)
                ){
                    /* Undo button */
                    FilledIconButton(
                        onClick = {
                            viewModel.undo()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            }
                        },
                        modifier = Modifier.padding(
                            horizontal = dimensionResource(R.dimen.padding_small),
                            vertical = dimensionResource(R.dimen.padding_medium)
                        )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.undo),
                            contentDescription = "Undo deletion",
                            modifier = Modifier.padding(dimensionResource(R.dimen.padding_small))
                        )
                    }
                    /* Review deleted photos button */
                    ReviewDeletedButton(view, viewModel, numToDelete)
                    /* Info button */
                    FilledTonalIconButton(
                        onClick = {
                        /* TODO: info button*/
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            }
                        },
                        modifier = Modifier.padding(
                            horizontal = dimensionResource(R.dimen.padding_small),
                            vertical = dimensionResource(R.dimen.padding_medium)
                        )                        ) {
                        Icon(
                            painter = painterResource(R.drawable.info_bold),
                            contentDescription = "Show more image information",
                            modifier = Modifier.padding(dimensionResource(R.dimen.padding_small))
                        )
                    }
                }
            }
//            else
//                Column {
//                    Text("BeepBoop no photos")
//                    Button(onClick = { viewModel.getPhotos(context.contentResolver) }) {
//                        Text("Get more photos!")
//                    }
//                }
        }
    }
}

@Composable
fun ReviewDeletedButton(view: View, viewModel: MainViewModel, numToDelete: Int) {
    ElevatedButton(
        onClick = {
            if(numToDelete > 0) viewModel.showReviewDialog()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            }
        },
        modifier = Modifier.padding(
            horizontal = dimensionResource(R.dimen.padding_small),
            vertical = dimensionResource(R.dimen.padding_medium)
        )
//                                .height(92.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.check_bold),
                contentDescription = null,
                modifier = Modifier.padding(dimensionResource(R.dimen.padding_small))
            )
            Text(
                text = "Delete ${numToDelete} photos",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(dimensionResource(R.dimen.padding_small))
            )
        }
    }
}