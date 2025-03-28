package com.example.photoswooper.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import io.github.koalaplot.core.ChartLayout
import io.github.koalaplot.core.bar.*
import io.github.koalaplot.core.util.ExperimentalKoalaPlotApi
import io.github.koalaplot.core.xygraph.FloatLinearAxisModel
import io.github.koalaplot.core.xygraph.XYGraph

@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
fun StatsCard(modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
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
                                        modifier = modifier.padding()
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
}