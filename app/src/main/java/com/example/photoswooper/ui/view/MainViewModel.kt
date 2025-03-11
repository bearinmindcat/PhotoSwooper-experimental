package com.example.photoswooper.ui.view

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import com.example.photoswooper.data.models.Photo
import com.example.photoswooper.data.models.PhotoStatus
import com.example.photoswooper.data.photoLimit
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
    fun getPhotoBitmap(photo: Photo = uiState.value.photos[uiState.value.currentPhotoIndex]) =
        contentResolverInterface.getImageBitmap(
        uri = photo.uri,
    )

    fun getPhotos() {
        val newPhotos = contentResolverInterface.getPhotos()
        _uiState.update { currentState ->
            currentState.copy(
                photos = newPhotos,
                currentPhotoIndex = 0,
                uncategorisedPhotos = photoLimit
            )
        }
    }

    fun markPhotoDelete() {
        // Set the status
        _uiState.value.photos[uiState.value.currentPhotoIndex].status = PhotoStatus.DELETE
        Log.d("Photo marking", "Photo at index ${uiState.value.currentPhotoIndex} marked as ${_uiState.value.photos[uiState.value.currentPhotoIndex].status}")
        // Increment currentPhotoIndex
        _uiState.update { currentState ->
            currentState.copy(
                currentPhotoIndex = currentState.currentPhotoIndex + 1,
                uncategorisedPhotos = currentState.uncategorisedPhotos - 1
            )
        }
    }

    fun markPhotoKeep() {
        // Set the status
        _uiState.value.photos[uiState.value.currentPhotoIndex].status = PhotoStatus.KEEP
        Log.d("Photo marking", "Photo at index ${uiState.value.currentPhotoIndex} marked as ${_uiState.value.photos[uiState.value.currentPhotoIndex].status}")
        // Increment currentPhotoIndex
        _uiState.update { currentState ->
            currentState.copy(
                currentPhotoIndex = currentState.currentPhotoIndex + 1,
                uncategorisedPhotos = currentState.uncategorisedPhotos - 1
            )
        }
    }

    fun undo() {
        if(uiState.value.currentPhotoIndex > 0) { // First check if there is an action to undo
            // Decrement currentPhotoIndex
            _uiState.update { currentState ->
                currentState.copy(
                    currentPhotoIndex = currentState.currentPhotoIndex - 1,
                    uncategorisedPhotos = currentState.uncategorisedPhotos + 1
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
}