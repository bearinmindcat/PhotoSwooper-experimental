package com.example.photoswooper.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import com.example.photoswooper.R
import com.example.photoswooper.data.models.Photo
import com.example.photoswooper.data.uistates.MainUiState
import com.example.photoswooper.ui.view.MainViewModel
import io.github.koalaplot.core.util.ExperimentalKoalaPlotApi

@OptIn(ExperimentalFoundationApi::class, ExperimentalKoalaPlotApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ActionBar(
    currentPhoto: Photo?,
    numToDelete: Int,
    uiState: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current

    Column(modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = dimensionResource(R.dimen.padding_medium))
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                /* Undo button */
                FilledIconButton(
                    onClick = {
                        val undoResult = viewModel.undo()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            if (undoResult == true)
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            else
                                view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                        }
                    },
                    modifier = Modifier
                        .padding(
                            horizontal = dimensionResource(R.dimen.padding_small),
                        )
                        .weight(0.2f)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.undo),
                        contentDescription = "Undo deletion",
                        modifier = Modifier.padding(dimensionResource(R.dimen.padding_small))
                    )
                }
                /* Review deleted photos button */
                ReviewDeletedButton(
                    view = view,
                    viewModel = viewModel,
                    numToDelete = numToDelete,
                    reviewDialogEnabled = uiState.reviewDialogEnabled,
                    modifier = Modifier.padding(
                        horizontal = dimensionResource(R.dimen.padding_small),
                    )
                )
                /* Info button */
                FilledTonalIconButton(
                    onClick = {
                        if (currentPhoto != null) {
                            viewModel.toggleInfo()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                            view.performHapticFeedback(HapticFeedbackConstants.REJECT)

                    },
                    modifier = Modifier
                        .padding(
                            horizontal = dimensionResource(R.dimen.padding_small),
                        )
                        .weight(0.2f)
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
    TabbedPreferencesAndStatsPage(
        modifier = Modifier
    )
}