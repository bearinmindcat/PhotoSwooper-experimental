/*
 * SPDX-FileCopyrightText: 2025 Loowiz <loowiz@envs.net>
 *
 *  SPDX-License-Identifier: AGPL-3.0-only
 */

package com.example.photoswooper.ui.viewmodels

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import com.example.photoswooper.data.BooleanPreference
import com.example.photoswooper.data.IntPreference
import com.example.photoswooper.data.LongPreference
import com.example.photoswooper.data.MAX_TUTORIAL_INDEX
import com.example.photoswooper.data.StringPreference
import com.example.photoswooper.data.database.MediaStatusDao
import com.example.photoswooper.data.models.Media
import com.example.photoswooper.data.models.MediaFilter
import com.example.photoswooper.data.models.MediaSortField
import com.example.photoswooper.data.models.MediaStatus
import com.example.photoswooper.data.models.MediaType
import com.example.photoswooper.data.models.defaultMediaFilter
import com.example.photoswooper.data.uistates.MainUiState
import com.example.photoswooper.data.uistates.TimeFrame
import com.example.photoswooper.player
import com.example.photoswooper.utils.ContentResolverInterface
import com.example.photoswooper.utils.DataStoreInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Calendar
import java.util.Date
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

val defaultEntryAnimationSpec = spring<Float>(
    stiffness = Spring.StiffnessMediumLow,
    dampingRatio = Spring.DampingRatioLowBouncy,
)
val defaultExitAnimationSpec = spring<Float>(
    Spring.DampingRatioNoBouncy,
    Spring.StiffnessMedium
)

/**
 * View model used by [com.example.photoswooper.ui.components.ActionBar] and [com.example.photoswooper.ui.view.MainScreen]
 *
 * @param savedUiState UI state saved before a configuration change, ready to be restored
 */
@OptIn(ExperimentalMaterial3Api::class)
class MainViewModel(
    private val contentResolverInterface: ContentResolverInterface,
    private val mediaStatusDao: MediaStatusDao,
    private val dataStoreInterface: DataStoreInterface,
    private val startActivity: (Intent) -> Unit,
    private val makeToast: (String) -> Unit,
    private val checkPermissions: (onPermissionsGranted: suspend () -> Unit) -> Unit,
    private val uiCoroutineScope: CoroutineScope,
    val bottomSheetScaffoldState: BottomSheetScaffoldState,
    private val savedUiState: MainUiState,
    private val updateSavedUiState: (MainUiState) -> Unit,
    private val savedMediaFilter: MediaFilter?,
    private val updateSavedMediaFilter: (MediaFilter) -> Unit
//    val initialUiState: MainUiState,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState(isPlaying = player.isPlaying))
    val uiState = _uiState.asStateFlow()

    // Initialise mediaFilters (Updated with stored values when resetAndGetNewMediaItems() is first called in MainActivity)
    private val _mediaFilter = MutableStateFlow(
        savedMediaFilter?: defaultMediaFilter
    )
    val mediaFilter = _mediaFilter.asStateFlow()

    val statisticsEnabled = dataStoreInterface.getBooleanSettingValue(BooleanPreference.STATISTICS_ENABLED.setting)
    val reduceAnimations = dataStoreInterface
        .getBooleanSettingValue(BooleanPreference.REDUCE_ANIMATIONS.setting)

    val animatedMediaScale = Animatable(0f)

    init {
        // Restore from values before configuration change, if they exist
        runBlocking {
            val onboardingScreenInFocus = dataStoreInterface.getIntSettingValue(IntPreference.TUTORIAL_INDEX.setting)
                    .first() == 0
            if (savedUiState.mediaItems.isEmpty() && !onboardingScreenInFocus)
                CoroutineScope(Dispatchers.Main).launch { checkPermissions { resetAndGetNewMediaItems() } }
            else {
                _uiState.update {
                    savedUiState
                }
                // Restore video positions and state
                if (getCurrentMedia()?.type == MediaType.VIDEO) {
                    if(safeSetMediaItem()) {
                        player.seekTo(uiState.value.videoPosition)
                        if (uiState.value.isPlaying)
                            player.play()
                        else
                            player.pause()
                    }
                }
            }
        }
        // Update space saved statistics & whether tutorial should be shown
        CoroutineScope(Dispatchers.IO).launch { updateMediaFilterFromDataStore() }
        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(
                    spaceSavedInTimeFrame = getSpaceSavedInTimeFrame(),
                    tutorialMode = dataStoreInterface.getIntSettingValue(IntPreference.TUTORIAL_INDEX.setting)
                        .first() < MAX_TUTORIAL_INDEX,
                )
            }
        }
        // Update saved UI state on each change for restoring after configuration change
        CoroutineScope(Dispatchers.Main).launch {
            _uiState.collect {
                updateSavedUiState(it)
                delay(500)
            }
        }
        // Update saved filters on each change for restoring after configuration change
        CoroutineScope(Dispatchers.Main).launch {
            _mediaFilter.collect {
                updateSavedMediaFilter(it)
            }
        }
    }

    fun getCurrentMedia() =
        try {
            _uiState.value.mediaItems[_uiState.value.currentIndex]
        } catch (_: IndexOutOfBoundsException) {
            null
        }

    fun getMediaToDelete() = _uiState.value.mediaItems.filter { media ->
        media.status == MediaStatus.DELETE
    }

    fun animateMediaEntry() {
        uiCoroutineScope.launch {
            animatedMediaScale.snapTo(0f)
            if (reduceAnimations.first()) animatedMediaScale.snapTo(1f)
            else animatedMediaScale.animateTo(
                1f,
                defaultEntryAnimationSpec
            )
        }
    }

    fun animateMediaExit() {
        uiCoroutineScope.launch {
            if (reduceAnimations.first()) animatedMediaScale.snapTo(0f)
            else animatedMediaScale.animateTo(
                0f,
                defaultExitAnimationSpec
            )
        }
    }

    fun onMediaLoaded(mediaAspectRatio: Float = 1f) {
        _uiState.update { currentState ->
            currentState.copy(
                mediaReady = true,
                mediaAspectRatio = mediaAspectRatio
            )
        }
        animateMediaEntry()
    }

    fun onMediaError(errorMessage: String?) {
        val currentIndex = uiState.value.currentIndex
        _uiState.update { currentState ->
            currentState.mediaItems[currentIndex] = currentState.mediaItems[currentIndex].copy(decodingError = errorMessage)
            currentState.copy(mediaReady = true)
        }
        animateMediaEntry()
    }

    /** Get new media items and add them to the UI state */
    suspend fun resetAndGetNewMediaItems() {
        val numPerStackPreference =
            dataStoreInterface.getIntSettingValue(IntPreference.NUM_PHOTOS_PER_STACK.setting).first()

        // Delete old mediaItems from uiState
        _uiState.update { currentState ->
            currentState.copy(
                mediaItems = mutableListOf(),
                fetchingMedia = true,
                fetchIteration = currentState.fetchIteration + 1
            )
        }
        uiCoroutineScope.launch { player.clearMediaItems() }
        animatedMediaScale.snapTo(0f)
        // Add the first media synchronously
        val maxMediaItemsToAddSynchronously = minOf(numPerStackPreference, 2)
        val numPhotosToAddSynchronously = numPhotosToAddUsingFilters(maxMediaItemsToAddSynchronously)
        contentResolverInterface.getAllMediaFromMediaStore(
            onAddMedia = {
                insertMediaItemIntoListSorted(it, 0)
            },
            targetNumPhotos = numPhotosToAddSynchronously,
            targetNumVideos = maxMediaItemsToAddSynchronously - numPhotosToAddSynchronously,
            mediaFilter = mediaFilter.value
        )
        // Ensure media items were found before continuing
        if (_uiState.value.mediaItems.isEmpty()) {
            viewModelScope.launch {
                delay(1000) // Delay so that the loading indicator is shown
                _uiState.update { currentState ->
                    currentState.copy(
                        fetchingMedia = false
                    )
                }
            }
        }
        // Media items were found -> Update UI state & display the current media
        else {
            _uiState.update { currentState ->
                currentState.copy(
                    currentIndex = 0,
                    numUnset = numPerStackPreference,
                    fetchingMedia = false,
                )
            }
            if (getCurrentMedia()?.type == MediaType.VIDEO) {
                onCurrentMediaIsVideo()
            }
            if (_uiState.value.mediaItems.size < numPerStackPreference) {
                // Add the rest asynchronously
                CoroutineScope(Dispatchers.IO).launch {
                    val currentFetchIteration = uiState.value.fetchIteration
                    val numPhotosToAddAsync = numPhotosToAddUsingFilters(numPerStackPreference)
                    contentResolverInterface.getAllMediaFromMediaStore(
                        onAddMedia = {
                            if (uiState.value.fetchIteration != currentFetchIteration) cancel("Cancelled previous media fetching coroutine")
                            else insertMediaItemIntoListSorted(it)
                        },
                        targetNumPhotos = numPhotosToAddAsync - numPhotosToAddSynchronously,
                        targetNumVideos = numPerStackPreference - numPhotosToAddAsync - (maxMediaItemsToAddSynchronously - numPhotosToAddSynchronously),
                        mediaAdded = _uiState.value.mediaItems.toMutableSet(),
                        mediaFilter = mediaFilter.value
                    )

                    if (_uiState.value.mediaItems.size != numPerStackPreference) {
                        makeToast("Last round!")
                        // Update UI state with the actual number of media items found
                        _uiState.update { currentState ->
                            currentState.copy(
                                numUnset = currentState.mediaItems.filter { it.status == MediaStatus.UNSET }.size
                            )
                        }
                    }
                }
            }
        }
    }

    private fun numPhotosToAddUsingFilters(maxMediaItems: Int): Int {
        val random = Random(17530163829) // Random number generator
        return when (mediaFilter.value.mediaTypes) {
            setOf(MediaType.VIDEO) -> 0
            setOf(MediaType.PHOTO) -> maxMediaItems
            else -> random.nextInt(maxMediaItems)
        }
    }

    /** Inserts the media item into the UI state list according to the current sort field & direction */
    private fun insertMediaItemIntoListSorted(
        mediaItemToInsert: Media,
        fromIndex: Int = _uiState.value.currentIndex + 1
    ) {
        val mediaItems = uiState.value.mediaItems

        if (fromIndex < mediaItems.size) {
            val indexToInsertInto: Int
            // If random, select random index and insert
            if (mediaFilter.value.sortField == MediaSortField.RANDOM) {
                val random = Random(17530163829) // Random number generator
                indexToInsertInto = random.nextInt(fromIndex, mediaItems.size)
            }
            // If not random, use sort field to insert
            else {
                val comparisonValue =
                    when (mediaFilter.value.sortField) {
                        MediaSortField.SIZE -> mediaItemToInsert.size
                        MediaSortField.DATE -> mediaItemToInsert.dateTaken
                        MediaSortField.RANDOM -> mediaItemToInsert.id // Not used - MediaSortField.RANDOM handled above
                    }
                val listToCompareTo = when (mediaFilter.value.sortField) {
                    MediaSortField.SIZE -> mediaItems.map { it.size }
                    MediaSortField.DATE -> mediaItems.map { it.dateTaken }
                    MediaSortField.RANDOM -> mediaItems.map { it.id } // Not used - MediaSortField.RANDOM handled above
                }

                var currentIndex = fromIndex
                if (mediaFilter.value.sortAscending)
                    while ((listToCompareTo[currentIndex] ?: 0) < (comparisonValue ?: 0)) {
                        currentIndex++
                        if (currentIndex == mediaItems.size)
                            break
                    }
                else
                    while ((listToCompareTo.getOrNull(currentIndex)?: 0) > (comparisonValue ?: 0)) {
                        currentIndex++
                        if (currentIndex == mediaItems.size)
                            break
                    }
                indexToInsertInto = currentIndex
            }

            mediaItems.add(indexToInsertInto, mediaItemToInsert)
        } else
            mediaItems.add(mediaItemToInsert)
    }

    fun markItem(status: MediaStatus, index: Int = _uiState.value.currentIndex) {
        // Set the status
        _uiState.update { currentState ->
            currentState.mediaItems.set(index, currentState.mediaItems[index].copy(status = status))
            currentState
        }
        val markedMediaItem = _uiState.value.mediaItems[index]

        // Update database only if keeping/unsetting the media. Only marked as DELETE when confirmed and the file is deleted
        if (status == MediaStatus.SNOOZE) {
            val snoozeLength = dataStoreInterface.getLongSettingValue(
                setting = LongPreference.SNOOZE_LENGTH.setting
            )
            CoroutineScope(Dispatchers.IO).launch {
                val snoozeEndDate = Calendar.getInstance().timeInMillis + snoozeLength.first()
                mediaStatusDao.update(
                    markedMediaItem.getMediaStatusEntity(statisticsEnabled.first()).copy(
                        snoozedUntil = snoozeEndDate
                    )
                )
            }
            viewModelScope.launch {
                makeToast(
                    "Hidden for ${snoozeLength.first().milliseconds.inWholeDays} days"
                )
            }
        } else if (status != MediaStatus.DELETE)
            CoroutineScope(Dispatchers.IO).launch {
                mediaStatusDao.update(markedMediaItem.getMediaStatusEntity(statisticsEnabled.first()))
            }

        // If item being marked as UNSET (e.g. in the review screen), insert the unset photo to the index before the
        // current one, and seek to it. This preserves undo functionality
        Log.d("Media marking", "Media at index $index marked as ${markedMediaItem.status}")
        if (status == MediaStatus.UNSET) {
            _uiState.value.mediaItems.removeAt(index)
            val indexToInsertItem = if (_uiState.value.currentIndex - 1 < 0) 0 else _uiState.value.currentIndex - 1
            _uiState.value.mediaItems.add(indexToInsertItem, markedMediaItem)
            _uiState.update { currentState ->
                currentState.copy(
                    numUnset = currentState.numUnset + 1,
                )
            }
            seekToIndex(indexToInsertItem)
        } else {
            _uiState.update { currentState ->
                currentState.copy(
                    numUnset = currentState.numUnset - 1,
                )
            }
        }
    }

    fun next() {
        // Increment currentIndex
        seekToIndex(uiState.value.currentIndex + 1)
        Log.v(
            "MainViewModel",
            "Seeking to next media item, new index = ${_uiState.value.currentIndex}/${_uiState.value.mediaItems.size}"
        )
        Log.v("MainViewModel", "Media items left to swipe on = ${_uiState.value.numUnset}")
    }

    fun undo(): Boolean {
        val listOfMediaBeforeCurrent = _uiState.value.mediaItems.subList(0, _uiState.value.currentIndex)
        val canUndo = _uiState.value.currentIndex > 0 && !listOfMediaBeforeCurrent.all { it.status == MediaStatus.HIDE }
        if (canUndo) { // First check if there is an action to undo
            CoroutineScope(Dispatchers.Main).launch {
                animateMediaExit()
                delay(100)
                // Check if undo valid again, after the above delay
                if (canUndo) {
                    // Calculate which index to go to next
                    var indexToSeekTo = _uiState.value.currentIndex - 1
                    while (uiState.value.mediaItems[indexToSeekTo].status == MediaStatus.HIDE)
                        indexToSeekTo--
                    // Unset the status
                    _uiState.value.mediaItems[indexToSeekTo] = _uiState.value.mediaItems[indexToSeekTo].copy(status = MediaStatus.UNSET)
                    // Update numUnset
                    _uiState.update { currentState ->
                        currentState.copy(
                            numUnset = currentState.numUnset + 1
                        )
                    }
                    // Seek to the calculated index
                    seekToIndex(indexToSeekTo)
                    /* Update database  */
                    CoroutineScope(Dispatchers.IO).launch {
                        val mediaItem = _uiState.value.mediaItems[indexToSeekTo]
                        mediaStatusDao.update(mediaItem.getMediaStatusEntity(statisticsEnabled.first()))
                    }
                }
            }
            return true
        } else {
            makeToast("Nothing to undo!")
            return false
        }
    }

    fun seekToUnsetItemOrFalse(): Boolean {
        val unsetItemIndex = _uiState.value.mediaItems.indexOfFirst { it.status == MediaStatus.UNSET }
        if (unsetItemIndex != -1) {
            seekToIndex(unsetItemIndex)
            return true
        } else
            return false
    }

    /** Set [MainUiState.currentIndex] to [index] and perform shared required actions after changing index  */
    fun seekToIndex(index: Int) {
        // Revert floating action state to state before temporarily showing it for a video (if current item was video)
        revertShowFloatingActionsToPreviousState()
        // Pause the current video
        if (getCurrentMedia()?.type == MediaType.VIDEO)
            player.pause()
        _uiState.update { currentState ->
            currentState.copy(
                currentIndex = index,
                mediaReady = false
            )
        }
        if (getCurrentMedia()?.type == MediaType.VIDEO)
            onCurrentMediaIsVideo()
        // Check if the new media has an error to show
        try {
            val targetMediaItem = uiState.value.mediaItems[index]
            if (targetMediaItem.decodingError != null) {
                onMediaError(targetMediaItem.decodingError)
            }
        }
        catch (_: IndexOutOfBoundsException) {}
    }


    fun deleteMarkedMedia() {
        val mediaToDelete = getMediaToDelete()
        // Delete the media in user's storage
        if (mediaToDelete.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                contentResolverInterface.deleteMedia(
                    mediaToDelete.map { it.uri },
                    onDelete = {
                        CoroutineScope(Dispatchers.Main).launch { onDeletion(it) }
                    }
                )
            }

        }
        // If user cancels deletion of all items
        else {
            makeToast("No items were deleted")
        }
    }

    suspend fun onDeletion(deletedMediaUris: List<Uri>, deletionCancelled: Boolean = false) {
        /* If at least one media item was successfully deleted and android version != 10 */
        if (deletedMediaUris.isNotEmpty()) {
            if (Build.VERSION.SDK_INT != Build.VERSION_CODES.Q) // Prevents spam of Toasts to user
                makeToast("${deletedMediaUris.size}/${getMediaToDelete().size} items successfully deleted")
            else if (deletedMediaUris.size == getMediaToDelete().size)
                makeToast("All selected items deleted")
            /* Update database & hide media in UI*/
            val deletedMediaItems = _uiState.value.mediaItems.filter { deletedMediaUris.contains(it.uri) }
            CoroutineScope(Dispatchers.IO).launch {
                mediaStatusDao.update(*deletedMediaItems.map {
                    it.getMediaStatusEntity(statisticsEnabled.first())
                }.toTypedArray())
            }
            _uiState.update { currentState ->
                currentState.copy(
                    mediaItems = currentState.mediaItems.map {
                        if (deletedMediaItems.contains(it))
                            it.copy(status = MediaStatus.HIDE)
                        else it
                    }.toMutableList()
                )
            }
            /* Update space saved in the current time frame */
            _uiState.update { currentState ->
                currentState.copy(
                    spaceSavedInTimeFrame = getSpaceSavedInTimeFrame()
                )
            }
            // Hide the bottom sheet so user can immediately return to swiping
            uiCoroutineScope.launch{
                delay(500)
                bottomSheetScaffoldState.bottomSheetState.partialExpand()
            }
            if (_uiState.value.numUnset <= 0)
                CoroutineScope(Dispatchers.IO).launch { checkPermissions { resetAndGetNewMediaItems() } }
        } else {
            if (deletionCancelled) {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q)
                    makeToast("Please click 'Allow' on each popup to delete the selected items")
                else
                    makeToast("Deletion cancelled")
            } else
                makeToast("Deletion unsuccessful, please check permissions.")
        }
    }

    fun expandBottomSheet(coroutineScope: CoroutineScope) {
        coroutineScope.launch {
            if (bottomSheetScaffoldState.bottomSheetState.targetValue != SheetValue.Expanded)
                bottomSheetScaffoldState.bottomSheetState.expand()
        }
    }

    fun toggleInfoRowExpanded() {
        // Update user preference
        CoroutineScope(Dispatchers.IO).launch {
            val infoRowExpandedSetting = BooleanPreference.INFO_ROW_EXPANDED.setting
            dataStoreInterface.setBooleanSettingValue(
                newValue = !(dataStoreInterface.getBooleanSettingValue(infoRowExpandedSetting).first()),
                setting = infoRowExpandedSetting
            )
        }
    }

    fun toggleInfoAndFloatingActionsRow(newState: Boolean = !uiState.value.showInfoAndFloatingActionsRow) {
        _uiState.update { currentState ->
            currentState.copy(
                showInfoAndFloatingActionsRow = newState,
                previousShowInfoAndFloatingActionsRow = newState
            )
        }
    }

    fun tempShowFloatingActions() {
        _uiState.update { currentState ->
                currentState.copy(
                    previousShowInfoAndFloatingActionsRow = currentState.showInfoAndFloatingActionsRow,
                    showInfoAndFloatingActionsRow = true
                )
        }
    }
    fun revertShowFloatingActionsToPreviousState() {
        _uiState.update { currentState ->
            currentState.copy(
                showInfoAndFloatingActionsRow = currentState.previousShowInfoAndFloatingActionsRow
            )
        }
    }

    fun toggleInfo() {
        _uiState.update { currentState ->
            currentState.copy(
                showInfo = !currentState.showInfo
            )
        }
    }

    fun toggleFilterDialog(newState: Boolean = !_uiState.value.showFilterDialog) {
        _uiState.update { currentState ->
            currentState.copy(
                showFilterDialog = newState
            )
        }
    }

    fun openLocationInMapsApp(media: Media? = getCurrentMedia()) {
        val uri = "geo:${media?.location?.get(0)},${media?.location?.get(1)}"
        val intent = Intent(Intent.ACTION_VIEW, uri.toUri())
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            makeToast("No suitable app found")
        }
    }

    fun openInGalleryApp(media: Media? = getCurrentMedia()) {
        val uri = media?.uri
        val intent = Intent(Intent.ACTION_VIEW, uri)
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            makeToast("No suitable app found")
        }
    }

    fun share(media: Media? = _uiState.value.mediaItems[_uiState.value.currentIndex]) {
        if (media != null) {
            val shareIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, media.uri)
                type = contentResolverInterface.getMediaType(media.uri)
            }
            startActivity(Intent.createChooser(shareIntent, null))
        }
    }

    suspend fun cycleStorageStatsTimeFrame() {
        val currentStorageTimeFrame = _uiState.value.currentStorageStatsTimeFrame
        val newTimeFrame =
            if (currentStorageTimeFrame != TimeFrame.entries.last())
                TimeFrame.entries[currentStorageTimeFrame.ordinal + 1]
            else
                TimeFrame.entries.first()
        _uiState.update { currentState ->
            currentState.copy(
                currentStorageStatsTimeFrame = newTimeFrame,
                spaceSavedInTimeFrame = getSpaceSavedInTimeFrame(newTimeFrame)
            )
        }
    }

    suspend fun getSpaceSavedInTimeFrame(timeFrame: TimeFrame = _uiState.value.currentStorageStatsTimeFrame): Long {
        val currentDate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Date().toInstant().toEpochMilli()
        } else {
            System.currentTimeMillis()
        }

        val firstDateInTimeFrame = currentDate - timeFrame.milliseconds

        return mediaStatusDao.getDeletedBetweenDates(firstDateInTimeFrame, currentDate).sumOf { it.size }
    }

    fun updatePermissionsGranted(newState: Boolean) {
        _uiState.update { currentState ->
            currentState.copy(
                permissionsGranted = newState
            )
        }
    }

    fun navigateToAppSettingsForPermissions(context: Context) {
        val uri = Uri.fromParts("package", context.packageName, null)
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)

        context.startActivity(intent)
        Toast.makeText(
            context,
            "Click on \"Permissions\", then grant photos & videos / storage permissions",
            Toast.LENGTH_LONG
        ).show()
    }

    fun updateSnoozeLengthMillis(newSnoozeLength: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            dataStoreInterface.setLongSettingValue(
                setting = LongPreference.SNOOZE_LENGTH.setting,
                newValue = newSnoozeLength
            )
        }
    }

    /* Functions for playing video */
    private fun onCurrentMediaIsVideo() {
        uiCoroutineScope.launch {
            player.clearMediaItems()
            if(safeSetMediaItem()) {
                player.play()
                tempShowFloatingActions()
            }
        }
        // Add timeout error (default timeout is way too long)
        CoroutineScope(Dispatchers.Unconfined).launch {
            val indexBeforeCheckingTimeout = uiState.value.currentIndex
            delay(5000)
            if (!uiState.value.mediaReady && getCurrentMedia()?.decodingError == null && indexBeforeCheckingTimeout == uiState.value.currentIndex)
                onMediaError("Timeout")
        }
    }
    /** Set the current mediaItem in ExoPlayer, first checking if the media file is empty
     * (avoiding [androidx.media3.exoplayer.source.UnrecognizedInputFormatException])
     *
     * @return Whether the mediaItem was set or not
     * */
    private fun safeSetMediaItem(
        media: Media? = getCurrentMedia()
    ): Boolean {
        if ((media?.size ?: 1) > 0) {
            player.setMediaItem(
                MediaItem.fromUri(
                    media?.uri ?: "android.resource://com.example.photoswooper/drawable/file_not_found_cat".toUri()
                )
            )
            return true
        }
        return false
    }

    /* Ensures video is being shown before playing */
    fun safePlay() {
        if (getCurrentMedia()?.type == MediaType.VIDEO && !_uiState.value.showFilterDialog) player.play()
    }

    /* Ensures video is being shown before pausing */
    fun safePause() {
        if (getCurrentMedia()?.type == MediaType.VIDEO && !_uiState.value.showFilterDialog) player.pause()
    }

    /** Saves the current isPlaying state to [MainUiState.previousIsPlaying] for use in [revertIsPlayingToBeforeTempPause]
     * then pauses the current video if the current media being shown is a video
     */
    fun tempPause() {
        if (getCurrentMedia()?.type == MediaType.VIDEO && !_uiState.value.showFilterDialog) {
            _uiState.update { currentState ->
                currentState.copy(previousIsPlaying = currentState.isPlaying)
            }
            player.pause()
        }
    }
    fun revertIsPlayingToBeforeTempPause() {
        if (getCurrentMedia()?.type == MediaType.VIDEO && !_uiState.value.showFilterDialog) {
            if (_uiState.value.previousIsPlaying) player.play()
        }
    }

    fun updateIsPlaying(newState: Boolean = player.isPlaying) {
        _uiState.update { currentState ->
            currentState.copy(
                isPlaying = newState
            )
        }
    }

    fun updateVideoPosition(newPosition: Long = player.currentPosition) {
        _uiState.update { currentState ->
            currentState.copy(
                videoPosition = newPosition
            )
        }
    }

    suspend fun updateMediaFilterFromDataStore() {
        // Only perform if there is no mediaFilter to restore from before a configuration change
        if (savedMediaFilter == null) {
            val minSize = dataStoreInterface.getLongSettingValue(LongPreference.FILTER_MIN_FILE_SIZE.setting).first()
            val maxSize = dataStoreInterface.getLongSettingValue(LongPreference.FILTER_MAX_FILE_SIZE.setting).first()
            val includePhotos =
                dataStoreInterface.getBooleanSettingValue(BooleanPreference.FILTER_INCLUDE_PHOTOS.setting).first()
            val includeVideos =
                dataStoreInterface.getBooleanSettingValue(BooleanPreference.FILTER_INCLUDE_VIDEOS.setting).first()
            val sortOrderStringFromDataStore =
                dataStoreInterface.getStringSettingValue(StringPreference.FILTER_SORT_FIELD.setting).first()

            _mediaFilter.update { currentState ->
                currentState.copy(
                    sizeRange = minSize..maxSize,
                    mediaTypes =
                        when {
                            includePhotos && includeVideos -> setOf(MediaType.PHOTO, MediaType.VIDEO)
                            includePhotos -> setOf(MediaType.PHOTO)
                            else -> setOf(MediaType.VIDEO)
                        },
                    directory = dataStoreInterface.getStringSettingValue(StringPreference.FILTER_DIRECTORIES.setting)
                        .first(),
                    sortField = MediaSortField.entries.first { it.sortOrderString == sortOrderStringFromDataStore },
                    sortAscending = dataStoreInterface.getBooleanSettingValue(BooleanPreference.FILTER_SORT_ASCENDING.setting)
                        .first(),
                    containsText = dataStoreInterface.getStringSettingValue(StringPreference.FILTER_CONTAINS_TEXT.setting)
                        .first()
                )
            }
        }
    }

    /** Updates [mediaFilter] to [newFilter] and fetches media using this new filter.
     * If [setFilterAsDefault], also commit [newFilter] to the dataStore
     */
    fun updateMediaFilter(newFilter: MediaFilter, setFilterAsDefault: Boolean) {
        _mediaFilter.update {
            newFilter
        }
        CoroutineScope(Dispatchers.IO).launch {
            if (setFilterAsDefault) {
                dataStoreInterface.setLongSettingValue(
                    newFilter.sizeRange.first,
                    LongPreference.FILTER_MIN_FILE_SIZE.setting
                )
                dataStoreInterface.setLongSettingValue(
                    newFilter.sizeRange.last,
                    LongPreference.FILTER_MAX_FILE_SIZE.setting
                )
                dataStoreInterface.setBooleanSettingValue(
                    newFilter.mediaTypes.contains(MediaType.PHOTO),
                    BooleanPreference.FILTER_INCLUDE_PHOTOS.setting
                )
                dataStoreInterface.setBooleanSettingValue(
                    newFilter.mediaTypes.contains(MediaType.VIDEO),
                    BooleanPreference.FILTER_INCLUDE_VIDEOS.setting
                )
                dataStoreInterface.setStringSettingValue(
                    newFilter.sortField.sortOrderString,
                    StringPreference.FILTER_SORT_FIELD.setting
                )
                dataStoreInterface.setStringSettingValue(
                    newFilter.directory,
                    StringPreference.FILTER_DIRECTORIES.setting
                )
                dataStoreInterface.setBooleanSettingValue(
                    newFilter.sortAscending,
                    BooleanPreference.FILTER_SORT_ASCENDING.setting
                )
                dataStoreInterface.setStringSettingValue(
                    newFilter.containsText,
                    StringPreference.FILTER_CONTAINS_TEXT.setting
                )
            }
        }
        CoroutineScope(Dispatchers.IO).launch { checkPermissions { resetAndGetNewMediaItems() } }
    }

    fun onEndTutorial() {
        CoroutineScope(Dispatchers.IO).launch {
            mediaStatusDao.delete(
                mediaEntityList = mediaStatusDao.getSwipedMediaBetweenDates(
                    firstDate = dataStoreInterface.getLongSettingValue(LongPreference.TUTORIAL_START_TIME.setting)
                        .first(),
                    secondDate = Long.MAX_VALUE,
                )
            )
            checkPermissions {
                updateMediaFilterFromDataStore()
                resetAndGetNewMediaItems()
            }
            uiCoroutineScope.launch {  bottomSheetScaffoldState.bottomSheetState.partialExpand() }
            _uiState.update { currentState ->
                currentState.copy(tutorialMode = false)
            }
        }
    }
}