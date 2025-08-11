package com.example.photoswooper.ui.components

import android.os.Build
import android.text.format.Formatter.formatShortFileSize
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import com.example.photoswooper.R
import com.example.photoswooper.data.models.Photo
import com.example.photoswooper.data.uistates.MainUiState
import com.example.photoswooper.ui.view.MainViewModel
import io.github.koalaplot.core.util.ExperimentalKoalaPlotApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
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
                            if (undoResult)
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
                        painter = painterResource(R.drawable.info),
                        contentDescription = "Show more image information",
                        modifier = Modifier.padding(dimensionResource(R.dimen.padding_small))
                    )
                }
            }
        }
    }

    /* Statistics row */
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .clickable(onClickLabel = "Click to change time frame") {
                CoroutineScope(Dispatchers.Main).launch {
                    viewModel.cycleStorageStatsTimeFrame()
                }
            }
            .fillMaxWidth()
            .padding(dimensionResource(R.dimen.padding_small))
            .clip(MaterialTheme.shapes.medium)
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
                .size(dimensionResource(R.dimen.xsmall_icon))
        )
        Text(
            text = buildAnnotatedString {
                append(" ")
                pushStyle(SpanStyle(textDecoration = TextDecoration.Underline))
                append(uiState.currentStorageStatsTimeFrame.name.lowercase())
                pop()
                append(": ${formatShortFileSize(LocalContext.current, uiState.spaceSavedInTimeFrame)}")
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
}