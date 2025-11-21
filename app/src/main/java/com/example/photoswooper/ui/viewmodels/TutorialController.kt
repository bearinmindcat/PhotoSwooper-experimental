package com.example.photoswooper.ui.viewmodels

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.ViewModel
import com.example.photoswooper.R
import com.example.photoswooper.data.IntPreference
import com.example.photoswooper.data.models.Media
import com.example.photoswooper.data.models.MediaSortField
import com.example.photoswooper.data.models.MediaStatus
import com.example.photoswooper.data.models.MediaType
import com.example.photoswooper.data.uistates.StatsData
import com.example.photoswooper.data.uistates.TimeFrame
import com.example.photoswooper.data.uistates.TutorialUiState
import com.example.photoswooper.ui.view.TabIndex
import com.example.photoswooper.utils.DataStoreInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TutorialController(
    val dataStoreInterface: DataStoreInterface
): ViewModel() {

    private val _uiState = MutableStateFlow(TutorialUiState())
    val uiState = _uiState.asStateFlow()

    fun updateTutorialIndex(newIndex: Int) {
        if (newIndex > 0) {
            CoroutineScope(Dispatchers.IO).launch {
                dataStoreInterface.setIntSettingValue(
                    newIndex,
                    IntPreference.TUTORIAL_INDEX.setting
                )
            }
        }
    }

    private fun updateTutorialCardContent(title: String, body: AnnotatedString, iconDrawableId: Int) {
        _uiState.update { currentState ->
            currentState.copy(
                tutorialCardIconDrawableId = iconDrawableId,
                tutorialCardTitle = title,
                tutorialCardBody = body
            )
        }
    }

    /** Controls the tutorial index based on user interaction*/
    @OptIn(ExperimentalMaterial3Api::class)
    suspend fun awaitUserInteractionAndControlTutorial(
        tutorialIndex: Int,
        mainViewModel: MainViewModel,
        context: Context,
        currentMediaItem: Media?,
        tertiaryColor: Color,
        coroutineScope: CoroutineScope,
        updateSheetContentTabIndex: (Int) -> Unit,
        statsViewModel: StatsViewModel
    ) {

        fun onStepComplete(showToast: Boolean = true) {
            Log.i("Tutorial", "Step complete, seeking to index ${tutorialIndex + 1}")
            if (showToast)
                Toast.makeText(
                    context,
                    "Nice! :) \uD83C\uDF89",
                    Toast.LENGTH_SHORT
                ).show()
            updateTutorialIndex(tutorialIndex + 1)
        }

        when (tutorialIndex) {
            // Swipe right to keep
            1 -> {
                Log.d("Tutorial", "Current tutorial index = $tutorialIndex")
                updateTutorialCardContent(
                    iconDrawableId = R.drawable.bookmark_simple,
                    title = context.getString(R.string.tutorial_swipe_right_title),
                    body = buildAnnotatedString {
                        append(context.getString(R.string.tutorial_swipe_right_desc) + "\n\n")
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append(context.getString(R.string.try_it))
                        pop()
                    }
                )
                while ((mainViewModel.uiState.value.mediaItems.getOrElse(mainViewModel.uiState.value.currentIndex - 1, { currentMediaItem })?.status
                        ?: MediaStatus.DELETE) != MediaStatus.KEEP
                ) {
                    delay(500)
                }
                onStepComplete()
            }
            // Swipe left to mark item to be deleted
            2 -> {
                Log.d("Tutorial", "Current tutorial index = $tutorialIndex")
                updateTutorialCardContent(
                    iconDrawableId = R.drawable.trash,
                    title = context.getString(R.string.tutorial_swipe_left_title),
                    body = buildAnnotatedString {
                        append(context.getString(R.string.tutorial_swipe_left_desc) + "\n\n")
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append(context.getString(R.string.try_it))
                        pop()
                    }
                )
                while ((mainViewModel.uiState.value.mediaItems.getOrElse(mainViewModel.uiState.value.currentIndex - 1, { currentMediaItem })?.status
                        ?: MediaStatus.KEEP) != MediaStatus.DELETE
                ) {
                    delay(500)
                }
                onStepComplete()
            }
            // Tap on the photo/video to view more actions. See the wiki for more info on each of these.
            3 -> {
                updateTutorialCardContent(
                    iconDrawableId = R.drawable.info,
                    title = context.getString(R.string.tutorial_tap_title),
                    body = buildAnnotatedString {
                        append(context.getString(R.string.see) + " ")
                        pushLink(LinkAnnotation.Url("https://codeberg.org/Loowiz/PhotoSwooper/wiki/Extra-actions"))
                        pushStyle(SpanStyle(color = tertiaryColor, textDecoration = TextDecoration.Underline))
                        append(context.getString(R.string.the_wiki))
                        pop()
                        pop()
                        append(" " + context.getString(R.string.for_info_about_these_actions) + "\n\n")
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append(context.getString(R.string.try_it))
                        pop()
                    },
                )
                delay(500)
                mainViewModel.toggleInfoAndFloatingActionsRow(false)
                delay(500)
                while (!mainViewModel.uiState.value.showInfoAndFloatingActionsRow) {
                    delay(500)
                }
                delay(1000) // Delay so user doesn't think that tapping brings up bottom sheet (seeking to next step does this)
                onStepComplete()
            }
            // Undo button to undo the previous swipe (bring the swiped photo/video back)
            4 -> {
                // If first start-up, wait for media items to be fetched
                while (mainViewModel.uiState.value.mediaItems.size < 3) {
                    delay(200)
                }
                if (mainViewModel.uiState.value.currentIndex == 0) {
                    mainViewModel.markItem(MediaStatus.DELETE)
                    mainViewModel.next()
                    delay(200) // Allow time to update currentIndex
                }
                val initialIndex = mainViewModel.uiState.value.currentIndex
                updateTutorialCardContent(
                    iconDrawableId = R.drawable.undo,
                    title = context.getString(R.string.tutorial_bottom_sheet_title),
                    body = buildAnnotatedString {
                        append(
                            context.getString(R.string.tutorial_bottom_sheet_desc) +
                                    "\n\n"
                        )
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append(context.getString(R.string.tutorial_bottom_sheet_try))
                        pop()
                    }
                )
                while (mainViewModel.uiState.value.currentIndex != 0) {
                    delay(500)
                }
                onStepComplete()
            }
            // Filter button shows a dialog to choose what you media want to see (e.g. swipe only videos)
            5 -> {
                delay(500)
                mainViewModel.bottomSheetScaffoldState.bottomSheetState.partialExpand()
                updateTutorialCardContent(
                    iconDrawableId = R.drawable.funnel,
                    title = context.getString(R.string.tutorial_filter_sort_title),
                    body = buildAnnotatedString {
                        append(context.getString(R.string.tutorial_filter_sort_desc) + "\n\n")
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append(context.getString(R.string.tutorial_filter_sort_try))
                        pop()
                    }
                )
                while (
                    mainViewModel.mediaFilter.value.sortAscending
                    || mainViewModel.mediaFilter.value.mediaTypes != setOf(MediaType.PHOTO)
                    || mainViewModel.mediaFilter.value.sortField != MediaSortField.DATE
                ) {
                    delay(500)
                }
                onStepComplete()
            }
            // Review screen shows media marked as deleted. To actually delete media, click the delete button in the bottom right
            6 -> {
                // If first start-up, wait for media items to be fetched
                while (mainViewModel.uiState.value.mediaItems.size < 3) {
                    delay(200)
                }
                delay(400)
                if (mainViewModel.getMediaToDelete().isEmpty()) {
                    mainViewModel.markItem(MediaStatus.DELETE)
                    mainViewModel.next()
                    mainViewModel.markItem(MediaStatus.DELETE)
                    mainViewModel.next()
                }
                delay(1000)
                val initialNumToDelete = mainViewModel.getMediaToDelete().size
                mainViewModel.expandBottomSheet(coroutineScope)
                updateSheetContentTabIndex(TabIndex.REVIEW.ordinal)
                updateTutorialCardContent(
                    iconDrawableId = R.drawable.check,
                    title = context.getString(R.string.review_screen),
                    body = buildAnnotatedString {
                        append(
                            context.getString(R.string.tutorial_review_desc) + "\n\n"
                        )
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append(context.getString(R.string.tutorial_review_try))
                        pop()
                    }
                )
                while (mainViewModel.getMediaToDelete().size >= initialNumToDelete) {
                    delay(500)
                }
                onStepComplete()
            }
            // Stats screen shows you how much you have swiped in the past
            7 -> {
                mainViewModel.expandBottomSheet(coroutineScope)
                updateSheetContentTabIndex(TabIndex.STATS.ordinal)
                updateTutorialCardContent(
                    iconDrawableId = R.drawable.chart,
                    title = context.getString(R.string.statistics_screen),
                    body = buildAnnotatedString {
                        append(context.getString(R.string.tutorial_statistics_desc) + "\n\n")
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append(context.getString(R.string.tutorial_statistics_try))
                        pop()
                    }
                )
                while (
                    statsViewModel.uiState.value.dataType != StatsData.SPACE_SAVED
                    || statsViewModel.uiState.value.timeFrame != TimeFrame.DAY
                ) {
                    delay(500)
                }
                onStepComplete()
            }
            // Settings screen lets you customise the app
            8 -> {
                mainViewModel.expandBottomSheet(coroutineScope)
                updateSheetContentTabIndex(TabIndex.SETTINGS.ordinal)
                updateTutorialCardContent(
                    iconDrawableId = R.drawable.gear,
                    title = context.getString(R.string.settings_screen),
                    body = buildAnnotatedString {
                        append(context.getString(R.string.settings_desc))
                    }
                )
                delay(5000)
                onStepComplete(showToast = false)
            }

            9 -> {
                updateTutorialCardContent(
                    iconDrawableId = R.drawable.check,
                    title = context.getString(R.string.tutorial_end_title),
                    body = buildAnnotatedString {
                        append(context.getString(R.string.tutorial_end_desc))
                    }
                )
                Toast.makeText(
                    context,
                    "Fin :) \uD83C\uDF89 \uD83C\uDF89 \uD83C\uDF89",
                    Toast.LENGTH_SHORT
                ).show()
                delay(5000)
                mainViewModel.onEndTutorial()
            }

            else -> {
                mainViewModel.onEndTutorial()
            }
        }
    }
}