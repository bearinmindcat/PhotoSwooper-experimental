package com.example.photoswooper.ui.view

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import com.example.photoswooper.data.models.Photo
import com.example.photoswooper.data.models.PhotoStatus
import com.example.photoswooper.data.uistates.MainUiState
import com.example.photoswooper.utils.ContentResolverInterface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MainViewModel(
    val contentResolverInterface: ContentResolverInterface,
    val context: Context
): ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()

    fun getPhotosToDelete() = uiState.value.photos.filter { photo ->
        photo.status == PhotoStatus.DELETE
    }

    fun getPhotos() {
        val newPhotos = contentResolverInterface.getPhotos()
        _uiState.update { currentState ->
            currentState.copy(
                photos = newPhotos,
                currentPhotoIndex = 0,
                numUnset = newPhotos.size
            )
        }
    }

    fun markPhoto(status: PhotoStatus, index: Int = uiState.value.currentPhotoIndex) {
        // Set the status
        _uiState.value.photos[index].status = status
        Log.d("Photo marking", "Photo at index ${index} marked as ${_uiState.value.photos[index].status}")
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
        }
        else {
            Toast.makeText(
                context,
                "Nothing to undo!",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun deletePhotos(photosToDelete: List<Photo> = getPhotosToDelete()) {
        if(photosToDelete.isNotEmpty()) {
            contentResolverInterface.deletePhotos(photosToDelete.map { it.uri })
            dismissReviewDialog()
            if (photosToDelete.isEmpty()) getPhotos()
            else _uiState.update { currentState ->
                currentState.copy(
                    photos = currentState.photos.filter {
                        !photosToDelete.contains(it)
                    },
                    currentPhotoIndex = currentState.currentPhotoIndex - photosToDelete.size,
                )
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
}