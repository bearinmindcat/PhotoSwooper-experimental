package com.example.photoswooper.ui.components

import android.os.Build
import android.util.Log
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.photoswooper.R
import com.example.photoswooper.data.uistates.StatsUiState
import com.example.photoswooper.data.uistates.TimeFrame
import com.example.photoswooper.dataStore
import com.example.photoswooper.ui.view.StatsViewModel
import com.example.photoswooper.utils.DataStoreInterface
import io.github.koalaplot.core.ChartLayout
import io.github.koalaplot.core.bar.*
import io.github.koalaplot.core.util.ExperimentalKoalaPlotApi
import io.github.koalaplot.core.xygraph.CategoryAxisModel
import io.github.koalaplot.core.xygraph.FloatLinearAxisModel
import io.github.koalaplot.core.xygraph.XYGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt

private enum class TabIndex {
    STATS, SETTINGS
}

@Composable
fun TabbedPreferencesAndStatsPage(
    statsViewModel: StatsViewModel,
    modifier: Modifier = Modifier,
    numPhotosUnset: Int
) {
    val uiState by statsViewModel.uiState.collectAsState()

    /* Update the stats when the time frame or date to fetch changes */
    LaunchedEffect(uiState.timeFrame, uiState.dateToFetchFromMillis) {
        statsViewModel.updateStatsData()
    }

    /* Update the stats after 1.5 seconds of no swipes (roughly the time it takes to open stats page) */
    LaunchedEffect(numPhotosUnset) {
        delay(1500) // Only update every 10 seconds
        statsViewModel.updateStatsData()
    }

    var tabIndex by remember { mutableStateOf(TabIndex.STATS.ordinal) }
    Column(modifier) {
        TabRow(
            selectedTabIndex = tabIndex,
            containerColor = Color(0f, 0f, 0f, 0f),
            modifier = Modifier.padding(vertical = dimensionResource(R.dimen.padding_medium)),
        ) {
            Tab(
                selected = (tabIndex == TabIndex.STATS.ordinal),
                onClick = { tabIndex = TabIndex.STATS.ordinal },
                modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.padding_small))
            ) {
                Icon(
                    painter = painterResource(R.drawable.chart),
                    contentDescription = stringResource(R.string.show_stats),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    stringResource(R.string.statistics),
                )
            }
            Tab(
                selected = (tabIndex == TabIndex.SETTINGS.ordinal),
                onClick = { tabIndex = TabIndex.SETTINGS.ordinal },
                modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.padding_small))
            ) {
                Icon(
                    painter = painterResource(R.drawable.gear),
                    contentDescription = stringResource(R.string.show_settings),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    stringResource(R.string.settings),
                )
            }
        }
        when (tabIndex) {
            TabIndex.STATS.ordinal -> {
                if (uiState.latestData.isNotEmpty()) // If there is data to plot
                    StatsCard(statsViewModel, uiState)
            }

            TabIndex.SETTINGS.ordinal -> {
                PreferencesCard(Modifier.fillMaxSize())
            }
        }
    }
}

@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
private fun StatsCard(viewModel: StatsViewModel, uiState: StatsUiState) {
    val context = LocalContext.current
    val view = LocalView.current
    val currentTimeFrame = uiState.timeFrame
    val data = uiState.latestData

    val maxYValue =
        if (data.values.max() != 0)
            data.values.max().toFloat().times(1.15f)
        else
            30f // Default max value if all other values are zero


    Column(verticalArrangement = Arrangement.SpaceEvenly) {
        RowOfFilterChips(
            chipsText = listOf(TimeFrame.DAY, TimeFrame.WEEK, TimeFrame.YEAR).map { it.toString().lowercase() },
            current = currentTimeFrame.toString().lowercase(),
            updateCurrent = {
                viewModel.updateTimeFrame(TimeFrame.valueOf(it.uppercase()))
                            },
            modifier = Modifier.weight(0.1f)
        )
        ChartLayout(
            modifier = Modifier
                .padding(dimensionResource(R.dimen.padding_small))
                .weight(0.8f),
        ) {
            Log.v("UI", "Loading chart")
            val YAxisRange = 0f..maxYValue
            val XAxisRange = viewModel.getXAxisRange()
            val xAxisValues = viewModel.getNamedXAxisValues()?: XAxisRange.map { it.toString() }.toList()
            fun barChartEntries(): List<VerticalBarPlotEntry<String, Float>> {
                Log.v("Stats", "Building bar chart entries")
                return buildList {
                    for (index in XAxisRange) {
                        add(
                            DefaultVerticalBarPlotEntry(
                                xAxisValues[index - 1],
                                if (uiState.latestData.size == viewModel.getXAxisRange().last) // If the data has been updated to the new time frame
                                    DefaultVerticalBarPosition(0f, data.getValue(index).toFloat())
                                else DefaultVerticalBarPosition(0f, 0f)
                            )
                        )
                    }
                }
            }

            XYGraph(
                xAxisModel = CategoryAxisModel(xAxisValues),
                yAxisModel = FloatLinearAxisModel(
                    YAxisRange,
                ),
            ) {
                VerticalBarPlot(
                    data = barChartEntries(),
                    bar = { index ->
                        DefaultVerticalBar(
                            brush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.padding(dimensionResource(R.dimen.padding_small))
                            ) {
                                Box(modifier = Modifier.padding(dimensionResource(R.dimen.padding_small))) {
                                    Text(barChartEntries()[index].y.yMax.toString())
                                }
                            }
                        }
                    },
                )
            }
        }
        Text(
            text = viewModel.getDateTitle(),
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                    view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
            }) {
                Icon(
                    painter = painterResource(R.drawable.caret_left),
                    contentDescription = "view previous $currentTimeFrame",
                    modifier = Modifier.size(24.dp)
                )
            }
            /* Reset date button */
            FilledTonalButton(
                onClick = {
                    viewModel.resetDate()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                          },
                enabled = !uiState.currentDateShown
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.calendar),
                        contentDescription = "Seek to current $currentTimeFrame",
                        modifier = Modifier
                            .size(24.dp)
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
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                    }
                    else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                            view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
                    }
                },
                enabled = !uiState.currentDateShown
            ) {
                Icon(
                    painter = painterResource(R.drawable.caret_right),
                    contentDescription = "view next $currentTimeFrame",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun PreferencesCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current

    fun performSwitchHapticFeedback(toggledOn: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (toggledOn)
                view.performHapticFeedback(HapticFeedbackConstants.TOGGLE_ON)
            else
                view.performHapticFeedback(HapticFeedbackConstants.TOGGLE_OFF)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    val dataStoreInterface = DataStoreInterface(context.dataStore)

    val numPhotosPerStackPref: Int?
    runBlocking(Dispatchers.IO) {
        numPhotosPerStackPref = dataStoreInterface.getIntSettingValue("num_photos_per_stack").first()
    }
    var numPhotosPerStack by remember { mutableStateOf(numPhotosPerStackPref) }
    var displayedNumPhotosPerStack by remember { mutableStateOf(numPhotosPerStack?.toString()?: "30") }

    val permanentlyDeletePref: Boolean?
    runBlocking {
        permanentlyDeletePref = dataStoreInterface.getBooleanSettingValue("permanently_delete").first()
    }
    var displayedPermanentlyDelete by remember { mutableStateOf(permanentlyDeletePref) }

    Card(modifier.verticalScroll(rememberScrollState())) {
        ListItem(
            headlineContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = dimensionResource(R.dimen.padding_small))
                ) {
                    Icon(
                        painter = painterResource(R.drawable.images),
                        contentDescription = null, // Described in adjacent text
                        modifier = Modifier.padding(end= dimensionResource(R.dimen.padding_medium))
                    )
                    Text(
                        text = stringResource(R.string.num_photos_per_stack)
                    )
                }
            },
            supportingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.padding_medium))
                ) {
                    TextField(
                        value = displayedNumPhotosPerStack,
                        onValueChange = { input -> // Update UI value & dataStore only if valid
                            val inputAsInt = input.toIntOrNull()
                            if (inputAsInt != null) {
                                if (inputAsInt in 1..100) { // Separate if statements so user doesn't see error message when inputting 0
                                    displayedNumPhotosPerStack = input
                                    numPhotosPerStack = input.toInt()
                                    CoroutineScope(Dispatchers.IO).launch {
                                        dataStoreInterface.setIntSettingValue(
                                            setting = "num_photos_per_stack",
                                            newValue = numPhotosPerStack ?: 30
                                        )
                                    }
                                }
                            }
                            else if (input == "")
                                    displayedNumPhotosPerStack = input
                            else
                                Toast.makeText(
                                context,
                                "Not a number :(",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        keyboardOptions = KeyboardOptions.Default.copy(
                            keyboardType = KeyboardType.Number
                        ),
                        modifier = Modifier
                            .weight(0.2f)
                            .padding(horizontal = dimensionResource(R.dimen.padding_small))
                    )
                    Slider(
                        value = numPhotosPerStack?.toFloat() ?: 30f,
                        onValueChange = {
                            numPhotosPerStack = it.toInt()
                            displayedNumPhotosPerStack = it.roundToInt().toString()
                                        },
                        valueRange = 1f..100f,
                        onValueChangeFinished = {
                            CoroutineScope(Dispatchers.IO).launch {
                                dataStoreInterface.setIntSettingValue(
                                    setting = "num_photos_per_stack",
                                    newValue = numPhotosPerStack ?: 30
                                )
                            }
                        },
                        modifier = Modifier
                            .weight(0.5f)
                            .padding(horizontal = dimensionResource(R.dimen.padding_small))
                    )
                }
            }
        )
        ListItem(
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.trash),
                    contentDescription = null // Described in adjacent text
                )
            },
            headlineContent = {
                Text(
                    stringResource(R.string.permanently_delete)
                )
            },
            trailingContent = {
                Switch(
                    checked = displayedPermanentlyDelete ?: false,
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
                    onCheckedChange = {
                        displayedPermanentlyDelete = it
                        CoroutineScope(Dispatchers.IO).launch {
                            dataStoreInterface.setBooleanSettingValue(
                                setting = "permanently_delete",
                                newValue = displayedPermanentlyDelete ?: false
                            )
                        }
                        performSwitchHapticFeedback(it)
                    }
                )
            },
            supportingContent = {
                Text(
                    stringResource(R.string.permanently_delete_desc)
                )
            },
            modifier = Modifier.clickable { // Allows user to click on the whole row to toggle
                displayedPermanentlyDelete = !(displayedPermanentlyDelete ?: false)
                CoroutineScope(Dispatchers.IO).launch {
                    dataStoreInterface.setBooleanSettingValue(
                        setting = "permanently_delete",
                        newValue = displayedPermanentlyDelete ?: false
                    )
                }
                performSwitchHapticFeedback(!(displayedPermanentlyDelete ?: false))
            }
        )
    }
}