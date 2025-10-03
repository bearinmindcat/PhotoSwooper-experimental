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
import androidx.compose.ui.text.AnnotatedString
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.example.photoswooper.data.MAX_TUTORIAL_INDEX
import com.example.photoswooper.data.database.MediaStatusDao
import com.example.photoswooper.data.models.Media
import com.example.photoswooper.data.models.MediaFilter
import com.example.photoswooper.data.models.MediaSortField
import com.example.photoswooper.data.models.MediaStatus
import com.example.photoswooper.data.models.MediaType
import com.example.photoswooper.data.models.defaultMediaFilter
import com.example.photoswooper.data.uistates.BooleanPreference
import com.example.photoswooper.data.uistates.IntPreference
import com.example.photoswooper.data.uistates.LongPreference
import com.example.photoswooper.data.uistates.MainUiState
import com.example.photoswooper.data.uistates.StringPreference
import com.example.photoswooper.data.uistates.TimeFrame
import com.example.photoswooper.utils.ContentResolverInterface
import com.example.photoswooper.utils.DataStoreInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

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
    val player: ExoPlayer,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState(isPlaying = player.isPlaying))
    val uiState = _uiState.asStateFlow()

    // Initialise mediaFilters (Updated with stored values when resetAndGetNewMediaItems() is first called in MainActivity)
    private val _mediaFilter = MutableStateFlow(
        defaultMediaFilter
    )
    val mediaFilter = _mediaFilter.asStateFlow()

    /* On first start, update the space saved in time frame value & set the filters from  */
    init {
        CoroutineScope(Dispatchers.IO).launch { updateMediaFiltersFromDataStore() }
        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(
                    spaceSavedInTimeFrame = getSpaceSavedInTimeFrame(),
                    tutorialMode = dataStoreInterface.getIntSettingValue(IntPreference.TUTORIAL_INDEX.setting)
                        .first() < MAX_TUTORIAL_INDEX,
                    fetchingMedia = getMediaToDelete().isEmpty()
                )
            }
        }
    }
    val statisticsEnabled = dataStoreInterface.getBooleanSettingValue(BooleanPreference.STATISTICS_ENABLED.setting)
    fun getCurrentMedia() =
        try {
            _uiState.value.mediaItems[_uiState.value.currentIndex]
        } catch (_: IndexOutOfBoundsException) {
            null
        }

    fun getMediaToDelete() = _uiState.value.mediaItems.filter { media ->
        media.status == MediaStatus.DELETE
    }

    val reduceAnimations = dataStoreInterface
        .getBooleanSettingValue(BooleanPreference.REDUCE_ANIMATIONS.setting)
    val defaultEntryAnimationSpec = spring<Float>(
        stiffness = Spring.StiffnessMediumLow,
        dampingRatio = Spring.DampingRatioLowBouncy,
    )
    val defaultExitAnimationSpec = spring<Float>(
        Spring.DampingRatioNoBouncy,
        Spring.StiffnessMedium
    )
    val animatedImageScaleEntry = Animatable(0f)
    fun enterImage() {
        uiCoroutineScope.launch {
            animatedImageScaleEntry.snapTo(0f)
            if (reduceAnimations.first()) animatedImageScaleEntry.snapTo(1f)
            else animatedImageScaleEntry.animateTo(
                1f,
                defaultEntryAnimationSpec
            )
        }
    }

    fun exitImage() {
        uiCoroutineScope.launch {
            if (reduceAnimations.first()) animatedImageScaleEntry.snapTo(0f)
            else animatedImageScaleEntry.animateTo(
                0f,
                defaultExitAnimationSpec
            )
        }
    }

    fun onMediaLoaded() {
        _uiState.update { currentState ->
            currentState.copy(
                mediaBuffering = false
            )
        }
    }

    /* Get new media items and add them to the UI state */
    suspend fun resetAndGetNewMediaItems() {
        val numPerStackPreference =
            dataStoreInterface.getIntSettingValue(IntPreference.NUM_PHOTOS_PER_STACK.setting).first()

        // Delete old mediaItems from uiState
        _uiState.update { currentState ->
            currentState.copy(
                mediaItems = mutableListOf(),
                fetchingMedia = true
            )
        }
        uiCoroutineScope.launch { player.clearMediaItems() }
        animatedImageScaleEntry.snapTo(0f)
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
                    mediaBuffering = true
                )
            }
            if (getCurrentMedia()?.type == MediaType.VIDEO) {
                onCurrentMediaIsVideo()
            }
            if (_uiState.value.mediaItems.size < numPerStackPreference) {
                // Add the rest asynchronously
                viewModelScope.launch {
                    val numPhotosToAddAsync = numPhotosToAddUsingFilters(numPerStackPreference)
                    contentResolverInterface.getAllMediaFromMediaStore(
                        onAddMedia = {
                            insertMediaItemIntoListSorted(it)
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

        if (mediaItems.isNotEmpty()) {
            val indexToInsertInto: Int
            // If random, select random index and insert
            if (mediaFilter.value.sortField == MediaSortField.RANDOM) {
                val random = Random(17530163829) // Random number generator}
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
        val mediaToMark = _uiState.value.mediaItems[index]
        _uiState.value.mediaItems[index].status = status

        // Update database only if keeping/unsetting the media. Only marked as DELETE when confirmed and the file is deleted
        if (status == MediaStatus.SNOOZE) {
            val snoozeLength = dataStoreInterface.getLongSettingValue(
                setting = LongPreference.SNOOZE_LENGTH.setting
            )
            CoroutineScope(Dispatchers.IO).launch {
                val snoozeEndDate = Calendar.getInstance().timeInMillis + snoozeLength.first()
                mediaStatusDao.update(
                    mediaToMark.getMediaStatusEntity(statisticsEnabled.first()).copy(
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
                mediaStatusDao.update(mediaToMark.getMediaStatusEntity(statisticsEnabled.first()))
            }

        // If item being marked as UNSET (e.g. in the review screen), insert the unset photo to the index before the
        // current one, and seek to it. This preserves undo functionality
        Log.d("Media marking", "Media at index $index marked as ${mediaToMark.status}")
        if (status == MediaStatus.UNSET) {
            _uiState.value.mediaItems.removeAt(index)
            val indexToInsertItem = if (_uiState.value.currentIndex - 1 < 0) 0 else _uiState.value.currentIndex - 1
            _uiState.value.mediaItems.add(indexToInsertItem, mediaToMark)
            _uiState.update { currentState ->
                currentState.copy(
                    numUnset = currentState.numUnset + 1,
                    currentIndex = currentState.currentIndex - 1
                )
            }
        } else {
            _uiState.update { currentState ->
                currentState.copy(
                    numUnset = currentState.numUnset - 1,
                )
            }
        }
    }

    fun next() {
        if (getCurrentMedia()?.type == MediaType.VIDEO)
            player.pause()
        // Increment currentIndex
        _uiState.update { currentState ->
            currentState.copy(
                currentIndex = currentState.currentIndex + 1,
                mediaBuffering = true
            )
        }
        if (getCurrentMedia()?.type == MediaType.VIDEO)
            onCurrentMediaIsVideo()
        Log.v(
            "MainViewModel",
            "Seeking to next media item, new index = ${_uiState.value.currentIndex}/${_uiState.value.mediaItems.size}"
        )
        Log.v("MainViewModel", "Media items left to swipe on = ${_uiState.value.numUnset}")
    }

    fun seekToUnsetItemOrFalse(): Boolean {
        val unsetItemIndex = _uiState.value.mediaItems.indexOfFirst { it.status == MediaStatus.UNSET }
        if (unsetItemIndex != -1) {
            _uiState.update { currentState ->
                currentState.copy(
                    currentIndex = unsetItemIndex,
                    mediaBuffering = true
                )
            }
            if (getCurrentMedia()?.type == MediaType.VIDEO)
                onCurrentMediaIsVideo()
            return true
        } else return false
    }

    fun undo(): Boolean {
        val listOfMediaBeforeCurrent = _uiState.value.mediaItems.subList(0, _uiState.value.currentIndex)
        var canUndo = _uiState.value.currentIndex > 0 && !listOfMediaBeforeCurrent.all { it.status == MediaStatus.HIDE }
        if (canUndo) { // First check if there is an action to undo
            viewModelScope.launch {
                exitImage()
                delay(100)
                // Check if undo valid again, after the above delay
                if (canUndo) {
                    if (getCurrentMedia()?.type == MediaType.VIDEO)
                        player.pause()
                    var indexToSeekTo = _uiState.value.currentIndex - 1
                    while (uiState.value.mediaItems[indexToSeekTo].status == MediaStatus.HIDE)
                        indexToSeekTo--
                    // Unset the status
                    _uiState.value.mediaItems[indexToSeekTo].status = MediaStatus.UNSET

                    // Decrement currentIndex
                    _uiState.update { currentState ->
                        currentState.copy(
                            currentIndex = indexToSeekTo,
                            numUnset = currentState.numUnset + 1,
                            mediaBuffering = true
                        )
                    }
                    if (getCurrentMedia()?.type == MediaType.VIDEO)
                        onCurrentMediaIsVideo()
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

            if (getMediaToDelete().isEmpty()) {
                if (_uiState.value.numUnset <= 0)
                    CoroutineScope(Dispatchers.IO).launch { checkPermissions { resetAndGetNewMediaItems() } }
            }
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
                showInfoAndFloatingActionsRow = newState
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
            player.setMediaItem(
                MediaItem.fromUri(
                    getCurrentMedia()?.uri
                        ?: "android.resource://com.example.photoswooper/drawable/file_not_found_cat".toUri()
                )
            )
            player.play()
            _uiState.update { currentState ->
                currentState.copy(
                    showInfoAndFloatingActionsRow = true
                )
            }
        }
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

    suspend fun updateMediaFiltersFromDataStore() {
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

    fun updateTutorialCardContent(title: String, body: AnnotatedString, iconDrawableId: Int) {
        _uiState.update { currentState ->
            currentState.copy(
                tutorialCardIconDrawableId = iconDrawableId,
                tutorialCardTitle = title,
                tutorialCardBody = body
            )
        }
    }

    fun onEndTutorial() {
        _uiState.update { currentState ->
            currentState.copy(tutorialMode = false)
        }
        CoroutineScope(Dispatchers.IO).launch {
            mediaStatusDao.delete(
                mediaEntityList = mediaStatusDao.getSwipedMediaBetweenDates(
                    firstDate = dataStoreInterface.getLongSettingValue(LongPreference.TUTORIAL_START_TIME.setting)
                        .first(),
                    secondDate = Long.MAX_VALUE,
                )
            )
           checkPermissions { resetAndGetNewMediaItems() }
        }
    }
}