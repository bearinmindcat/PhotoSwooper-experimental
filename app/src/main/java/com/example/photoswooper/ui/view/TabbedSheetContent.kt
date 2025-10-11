/*
 * SPDX-FileCopyrightText: 2025 Loowiz <loowiz@envs.net>
 *
 *  SPDX-License-Identifier: AGPL-3.0-only
 */

package com.example.photoswooper.ui.view

import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.photoswooper.R
import com.example.photoswooper.data.uistates.BooleanPreference
import com.example.photoswooper.dataStore
import com.example.photoswooper.ui.viewmodels.MainViewModel
import com.example.photoswooper.ui.viewmodels.StatsViewModel
import com.example.photoswooper.utils.DataStoreInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


enum class TabIndex {
    REVIEW, STATS, SETTINGS
}

private const val fractionOfScreenForContent = 0.9f

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
        // Calculate direction of tab movement for content slide animation direction
        tabIndexChange = newTabIndex.ordinal - tabIndex
        updateTabIndex(newTabIndex.ordinal)
        if (SDK_INT >= Build.VERSION_CODES.R) {
            if (tabIndexChange != 0
                || mainViewModel.bottomSheetScaffoldState.bottomSheetState.currentValue == SheetValue.PartiallyExpanded
            )
                view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
            else
                view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        }
        expandBottomSheet(currentCoroutineScope)
        // Expand, then shrink the tab indicator while it is moving for a smooth animation
        currentCoroutineScope.launch {
            delay(10)
            tabIndicatorWidth = 40.dp
            delay(100)
            tabIndicatorWidth = 24.dp
        }
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

    BackHandler(mainViewModel.bottomSheetScaffoldState.bottomSheetState.targetValue == SheetValue.Expanded) {
        currentCoroutineScope.launch { mainViewModel.bottomSheetScaffoldState.bottomSheetState.partialExpand() }
    }

    Column(modifier) {
        PrimaryTabRow(
            selectedTabIndex = tabIndex,
            containerColor = Color(0f, 0f, 0f, 0f),
            indicator = {
                TabRowDefaults.PrimaryIndicator(
                    width = animatedTabIndicatorWidth.value,
                    modifier = Modifier
                        .tabIndicatorOffset(
                            if (tabIndex == 2 && !statisticsEnabled) 1
                                    else tabIndex
                        )
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
            modifier = Modifier.fillMaxHeight(fractionOfScreenForContent) // Prevents bottom sheet drag handle being hidden by status bar due to height
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
                    PreferencesScreen()
                }
            }
        }
    }
}
