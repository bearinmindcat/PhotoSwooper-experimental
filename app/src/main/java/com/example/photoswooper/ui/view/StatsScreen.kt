/*
 * SPDX-FileCopyrightText: 2025 Loowiz <loowiz@envs.net>
 *
 *  SPDX-License-Identifier: AGPL-3.0-only
 */

package com.example.photoswooper.ui.view

import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.util.Log
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.photoswooper.R
import com.example.photoswooper.data.uistates.StatsData
import com.example.photoswooper.data.uistates.StatsUiState
import com.example.photoswooper.data.uistates.TimeFrame
import com.example.photoswooper.ui.components.DropdownFilterChip
import com.example.photoswooper.ui.viewmodels.StatsViewModel
import io.github.koalaplot.core.ChartLayout
import io.github.koalaplot.core.bar.DefaultBar
import io.github.koalaplot.core.bar.DefaultBarPosition
import io.github.koalaplot.core.bar.DefaultVerticalBarPlotEntry
import io.github.koalaplot.core.bar.VerticalBarPlot
import io.github.koalaplot.core.bar.VerticalBarPlotEntry
import io.github.koalaplot.core.util.ExperimentalKoalaPlotApi
import io.github.koalaplot.core.xygraph.CategoryAxisModel
import io.github.koalaplot.core.xygraph.FloatLinearAxisModel
import io.github.koalaplot.core.xygraph.XYGraph


@OptIn(ExperimentalKoalaPlotApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    startWeekOnMonday: Boolean,
    viewModel: StatsViewModel,
    uiState: StatsUiState
) {
    val context = LocalContext.current
    val view = LocalView.current

    val currentTimeFrame = uiState.timeFrame
    val currentDataType = uiState.dataType
    val data = uiState.latestData

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier.padding(top = dimensionResource(R.dimen.padding_small))
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .weight(0.15f)
                .fillMaxWidth()
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Data",
                    style = MaterialTheme.typography.labelLarge,
                )
                DropdownFilterChip(
                    leadingIconPainter = painterResource(currentDataType.iconDrawableId),
                    selectedMenuItem = currentDataType.toString().lowercase() + " " + currentDataType.extraInfo,
                    menuItemsDescription = "data types for y-axis",
                    menuItems = StatsData.entries.map { it.toString().lowercase() + " " + it.extraInfo }.toTypedArray(),
                    menuItemIcons = StatsData.entries.map { painterResource(it.iconDrawableId) }.toTypedArray(),
                    onSelectionChange = {
                        viewModel.updateDataType(
                            StatsData.valueOf(
                                it.substringBefore(" ").uppercase()
                            ),
                            startWeekOnMonday
                        )
                        if (SDK_INT >= Build.VERSION_CODES.R)
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    },
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Time frame",
                    style = MaterialTheme.typography.labelLarge,
                )
                DropdownFilterChip(
                    leadingIconPainter = painterResource(currentTimeFrame.iconDrawableId),
                    selectedMenuItem = currentTimeFrame.toString().lowercase(),
                    menuItemsDescription = "Time frame for x-axis",
                    menuItems = listOf(TimeFrame.DAY, TimeFrame.WEEK, TimeFrame.YEAR).map { it.toString().lowercase() }
                        .toTypedArray(),
                    menuItemIcons = listOf(
                        TimeFrame.DAY,
                        TimeFrame.WEEK,
                        TimeFrame.YEAR
                    ).map { painterResource(it.iconDrawableId) }.toTypedArray(),
                    onSelectionChange = {
                        viewModel.updateTimeFrame(TimeFrame.valueOf(it.uppercase()))
                        if (SDK_INT >= Build.VERSION_CODES.R)
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    },
                )
            }
        }

        ChartLayout(
            modifier = Modifier
                .padding(dimensionResource(R.dimen.padding_small))
                .weight(0.8f),
        ) {
            Log.v("UI", "Loading chart")
            val maxYValue =
                if (data.max() != 0f)
                    data.max().times(1.15f)
                else
                    30f // Default max value if all other values are zero
            val yAxisRange = 0f..maxYValue
            val xAxisRange = viewModel.getXAxisRange()
            val xAxisValues = viewModel.getNamedXAxisValues(startWeekOnMonday) ?: xAxisRange.map { it.toString() }.toList()
            fun barChartEntries(): List<VerticalBarPlotEntry<String, Float>> {
                Log.v("Stats", "Building bar chart entries")
                return buildList {
                    for (index in xAxisRange) {
                        add(
                            DefaultVerticalBarPlotEntry(
                                xAxisValues[index],
                                if (uiState.latestData.size == viewModel.getXAxisRange().last + 1) // If the data has been updated to the new time frame
                                    DefaultBarPosition(0f, data[index])
                                else DefaultBarPosition(0f, 0f)
                            )
                        )
                    }
                }
            }

            XYGraph(
                xAxisModel = CategoryAxisModel(xAxisValues),
                yAxisModel = FloatLinearAxisModel(
                    yAxisRange,
                ),
            ) {
                VerticalBarPlot(
                    data = barChartEntries(),
                    bar = { index, _, _ ->
                        DefaultBar(
                            brush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    Toast.makeText(
                                        context,
                                        if (currentDataType == StatsData.SPACE_SAVED) barChartEntries()[index].y.end.toString() + " MB"
                                        else barChartEntries()[index].y.end.toString(),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                        ) {
                            Surface(
                                shadowElevation = 2.dp,
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .padding(dimensionResource(R.dimen.padding_small))
                            ) {
                                Box(modifier = Modifier.padding(dimensionResource(R.dimen.padding_small))) {
                                    Text(barChartEntries()[index].y.end.toString())
                                }
                            }
                        }
                    },
                )
            }
        }
        Text(
            text = viewModel.getDateRangeTitle(startWeekOnMonday),
            style = MaterialTheme.typography.labelLarge,
        )
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(0.2f)
                .fillMaxWidth()
        ) {
            /* Previous date button */
            IconButton(onClick = {
                viewModel.previousDate()
                if (SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                    view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
            }) {
                Icon(
                    painter = painterResource(R.drawable.caret_left),
                    contentDescription = "view previous $currentTimeFrame",
                    modifier = Modifier.size(dimensionResource(R.dimen.small_icon))
                )
            }
            /* Reset date button */
            FilledTonalButton(
                onClick = {
                    viewModel.resetDate()
                    if (SDK_INT >= Build.VERSION_CODES.R)
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                },
                enabled = !uiState.isToday
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.calendar),
                        contentDescription = "Seek to current $currentTimeFrame",
                        modifier = Modifier
                            .size(dimensionResource(R.dimen.small_icon))
                            .padding(end = dimensionResource(R.dimen.padding_xsmall))
                    )
                    Text("Today")
                }
            }
            /* Next date button */
            IconButton(
                /* TODO("remove Toast, only use enabling/disabling the button. Will need to check if the next date is in the future in UI, not viewModel function") */
                onClick = {
                    if (!viewModel.nextDate()) {
                        Toast.makeText(
                            context,
                            "Cannot see into the future.",
                            Toast.LENGTH_SHORT
                        ).show()
                        if (SDK_INT >= Build.VERSION_CODES.R)
                            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                    } else {
                        if (SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                            view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
                    }
                },
                enabled = !uiState.isToday
            ) {
                Icon(
                    painter = painterResource(R.drawable.caret_right),
                    contentDescription = "view next $currentTimeFrame",
                    modifier = Modifier.size(dimensionResource(R.dimen.small_icon))
                )
            }
        }
    }
}