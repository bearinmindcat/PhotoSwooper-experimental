/*
 * SPDX-FileCopyrightText: 2025 Loowiz <loowiz@envs.net>
 *
 *  SPDX-License-Identifier: AGPL-3.0-only
 */

package com.example.photoswooper.ui.viewmodels

import android.text.format.DateUtils
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.photoswooper.data.database.MediaStatusDao
import com.example.photoswooper.data.uistates.StatsData
import com.example.photoswooper.data.uistates.StatsUiState
import com.example.photoswooper.data.uistates.TimeFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.RoundingMode
import java.util.Calendar
import kotlin.math.roundToLong

/**
 * The viewModel used by the `StatsScreen` function in [com.example.photoswooper.ui.view.TabbedSheetContent]
 */
class StatsViewModel(
    val mediaStatusDao: MediaStatusDao,
    val formatDateTime: (millis: Long, flags: Int) -> String,
    val formatDateTimeRange: (startMillis: Long, endMillis: Long, flags: Int) -> String
) : ViewModel() {
    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState = _uiState.asStateFlow()

    val daysOfTheWeek = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val monthsOfTheYear = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    fun getXAxisRange(): IntRange {
        return when (uiState.value.timeFrame) {
            TimeFrame.DAY -> (0..23)
            TimeFrame.WEEK -> 0..6
            else -> 0..11 // TimeFrame.YEAR
        }
    }

    fun getNamedXAxisValues(): List<String>? {
        val currentTimeFrame = uiState.value.timeFrame
        return when (currentTimeFrame) {
            TimeFrame.DAY -> (0..23).map {
                if (it.mod(3) == 0) it.toString()
                else " ".repeat(it) // Repeat to make graph think each one is a different category
            } // Original is 1..24
            TimeFrame.WEEK -> daysOfTheWeek
            else -> monthsOfTheYear
        }
    }

    /**
     * Updates the [uiState] with the latest statistics data
     */
    suspend fun updateStatsData() {
        Log.i("Stats", "Updating stats data")

        val currentTimeFrame = uiState.value.timeFrame

        /** milliseconds to increment by to get next set of data */
        val intervalMilliseconds: Long


        /** Calendar field to zero & max e.g. Calendar.HOUR_OF_DAY to get the start & end of each day for the week timeframe */
        val fieldToZeroAndMaxOnIteration: Int

        /** Calendar field to zero & max to find the start & end of the current time frame */
        val fieldToZeroAndMaxForTimeFrame: Int

        /** The current x-axis value for the data being fetched e.g. when fetching data for 2am, currentXValue would be 2.*/
        var currentXValue: Int

        /* Obtain  values required to fetch data */
        when (currentTimeFrame) {
            TimeFrame.DAY -> {
                intervalMilliseconds = 3600000 // Number of milliseconds in an hour
                fieldToZeroAndMaxOnIteration = Calendar.MINUTE
                fieldToZeroAndMaxForTimeFrame = Calendar.HOUR_OF_DAY
                currentXValue = 0 // First hour of day
            }

            TimeFrame.WEEK -> {
                intervalMilliseconds = TimeFrame.DAY.milliseconds
                fieldToZeroAndMaxOnIteration = Calendar.HOUR_OF_DAY
                fieldToZeroAndMaxForTimeFrame = Calendar.DAY_OF_WEEK
                currentXValue = 1 // First day of week
            }

            else -> { // TimeFrame.YEAR ->
                intervalMilliseconds = TimeFrame.MONTH.milliseconds
                fieldToZeroAndMaxOnIteration = Calendar.DAY_OF_MONTH
                fieldToZeroAndMaxForTimeFrame = Calendar.DAY_OF_YEAR
                currentXValue = 0 // First month of year (classed as 0)
            }
        }

        /* Fetch & return data */

        suspend fun getDataFromDatabase(firstDateMillis: Long, secondDateMillis: Long): Float {
            return when (uiState.value.dataType) {
                StatsData.SWIPE_COUNT -> mediaStatusDao.getSwipedMediaBetweenDates(
                    firstDateMillis,
                    secondDateMillis
                ).size.toFloat()

                StatsData.DELETED_COUNT -> mediaStatusDao.getDeletedBetweenDates(
                    firstDateMillis,
                    secondDateMillis
                ).size.toFloat()

                StatsData.SPACE_SAVED -> mediaStatusDao.getDeletedBetweenDates(firstDateMillis, secondDateMillis)
                    .sumOf { it.size }.toInt().div(1000000f)
                    .toBigDecimal().setScale(2, RoundingMode.HALF_UP)
                    .toFloat() // div 1000000 to convert to MB TODO("May need to adjust depending on max  value")
            }
        }

        val statsData = mutableListOf<Float>() // Initialise

        var firstDateMillis = getCalendarAtZero(
            finalZeroedJavaTime = fieldToZeroAndMaxForTimeFrame,
            dateMillisToZero = uiState.value.dateToFetchFromMillis
        ).timeInMillis
        var secondDateMillis = getCalendarAtZero(
            finalZeroedJavaTime = fieldToZeroAndMaxOnIteration,
            dateMillisToZero = firstDateMillis + intervalMilliseconds.times(1.5f)
                .roundToLong() // zeroed time of next date = max time of this date
        ).timeInMillis
        val finalTimeInMillis = getCalendarAtZero(
            finalZeroedJavaTime = fieldToZeroAndMaxForTimeFrame,
            dateMillisToZero = uiState.value.dateToFetchFromMillis + currentTimeFrame.milliseconds // zeroed time of next date = max time of this date
        ).timeInMillis

        Log.d("Stats", "firstDate = ${formatDateTime(firstDateMillis, 0)} at ${formatDateTime(firstDateMillis,
            DateUtils.FORMAT_SHOW_TIME)}," +
                " finalTime = ${formatDateTime(finalTimeInMillis, 0)} at ${formatDateTime(finalTimeInMillis, DateUtils.FORMAT_SHOW_TIME)}")
        while (secondDateMillis <= finalTimeInMillis) {
            Log.v("Stats", "getting data for ${formatDateTime(firstDateMillis, 0)} at ${formatDateTime(firstDateMillis,
                DateUtils.FORMAT_SHOW_TIME)} to ${formatDateTime(secondDateMillis, 0)} at ${formatDateTime(secondDateMillis,
                DateUtils.FORMAT_SHOW_TIME)}")
            statsData.add(
                getDataFromDatabase(firstDateMillis, secondDateMillis)
            )

            /* Increment values */
            currentXValue++

            firstDateMillis = getCalendarAtZero(
                // Must be zeroed each time as months have varying numbers of days/milliseconds.
                dateMillisToZero = firstDateMillis + intervalMilliseconds.times(1.5f).roundToLong(),
                finalZeroedJavaTime = fieldToZeroAndMaxOnIteration,
            ).timeInMillis

            secondDateMillis = getCalendarAtZero(
                // Must be zeroed each time as months have varying numbers of days/milliseconds.
                dateMillisToZero = secondDateMillis + intervalMilliseconds.times(1.5f).roundToLong(),
                finalZeroedJavaTime = fieldToZeroAndMaxOnIteration,
            ).timeInMillis
        }
        Log.i("Stats", "final stats data = $statsData")
        _uiState.update { currentState ->
            currentState.copy(
                latestData = statsData
            )
        }
    }

    /** Returns a Java calendar class at e.g. 0:00 for the day, or 1st day of month, depending on the arguement */
    fun getCalendarAtZero(
        finalZeroedJavaTime: Int,
        dateMillisToZero: Long = uiState.value.dateToFetchFromMillis,
    ): Calendar {
        val calendarToZero = Calendar.getInstance()
        calendarToZero.timeInMillis = dateMillisToZero

        val selectedJavaTimeFrames =
            listOf( // These time frames, when all minimised (zeroed), is exactly 00:00 on the first day of the year
                Calendar.DAY_OF_YEAR, // This is used to find the final day to fetch data for when the selected time frame is week
                Calendar.DAY_OF_MONTH,
                Calendar.DAY_OF_WEEK, // This is used to find the final day to fetch data for when the selected time frame is week
                Calendar.HOUR_OF_DAY,
                Calendar.MINUTE,
                Calendar.SECOND,
                Calendar.MILLISECOND
            )
        val finalZeroedJavaTimeIndex = selectedJavaTimeFrames.indexOf(finalZeroedJavaTime)

        var timeFrameIndex = selectedJavaTimeFrames.lastIndex
        var currentJavaTimeFrame: Int
        while (finalZeroedJavaTimeIndex <= timeFrameIndex) {
            currentJavaTimeFrame = selectedJavaTimeFrames[timeFrameIndex]
            calendarToZero.set(
                currentJavaTimeFrame,
                calendarToZero.getMinimum(currentJavaTimeFrame)
            )
            timeFrameIndex--
        }
        return calendarToZero
    }

    fun updateTimeFrame(newTimeFrame: TimeFrame) {
        _uiState.update { currentState ->
            currentState.copy(
                timeFrame = newTimeFrame
            )
        }
    }

    /** Changes date to fetch data from to one day/week/year in the past (amount depends on current time frame),
     * prompting stats update */
    fun previousDate() {
        _uiState.update { currentState ->
            val newDateToFetchFromMillis = currentState.dateToFetchFromMillis - currentState.timeFrame.milliseconds
            currentState.copy(
                dateToFetchFromMillis = newDateToFetchFromMillis
            )
        }
        checkCurrentDateShown()
    }

    /** Changes date to fetch data from to one day/week/year in the future (amount depends on current time frame),
     * prompting stats update */
    fun nextDate(): Boolean {
        val newDateToFetchFromMillis = uiState.value.dateToFetchFromMillis + uiState.value.timeFrame.milliseconds
        if (newDateToFetchFromMillis < Calendar.getInstance().timeInMillis) { // If the new date is not in the future
            _uiState.update { currentState ->
                currentState.copy(
                    dateToFetchFromMillis = newDateToFetchFromMillis
                )
            }
            checkCurrentDateShown()
            return true
        } else return false
    }
    /* Move date to fetch stats data to the current date */
    /** Changes date to fetch data from to current date,
     * prompting stats update */
    fun resetDate() {
        _uiState.update { currentState ->
            currentState.copy(
                dateToFetchFromMillis = Calendar.getInstance().timeInMillis,
                currentDateShown = true
            )
        }
    }

    private fun checkCurrentDateShown() {
        _uiState.update { currentState ->
            currentState.copy(
                currentDateShown = Calendar.getInstance().timeInMillis.floorDiv(86000000)
                        == currentState.dateToFetchFromMillis.floorDiv(86000000) // currentTime ≈ newTime
            )
        }
    }

    /** Returns a human-readable string indicating the date that statistics are showing */
    fun getDateTitle(): String {
        val dateToFetchFromMillis = uiState.value.dateToFetchFromMillis
        when (val currentTimeFrame = uiState.value.timeFrame) {
            TimeFrame.DAY -> {
                return formatDateTime(dateToFetchFromMillis, DateUtils.FORMAT_SHOW_WEEKDAY) + ", " +
                        formatDateTime(dateToFetchFromMillis, DateUtils.FORMAT_SHOW_DATE)
            }

            TimeFrame.WEEK -> {
                val startOfWeekMillis = getCalendarAtZero(Calendar.DAY_OF_WEEK).timeInMillis
                val endOfWeekMillis = getCalendarAtZero(
                    Calendar.DAY_OF_WEEK,
                    dateMillisToZero = dateToFetchFromMillis + currentTimeFrame.milliseconds
                ).timeInMillis - TimeFrame.DAY.milliseconds

                return formatDateTimeRange(
                    startOfWeekMillis,
                    endOfWeekMillis,
                    0
                )
            }

            else -> { // TimeFrame.YEAR ->
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = dateToFetchFromMillis
                return calendar.get(Calendar.YEAR).toString()
            }
        }
    }

    fun updateDataType(newDataType: StatsData) {
        _uiState.update { currentState ->
            currentState.copy(
                dataType = newDataType
            )
        }
        viewModelScope.launch {
            updateStatsData()
        }
    }

}