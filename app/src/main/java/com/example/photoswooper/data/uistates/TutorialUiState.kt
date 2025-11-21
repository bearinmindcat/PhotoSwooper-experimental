package com.example.photoswooper.data.uistates

import androidx.annotation.DrawableRes
import androidx.compose.ui.text.AnnotatedString
import kotlinx.parcelize.IgnoredOnParcel

data class TutorialUiState (
    val tutorialCardTitle: String = "",
    @IgnoredOnParcel val tutorialCardBody: AnnotatedString = AnnotatedString(""),
    @param:DrawableRes val tutorialCardIconDrawableId: Int? = null,
)