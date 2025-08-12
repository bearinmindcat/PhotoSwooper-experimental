package com.example.photoswooper.ui.viewmodels

import androidx.lifecycle.ViewModel
import com.example.photoswooper.data.uistates.IntPreference
import com.example.photoswooper.data.uistates.PrefsUiState
import com.example.photoswooper.utils.DataStoreInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The viewModel used by the `PreferencesCard` function in [com.example.photoswooper.ui.view.TabbedPreferencesAndStatsPage]
 */
class PrefsViewModel(val dataStoreInterface: DataStoreInterface) : ViewModel() {
    private val _uiState = MutableStateFlow(PrefsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        updateStackTextInput()
    }
    fun updateStackTextInput() {
        CoroutineScope(Dispatchers.IO).launch {
            _uiState.update { currentState ->
                currentState.copy(
                    numPhotosPerStackTextInput = dataStoreInterface.getIntSettingValue(IntPreference.num_photos_per_stack.toString()).first().toString()
                )
            }
        }
    }

    fun validatePhotosPerStackInputAndUpdate(input: String): Boolean {
        val inputAsInt = input.toIntOrNull()
        when {
            (inputAsInt != null) -> {
                if (inputAsInt in 1..100) { // Separate if statements so user doesn't see error message when inputting 0
                    updatePhotosPerStackInput(input)
                    updateIntPreference(IntPreference.num_photos_per_stack.toString(), inputAsInt)
                }
            }
            (input == "") -> updatePhotosPerStackInput(input)
            else -> return false
        }

        return true
    }

    fun updatePhotosPerStackInput(input: String) {
        _uiState.update { currentState ->
            currentState.copy(
                numPhotosPerStackTextInput = input
            )
        }
    }

    fun updateIntPreference(setting: String, newPreference: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            dataStoreInterface.setIntSettingValue(
                setting = setting,
                newValue = newPreference
            )
        }
    }

    fun toggleBooleanSetting(setting: String) {
        CoroutineScope(Dispatchers.IO).launch {
            dataStoreInterface.setBooleanSettingValue(
                setting = setting,
                newValue = !dataStoreInterface.getBooleanSettingValue(setting).first(),
            )
        }
    }
}