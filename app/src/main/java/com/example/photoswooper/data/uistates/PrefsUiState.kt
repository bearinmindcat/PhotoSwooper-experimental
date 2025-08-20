package com.example.photoswooper.data.uistates

import android.os.Build
import android.os.Build.VERSION.SDK_INT

enum class BooleanPreference(val default: Boolean, val setting: String){
    PERMANENTLY_DELETE(SDK_INT < Build.VERSION_CODES.R, "permanently_delete"),
    SYSTEM_FONT(false,"system_font"),
    DYNAMIC_THEME(SDK_INT >= Build.VERSION_CODES.S, "dynamic_theme"),
    REDUCE_ANIMATIONS(false, "reduce_animations"),
    INFO_ROW_EXPANDED(false, "info_row_expanded") // Save whether the user last set the info row to be in the expanded view, or single row
}
enum class IntPreference(val default: Int, val setting: String) {
    /** Number of media items per stack (poorly named due to being created before videos) */
    NUM_PHOTOS_PER_STACK(30, "num_photos_per_stack"),
}

enum class LongPreference(val default: Long, val setting: String) {
    SNOOZE_LENGTH(2*TimeFrame.WEEK.milliseconds, "snooze_length"),
}

data class PrefsUiState (
    /** Text input from user to be validated before committed to preferences */
    val numPhotosPerStackTextInput: String = IntPreference.NUM_PHOTOS_PER_STACK.default.toString()
   )