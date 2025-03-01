package com.example.photoswooper.ui.view

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.photoswooper.R
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    val blurState = remember { HazeState() } // For bottom bar
    val view = LocalView.current

    Scaffold {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
//            if (uiState.photos.isNotEmpty())
                Image(
                    painter = painterResource(R.drawable.test_image),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(dimensionResource(R.dimen.padding_medium))
                        .haze(
                            blurState,
                            backgroundColor = MaterialTheme.colorScheme.background,
                            tint = Color.Black.copy(alpha = .2f),
                            blurRadius = 30.dp,
                        )
                )
                Column(
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(dimensionResource(R.dimen.padding_medium))
                ) {
                    Text(
                        text = "Title",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Row(
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(dimensionResource(R.dimen.padding_medium))
                            .hazeChild(state = blurState, shape = MaterialTheme.shapes.large)
                    ){
                        FilledIconButton(
                            onClick = {
                            /* TODO: undo button */
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
                        ElevatedButton(
                            onClick = {
                            /* TODO: confirm deletion button */
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
                                    contentDescription = "Permanently delete selected photos",
                                    modifier = Modifier.padding(dimensionResource(R.dimen.padding_small))
                                )
                                Text(
                                    text = "Delete ${uiState.toDelete.size} photos",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(dimensionResource(R.dimen.padding_small))
                                )
                            }
                        }
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