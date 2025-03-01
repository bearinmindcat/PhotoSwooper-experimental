package com.example.photoswooper.ui.view

import android.content.ContentResolver
import androidx.lifecycle.ViewModel
import com.example.photoswooper.data.uistates.MainUiState
import com.example.photoswooper.utils.ContentResolverInterface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MainViewModel(val contentResolverInterface: ContentResolverInterface): ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()

    fun getPhotos(contentResolver: ContentResolver) {
        val newPhotos = contentResolverInterface.getPhotos(contentResolver)
        _uiState.update { currentState ->
            currentState.copy(
                photos = newPhotos
            )
        }
    }
}