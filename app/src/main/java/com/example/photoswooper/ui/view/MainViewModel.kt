package com.example.photoswooper.ui.view

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.photoswooper.data.database.MediaStatusDao
import com.example.photoswooper.data.models.Photo
import com.example.photoswooper.data.models.PhotoStatus
import com.example.photoswooper.data.photoLimit
import com.example.photoswooper.data.uistates.MainUiState
import com.example.photoswooper.data.uistates.TimeFrame
import com.example.photoswooper.utils.ContentResolverInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.*

class MainViewModel(
    val contentResolverInterface: ContentResolverInterface,
    val mediaStatusDao: MediaStatusDao,
    val context: Context
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
    suspend fun getPhotos() {
        // Add the first two photos
        contentResolverInterface.getPhotos(
            onAddPhoto = {
                _uiState.value.photos.add(it)
            },
            numPhotos = 2
        )
        // Add the rest of the photos asynchronously (speedy)
        viewModelScope.launch {
            contentResolverInterface.getPhotos(
                onAddPhoto = {
                    _uiState.value.photos.add(it)
                },
                numPhotos = photoLimit - 2
            )
        }

        // Update UI state & prompt recomposition/update
        _uiState.update { currentState ->
            currentState.copy(
                currentPhotoIndex = 0,
                numUnset = photoLimit,
            )
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
                    numUnset = currentState.numUnset + 1
                )
            }
    }

    fun nextPhoto() {
        // Increment currentPhotoIndex
        _uiState.update { currentState ->
            currentState.copy(
                currentPhotoIndex = currentState.currentPhotoIndex + 1,
                numUnset = currentState.numUnset - 1
            )
        }
    }

    fun findUnsetPhoto() {
        _uiState.update { currentState ->
            currentState.copy(
                currentPhotoIndex = currentState.photos.indexOfFirst { it.status == PhotoStatus.UNSET }
            )
        }
    }

    fun undo() {
        if(uiState.value.currentPhotoIndex > 0) { // First check if there is an action to undo
            // Decrement currentPhotoIndex
            _uiState.update { currentState ->
                currentState.copy(
                    currentPhotoIndex = currentState.currentPhotoIndex - 1,
                    numUnset = currentState.numUnset + 1
                )
            }
            // Unset the status
            _uiState.value.photos[uiState.value.currentPhotoIndex].status = PhotoStatus.UNSET

            /* Update database  */
            CoroutineScope(Dispatchers.IO).launch {
                val photo = _uiState.value.photos[uiState.value.currentPhotoIndex]
                mediaStatusDao.update(photo.getMediaStatusEntity())
            }
        }
        else {
            Toast.makeText(
                context,
                "Nothing to undo!",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    suspend fun deletePhotos(photosToDelete: List<Photo> = getPhotosToDelete()) {
        if(photosToDelete.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                contentResolverInterface.deletePhotos(photosToDelete.map { it.uri }) // Delete the photo in user's storage
            }

            /* Update database */
            CoroutineScope(Dispatchers.IO).launch {
                photosToDelete.forEach { photo ->
                    mediaStatusDao.update(photo.getMediaStatusEntity())
                }
            }

            dismissReviewDialog()

            if (photosToDelete.isEmpty()) getPhotos() // TODO("Is this if statement needed?")
            else _uiState.update { currentState ->
                currentState.copy(
                    photos = currentState.photos.filter {
                        !photosToDelete.contains(it)
                    }.toMutableList(),
                    currentPhotoIndex = currentState.currentPhotoIndex - photosToDelete.size,
                    spaceSavedInTimeFrame = getSpaceSavedInTimeFrame()
                )
            }
        }
        else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Toast.makeText(
                    context,
                    "No photos were deleted",
                    Toast.LENGTH_SHORT
                ).show()
            }
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
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        context.startActivity(intent)
    }

    fun sharePhoto(photo: Photo? = _uiState.value.photos[uiState.value.currentPhotoIndex]) {
        if (photo != null) {
            val shareIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, photo.uri)
                type = context.contentResolver.getType(photo.uri)
            }
            context.startActivity(Intent.createChooser(shareIntent, null))
        }
    }

    fun disableReviewDialog() {
        _uiState.update { currentState ->
            currentState.copy(
                reviewDialogEnabled = false
            )
        }
    }

    suspend fun cycleStatsTimeFrame() {
        val currentTimeFrame = _uiState.value.currentStatsTimeFrame
        val newTimeFrame =
            if (currentTimeFrame != TimeFrame.entries.last())
                TimeFrame.entries[currentTimeFrame.ordinal + 1]
            else
                TimeFrame.entries.first()
        _uiState.update { currentState ->
            currentState.copy(
                currentStatsTimeFrame =  newTimeFrame,
                spaceSavedInTimeFrame = getSpaceSavedInTimeFrame(newTimeFrame)
            )
        }
    }
    suspend fun getSpaceSavedInTimeFrame(timeFrame: TimeFrame = _uiState.value.currentStatsTimeFrame): Long {
        val currentDate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Date().toInstant().toEpochMilli()
        } else {
            TODO("Get current date in epoch milli for Android version < O")
        }
        val firstDateInTimeFrame = currentDate - timeFrame.milliseconds

        return mediaStatusDao.getSizeBetweenDates(firstDateInTimeFrame, currentDate)?.sum()?: 0
    }
}