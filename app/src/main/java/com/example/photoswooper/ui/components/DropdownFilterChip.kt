/*
 * SPDX-FileCopyrightText: 2025 Loowiz <loowiz@envs.net>
 *
 *  SPDX-License-Identifier: AGPL-3.0-only
 */

package com.example.photoswooper.ui.components

import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import com.example.photoswooper.R
import com.example.photoswooper.ui.components.tiny.AnimatedExpandCollapseIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownFilterChip(
    leadingIconPainter: Painter,
    currentMenuItemSelection: String,
    filterChipSelected: Boolean = false,
    menuItemsDescription: String,
    menuItems: Array<String>,
    menuItemIcons: Array<Painter> = emptyArray(),
    onSelectionChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    fun getDisplayedString(menuItem: String): String = menuItem.replace("_", " ").replaceFirstChar { it.uppercase() }

    val view = LocalView.current
    var expanded by remember { mutableStateOf(false) }

    Column(modifier) {
        FilterChip(
            selected = filterChipSelected,
            leadingIcon = {
                Icon(
                    leadingIconPainter,
                    contentDescription = null,
                    tint = if (filterChipSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(dimensionResource(R.dimen.small_icon))
                )
            },
            label = {
                Text(
                    getDisplayedString(currentMenuItemSelection),
                    style = MaterialTheme.typography.labelLarge,
                )
            },
            trailingIcon = {
                AnimatedExpandCollapseIcon(
                    expanded = expanded,
                    contentDescription = if (expanded) "Hide $menuItemsDescription" else "Display $menuItemsDescription"
                )
            },
            onClick = {
                expanded = !expanded
                if (SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                    view.performHapticFeedback(HapticFeedbackConstants.TOGGLE_ON)
            },
            colors =
                if (expanded)
                    FilterChipDefaults.filterChipColors().copy(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                else FilterChipDefaults.filterChipColors(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                if (SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                    view.performHapticFeedback(HapticFeedbackConstants.TOGGLE_OFF)
            },
            border = FilterChipDefaults.filterChipBorder(enabled = true, selected = false),
            shape = FilterChipDefaults.shape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            menuItems.forEach { menuItem ->
                val menuItemIndex = menuItems.indexOf(menuItem)
                DropdownMenuItem(
                    leadingIcon = {
                        if (menuItemIcons.getOrNull(menuItemIndex) is Painter)
                            Icon(
                                menuItemIcons[menuItemIndex],
                                contentDescription = null,
                                modifier = Modifier.size(dimensionResource(R.dimen.small_icon))
                            )
                    },
                    text = {
                        Text(
                            getDisplayedString(menuItem),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                    onClick = {
                        expanded = !expanded
                        onSelectionChange(menuItem)
                    },
                    enabled = menuItem != currentMenuItemSelection
                )
            }
        }
    }
}