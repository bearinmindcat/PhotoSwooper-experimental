package com.example.photoswooper.ui.components

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.photoswooper.R
import com.example.photoswooper.dataStore
import com.example.photoswooper.utils.DataStoreInterface
import io.github.koalaplot.core.ChartLayout
import io.github.koalaplot.core.bar.*
import io.github.koalaplot.core.util.ExperimentalKoalaPlotApi
import io.github.koalaplot.core.xygraph.FloatLinearAxisModel
import io.github.koalaplot.core.xygraph.XYGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private enum class TabIndex {
    STATS, SETTINGS
}

@Composable
fun TabbedPreferencesAndStatsPage(modifier: Modifier = Modifier) {
    var tabIndex by remember { mutableStateOf(TabIndex.STATS.ordinal) }
    Column(modifier) {
        TabRow(
            selectedTabIndex = tabIndex,
            containerColor = Color(0f, 0f, 0f, 0f),
            modifier = Modifier.padding(vertical = dimensionResource(R.dimen.padding_medium))
        ) {
            Tab(
                selected = (tabIndex == TabIndex.STATS.ordinal),
                onClick = { tabIndex = TabIndex.STATS.ordinal }
            ) {
                Icon(
                    painter = painterResource(R.drawable.chart),
                    contentDescription = stringResource(R.string.show_stats),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    stringResource(R.string.statistics),
//                    style = MaterialTheme.typography.headlineSmall
                )
            }
            Tab(
                selected = (tabIndex == TabIndex.SETTINGS.ordinal),
                onClick = { tabIndex = TabIndex.SETTINGS.ordinal }
            ) {
                Icon(
                    painter = painterResource(R.drawable.gear),
                    contentDescription = stringResource(R.string.show_settings),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    stringResource(R.string.settings),
//                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
        when (tabIndex) {
            TabIndex.STATS.ordinal -> {
                StatsCard()
            }

            TabIndex.SETTINGS.ordinal -> {
                PreferencesCard()
            }
        }
    }
}

@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
private fun StatsCard() {
    ChartLayout(
        modifier = Modifier,
        title = { Text("Title") }
    ) {
        val YAxisRange = 0f..25f
        val XAxisRange = 0.5f..8.5f
        fun barChartEntries(): List<VerticalBarPlotEntry<Float, Float>> {
            return buildList {
                for (index in 1..100) {
                    add(DefaultVerticalBarPlotEntry((index + 1).toFloat(), DefaultVerticalBarPosition(0f, index.toFloat())))
                }
            }
        }

        XYGraph(
            xAxisModel = FloatLinearAxisModel(
                XAxisRange,
            ),
            yAxisModel = FloatLinearAxisModel(
                YAxisRange,
            ),
        ) {
            VerticalBarPlot(
                data = barChartEntries(),
                bar = { index ->
                    DefaultVerticalBar(
                        brush = SolidColor(Color.LightGray),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Surface(
                            shadowElevation = 2.dp,
                            shape = MaterialTheme.shapes.medium,
                            color = Color.LightGray,
                            modifier = Modifier.padding()
                        ) {
                            Box(modifier = Modifier.padding()) {
                                Text("hi")
                            }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun PreferencesCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val dataStoreInterface = DataStoreInterface(context.dataStore)

    val numPhotosPerStackPref: Int?
    runBlocking(Dispatchers.IO) {
        numPhotosPerStackPref = dataStoreInterface.getIntSettingValue("num_photos_per_stack").first()
    }
    var numPhotosPerStack by remember { mutableStateOf(numPhotosPerStackPref) }

    val permanentlyDeletePref: Boolean?
    runBlocking {
        permanentlyDeletePref = dataStoreInterface.getBooleanSettingValue("permanently_delete").first()
    }
    var displayedPermanentlyDelete by remember { mutableStateOf(permanentlyDeletePref) }

    Card(modifier.verticalScroll(rememberScrollState())) {
        ListItem(
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.images),
                    contentDescription = null // Described in adjacent text
                )
            },
            headlineContent = {
                Text(
                    text = stringResource(R.string.num_photos_per_stack)
                )
            },
            supportingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.padding_medium))
                ) {
                    /* FIXME("number shows nothing on first start. Should show 30 as default") */
                    BasicTextField(
                        value = (numPhotosPerStack ?: "").toString(),
                        onValueChange = { // Update UI value & dataStore only if valid
                            try {
                                if (it == "")
                                    numPhotosPerStack = null
                                else if (it.toInt() in 1..100) {
                                    numPhotosPerStack = it.toInt()
                                    CoroutineScope(Dispatchers.IO).launch {
                                        dataStoreInterface.setIntSettingValue(
                                            setting = "num_photos_per_stack",
                                            newValue = numPhotosPerStack ?: 0
                                        )
                                    }
                                }
                            } catch (_: NumberFormatException) {
                                Toast.makeText(
                                    context,
                                    "Not a number :(",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        keyboardOptions = KeyboardOptions.Default.copy(
                            keyboardType = KeyboardType.Number
                        ),
                        modifier = Modifier
                            .weight(0.1f)
                    )
                    Slider(
                        value = numPhotosPerStack?.toFloat() ?: 30f,
                        onValueChange = { numPhotosPerStack = it.toInt() },
                        valueRange = 1f..100f,
                        onValueChangeFinished = {
                            CoroutineScope(Dispatchers.IO).launch {
                                dataStoreInterface.setIntSettingValue(
                                    setting = "num_photos_per_stack",
                                    newValue = numPhotosPerStack ?: 30
                                )
                            }
                        },
                        modifier = Modifier.weight(0.5f)
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
                    onCheckedChange = {
                        displayedPermanentlyDelete = it
                        CoroutineScope(Dispatchers.IO).launch {
                            dataStoreInterface.setBooleanSettingValue(
                                setting = "permanently_delete",
                                newValue = displayedPermanentlyDelete ?: false
                            )
                        }
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
            }
        )
    }
}