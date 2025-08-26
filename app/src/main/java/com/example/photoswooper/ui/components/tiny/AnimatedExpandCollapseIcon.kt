/*
 * SPDX-FileCopyrightText: 2025 Loowiz <loowiz@envs.net>
 *
 *  SPDX-License-Identifier: AGPL-3.0-only
 */

package com.example.photoswooper.ui.components.tiny

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import com.example.photoswooper.R
import com.example.photoswooper.data.uistates.BooleanPreference
import com.example.photoswooper.dataStore
import com.example.photoswooper.utils.DataStoreInterface

@Composable
fun AnimatedExpandCollapseIcon(
    modifier: Modifier = Modifier,
    expanded: Boolean,
    onClick: (() -> Unit)? = null,
    contentDescription: String?,
) {
    val context = LocalContext.current
    val reduceAnimations = DataStoreInterface(context.dataStore)
        .getBooleanSettingValue(BooleanPreference.REDUCE_ANIMATIONS.setting).collectAsState(false)
    val caretRotation = animateFloatAsState(
        if (expanded) 180f else 0f,
        animationSpec = spring(
            stiffness = if (reduceAnimations.value) 0f else Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioLowBouncy,
        ),
        label = "filterChipCaretRotation",
    )

    Icon(
        painter = painterResource(R.drawable.caret_down),
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .size(dimensionResource(R.dimen.small_icon))
            .rotate(caretRotation.value)
            .then(
                if (onClick != null) Modifier.clickable { onClick() }
                else Modifier
            )
    )
}