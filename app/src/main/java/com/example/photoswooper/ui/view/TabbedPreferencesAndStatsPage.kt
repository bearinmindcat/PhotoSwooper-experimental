package com.example.photoswooper.ui.view

import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.util.Log
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.photoswooper.data.uistates.BooleanPreference
import com.example.photoswooper.data.uistates.IntPreference
import com.example.photoswooper.data.uistates.StatsData
import com.example.photoswooper.data.uistates.StatsUiState
import com.example.photoswooper.data.uistates.TimeFrame
import com.example.photoswooper.dataStore
import com.example.photoswooper.ui.components.DropdownFilterChip
import com.example.photoswooper.ui.viewmodels.PrefsViewModel
import com.example.photoswooper.ui.viewmodels.StatsViewModel
import com.example.photoswooper.utils.DataStoreInterface
import io.github.koalaplot.core.ChartLayout
import io.github.koalaplot.core.bar.DefaultVerticalBar
import io.github.koalaplot.core.bar.DefaultVerticalBarPlotEntry
import io.github.koalaplot.core.bar.DefaultVerticalBarPosition
import io.github.koalaplot.core.bar.VerticalBarPlot
import io.github.koalaplot.core.bar.VerticalBarPlotEntry
import io.github.koalaplot.core.util.ExperimentalKoalaPlotApi
import io.github.koalaplot.core.xygraph.CategoryAxisModel
import io.github.koalaplot.core.xygraph.FloatLinearAxisModel
import io.github.koalaplot.core.xygraph.XYGraph
import kotlinx.coroutines.delay
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
    val context = LocalContext.current
    val view = LocalView.current
    val reduceAnimations = DataStoreInterface(context.dataStore)
        .getBooleanSettingValue(BooleanPreference.reduce_animations.toString()).collectAsState(false)

    val uiState by statsViewModel.uiState.collectAsState()

    var tabIndex by remember { mutableIntStateOf(TabIndex.STATS.ordinal) }
    var tabIndicatorWidth by remember { mutableStateOf(24.dp) }
    val animatedTabIndicatorWidth = animateDpAsState(
        tabIndicatorWidth,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioLowBouncy,
        ),
    )
    val onTabChange = {
        if (SDK_INT >= Build.VERSION_CODES.R)
        view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
    }

    /* Update the stats when the time frame or date to fetch changes */
    LaunchedEffect(uiState.timeFrame, uiState.dateToFetchFromMillis) {
        statsViewModel.updateStatsData()
    }

    /* Update the stats after 1.5 seconds of no swipes (roughly the time it takes to open stats page) */
    LaunchedEffect(numPhotosUnset) {
        delay(1500) // Only update every 10 seconds
        statsViewModel.updateStatsData()
    }

    /* Expand, then shrink the tab indicator while it is moving for a smooth animation */
    LaunchedEffect(tabIndex) {
        delay(10)
        tabIndicatorWidth = 64.dp
        delay(125)
        tabIndicatorWidth = 24.dp
    }

    Column(modifier) {
        TabRow(
            selectedTabIndex = tabIndex,
            containerColor = Color(0f, 0f, 0f, 0f),
            indicator = { tabPositions ->
                val currentTabPosition = tabPositions[tabIndex]
                TabRowDefaults.PrimaryIndicator(
                    width = animatedTabIndicatorWidth.value,
                    modifier = Modifier
                        .tabIndicatorOffset(currentTabPosition)
                )
            },
            modifier = Modifier.padding(vertical = dimensionResource(R.dimen.padding_medium)),
        ) {
            Tab(
                selected = (tabIndex == TabIndex.STATS.ordinal),
                onClick = {
                    tabIndex = TabIndex.STATS.ordinal
                    onTabChange()
                          },
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
                onClick = {
                    tabIndex = TabIndex.SETTINGS.ordinal
                    onTabChange()
                          },
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
        AnimatedContent(
            tabIndex,
            transitionSpec = {
                if (reduceAnimations.value) fadeIn().togetherWith(fadeOut())
                else
                    slideIntoContainer(
                        towards =
                            if (tabIndex == TabIndex.STATS.ordinal) AnimatedContentTransitionScope.SlideDirection.End
                            else AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = spring(
                            stiffness = Spring.StiffnessMediumLow,
                            dampingRatio = Spring.DampingRatioLowBouncy,
                        ),
                    ).togetherWith(
                        slideOutOfContainer(
                            towards =
                                if (tabIndex == TabIndex.STATS.ordinal) AnimatedContentTransitionScope.SlideDirection.End
                                else AnimatedContentTransitionScope.SlideDirection.Start,
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMediumLow,
                                dampingRatio = Spring.DampingRatioLowBouncy,
                            )
                        )
                    )
            },
        ) {
            when (it) {
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
}

@OptIn(ExperimentalKoalaPlotApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun StatsCard(viewModel: StatsViewModel, uiState: StatsUiState) {
    val context = LocalContext.current
    val view = LocalView.current

    val currentTimeFrame = uiState.timeFrame
    val currentDataType = uiState.dataType
    val data = uiState.latestData

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
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
                    currentSelection = currentDataType.toString().lowercase() + " " + currentDataType.extraInfo,
                    expandContentDescription = "Select data type for y-axis",
                    menuItems = StatsData.entries.map { it.toString().lowercase() + " " + it.extraInfo }.toTypedArray(),
                    menuItemIcons = StatsData.entries.map { painterResource(it.iconDrawableId) }.toTypedArray(),
                    onSelectionChange = {
                        viewModel.updateDataType(
                            StatsData.valueOf(
                                it.substringBefore(" ").uppercase()
                            )
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
                    currentSelection = currentTimeFrame.toString().lowercase(),
                    expandContentDescription = "Select time frame for x-axis",
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
            val YAxisRange = 0f..maxYValue
            val XAxisRange = viewModel.getXAxisRange()
            val xAxisValues = viewModel.getNamedXAxisValues()?: XAxisRange.map { it.toString() }.toList()
            fun barChartEntries(): List<VerticalBarPlotEntry<String, Float>> {
                Log.v("Stats", "Building bar chart entries")
                return buildList {
                    for (index in XAxisRange) {
                        add(
                            DefaultVerticalBarPlotEntry(
                                xAxisValues[index],
                                if (uiState.latestData.size == viewModel.getXAxisRange().last + 1) // If the data has been updated to the new time frame
                                    DefaultVerticalBarPosition(0f, data[index])
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    Toast.makeText(
                                        context,
                                        if (currentDataType == StatsData.SPACE_SAVED)
                                            barChartEntries()[index].y.yMax.toString() + " MB"
                                        else barChartEntries()[index].y.yMax.toString(),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
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
                if (SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
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
                    if (SDK_INT >= Build.VERSION_CODES.R)
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
                        if (SDK_INT >= Build.VERSION_CODES.R)
                            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                    }
                    else {
                        if (SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
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

    val viewModel = PrefsViewModel(dataStoreInterface = DataStoreInterface(context.dataStore))
    viewModel.updateStackTextInput()
    val uiState by viewModel.uiState.collectAsState()
    val permanentlyDelete = viewModel.dataStoreInterface.getBooleanSettingValue(BooleanPreference.permanently_delete.toString()).collectAsState(false)
    val systemFont = viewModel.dataStoreInterface.getBooleanSettingValue(BooleanPreference.system_font.toString()).collectAsState(false)
    val dynamicTheme = viewModel.dataStoreInterface.getBooleanSettingValue(BooleanPreference.dynamic_theme.toString()).collectAsState(false)
    val reduceAnimations = viewModel.dataStoreInterface.getBooleanSettingValue(BooleanPreference.reduce_animations.toString()).collectAsState(false)


    fun performSwitchHapticFeedback(toggledOn: Boolean) {
        if (SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (toggledOn)
                view.performHapticFeedback(HapticFeedbackConstants.TOGGLE_ON)
            else
                view.performHapticFeedback(HapticFeedbackConstants.TOGGLE_OFF)
        } else if (SDK_INT >= Build.VERSION_CODES.R)
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    Card(modifier.verticalScroll(rememberScrollState())) {
        /* Photos per stack preference */
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
                    OutlinedTextField(
                        value = uiState.numPhotosPerStackTextInput,
                        onValueChange = { input -> // Update UI value & dataStore only if valid
                            if(!viewModel.validatePhotosPerStackInputAndUpdate(input))
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
                            .weight(0.15f)
                            .padding(dimensionResource(R.dimen.padding_small))
                    )
                    Slider(
                        value = uiState.numPhotosPerStackTextInput.toFloatOrNull() ?: IntPreference.num_photos_per_stack.default.toFloat(),
                        onValueChange = {
                            if (it.roundToInt() != uiState.numPhotosPerStackTextInput.toIntOrNull()) {
                                viewModel.updatePhotosPerStackInput(it.roundToInt().toString())
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                                        },
                        valueRange = 10f..100f,
                        onValueChangeFinished = {
                            viewModel.updateIntPreference(IntPreference.num_photos_per_stack.toString(),uiState.numPhotosPerStackTextInput.toInt())
                        },
                        steps = 8,
                        modifier = Modifier
                            .weight(0.5f)
                            .padding(horizontal = dimensionResource(R.dimen.padding_small))
                    )
                }
            }
        )
        // TODO("Make BooleanSettingItem Composable")
        /* Permanently delete preference */
        if (SDK_INT >= Build.VERSION_CODES.R)
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
                    checked = permanentlyDelete.value,
                    onCheckedChange = {
                        viewModel.toggleBooleanSetting(BooleanPreference.permanently_delete.toString())
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
                viewModel.toggleBooleanSetting(BooleanPreference.permanently_delete.toString())
                performSwitchHapticFeedback(permanentlyDelete.value)
            }
        )
        /* System font preference */
        ListItem(
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.text_aa),
                    contentDescription = null // Described in adjacent text
                )
            },
            headlineContent = {
                Text(
                    stringResource(R.string.system_font)
                )
            },
            trailingContent = {
                Switch(
                    checked = systemFont.value,
                    onCheckedChange = {
                        viewModel.toggleBooleanSetting(BooleanPreference.system_font.toString())
                        performSwitchHapticFeedback(it)
                    }
                )
            },
            supportingContent = {
                Text(
                    stringResource(R.string.system_font_desc)
                )
            },
            modifier = Modifier.clickable { // Allows user to click on the whole row to toggle
                viewModel.toggleBooleanSetting(BooleanPreference.system_font.toString())
                performSwitchHapticFeedback(systemFont.value)
            }
        )
        /* Dynamic theme preference */
        if (SDK_INT >= Build.VERSION_CODES.S)
            ListItem(
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.palette),
                    contentDescription = null // Described in adjacent text
                )
            },
            headlineContent = {
                Text(
                    stringResource(R.string.dynamic_theme)
                )
            },
            trailingContent = {
                Switch(
                    checked = dynamicTheme.value,
                    onCheckedChange = {
                        viewModel.toggleBooleanSetting(BooleanPreference.dynamic_theme.toString())
                        performSwitchHapticFeedback(it)
                    }
                )
            },
            supportingContent = {
                Text(
                    stringResource(R.string.dynamic_theme_desc)
                )
            },
            modifier = Modifier.clickable { // Allows user to click on the whole row to toggle
                viewModel.toggleBooleanSetting(BooleanPreference.dynamic_theme.toString())
                performSwitchHapticFeedback(dynamicTheme.value)
            }
        )
        /* Reduce animations preference */
        ListItem(
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.film_strip),
                    contentDescription = null // Described in adjacent text
                )
            },
            headlineContent = {
                Text(
                    stringResource(R.string.reduce_animations)
                )
            },
            trailingContent = {
                Switch(
                    checked = reduceAnimations.value,
                    onCheckedChange = {
                        viewModel.toggleBooleanSetting(BooleanPreference.reduce_animations.toString())
                        performSwitchHapticFeedback(it)
                    }
                )
            },
            supportingContent = {
                Text(
                    stringResource(R.string.reduce_animations_desc)
                )
            },
            modifier = Modifier.clickable { // Allows user to click on the whole row to toggle
                viewModel.toggleBooleanSetting(BooleanPreference.reduce_animations.toString())
                performSwitchHapticFeedback(reduceAnimations.value)
            }
        )
    }
}