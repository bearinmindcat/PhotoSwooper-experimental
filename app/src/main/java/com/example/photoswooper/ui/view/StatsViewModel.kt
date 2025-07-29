package com.example.photoswooper.ui.view

import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import com.example.photoswooper.data.database.MediaStatusDao
import com.example.photoswooper.data.uistates.StatsUiState
import com.example.photoswooper.data.uistates.TimeFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.util.*

class StatsViewModel(
//    val contentResolverInterface: ContentResolverInterface,
    val mediaStatusDao: MediaStatusDao,
): ViewModel() {
    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState = _uiState.asStateFlow()

    val daysOfTheWeek = listOf("Sun","Mon","Tue","Wed","Thu","Fri","Sat")
    val monthsOfTheYear = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")

    fun getXAxisRange(): IntRange {
        return when (uiState.value.timeFrame) {
            TimeFrame.DAY -> (1..24)
            TimeFrame.WEEK -> 1..7
//            TimeFrame.MONTH -> 1..getLengthOfMonth()
            else -> 1..12 // TimeFrame.YEAR
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

    suspend fun updateStatsData() { // TODO("Add options for number of: swipes, deletes OR size deleted")
        Log.d("Stats", "Updating stats data")

        val currentTimeFrame = uiState.value.timeFrame
        val intervalMilliseconds: Long
        val calendarAtZero: Calendar // Java Calendar class at e.g. 0:00 for the day, or 1st day of month
        val finalTimeMillis: Long // epoch milliseconds of the final time group to get data for
        var currentXValue: Int // e.g. day of the month, month of the year. This is decremented to find data for each hour/day/month
        val calendar = Calendar.getInstance()

        /* This function returns a Java calendar class at e.g. 0:00 for the day, or 1st day of month, depending on the arguement */
        fun getCalendarAtZero(finalZeroedJavaTime: Int): Calendar {
            val tempCalendar = Calendar.getInstance()
            tempCalendar.timeInMillis = uiState.value.dateToFetchFromMillis


            val selectedJavaTimeFrames = listOf( // These time frames, when all minimised (zeroed), is exactly 00:00 on the first day of the year
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
            var currentJavaTimeFrame = selectedJavaTimeFrames[timeFrameIndex]
            while (finalZeroedJavaTimeIndex <= timeFrameIndex) {
                currentJavaTimeFrame = selectedJavaTimeFrames[timeFrameIndex]
                tempCalendar.set(
                    currentJavaTimeFrame,
                    tempCalendar.getMinimum(currentJavaTimeFrame)
                )
                timeFrameIndex --
            }
            return tempCalendar
        }

        /* Obtain  values required to fetch data */
        when (currentTimeFrame) {
            TimeFrame.DAY -> {
                intervalMilliseconds = 3600000 // Number of milliseconds in an hour

                calendarAtZero = getCalendarAtZero(Calendar.MINUTE)

                finalTimeMillis = getCalendarAtZero(Calendar.HOUR_OF_DAY).timeInMillis

                currentXValue = calendar.get(Calendar.HOUR_OF_DAY) + 1
            }

            TimeFrame.WEEK -> {
                intervalMilliseconds = TimeFrame.DAY.milliseconds

                calendarAtZero = getCalendarAtZero(Calendar.HOUR_OF_DAY)

                finalTimeMillis = getCalendarAtZero(Calendar.DAY_OF_WEEK).timeInMillis

                currentXValue = calendar.get(Calendar.DAY_OF_WEEK)
            }

            else -> { // TimeFrame.YEAR ->
                intervalMilliseconds = TimeFrame.MONTH.milliseconds

                calendarAtZero = getCalendarAtZero(Calendar.DAY_OF_MONTH)

                finalTimeMillis = getCalendarAtZero(Calendar.DAY_OF_YEAR).timeInMillis

                currentXValue = calendar.get(Calendar.MONTH) + 1
            }
        }
        Log.v("Stats", "Time at zero: ${calendarAtZero.time}")

        /* Fetch & return data */

        suspend fun getSwipesFromDatabase(firstDateMillis: Long, secondDateMillis: Long): Int =
            mediaStatusDao.getSwipedMediaBetweenDates(
                firstDateMillis,
                secondDateMillis,
            )?.size ?: 0

        val statsData = mutableMapOf<Int, Int>()
        val numGroups = getXAxisRange().last

        for (dateIndex in 1..numGroups) {
            statsData.put(
                dateIndex,
                0
            )
        }
        Log.v("Stats", "data = ${statsData}")

        Log.v("Stats", "setting data for time = $currentXValue")
        statsData.set(
            currentXValue,
            getSwipesFromDatabase(calendarAtZero.timeInMillis, uiState.value.dateToFetchFromMillis)
        )
        currentXValue --
        var firstDateMillis = calendarAtZero.timeInMillis - intervalMilliseconds
        var secondDateMillis = calendarAtZero.timeInMillis

        Log.v("Stats", "firstDateMillis = $firstDateMillis, finalTimeMillis = $finalTimeMillis")
        while (firstDateMillis >= finalTimeMillis) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.v("Stats", "getting data for ${Instant.ofEpochSecond(firstDateMillis)} to ${Instant.ofEpochSecond(secondDateMillis)}")
            }
            statsData.set(
                currentXValue,
                getSwipesFromDatabase(firstDateMillis, secondDateMillis)
            )
            currentXValue --
            firstDateMillis -= intervalMilliseconds
            secondDateMillis -= intervalMilliseconds
        }
        Log.v("Stats", "final stats data = ${statsData}")
        _uiState.update { currentState ->
            currentState.copy(
                latestData = statsData
            )
        }
    }

    fun updateTimeFrame(newTimeFrame: TimeFrame) {
        _uiState.update { currentState ->
            currentState.copy(
                timeFrame = newTimeFrame
            )
        }
        resetDate()
    }

    /* Move date to fetch stats data to one day/week/year in the past (amount depends on current time frame) */
    fun previousDate() {
        _uiState.update { currentState ->
            val newDateToFetchFromMillis = currentState.dateToFetchFromMillis - currentState.timeFrame.milliseconds
            currentState.copy(
                dateToFetchFromMillis = newDateToFetchFromMillis,
                currentDateShown = Calendar.getInstance().timeInMillis.floorDiv(10000)
                        == newDateToFetchFromMillis.floorDiv(10000) // currentTime ≈ newTime
            )
        }
    }

    /* Move date to fetch stats data to one day/week/year in the future (amount depends on current time frame) */
    fun nextDate(): Boolean {
        val newDateToFetchFromMillis = uiState.value.dateToFetchFromMillis + uiState.value.timeFrame.milliseconds
        if (newDateToFetchFromMillis < Calendar.getInstance().timeInMillis) { // If the new date is not in the future
            _uiState.update { currentState ->
                currentState.copy(
                    dateToFetchFromMillis = newDateToFetchFromMillis,
                    currentDateShown = Calendar.getInstance().timeInMillis.floorDiv(10000)
                            == newDateToFetchFromMillis.floorDiv(10000) // currentTime ≈ newTime
                )
            }
            return true
        }
        else return false
    }
    /* Move date to fetch stats data to the current date */
    fun resetDate() {
        _uiState.update { currentState ->
            currentState.copy(
                dateToFetchFromMillis = Calendar.getInstance().timeInMillis,
                currentDateShown = true
            )
        }
    }

}