/*
 * SPDX-FileCopyrightText: 2025 Loowiz <loowiz@envs.net>
 *
 *  SPDX-License-Identifier: AGPL-3.0-only
 */

package com.example.photoswooper.ui.view

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.util.Log
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetValue
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.example.photoswooper.R
import com.example.photoswooper.data.models.MediaStatus
import com.example.photoswooper.data.uistates.BooleanPreference
import com.example.photoswooper.data.uistates.IntPreference
import com.example.photoswooper.data.uistates.StatsData
import com.example.photoswooper.data.uistates.StatsUiState
import com.example.photoswooper.data.uistates.TimeFrame
import com.example.photoswooper.dataStore
import com.example.photoswooper.ui.components.DropdownFilterChip
import com.example.photoswooper.ui.components.FloatingAction
import com.example.photoswooper.ui.viewmodels.MainViewModel
import com.example.photoswooper.ui.viewmodels.ReviewViewModel
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class TabIndex {
    REVIEW, STATS, SETTINGS
}
enum class PreferencesCategory(@param:StringRes val titleStringId: Int, @param:StringRes val descriptionStringId: Int, @param:DrawableRes val iconDrawableId: Int) {
    BEHAVIOUR(R.string.prefs_behaviour_title, R.string.prefs_behaviour_desc, R.drawable.navigation_arrow),
    APPEARANCE(R.string.prefs_appearance_title, R.string.prefs_appearance_desc, R.drawable.paint_brush_broad),
    STATISTICS(R.string.statistics, R.string.prefs_statistics_desc, R.drawable.chart),
    BACKUP_RESTORE(R.string.prefs_backup_restore_title, R.string.prefs_backup_restore_desc, R.drawable.clock_counter_clockwise),
}

/**
 * A tabbed screen containing the Review, stats & settings screens
 *
 * @param expandBottomSheet Function to call when the user clicks on a tab, to expand the bottom sheet if it isn't already
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabbedSheetContent(
    tabIndex: Int,
    updateTabIndex: (Int) -> Unit,
    mainViewModel: MainViewModel,
    statsViewModel: StatsViewModel,
    expandBottomSheet: (CoroutineScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val currentCoroutineScope = rememberCoroutineScope()
    val reduceAnimations by DataStoreInterface(context.dataStore)
        .getBooleanSettingValue(BooleanPreference.REDUCE_ANIMATIONS.setting).collectAsState(false)
    val statisticsEnabled by DataStoreInterface(context.dataStore)
        .getBooleanSettingValue(BooleanPreference.STATISTICS_ENABLED.setting).collectAsState(true)
    val startWeekOnMonday by DataStoreInterface(context.dataStore).getBooleanSettingValue(
        BooleanPreference.START_WEEK_ON_MONDAY.setting
    ).collectAsState(BooleanPreference.START_WEEK_ON_MONDAY.default)

    val statsUiState by statsViewModel.uiState.collectAsState()
    val bottomSheetTargetValue = mainViewModel.bottomSheetScaffoldState.bottomSheetState.targetValue

    var tabIndexChange by rememberSaveable { mutableStateOf(0) } // To decide direction of tab change animation
    var tabIndicatorWidth by remember { mutableStateOf(24.dp) }
    val animatedTabIndicatorWidth = animateDpAsState(
        tabIndicatorWidth,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioLowBouncy,
        ),
    )

    fun onTabChange(newTabIndex: TabIndex) {
        tabIndexChange = newTabIndex.ordinal - tabIndex
        updateTabIndex(newTabIndex.ordinal)
        if (SDK_INT >= Build.VERSION_CODES.R)
            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
        expandBottomSheet(currentCoroutineScope)
    }

    /* Update the stats when:
    * - the time frame or date to fetch changes
    * - User pulls up bottom sheet
    * - user updates startWeekOnMonday preference
    */
    LaunchedEffect(
        statsUiState.timeFrame,
        statsUiState.dateToFetchFromMillis,
        bottomSheetTargetValue,
        startWeekOnMonday
    ) {
        if (bottomSheetTargetValue == SheetValue.Expanded)
            statsViewModel.updateStatsData(startWeekOnMonday)
    }

    /* Expand, then shrink the tab indicator while it is moving for a smooth animation */
    LaunchedEffect(tabIndex) {
        delay(10)
        tabIndicatorWidth = 64.dp
        delay(125)
        tabIndicatorWidth = 24.dp
    }

    BackHandler(mainViewModel.bottomSheetScaffoldState.bottomSheetState.targetValue == SheetValue.Expanded) {
        currentCoroutineScope.launch { mainViewModel.bottomSheetScaffoldState.bottomSheetState.partialExpand() }
    }

    Column(modifier) {
        TabRow(
            selectedTabIndex = tabIndex,
            containerColor = Color(0f, 0f, 0f, 0f),
            indicator = { tabPositions ->
                val currentTabPosition = tabPositions.getOrElse(tabIndex, { tabPositions.last() })
                TabRowDefaults.PrimaryIndicator(
                    width = animatedTabIndicatorWidth.value,
                    modifier = Modifier
                        .tabIndicatorOffset(currentTabPosition)
                )
            },
            modifier = Modifier.padding(top = dimensionResource(R.dimen.padding_medium)),
        ) {
            Tab(
                selected = (tabIndex == TabIndex.REVIEW.ordinal),
                onClick = { CoroutineScope(Dispatchers.Default).launch { onTabChange(TabIndex.REVIEW) } },
                modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.padding_small))
            ) {
                Icon(
                    painter = painterResource(R.drawable.check),
                    contentDescription = stringResource(R.string.show_review),
                    modifier = Modifier.size(dimensionResource(R.dimen.small_icon))
                )
                Text(
                    stringResource(R.string.review),
                )
            }
            if(statisticsEnabled) {
                Tab(
                    selected = (tabIndex == TabIndex.STATS.ordinal),
                    onClick = { CoroutineScope(Dispatchers.Default).launch { onTabChange(TabIndex.STATS) } },
                    modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.padding_small))
                ) {
                    Icon(
                        painter = painterResource(R.drawable.chart),
                        contentDescription = stringResource(R.string.show_stats),
                        modifier = Modifier.size(dimensionResource(R.dimen.small_icon))
                    )
                    Text(
                        stringResource(R.string.statistics),
                    )
                }
            }
            Tab(
                selected = (tabIndex == TabIndex.SETTINGS.ordinal),
                onClick = { CoroutineScope(Dispatchers.Default).launch { onTabChange(TabIndex.SETTINGS) } },
                modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.padding_small))
            ) {
                Icon(
                    painter = painterResource(R.drawable.gear),
                    contentDescription = stringResource(R.string.show_settings),
                    modifier = Modifier.size(dimensionResource(R.dimen.small_icon))
                )
                Text(
                    stringResource(R.string.settings),
                )
            }
        }
        AnimatedContent(
            tabIndex,
            transitionSpec = {
                if (reduceAnimations) fadeIn().togetherWith(fadeOut())
                else
                    slideIntoContainer(
                        towards =
                            if (tabIndexChange < 0) AnimatedContentTransitionScope.SlideDirection.End
                            else AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = spring(
                            stiffness = Spring.StiffnessMediumLow,
                            dampingRatio = Spring.DampingRatioLowBouncy,
                        ),
                    ).togetherWith(
                        slideOutOfContainer(
                            towards =
                                if (tabIndexChange < 0) AnimatedContentTransitionScope.SlideDirection.End
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
                TabIndex.REVIEW.ordinal -> {
                    ReviewScreen(mainViewModel)
                }

                TabIndex.STATS.ordinal -> {
                    if (statsUiState.latestData.isNotEmpty()) // If there is data to plot
                        StatsScreen(
                            startWeekOnMonday,
                            statsViewModel,
                            statsUiState
                        )
                }

                TabIndex.SETTINGS.ordinal -> {
                    PreferencesScreen(Modifier.fillMaxSize())
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewScreen(
    mainViewModel: MainViewModel,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val reduceAnimations by DataStoreInterface(context.dataStore)
        .getBooleanSettingValue(BooleanPreference.REDUCE_ANIMATIONS.setting).collectAsState(false)

    val reviewViewModel = remember { ReviewViewModel() }
    val reviewUiState by reviewViewModel.uiState.collectAsState()
    val mainUiState by mainViewModel.uiState.collectAsState()

    var floatingActionButtonSize by remember { mutableStateOf(DpSize(0.dp, 0.dp)) }

    if (mainUiState.mediaItems.firstOrNull { it.status != MediaStatus.UNSET } != null)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(top = dimensionResource(R.dimen.padding_small))
                .fillMaxSize()
        ) {
            Text("Status", style = MaterialTheme.typography.labelLarge)
            val listOfMediaStatusToFilter = MediaStatus.entries.minusElement(MediaStatus.UNSET)
            DropdownFilterChip(
                leadingIconPainter = painterResource(reviewUiState.currentStatusFilter.iconDrawableId),
                currentMenuItemSelection = reviewUiState.currentStatusFilter.toString().lowercase(),
                menuItemsDescription = "Statuses of the media to show",
                menuItems = listOfMediaStatusToFilter.map { it.toString().lowercase() }.toTypedArray(),
                menuItemIcons = listOfMediaStatusToFilter.map { painterResource(it.iconDrawableId) }.toTypedArray(),
                onSelectionChange = {
                    if (SDK_INT >= Build.VERSION_CODES.R)
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    reviewViewModel.updateCurrentStatusFilter(MediaStatus.valueOf(it.uppercase()))
                },
                modifier = Modifier.padding(bottom = dimensionResource(R.dimen.padding_small))
            )

            Box(Modifier.fillMaxSize()) {
                LazyVerticalStaggeredGrid(columns = StaggeredGridCells.Adaptive(120.dp)) {
                    items(mainUiState.mediaItems.filter { it.status == reviewUiState.currentStatusFilter }) { mediaItem ->
                        val coroutineScope = rememberCoroutineScope()
                        val imageScale = remember { Animatable(1f) }
                        fun animateImageSelect() {
                            coroutineScope.launch {
                                val newTargetValue: Float = if (reviewUiState.selectedMedia.contains(mediaItem)) 0.9f
                                else 1f
                                imageScale.animateTo(
                                    newTargetValue,
                                    spring(
                                        stiffness = Spring.StiffnessMedium,
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                    )
                                )
                            }
                        }
                        LaunchedEffect(reviewUiState.selectedMedia) {
                            animateImageSelect()
                        }
                        Box(
                            Modifier.combinedClickable(
                                onClick = {
                                    if (reviewUiState.mediaSelectionEnabled) {
                                        reviewViewModel.toggleMediaItemSelected(mediaItem)
                                    } else
                                        mainViewModel.openInGalleryApp(mediaItem)
                                },
                                onClickLabel =
                                    if (reviewUiState.mediaSelectionEnabled) stringResource(R.string.select_item)
                                    else stringResource(R.string.open_externally_desc),
                                onLongClick = {
                                    if (reviewUiState.mediaSelectionEnabled) {
                                        reviewViewModel.toggleMediaItemSelected(mediaItem)
                                    } else {
                                        reviewViewModel.toggleMediaSelectionEnabled()
                                        reviewViewModel.toggleMediaItemSelected(mediaItem)
                                    }
                                },
                                onLongClickLabel =
                                    if (reviewUiState.mediaSelectionEnabled) stringResource(R.string.select_item)
                                    else stringResource(R.string.enable_media_selection)
                            )
                        ) {
                            AsyncImage(
                                model = mediaItem.uri,
                                contentDescription = null,
                                alignment = Alignment.Center,
                                modifier = Modifier
                                    .scale(imageScale.value)
                                    .padding(dimensionResource(R.dimen.padding_small))

                            )
                            if (reviewUiState.selectedMedia.contains(mediaItem))
                                Icon(
                                    painter = painterResource(R.drawable.check),
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    contentDescription = stringResource(R.string.selected),
                                    modifier = Modifier
                                        .background(
                                            color = MaterialTheme.colorScheme.tertiaryContainer, shape = CircleShape
                                        )
                                        .align(Alignment.TopEnd)
                                        .padding(dimensionResource(R.dimen.padding_xsmall))
                                        .size(dimensionResource(R.dimen.xsmall_icon))
                                )
                        }
                    }
                    item(span = StaggeredGridItemSpan.FullLine) {
                        Spacer(
                            Modifier
                                .height(floatingActionButtonSize.height + 32.dp/*(padding)*/)
                        )
                    }
                }
                if (mainUiState.mediaItems.none { it.status == reviewUiState.currentStatusFilter })
                        Text(
                            text = when (reviewUiState.currentStatusFilter) {
                                MediaStatus.DELETE -> stringResource(R.string.no_deleted_photos)
                                MediaStatus.KEEP -> stringResource(R.string.no_kept_photos)
                                else -> stringResource(R.string.no_snoozed_photos)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .padding(dimensionResource(R.dimen.padding_medium))
                                .fillMaxSize()
                        )
                // Floating actions
                androidx.compose.animation.AnimatedVisibility(
                    visible = reviewUiState.mediaSelectionEnabled,
                    enter =
                        if (reduceAnimations) fadeIn()
                        else slideInVertically(
                            animationSpec = spring(
                                stiffness = Spring.StiffnessLow,
                                dampingRatio = Spring.DampingRatioLowBouncy,
                            ),
                            initialOffsetY = { it * 2 }
                        ),
                    exit =
                        if (reduceAnimations) fadeOut()
                        else slideOutVertically(
                            animationSpec = spring(
                                stiffness = Spring.StiffnessLow,
                                dampingRatio = Spring.DampingRatioLowBouncy,
                            ),
                            targetOffsetY = { it * 2 }
                        ),
                    label = "Review screen actions",
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.BottomStart)
                ) {
                    Box(contentAlignment = Alignment.BottomStart) {
                        Row(
                            modifier = Modifier
                                .dropShadow(
                                    shape = MaterialTheme.shapes.medium,
                                    shadow = Shadow(
                                        radius = 128.dp,
                                        alpha = 0.6f
                                    )
                                )
                                .padding(dimensionResource(R.dimen.padding_medium))
                        ) {
                            FloatingAction(
                                drawableIconId = R.drawable.x,
                                actionTitle = stringResource(R.string.cancel),
                                actionDescription = stringResource(R.string.cancel_selection),
                                onClick = { reviewViewModel.cancelSelection() },
                            )
                            FloatingAction(
                                drawableIconId = R.drawable.selection_all,
                                actionTitle = stringResource(R.string.select_all),
                                actionDescription = null,
                                onClick = {
                                    mainUiState.mediaItems.forEach {
                                        reviewViewModel.toggleMediaItemSelected(
                                            it,
                                            true
                                        )
                                    }
                                },
                            )
                            FloatingAction(
                                drawableIconId = R.drawable.undo,
                                actionTitle = stringResource(R.string.unswipe),
                                actionDescription = stringResource(R.string.unswipe_selected_photos),
                                onClick = {
                                    if (SDK_INT >= Build.VERSION_CODES.R)
                                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    reviewViewModel.markSelectedItemsAsUnset {
                                        mainViewModel.markItem(
                                            status = MediaStatus.UNSET,
                                            index = mainUiState.mediaItems.indexOf(it)
                                        )
                                    }
                                },
                            )

                        }
                    }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = mainViewModel.getMediaToDelete().isNotEmpty()
                            && !reviewUiState.mediaSelectionEnabled
                            && reviewUiState.currentStatusFilter == MediaStatus.DELETE,
                    enter =
                        if (reduceAnimations) fadeIn()
                        else slideInHorizontally(
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMediumLow,
                                dampingRatio = Spring.DampingRatioLowBouncy,
                            ),
                            initialOffsetX = { it * 2 }
                        ),
                    exit =
                        if (reduceAnimations) fadeOut()
                        else slideOutHorizontally(
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMediumLow,
                                dampingRatio = Spring.DampingRatioLowBouncy,
                            ),
                            targetOffsetX = { it * 2 }
                        ),
                    modifier = Modifier
                        .padding(dimensionResource(R.dimen.padding_medium))
                        .fillMaxSize()
                        .align(Alignment.BottomEnd)

                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        ExtendedFloatingActionButton(
                            onClick = { mainViewModel.confirmDeletion() },
                            modifier = Modifier
                                .onGloballyPositioned {
                                    with(density) {
                                        floatingActionButtonSize = it.size.toSize().toDpSize()
                                    }
                                }
                        ) {
                            Icon(painterResource(MediaStatus.DELETE.iconDrawableId), null)
                            Spacer(Modifier.width(dimensionResource(R.dimen.padding_small)))
                            Text("Delete ${mainViewModel.getMediaToDelete().size} items")
                        }
                    }
                }
            }
        }
    // Show screen instructing the user to swipe on photos/videos to see items here
    else
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(dimensionResource(R.dimen.padding_medium))
        ) {
            Icon(
                painter = painterResource(R.drawable.info),
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .padding(bottom = dimensionResource(R.dimen.padding_medium))
            )
            Text(
                stringResource(R.string.no_photos_to_review),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = dimensionResource(R.dimen.padding_small))
            )
            Text(
                stringResource(R.string.swiped_photos_will_show_here),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = dimensionResource(R.dimen.padding_small))
            )
        }
}

@OptIn(ExperimentalKoalaPlotApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun StatsScreen(
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
                    currentMenuItemSelection = currentDataType.toString().lowercase() + " " + currentDataType.extraInfo,
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
                    currentMenuItemSelection = currentTimeFrame.toString().lowercase(),
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
                    yAxisRange,
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
                                        if (currentDataType == StatsData.SPACE_SAVED) barChartEntries()[index].y.yMax.toString() + " MB"
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
                enabled = !uiState.currentDateShown
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
                enabled = !uiState.currentDateShown
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

@Composable
private fun PreferencesScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current
    val dataStoreInterface = DataStoreInterface(context.dataStore)

    var section: PreferencesCategory? by remember { mutableStateOf(null) }
    BackHandler(enabled = section != null) { section = null }
    @Composable
    fun BackButtonListItem() {
        ListItem(
            headlineContent = { Text("Back") },
            leadingContent = { Icon(painterResource(R.drawable.arrow_left), null) },
            modifier = Modifier.clickable {
                section = null
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            }
        )
    }

    AnimatedContent(
        section,
        transitionSpec = {
            if (section != null)
                slideInHorizontally(
                    spring(
                        Spring.DampingRatioNoBouncy,
                        Spring.StiffnessMedium
                    ),
                    { it }
                ).togetherWith(
                    slideOutHorizontally(
                        spring(
                            Spring.DampingRatioNoBouncy,
                            Spring.StiffnessMedium
                        ),
                        targetOffsetX = {-it}
                    ) + fadeOut()
                )
            else
                (slideInHorizontally(
                    spring(
                        Spring.DampingRatioNoBouncy,
                        Spring.StiffnessMedium
                    ),
                    initialOffsetX = {-it/2}
                ) + fadeIn()).togetherWith(
                    slideOutHorizontally(
                        spring(
                            Spring.DampingRatioNoBouncy,
                            Spring.StiffnessMedium
                        ),
                        { it }
                    )
                )
        },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier
                .verticalScroll(rememberScrollState())
                .fillMaxSize()
        ) {
            when (it) {
                PreferencesCategory.APPEARANCE -> {
                    BackButtonListItem()
                    /* System font preference */
                    BooleanPreferenceEditor(
                        dataStoreInterface = dataStoreInterface,
                        preference = BooleanPreference.SYSTEM_FONT
                    )
                    /* Dynamic theme preference */
                    if (SDK_INT >= Build.VERSION_CODES.S)
                        BooleanPreferenceEditor(
                            dataStoreInterface = dataStoreInterface,
                            preference = BooleanPreference.DYNAMIC_THEME
                        )
                    /* Reduce animations preference */
                    BooleanPreferenceEditor(
                        dataStoreInterface = dataStoreInterface,
                        preference = BooleanPreference.REDUCE_ANIMATIONS
                    )
                }

                PreferencesCategory.BEHAVIOUR -> {
                    BackButtonListItem()
                    /* Media items per stack preference */
                    IntPreferenceEditor(
                        dataStoreInterface = dataStoreInterface,
                        preference = IntPreference.NUM_PHOTOS_PER_STACK,
                    )
                    /* Permanently delete preference */
                    if (SDK_INT >= Build.VERSION_CODES.R)
                        BooleanPreferenceEditor(
                            dataStoreInterface = dataStoreInterface,
                            preference = BooleanPreference.PERMANENTLY_DELETE
                        )
                    /* Skip review screen */
                    BooleanPreferenceEditor(
                        dataStoreInterface = dataStoreInterface,
                        preference = BooleanPreference.SKIP_REVIEW
                    )
                    /* Pause background media when video starts playing */
                    BooleanPreferenceEditor(
                        dataStoreInterface = dataStoreInterface,
                        preference = BooleanPreference.PAUSE_BACKGROUND_MEDIA
                    )
                }

                PreferencesCategory.STATISTICS -> {
                    BackButtonListItem()
                    // Statistics Enabled
                    BooleanPreferenceEditor(
                        dataStoreInterface = dataStoreInterface,
                        preference = BooleanPreference.STATISTICS_ENABLED
                    )
                    // Start week on monday
                    BooleanPreferenceEditor(
                        dataStoreInterface = dataStoreInterface,
                        preference = BooleanPreference.START_WEEK_ON_MONDAY
                    )
                    // Clear statistics (always enabled)
                }
                PreferencesCategory.BACKUP_RESTORE -> {
                    BackButtonListItem()

                }
                null -> {
                    Column(
                        verticalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column {
                            for (category in PreferencesCategory.entries) {

                                ListItem(
                                    headlineContent = {
                                        Text(stringResource(category.titleStringId))
                                    },
                                    supportingContent = { Text(stringResource(category.descriptionStringId)) },
                                    leadingContent = {
                                        Icon(
                                            painterResource(category.iconDrawableId),
                                            null
                                        )
                                    },
                                    trailingContent = {
                                        Icon(
                                            painterResource(R.drawable.caret_right),
                                            contentDescription = null
                                        )
                                    },
                                    modifier = Modifier.clickable {
                                        section = category
                                        if (SDK_INT >= Build.VERSION_CODES.R)
                                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    }
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(dimensionResource(R.dimen.padding_medium))
                        ) {
                            FooterLink(
                                icon = painterResource(R.drawable.bug),
                                title = stringResource(R.string.report_bug),
                                link = "https://codeberg.org/Loowiz/PhotoSwooper/issues/new".toUri()
                            )
                            FooterLink(
                                icon = painterResource(R.drawable.heart),
                                title = stringResource(R.string.donate),
                                link = "https://liberapay.com/loowiz".toUri()
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Composable containing a clickable icon & text, used as footer links in [PreferencesScreen] */
@Composable
private fun FooterLink(
    icon: Painter,
    title: String,
    link: Uri
) {
    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(dimensionResource(R.dimen.padding_small))
            .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, link)) }
    ) {
        Icon(icon, null, modifier = Modifier.size(dimensionResource(R.dimen.small_icon)))
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun BooleanPreferenceEditor(
    dataStoreInterface: DataStoreInterface,
    preference: BooleanPreference
) {
    val view = LocalView.current

    val preferenceValue by dataStoreInterface.getBooleanSettingValue(preference.setting).collectAsState(false)

    fun togglePreference() = CoroutineScope(Dispatchers.IO).launch {
        dataStoreInterface.setBooleanSettingValue(
            setting = preference.setting,
            newValue = !preferenceValue
        )
    }

    fun performSwitchHapticFeedback(toggledOn: Boolean) {
        if (SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (toggledOn)
                view.performHapticFeedback(HapticFeedbackConstants.TOGGLE_ON)
            else
                view.performHapticFeedback(HapticFeedbackConstants.TOGGLE_OFF)
        } else if (SDK_INT >= Build.VERSION_CODES.R)
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    // UI
    ListItem(
        leadingContent = {
            Icon(
                painter = painterResource(preference.icon),
                contentDescription = null // Described in adjacent text
            )
        },
        headlineContent = {
            Text(
                stringResource(preference.title)
            )
        },
        trailingContent = {
            Switch(
                checked = preferenceValue,
                onCheckedChange = {
                    togglePreference()
                    performSwitchHapticFeedback(it)
                }
            )
        },
        supportingContent = {
            if (preference.description != null)
                Text(
                    stringResource(preference.description)
                )
        },
        modifier = Modifier.clickable { // Allows user to click on the whole row to toggle
            togglePreference()
            performSwitchHapticFeedback(preferenceValue)
        }
    )

}

@Composable
fun IntPreferenceEditor(
    dataStoreInterface: DataStoreInterface,
    preference: IntPreference
) {
    val context = LocalContext.current
    val view = LocalView.current

    val preferenceValue by dataStoreInterface.getIntSettingValue(preference.setting).collectAsState(0)
    var displayedPreferenceValue by remember { mutableStateOf(preferenceValue.toString()) }
    LaunchedEffect(preferenceValue) {
        displayedPreferenceValue = preferenceValue.toString()
    }

    fun onUpdate(newValue: Int) = CoroutineScope(Dispatchers.IO).launch {
        dataStoreInterface.setIntSettingValue(
            setting = preference.setting,
            newValue = newValue
        )
    }

    fun validateInputAndUpdate(input: String) {
        val inputAsInt = input.toIntOrNull()
        when {
            (inputAsInt != null) -> {
                displayedPreferenceValue = input
                if (inputAsInt in 1..100)
                    onUpdate(inputAsInt)
                else Toast.makeText(context, "Value must be within 1-100", Toast.LENGTH_SHORT).show()
            }

            (input == "") -> displayedPreferenceValue = input
        }
    }
    ListItem(
        leadingContent = {
            Icon(
                painter = painterResource(preference.icon),
                contentDescription = null, // Described in adjacent text
                modifier = Modifier.padding(end = dimensionResource(R.dimen.padding_medium))
            )
        },
        headlineContent = {
            Text(
                text = stringResource(preference.title),
                modifier = Modifier.padding(bottom = dimensionResource(R.dimen.padding_xsmall))
            )
        },
        supportingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = displayedPreferenceValue,
                    isError = (displayedPreferenceValue.toIntOrNull() ?: -1) !in 1..100,
                    onValueChange = { input -> // Update UI value & dataStore only if valid
                        validateInputAndUpdate(input)
                    },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Number
                    ),
                    modifier = Modifier
                        .weight(0.15f)
                )
                Slider(
                    value = displayedPreferenceValue.toFloatOrNull()
                        ?: IntPreference.NUM_PHOTOS_PER_STACK.default.toFloat(),
                    onValueChange = {
                        if (it.roundToInt() != displayedPreferenceValue.toIntOrNull()) {
                            displayedPreferenceValue = it.roundToInt().toString()
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                    },
                    valueRange = 10f..100f,
                    onValueChangeFinished = {
                        onUpdate(displayedPreferenceValue.toInt())
                    },
                    steps = 8,
                    modifier = Modifier
                        .weight(0.5f)
                        .padding(horizontal = dimensionResource(R.dimen.padding_small))
                )
            }
        }
    )
}