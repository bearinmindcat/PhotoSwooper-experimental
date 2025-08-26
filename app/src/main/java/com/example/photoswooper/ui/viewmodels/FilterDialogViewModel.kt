package com.example.photoswooper.ui.viewmodels

import com.example.photoswooper.data.models.MediaFilter
import com.example.photoswooper.data.models.MediaSortField
import com.example.photoswooper.data.models.MediaType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class FileSize(val multiplier: Float) {
    KB(1000f),
    MB(1000000f),
    GB(1000000000f)
}

class FilterDialogViewModel(initialFilter: MediaFilter) {
    private val _newFilters = MutableStateFlow(initialFilter)
    val newFilters = _newFilters.asStateFlow()

    fun toggleType(type: MediaType, onError: () -> Unit) {
        val newMediaTypes = if (newFilters.value.mediaTypes.contains(type)) newFilters.value.mediaTypes.minus(type)
        else newFilters.value.mediaTypes.plus(type)

        // Validate that there at least one media type (photo or video) is selected
        if (newMediaTypes.isNotEmpty()) {
            _newFilters.update { currentFilters ->
                currentFilters.copy(
                    mediaTypes = newMediaTypes
                )
            }
        }
        else
            onError()
    }

    fun updateSortField(newSortField: MediaSortField) {
        _newFilters.update { currentFilters ->
            currentFilters.copy(
                sortField = newSortField
            )
        }
    }
    fun toggleSortAscending(newValue: Boolean = !_newFilters.value.sortAscending) {
        _newFilters.update { currentFilters ->
            currentFilters.copy(
                sortAscending = newValue
            )
        }
    }

    fun updateContainsText(input: String) {
        _newFilters.update { currentFilters ->
            currentFilters.copy(
                containsText = input
            )
        }
    }

    fun updateDirectory(newDirectory: String) {
        _newFilters.update { currentFilters ->
            currentFilters.copy(
                directory = newDirectory
                    .substringAfterLast("%3A")
                    .replace("%2F", "/")
            )
        }
    }
}