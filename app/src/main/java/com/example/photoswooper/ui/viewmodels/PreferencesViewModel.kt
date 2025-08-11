package com.example.photoswooper.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.photoswooper.data.uistates.BooleanPreference
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
        viewModelScope.launch { 
            updateAllPreferences()
        }
    }

    fun updateAllPreferences() {
        CoroutineScope(Dispatchers.IO).launch {
            val numPhotosPerStackPreference = dataStoreInterface.getIntSettingValue(IntPreference.num_photos_per_stack.toString()).first()
                ?: IntPreference.num_photos_per_stack.default
            _uiState.update { currentState ->
                currentState.copy(
                    numPhotosPerStack = numPhotosPerStackPreference,
                    numPhotosPerStackTextInput = numPhotosPerStackPreference.toString()
                )
            }
        }
        CoroutineScope(Dispatchers.IO).launch {
            _uiState.update { currentState ->
                currentState.copy(
                    permanentlyDelete = dataStoreInterface.getBooleanSettingValue(BooleanPreference.permanently_delete.toString()).first()
                        ?: BooleanPreference.permanently_delete.default
                )
            }
        }
        CoroutineScope(Dispatchers.IO).launch {
            _uiState.update { currentState ->
                currentState.copy(
                    systemFont = dataStoreInterface.getBooleanSettingValue(BooleanPreference.system_font.toString()).first()
                        ?: BooleanPreference.system_font.default
                )
            }
        }
        CoroutineScope(Dispatchers.IO).launch {
            _uiState.update { currentState ->
                currentState.copy(
                    dynamicTheme = dataStoreInterface.getBooleanSettingValue(BooleanPreference.dynamic_theme.toString()).first()
                        ?: BooleanPreference.dynamic_theme.default
                )
            }
        }
        CoroutineScope(Dispatchers.IO).launch {
            _uiState.update { currentState ->
                currentState.copy(
                    dynamicTheme = dataStoreInterface.getBooleanSettingValue(BooleanPreference.reduce_animations.toString()).first()
                        ?: BooleanPreference.reduce_animations.default
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
                    updatePhotosPerStackPreference(inputAsInt)
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

    fun updatePhotosPerStackPreference(newPreference: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            dataStoreInterface.setIntSettingValue(
                setting = IntPreference.num_photos_per_stack.toString(),
                newValue = newPreference
            )
        }
        _uiState.update { currentState ->
            currentState.copy(
                numPhotosPerStack = newPreference
            )
        }
    }

    fun togglePermanentlyDelete() {
        val newPreference = !uiState.value.permanentlyDelete
        CoroutineScope(Dispatchers.IO).launch {
            dataStoreInterface.setBooleanSettingValue(
                setting = BooleanPreference.permanently_delete.toString(),
                newValue = newPreference
            )
        }
        _uiState.update { currentState ->
            currentState.copy(
                permanentlyDelete = newPreference
            )
        }
    }

    fun toggleSystemFont() {
        val newPreference = !uiState.value.systemFont
        CoroutineScope(Dispatchers.IO).launch {
            dataStoreInterface.setBooleanSettingValue(
                setting = BooleanPreference.system_font.toString(),
                newValue = newPreference
            )
        }
        _uiState.update { currentState ->
            currentState.copy(
                systemFont = newPreference
            )
        }
    }

    fun toggleDynamicTheme() {
        val newPreference = !uiState.value.dynamicTheme
        CoroutineScope(Dispatchers.IO).launch {
            dataStoreInterface.setBooleanSettingValue(
                setting = BooleanPreference.dynamic_theme.toString(),
                newValue = newPreference
            )
        }
        _uiState.update { currentState ->
            currentState.copy(
                dynamicTheme = newPreference
            )
        }
    }
    fun toggleReduceAnimations() {
        val newPreference = !uiState.value.reduceAnimations
        CoroutineScope(Dispatchers.IO).launch {
            dataStoreInterface.setBooleanSettingValue(
                setting = BooleanPreference.reduce_animations.toString(),
                newValue = newPreference
            )
        }
        _uiState.update { currentState ->
            currentState.copy(
                reduceAnimations = newPreference
            )
        }
    }
}