package com.example.photoswooper.ui.view

import android.content.ContentResolver
import android.util.Log
import androidx.lifecycle.ViewModel
import com.example.photoswooper.data.models.Photo
import com.example.photoswooper.data.models.PhotoStatus
import com.example.photoswooper.data.uistates.MainUiState
import com.example.photoswooper.utils.ContentResolverInterface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MainViewModel(val contentResolverInterface: ContentResolverInterface): ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()

    fun getPhotosToDelete() = uiState.value.photos.filter { photo ->
        photo.status == PhotoStatus.DELETE
    }
    fun getUnCategorisedPhotos() = uiState.value.photos.filter { photo ->
        photo.status == PhotoStatus.UNSET
    }
    fun currentPhotoBitmap(photo: Photo = uiState.value.photos[uiState.value.currentPhotoIndex]) =
        contentResolverInterface.getImageBitmap(
        uri = photo.uri,
    )

    fun getPhotos(contentResolver: ContentResolver) {
        val newPhotos = contentResolverInterface.getPhotos()
        _uiState.update { currentState ->
            currentState.copy(
                photos = newPhotos
            )
        }
    }

    fun markPhotoDelete() {
        // Set the status
        _uiState.value.photos[uiState.value.currentPhotoIndex].status = PhotoStatus.DELETE
        Log.i("Photo marking", "Photo at index ${uiState.value.currentPhotoIndex} marked as ${_uiState.value.photos[uiState.value.currentPhotoIndex].status}")
        // Increment currentPhotoIndex
        _uiState.update { currentState ->
            currentState.copy(
                currentPhotoIndex = currentState.currentPhotoIndex + 1
            )
        }
    }

    fun markPhotoKeep() {
        // Set the status
        _uiState.value.photos[uiState.value.currentPhotoIndex].status = PhotoStatus.KEEP
        Log.i("Photo marking", "Photo at index ${uiState.value.currentPhotoIndex} marked as ${_uiState.value.photos[uiState.value.currentPhotoIndex].status}")
        // Increment currentPhotoIndex
        _uiState.update { currentState ->
            currentState.copy(
                currentPhotoIndex = currentState.currentPhotoIndex + 1
            )
        }
    }

    fun undo() {
        // Decrement currentPhotoIndex
        _uiState.update { currentState ->
            currentState.copy(
                currentPhotoIndex = currentState.currentPhotoIndex - 1
            )
        }
        // Unset the status
        _uiState.value.photos[uiState.value.currentPhotoIndex].status = PhotoStatus.UNSET
    }
}