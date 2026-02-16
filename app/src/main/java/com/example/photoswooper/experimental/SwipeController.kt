package com.example.photoswooper.experimental

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import com.example.photoswooper.data.models.MediaStatus

interface SwipeController {
    val animatedMediaScale: Animatable<Float, AnimationVector1D>
    fun markItem(status: MediaStatus)
    fun next()
    fun onMediaLoaded(mediaAspectRatio: Float = 1f)
    fun onMediaError(errorMessage: String?)
    fun toggleInfoAndFloatingActionsRow()
    fun getCurrentItemSize(): Long?
}
