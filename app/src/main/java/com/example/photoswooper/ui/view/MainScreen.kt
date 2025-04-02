package com.example.photoswooper.ui.view

import android.os.Build
import android.text.format.Formatter.formatShortFileSize
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.photoswooper.R
import com.example.photoswooper.checkPermissionsAndGetPhotos
import com.example.photoswooper.data.models.Photo
import com.example.photoswooper.data.models.PhotoStatus
import com.example.photoswooper.ui.components.ReviewDialog
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
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
    imageLoader: coil3.ImageLoader
) {
    val context = LocalContext.current
    val view = LocalView.current

    val uiState by viewModel.uiState.collectAsState()
    val numToDelete = uiState.photos.count { it.status == PhotoStatus.DELETE }
    val currentPhoto =
        try { uiState.photos[uiState.currentPhotoIndex] }
        catch (_: IndexOutOfBoundsException) { null }

    val density = LocalDensity.current
    val blurState = remember { HazeState() } // For bottom bar
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
        snapshotFlow { anchoredDraggableState.currentValue }
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
                    else -> { /* Maybe add a markPhotoUnset() function if necessary? */ }
                }
            }
    }

    if(uiState.showReviewDialog == true) {
        ReviewDialog(
            photosToDelete = viewModel.getPhotosToDelete(),
            onDismissRequest = { viewModel.dismissReviewDialog() },
            onCancellation = { CoroutineScope(Dispatchers.Main).launch { viewModel.getPhotos() } },
            onUnsetPhoto = { viewModel.markPhoto(PhotoStatus.UNSET, uiState.photos.indexOf(it)) },
            onConfirmation = { CoroutineScope(Dispatchers.Main).launch { viewModel.deletePhotos() } },
            onDisableReviewDialog = { viewModel.disableReviewDialog() },
        )
    }

    Scaffold { paddingValues ->
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
                    .fillMaxSize()
                    .padding(dimensionResource(R.dimen.padding_medium))
            ) {
                /* Space saved text row */
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .wrapContentSize()
                        .clickable(onClickLabel = "Click to change time frame") {
                            CoroutineScope(Dispatchers.Main).launch {
                                viewModel.cycleStatsTimeFrame()
                            }
                        }
                        .padding(dimensionResource(R.dimen.padding_small))
                        .clip(MaterialTheme.shapes.medium)
                        .hazeChild(
                            state = blurState,
                            shape = MaterialTheme.shapes.medium
                        )
                ) {
                    val statsTextStyle = MaterialTheme.typography.bodyLarge
                    Text(
                        text = "Space saved this ",
                        style = statsTextStyle,
                        modifier = Modifier
                            .padding(
                                start = dimensionResource(R.dimen.padding_small),
                                top = dimensionResource(R.dimen.padding_small),
                                bottom = dimensionResource(R.dimen.padding_small)
                            )
                    )
                    Icon(
                        painter = painterResource(R.drawable.shuffle),
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                    )
                    Text(
                        text = buildAnnotatedString {
                            append(" ")
                            pushStyle(SpanStyle(textDecoration = TextDecoration.Underline))
                            append(uiState.currentStatsTimeFrame.name.lowercase())
                            pop()
                            append(": ${formatShortFileSize(context, uiState.spaceSavedInTimeFrame)}")
                        },
                        style = statsTextStyle,
                        modifier = Modifier
                            .padding(
                                end = dimensionResource(R.dimen.padding_small),
                                top = dimensionResource(R.dimen.padding_small),
                                bottom = dimensionResource(R.dimen.padding_small)
                            )
                    )
                }
                Column {
                    AnimatedVisibility(
                        visible = uiState.showInfo && currentPhoto != null,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        InfoRow(viewModel, currentPhoto)
                    }
                    /* Bottom blurred-background bar */
                    val barTopCornerSize = if (uiState.showInfo) CornerSize(0) else MaterialTheme.shapes.medium.topEnd
                    Row(
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dimensionResource(R.dimen.padding_medium))
                            .hazeChild(
                                state = blurState,
                                shape = MaterialTheme.shapes.medium.copy(
                                    topEnd = barTopCornerSize,
                                    topStart = barTopCornerSize
                                )
                            )
                    ) {
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
                        ReviewDeletedButton(view, viewModel, numToDelete, uiState.reviewDialogEnabled)
                        /* Info button */
                        FilledTonalIconButton(
                            onClick = {
                                viewModel.toggleInfo()
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
                                painter = painterResource(R.drawable.info_bold),
                                contentDescription = "Show more image information",
                                modifier = Modifier.padding(dimensionResource(R.dimen.padding_small))
                            )
                        }
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
fun ReviewDeletedButton(view: View, viewModel: MainViewModel, numToDelete: Int, reviewDialogEnabled: Boolean) {
    ElevatedButton(
        onClick = {
            if(numToDelete > 0) {
                if (reviewDialogEnabled)
                    viewModel.showReviewDialog()
                else
                    CoroutineScope(Dispatchers.Main).launch {
                        viewModel.deletePhotos()
                    }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
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
                text = "Delete $numToDelete photos",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(dimensionResource(R.dimen.padding_small))
            )
        }
    }
}

@Composable
fun InfoRow(
    viewModel: MainViewModel,
    currentPhoto: Photo?
) {
    val context = LocalContext.current
    val view = LocalView.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(horizontal = dimensionResource(R.dimen.padding_medium))
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium.copy(
                    bottomEnd = CornerSize(0.dp),
                    bottomStart = CornerSize(0.dp)
                )
            )
    ) {
        Text(
            text = currentPhoto?.title?: "Title",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .padding(
                    start = dimensionResource(R.dimen.padding_medium),
                    end =  dimensionResource(R.dimen.padding_medium),
                    top = dimensionResource(R.dimen.padding_medium),
                    bottom = dimensionResource(R.dimen.padding_small))
//                .align(Alignment.Start)
        )
        if (currentPhoto?.description != null)
            Text(
                text = currentPhoto.description,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(horizontal = dimensionResource(R.dimen.padding_medium))
    //                .align(Alignment.Start)
            )
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Info(
                title = "Date",
                icon = painterResource(R.drawable.calendar),
                value = {
                    Text(currentPhoto?.getFormattedDate() ?: "", style = MaterialTheme.typography.bodyMedium)
                }
            )
            Info(
                title = "Size",
                icon = painterResource(R.drawable.hard_drives),
                value = {
                    Text(
                        formatShortFileSize(context, currentPhoto?.size ?: 0),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            )
            Info(
                title = "Location",
                icon = painterResource(R.drawable.map),
                value = {
                    Text(
                        currentPhoto?.location?.toString() ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        textDecoration = TextDecoration.Underline,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            viewModel.openLocationInMapsApp(currentPhoto)
                        }
                    )
                }
            )
            Info(
                title = "Album",
                icon = painterResource(R.drawable.books),
                value = {
                    Text(currentPhoto?.album ?: "", style = MaterialTheme.typography.bodyMedium)
                }
            )
        }
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth()
        ) {
            Info(
                title = "Resolution",
                icon = painterResource(R.drawable.frame_corners),
                value = {
                    Text(currentPhoto?.resolution ?: "", style = MaterialTheme.typography.bodyMedium)
                }
            )
            OutlinedIconButton(
                onClick = {
                    viewModel.sharePhoto()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                }
            ) { Icon(
                painterResource(R.drawable.share_network),
                contentDescription = "Share photo",
                modifier = Modifier.padding(dimensionResource(R.dimen.padding_small))
            ) }
        }
    }
}

@Composable
fun Info(
    title: String,
    icon: Painter,
    value: @Composable () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(dimensionResource(R.dimen.padding_small))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                null,
                Modifier
                    .padding(end = dimensionResource(R.dimen.padding_xsmall))
                    .size(16.dp)
            )
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        value()
    }
}