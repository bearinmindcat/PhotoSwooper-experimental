package com.example.photoswooper.data.uistates

import android.os.Build
import android.os.Build.VERSION.SDK_INT

enum class BooleanPreference(val default: Boolean){
    permanently_delete(SDK_INT < Build.VERSION_CODES.R),
    system_font(false),
    dynamic_theme(SDK_INT >= Build.VERSION_CODES.S),
    reduce_animations(false)
}
enum class IntPreference(val default: Int) {
    num_photos_per_stack(30),
}

data class PrefsUiState (
    /** The actual preference for the number of photos to be shown before asking to fetch more photos */
    val numPhotosPerStack: Int = IntPreference.num_photos_per_stack.default,
    /** Text input from user to be validated before committed to preferences */
    val numPhotosPerStackTextInput: String = "",
    val permanentlyDelete: Boolean = BooleanPreference.permanently_delete.default,
    val systemFont: Boolean = BooleanPreference.system_font.default,
    val dynamicTheme: Boolean = BooleanPreference.dynamic_theme.default,
    val reduceAnimations: Boolean = BooleanPreference.reduce_animations.default
)