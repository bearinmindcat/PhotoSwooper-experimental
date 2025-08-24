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
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.example.photoswooper.data.database.MediaStatusDao
import com.example.photoswooper.data.models.Media
import com.example.photoswooper.data.models.MediaStatus
import com.example.photoswooper.data.models.MediaType
import com.example.photoswooper.data.uistates.BooleanPreference
import com.example.photoswooper.data.uistates.IntPreference
import com.example.photoswooper.data.uistates.LongPreference
import com.example.photoswooper.data.uistates.MainUiState
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
class MainViewModel (
    private val contentResolverInterface: ContentResolverInterface,
    private val mediaStatusDao: MediaStatusDao,
    private val startActivity: (Intent) -> Unit,
    private val dataStoreInterface: DataStoreInterface,
    private val makeToast: (String) -> Unit,
    private val uiCoroutineScope: CoroutineScope,
    val bottomSheetScaffoldState: BottomSheetScaffoldState,
    val player: ExoPlayer,
): ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState(isPlaying = player.isPlaying,))
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(
                    spaceSavedInTimeFrame = getSpaceSavedInTimeFrame()
                )
            }
        }
    }

    val snoozeLengthMillis = dataStoreInterface.getLongSettingValue(LongPreference.SNOOZE_LENGTH.setting)
    fun getCurrentMedia() =
        try {
            uiState.value.mediaItems[uiState.value.currentIndex]
        } catch (_: IndexOutOfBoundsException) {
            null
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


    fun getMediaToDelete() = uiState.value.mediaItems.filter { media ->
        media.status == MediaStatus.DELETE
    }

    /* Get new media items and add them to the UI state */
    suspend fun resetAndGetNewMediaItems() {
        val random = Random(17530163829)
        val numPerStackPreference =
            dataStoreInterface.getIntSettingValue(IntPreference.NUM_PHOTOS_PER_STACK.setting).first()

        // Delete old mediaItems from uiState
        _uiState.update { currentState ->
            currentState.copy(
                mediaItems = mutableListOf(),
                fetchingMedia = true
            )
        }
        // Add the first media synchronously
        val maxMediaItemsToAddSynchronously = minOf(numPerStackPreference, 2)
        val numPhotosToAddSynchronously = random.nextInt(maxMediaItemsToAddSynchronously)
        contentResolverInterface.getAllMediaFromMediaStore(
            onAddMedia = {
                _uiState.value.mediaItems.add(it)
            },
            targetNumPhotos = numPhotosToAddSynchronously,
            targetNumVideos = maxMediaItemsToAddSynchronously - numPhotosToAddSynchronously,
        )
        // Ensure media items were found before continuing
        if (uiState.value.mediaItems.isEmpty()) {
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
            if (getCurrentMedia()?.type == MediaType.VIDEO){
                onCurrentMediaIsVideo()
            }
            if (uiState.value.mediaItems.size < numPerStackPreference) {
                // Add the rest asynchronously
                viewModelScope.launch {
                    val numPhotosToAddAsync = random.nextInt(numPerStackPreference)
                    contentResolverInterface.getAllMediaFromMediaStore(
                        onAddMedia = {
                            _uiState.value.mediaItems.add(it)
                        },
                        targetNumPhotos = numPhotosToAddAsync,
                        targetNumVideos = numPerStackPreference - numPhotosToAddAsync,
                        mediaAdded = uiState.value.mediaItems.toMutableSet(),
                    )
                    shuffleItemsAfterCurrentIndex()
                    if (uiState.value.mediaItems.size != numPerStackPreference) {
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

    private fun shuffleItemsAfterCurrentIndex() {
        if (uiState.value.mediaItems.size > 1){
            Log.v("MainViewModel", "mediaItems before shuffle: ${uiState.value.mediaItems.map { it.type }}")
            val indecesRangeAfterCurrentIndex = uiState.value.currentIndex + 1..uiState.value.mediaItems.lastIndex
            uiState.value.mediaItems.subList(indecesRangeAfterCurrentIndex.first, indecesRangeAfterCurrentIndex.last)
                .shuffle()
            Log.v("MainViewModel", "mediaItems after shuffle: ${uiState.value.mediaItems.map { it.type }}")
        }
    }

    fun markItem(status: MediaStatus, index: Int = uiState.value.currentIndex) {
        // Set the status
        val mediaToMark = _uiState.value.mediaItems[index]
        _uiState.value.mediaItems[index].status = status

        /* Update database only if keeping/unsetting the media. Only marked as DELETE when confirmed and the file is deleted */
        if (status == MediaStatus.SNOOZE) {
            val snoozeLength = dataStoreInterface.getLongSettingValue(
                setting = LongPreference.SNOOZE_LENGTH.setting
            )
            CoroutineScope(Dispatchers.IO).launch {
                val snoozeEndDate = Calendar.getInstance().timeInMillis + snoozeLength.first()
                mediaStatusDao.update(
                    mediaToMark.getMediaStatusEntity().copy(
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
                mediaStatusDao.update(mediaToMark.getMediaStatusEntity())
            }

        /* If item being marked as UNSET (e.g. in the review screen), update the unset count & set the index to the first UNSET item */
        Log.d("Media marking", "Media at index $index marked as ${mediaToMark.status}")
        if (status == MediaStatus.UNSET) {
            seekToUnsetItemOrFalse()
            _uiState.update { currentState ->
                currentState.copy(
                    numUnset = currentState.numUnset + 1,
                )
            }
        }
        else {
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
            "Seeking to next media item, new index = ${uiState.value.currentIndex}/${uiState.value.mediaItems.size}"
        )
        Log.v("MainViewModel", "Media items left to swipe on = ${uiState.value.numUnset}")
    }

    fun seekToUnsetItemOrFalse(): Boolean {
        val unsetItemIndex = uiState.value.mediaItems.indexOfFirst { it.status == MediaStatus.UNSET }
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
        }
        else return false
    }

    fun undo(): Boolean {
        if (uiState.value.currentIndex > 0) { // First check if there is an action to undo
            viewModelScope.launch {
                exitImage()
                delay(100)
                if (uiState.value.currentIndex > 0) { // Check if undo valid again, after the above delay
                    val latestSwipedMediaIndex = uiState.value.currentIndex - 1
                    // Unset the status
                    _uiState.value.mediaItems[latestSwipedMediaIndex].status = MediaStatus.UNSET

                    // Decrement currentIndex
                    _uiState.update { currentState ->
                        currentState.copy(
                            currentIndex = latestSwipedMediaIndex,
                            numUnset = currentState.numUnset + 1,
                            mediaBuffering = true
                        )
                    }
                    if (getCurrentMedia()?.type == MediaType.VIDEO)
                        onCurrentMediaIsVideo()
                    /* Update database  */
                    CoroutineScope(Dispatchers.IO).launch {
                        val mediaItem = _uiState.value.mediaItems[latestSwipedMediaIndex]
                        mediaStatusDao.update(mediaItem.getMediaStatusEntity())
                    }
                }
            }
            return true
        }
        else {
            makeToast("Nothing to undo!")
            return false
        }
    }

    fun confirmDeletion() {
        val mediaToDelete = getMediaToDelete()
        // Delete the media in user's storage
        if(mediaToDelete.isNotEmpty()) {
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
            dismissReviewDialog()
        }
    }

    suspend fun onDeletion(deletedMediaUris: List<Uri>, deletionCancelled: Boolean = false) {
        /* If at least one media item was successfully deleted and android version != 10 */
        if (deletedMediaUris.isNotEmpty()) {
            if (Build.VERSION.SDK_INT != Build.VERSION_CODES.Q) { // Prevents spam of Toasts to user
                makeToast("${deletedMediaUris.size}/${getMediaToDelete().size} items successfully deleted")
            } else if (deletedMediaUris.size == getMediaToDelete().size)
                    makeToast("All selected items deleted")
            /* Update database & hide media in UI*/
            deletedMediaUris.forEach { deletedMediaUri ->
                val deletedMedia = uiState.value.mediaItems.find { it.uri == deletedMediaUri }
                if (deletedMedia != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        mediaStatusDao.update(deletedMedia.getMediaStatusEntity())
                    }
                    _uiState.update { currentState ->
                        val newMediaItems = currentState.mediaItems
                        newMediaItems[uiState.value.mediaItems.indexOf(deletedMedia)] = deletedMedia.copy(status = MediaStatus.KEEP)
                        return@update currentState.copy(
                            mediaItems = newMediaItems
                        )
                    }
                }
            }
            /* Update space saved in the current time frame */
            _uiState.update { currentState ->
                currentState.copy(
                    spaceSavedInTimeFrame = getSpaceSavedInTimeFrame()
                )
            }

            if (getMediaToDelete().isEmpty()) {
                dismissReviewDialog()
                if (uiState.value.numUnset <= 0)
                    CoroutineScope(Dispatchers.IO).launch { resetAndGetNewMediaItems() } // FIXME("Check permissions before getting media (cannot use checkPermissionsAndGetMedia() as no access to context)")
            }
        }
        else {
            if (deletionCancelled) {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q)
                    makeToast("Please click 'Allow' on each popup to delete the selected items")
                else
                    makeToast("Deletion cancelled")
            }
            else
                makeToast("Deletion unsuccessful, please check permissions.")
        }
    }

    fun expandBottomSheet(coroutineScope: CoroutineScope) {
        coroutineScope.launch {
            if (bottomSheetScaffoldState.bottomSheetState.hasExpandedState)
                bottomSheetScaffoldState.bottomSheetState.expand()
        }
    }

    fun showReviewDialog() {
        _uiState.update { currentState ->
            currentState.copy(
                showReviewDialog = true
            )
        }
    }
    fun dismissReviewDialog() {
        _uiState.update { currentState ->
            currentState.copy(
                showReviewDialog = false
            )
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

    fun toggleInfoAndFloatingActionsRow() {
        _uiState.update { currentState ->
            currentState.copy(
                showInfoAndFloatingActionsRow = !currentState.showInfoAndFloatingActionsRow
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

    fun openLocationInMapsApp(media: Media? = getCurrentMedia()) {
        val uri = "geo:${media?.location?.get(0)},${media?.location?.get(1)}"
        val intent = Intent(Intent.ACTION_VIEW, uri.toUri())
        try { startActivity(intent) }
        catch (_: ActivityNotFoundException) {
            makeToast("No suitable app found")
        }
    }
    fun openInGalleryApp(media: Media? = getCurrentMedia()) {
        val uri = media?.uri
        val intent = Intent(Intent.ACTION_VIEW, uri)
        try { startActivity(intent) }
        catch (_: ActivityNotFoundException) {
            makeToast("No suitable app found")
        }
    }

    fun share(media: Media? = _uiState.value.mediaItems[uiState.value.currentIndex]) {
        if (media != null) {
            val shareIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, media.uri)
                type = contentResolverInterface.getMediaType(media.uri)
            }
            startActivity(Intent.createChooser(shareIntent, null))
        }
    }

    fun disableReviewDialog() {
        _uiState.update { currentState ->
            currentState.copy(
                reviewDialogEnabled = false
            )
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
                currentStorageStatsTimeFrame =  newTimeFrame,
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

    fun toggleIsLoading(newState: Boolean = !uiState.value.fetchingMedia) {
        _uiState.update { currentState ->
            currentState.copy(
                fetchingMedia = newState
            )
        }
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
        if (getCurrentMedia()?.type == MediaType.VIDEO) player.play()
    }
    /* Ensures video is being shown before pausing */
    fun safePause() {
        if (getCurrentMedia()?.type == MediaType.VIDEO) player.pause()
    }
    fun updateIsPlaying(newState: Boolean = player.isPlaying) {
        _uiState.update { currentState ->
            currentState.copy(
                isPlaying = newState
            )
        }
    }

}