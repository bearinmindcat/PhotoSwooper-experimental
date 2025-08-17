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
import com.example.photoswooper.data.database.MediaStatusDao
import com.example.photoswooper.data.models.Photo
import com.example.photoswooper.data.models.PhotoStatus
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
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
class MainViewModel (
    private val contentResolverInterface: ContentResolverInterface,
    private val mediaStatusDao: MediaStatusDao,
    private val startActivity: (Intent) -> Unit,
    private val dataStoreInterface: DataStoreInterface,
    private val makeToast: (String) -> Unit,
    val bottomSheetScaffoldState: BottomSheetScaffoldState,
): ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()
    val snoozeLengthMillis = dataStoreInterface.getLongSettingValue(LongPreference.snooze_length.toString())
    fun getCurrentPhoto() =
        try {
            uiState.value.photos[uiState.value.currentPhotoIndex]
        } catch (_: IndexOutOfBoundsException) {
            null
        }

    val reduceAnimations = dataStoreInterface
        .getBooleanSettingValue(BooleanPreference.reduce_animations.toString())
    val defaultEntryAnimationSpec = spring<Float>(
        stiffness = Spring.StiffnessMediumLow,
        dampingRatio = Spring.DampingRatioLowBouncy,
    )
    val defaultExitAnimationSpec = spring<Float>(
        Spring.DampingRatioNoBouncy,
        Spring.StiffnessMedium
    )
    val animatedImageScaleEntry = Animatable(0f)
    fun enterImage(coroutineScope: CoroutineScope) {
        coroutineScope.launch {
            animatedImageScaleEntry.snapTo(0f)
            if (reduceAnimations.first()) animatedImageScaleEntry.snapTo(1f)
            else animatedImageScaleEntry.animateTo(
                1f,
                defaultEntryAnimationSpec
            )
        }
    }
    fun exitImage(coroutineScope: CoroutineScope) {
        coroutineScope.launch {
            if (reduceAnimations.first()) animatedImageScaleEntry.snapTo(0f)
            else animatedImageScaleEntry.animateTo(
                0f,
                defaultExitAnimationSpec
            )
        }
    }


    fun getPhotosToDelete() = uiState.value.photos.filter { photo ->
        photo.status == PhotoStatus.DELETE
    }

    init {
        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(
                    spaceSavedInTimeFrame = getSpaceSavedInTimeFrame()
                )
            }
        }
    }

    /* Get new photos and add them to the UI state */
    suspend fun getNewPhotos() {
        val numPhotosPerStackPreference =
            dataStoreInterface.getIntSettingValue(IntPreference.num_photos_per_stack.toString()).first()
        // Delete old photos from uiState
        _uiState.update { currentState ->
            currentState.copy(
                photos = mutableListOf(),
                isLoading = true
            )
        }
        // Add the first two photos
        contentResolverInterface.getPhotos(
            onAddPhoto = {
                _uiState.value.photos.add(it)
            },
            numPhotos = minOf(numPhotosPerStackPreference, 2)
        )
        if (uiState.value.photos.isEmpty()) { // Ensure photos were found before continuing
            viewModelScope.launch {
                delay(1000) // Delay so that the loading indicator is shown
                _uiState.update { currentState ->
                    currentState.copy(
                        isLoading = false
                    )
                }
            }
        }
        else { // Update UI state & prompt recomposition/update
            _uiState.update { currentState ->
                currentState.copy(
                    currentPhotoIndex = 0,
                    numUnset = numPhotosPerStackPreference,
                    isLoading = false,
                )
            }
            if (uiState.value.photos.size < numPhotosPerStackPreference) {
                // Add the rest of the photos asynchronously (speedy)
                viewModelScope.launch {
                    contentResolverInterface.getPhotos(
                        onAddPhoto = {
                            _uiState.value.photos.add(it)
                        },
                        numPhotos = numPhotosPerStackPreference,
                        photosAdded = uiState.value.photos.toMutableSet()
                    )
                    if (uiState.value.photos.size != numPhotosPerStackPreference) {
                        makeToast("No more photos found, Last round!")
                        // Update UI state with the actual number of photos found
                        _uiState.update { currentState ->
                            currentState.copy(
                                numUnset = currentState.photos.filter { it.status == PhotoStatus.UNSET }.size
                            )
                        }
                    }
                }
            }
        }
    }

    fun markPhoto(status: PhotoStatus, index: Int = uiState.value.currentPhotoIndex) {
        // Set the status
        val photo = _uiState.value.photos[index]
        _uiState.value.photos[index].status = status

        /* Update database only if keeping/unsetting the photo. Only marked as DELETE when confirmed and the file is deleted */
        if (status == PhotoStatus.SNOOZE) {
            val snoozeLength = dataStoreInterface.getLongSettingValue(
                setting = LongPreference.snooze_length.toString()
            )
            CoroutineScope(Dispatchers.IO).launch {
                val snoozeEndDate = Calendar.getInstance().timeInMillis + snoozeLength.first()
                mediaStatusDao.update(
                    photo.getMediaStatusEntity().copy(
                        snoozedUntil = snoozeEndDate
                    )
                )
            }
            viewModelScope.launch {
                makeToast(
                    "Hidden for ${snoozeLength.first().milliseconds.inWholeDays} days"
                )
            }
        }
            else if (status != PhotoStatus.DELETE)
                CoroutineScope(Dispatchers.IO).launch {
                mediaStatusDao.update(photo.getMediaStatusEntity())
        }

        /* If photo being marked as UNSET (e.g. in the review screen), update the unset count & set the index to the first UNSET photo */
        Log.d("Photo marking", "Photo at index $index marked as ${photo.status}")
        if (status == PhotoStatus.UNSET)
            _uiState.update { currentState ->
                currentState.copy(
                    currentPhotoIndex = currentState.photos.indexOfFirst { it.status == PhotoStatus.UNSET },
                    numUnset = currentState.numUnset + 1,
                )
            }
    }

    fun nextPhoto() {
        // Increment currentPhotoIndex
        _uiState.update { currentState ->
            currentState.copy(
                currentPhotoIndex = currentState.currentPhotoIndex + 1,
                numUnset = currentState.numUnset - 1,
            )
        }
        Log.v("MainViewModel","Seeking to next photo, new index = ${uiState.value.currentPhotoIndex}/${uiState.value.photos.size - 1}")
        Log.v("MainViewModel","Photos left to swipe on = ${uiState.value.numUnset}")
    }

    fun seekToUnsetPhotoOrFalse(): Boolean {
        val unsetPhotoIndex = uiState.value.photos.indexOfFirst { it.status == PhotoStatus.UNSET }
        if (unsetPhotoIndex != -1) {
            _uiState.update { currentState ->
                currentState.copy(
                    currentPhotoIndex = unsetPhotoIndex
                )
            }
            return true
        }
        else return false
    }

    fun undo(coroutineScope: CoroutineScope): Boolean {
        if (uiState.value.currentPhotoIndex > 0) { // First check if there is an action to undo
            viewModelScope.launch {
                exitImage(coroutineScope)
                delay(100)
                if (uiState.value.currentPhotoIndex > 0) { // Check if undo valid again, after the above delay
                val latestSwipedPhotoIndex = uiState.value.currentPhotoIndex - 1
                // Unset the status
                _uiState.value.photos[latestSwipedPhotoIndex].status = PhotoStatus.UNSET

                // Decrement currentPhotoIndex
                _uiState.update { currentState ->
                    currentState.copy(
                        currentPhotoIndex = latestSwipedPhotoIndex,
                        numUnset = currentState.numUnset + 1
                    )
                }
                /* Update database  */
                CoroutineScope(Dispatchers.IO).launch {
                    val photo = _uiState.value.photos[latestSwipedPhotoIndex]
                    mediaStatusDao.update(photo.getMediaStatusEntity())
                }
                enterImage(coroutineScope)
                }
                else enterImage(coroutineScope)
            }
            return true
        }
        else {
            makeToast("Nothing to undo!")
            return false
        }
    }

    fun deletePhotos() {
        val photosToDelete = getPhotosToDelete()
        if(photosToDelete.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                contentResolverInterface.deletePhotos(
                    photosToDelete.map { it.uri },
                    onDelete = {
                        CoroutineScope(Dispatchers.Main).launch { onDeletePhotos(it) }
                    }
                ) // Delete the photo in user's storage
            }


        }
        /* If user cancels deletion of all photos */
        else {
            makeToast("No photos were deleted")
            dismissReviewDialog()
        }
    }

    suspend fun onDeletePhotos(deletedPhotoUris: List<Uri>, deletionCancelled: Boolean = false) {
        /* If at least one photo was successfully deleted and android version != 10 */
        if (deletedPhotoUris.size > 0) {
            if (Build.VERSION.SDK_INT != Build.VERSION_CODES.Q) { // Prevents spam of Toasts to user
                makeToast("${deletedPhotoUris.size}/${getPhotosToDelete().size} photos successfully deleted")
            } else if (deletedPhotoUris.size == getPhotosToDelete().size)
                    makeToast("All selected photos deleted")
            /* Update database & hide photo in UI*/
            deletedPhotoUris.forEach { deletedPhotoUri ->
                val deletedPhoto = uiState.value.photos.find { it.uri == deletedPhotoUri }
                if (deletedPhoto != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        mediaStatusDao.update(deletedPhoto.getMediaStatusEntity())
                    }
                    _uiState.update { currentState ->
                        val newPhotos = currentState.photos
                        newPhotos.set(
                            uiState.value.photos.indexOf(deletedPhoto),
                            deletedPhoto.copy(status = PhotoStatus.KEEP) // Hides the photo/video
                        )
                        return@update currentState.copy(
                            photos = newPhotos
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

            if (getPhotosToDelete().isEmpty()) {
                dismissReviewDialog()
                if (uiState.value.numUnset <= 0)
                    CoroutineScope(Dispatchers.IO).launch { getNewPhotos() } // FIXME("Check permissions before getting photos (cannot use checkPermissionsAndGetPhotos() as no access to context)")
            }
        }
        else {
            if (deletionCancelled) {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q)
                    makeToast("Please click 'Allow' on each popup to delete the selected photos")
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

    fun toggleInfoRowSize() {
        // Update user preference
        CoroutineScope(Dispatchers.IO).launch {
            val infoRowExpandedSetting = BooleanPreference.info_row_expanded.toString()
            dataStoreInterface.setBooleanSettingValue(
                newValue = !(dataStoreInterface.getBooleanSettingValue(infoRowExpandedSetting).first()),
                setting = infoRowExpandedSetting
            )
        }
    }

    fun toggleInfoAndActionButtons() {
        _uiState.update { currentState ->
            currentState.copy(
                showInfoAndFloatingActions = !currentState.showInfoAndFloatingActions
            )
        }
    }

    fun openLocationInMapsApp(photo: Photo? = getCurrentPhoto()) {
        val uri = "geo:${photo?.location?.get(0)},${photo?.location?.get(1)}"
        val intent = Intent(Intent.ACTION_VIEW, uri.toUri())
        try { startActivity(intent) }
        catch (_: ActivityNotFoundException) {
            makeToast("No suitable app found")
        }
    }
    fun openPhotoInGalleryApp(photo: Photo? = getCurrentPhoto()) {
        val uri = photo?.uri
        val intent = Intent(Intent.ACTION_VIEW, uri)
        try { startActivity(intent) }
        catch (_: ActivityNotFoundException) {
            makeToast("No suitable app found")
        }
    }

    fun sharePhoto(photo: Photo? = _uiState.value.photos[uiState.value.currentPhotoIndex]) {
        if (photo != null) {
            val shareIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, photo.uri)
                type = contentResolverInterface.getMediaType(photo.uri)
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

    fun toggleIsLoading(newState: Boolean = !uiState.value.isLoading) {
        _uiState.update { currentState ->
            currentState.copy(
                isLoading = newState
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
                setting = LongPreference.snooze_length.toString(),
                newValue = newSnoozeLength
            )
        }
    }
}