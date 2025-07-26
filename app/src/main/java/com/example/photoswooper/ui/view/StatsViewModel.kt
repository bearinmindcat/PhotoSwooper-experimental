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
    
//    private fun getLengthOfMonth(
//        year: Int = Calendar.getInstance().get(Calendar.YEAR),
//        month: Int = Calendar.getInstance().get(Calendar.MONTH)
//    ): Int {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            return YearMonth.of(year, month).lengthOfMonth()
//        } else {
//            val calendar = Calendar.getInstance()
//            calendar.set(Calendar.YEAR, year)
//            calendar.set(Calendar.MONTH, month)
//            return calendar.getActualMaximum(Calendar.DATE)
//        }
//    }

    fun getXAxisRange(): IntRange {
        return when (uiState.value.timeFrame) {
            TimeFrame.DAY -> 1..24
            TimeFrame.WEEK -> 1..7
//            TimeFrame.MONTH -> 1..getLengthOfMonth()
            else -> 1..12 // TimeFrame.YEAR
        }
    }

    fun getNamedXAxisValues(): List<String>? {
        val currentTimeFrame = uiState.value.timeFrame
        return when (currentTimeFrame) {
            TimeFrame.DAY -> (0..23).map { it.toString() } // Original is 1..24
            TimeFrame.WEEK -> daysOfTheWeek
//            TimeFrame.MONTH -> (1..getLengthOfMonth()).map { it.toString() }
            else -> monthsOfTheYear
        }
    }

    suspend fun updateStatsData() { // TODO("Add options for number of: swipes, deletes OR size deleted")
        Log.d("Stats", "Updating stats data")

        val currentTimeFrame = uiState.value.timeFrame
        val intervalMilliseconds: Long
        val calendarAtZero: Calendar // milliseconds until e.g. 0:00 for the day, or 1st day of month
        val finalTimeMillis: Long // epoch milliseconds of the final time group to get data for
        var currentTime: Int // e.g. day of the month, month of the year. This is decremented to find data for each hour/day/month
        val calendar = Calendar.getInstance()

        /* Obtain  values required to fetch data */
        when (currentTimeFrame) {
            TimeFrame.DAY -> {
                intervalMilliseconds = 3600000 // Number of milliseconds in an hour

                calendarAtZero = getCalendarAtZero(Calendar.MINUTE)

                finalTimeMillis = getCalendarAtZero(Calendar.HOUR_OF_DAY).timeInMillis

                currentTime = calendar.get(Calendar.HOUR_OF_DAY) + 1
            }

            TimeFrame.WEEK -> {
                intervalMilliseconds = TimeFrame.DAY.milliseconds

                calendarAtZero = getCalendarAtZero(Calendar.HOUR_OF_DAY)

                finalTimeMillis = getCalendarAtZero(Calendar.DAY_OF_WEEK).timeInMillis

                currentTime = calendar.get(Calendar.DAY_OF_WEEK)
            }

//            TimeFrame.MONTH -> {
//                val selectedDateString = uiState.value.finalDateInTimeFrame.toString()
//                val year = selectedDateString.substring(30, 34).toInt()
//                val month = monthsOfTheYear.indexOf(
//                    selectedDateString.substring(
//                        4,
//                        7
//                    )
//                ) // Convert month word to integer e.g. Feb -> 2
//                intervalMilliseconds = TimeFrame.DAY.milliseconds
//                firstDateMilliseconds = getLengthOfMonth(
//                    year,
//                    month
//                ) * TimeFrame.DAY.milliseconds // More accurate as each month will have a different  number of days
//            }

            else -> { // TimeFrame.YEAR ->
                intervalMilliseconds = TimeFrame.MONTH.milliseconds

                calendarAtZero = getCalendarAtZero(Calendar.DAY_OF_MONTH)

                finalTimeMillis = getCalendarAtZero(Calendar.DAY_OF_YEAR).timeInMillis

                currentTime = calendar.get(Calendar.MONTH) + 1
            }
        }
        Log.v("Stats", "Time at zero: ${calendarAtZero.time}")

        /* Fetch & return data */

        suspend fun getSwipesFromDatabase(firstDateMillis: Long, secondDateMillis: Long): Int =
            mediaStatusDao.getAllBetweenDates(
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

        Log.v("Stats", "setting data for time = $currentTime")
        statsData.set(
            currentTime,
            getSwipesFromDatabase(calendarAtZero.timeInMillis, uiState.value.dateToFetchFromMillis)
        )
        currentTime --
        var firstDateMillis = calendarAtZero.timeInMillis - intervalMilliseconds
        var secondDateMillis = calendarAtZero.timeInMillis

        Log.v("Stats", "firstDateMillis = $firstDateMillis, finalTimeMillis = $finalTimeMillis")
        while (firstDateMillis >= finalTimeMillis) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.v("Stats", "getting data for ${Instant.ofEpochSecond(firstDateMillis)} to ${Instant.ofEpochSecond(secondDateMillis)}")
            }
            statsData.set(
                currentTime,
                getSwipesFromDatabase(firstDateMillis, secondDateMillis)
            )
            currentTime --
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

    private fun getCalendarAtZero(finalZeroedJavaTime: Int): Calendar {
        val tempCalendar = Calendar.getInstance()


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

    fun updateTimeFrame(newTimeFrame: TimeFrame) {
        _uiState.update { currentState ->
            currentState.copy(
                timeFrame = newTimeFrame
            )
        }
    }

}