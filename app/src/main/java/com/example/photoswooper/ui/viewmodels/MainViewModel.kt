package com.example.photoswooper.ui.viewmodels

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.photoswooper.data.database.MediaStatusDao
import com.example.photoswooper.data.models.Photo
import com.example.photoswooper.data.models.PhotoStatus
import com.example.photoswooper.data.uistates.IntPreference
import com.example.photoswooper.data.uistates.MainUiState
import com.example.photoswooper.data.uistates.TimeFrame
import com.example.photoswooper.utils.ContentResolverInterface
import com.example.photoswooper.utils.DataStoreInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date

class MainViewModel(
    private val contentResolverInterface: ContentResolverInterface,
    private val mediaStatusDao: MediaStatusDao,
    private val startActivity: (Intent) -> Unit,
    private val dataStoreInterface: DataStoreInterface,
    private val makeToast: (String) -> Unit,
): ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()

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
            numPhotos = 2
        )
        if (uiState.value.photos.isEmpty()) { // Check if zero unswiped photos could be found
            viewModelScope.launch {
                _uiState.update { currentState ->
                    currentState.copy(
                        isLoading = false
                    )
                }
            }
        }
        else {
            // Update UI state & prompt recomposition/update
            _uiState.update { currentState ->
                currentState.copy(
                    currentPhotoIndex = 0,
                    numUnset = dataStoreInterface.getIntSettingValue(IntPreference.num_photos_per_stack.toString()).first()
                        ?: IntPreference.num_photos_per_stack.default,
                    isLoading = false,
                )
            }
            // Add the rest of the photos asynchronously (speedy)
            viewModelScope.launch {
                contentResolverInterface.getPhotos(
                    onAddPhoto = {
                        _uiState.value.photos.add(it)
                    },
                    numPhotos = (dataStoreInterface.getIntSettingValue(IntPreference.num_photos_per_stack.toString()).first()
                        ?: IntPreference.num_photos_per_stack.default),
                    photosAdded = uiState.value.photos.toMutableSet()
                )
                if (uiState.value.photos.size != (dataStoreInterface.getIntSettingValue(IntPreference.num_photos_per_stack.toString()).first()
                        ?: IntPreference.num_photos_per_stack.default)
                ) {
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

    fun markPhoto(status: PhotoStatus, index: Int = uiState.value.currentPhotoIndex) {
        // Set the status
        val photo = _uiState.value.photos[index]
        _uiState.value.photos[index].status = status

        /* Update database only if keeping/unsetting the photo. Only marked as DELETE when confirmed and the file is deleted */
        CoroutineScope(Dispatchers.IO).launch {
            if (status != PhotoStatus.DELETE)
                mediaStatusDao.update(photo.getMediaStatusEntity())
        }

        /* If photo being marked as UNSET, update the unset count & set the index to the next UNSET photo */
        Log.d("Photo marking", "Photo at index ${index} marked as ${photo.status}")
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

    fun undo(): Boolean {
        if(uiState.value.currentPhotoIndex > 0) { // First check if there is an action to undo
            val decrementedPhotoIndex = uiState.value.currentPhotoIndex - 1
            // Unset the status
            _uiState.value.photos[decrementedPhotoIndex].status = PhotoStatus.UNSET

            // Decrement currentPhotoIndex
            _uiState.update { currentState ->
                currentState.copy(
                    currentPhotoIndex = decrementedPhotoIndex,
                    numUnset = currentState.numUnset + 1
                )
            }
            /* Update database  */
            CoroutineScope(Dispatchers.IO).launch {
                val photo = _uiState.value.photos[decrementedPhotoIndex]
                mediaStatusDao.update(photo.getMediaStatusEntity())
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

    fun toggleInfo() {
        _uiState.update { currentState ->
            currentState.copy(
                showInfo = !currentState.showInfo
            )
        }
    }

    fun openLocationInMapsApp(photo: Photo?) {
        val uri: String? = "geo:${photo?.location?.get(0)},${photo?.location?.get(1)}"
        val intent = Intent(Intent.ACTION_VIEW, uri?.toUri())
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
}