package com.example.photoswooper.data.uistates

import android.os.Build
import android.os.Build.VERSION.SDK_INT

enum class BooleanPreference(val default: Boolean){
    permanently_delete(SDK_INT < Build.VERSION_CODES.R),
    system_font(false),
    dynamic_theme(SDK_INT >= Build.VERSION_CODES.S),
    reduce_animations(false),
    info_row_expanded(false) // Save whether the user last set the info row to be in the expanded view, or single row
}
enum class IntPreference(val default: Int) {
    num_photos_per_stack(30),
}

data class PrefsUiState (
    /** Text input from user to be validated before committed to preferences */
    val numPhotosPerStackTextInput: String = IntPreference.num_photos_per_stack.default.toString()
   )