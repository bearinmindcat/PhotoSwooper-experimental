package com.example.photoswooper.ui.components

import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.photoswooper.R
import com.example.photoswooper.data.uistates.BooleanPreference
import com.example.photoswooper.dataStore
import com.example.photoswooper.utils.DataStoreInterface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownFilterChip(
    leadingIconPainter: Painter,
    currentSelection: String,
    expandContentDescription: String,
    menuItems: Array<String>,
    menuItemIcons: Array<Painter> = emptyArray(),
    onSelectionChange: (String) -> Unit
) {
    fun getDisplayedString(menuItem: String): String = menuItem.replace("_", " ").replaceFirstChar { it.uppercase() }

    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    var expanded by remember { mutableStateOf(false) }
    var filterChipWidth: Dp by remember { mutableStateOf(0.dp) }
    val reduceAnimations = DataStoreInterface(context.dataStore)
        .getBooleanSettingValue(BooleanPreference.reduce_animations.toString()).collectAsState(false)

    val caretRotation = animateFloatAsState(
        if (expanded) 180f else 0f,
        animationSpec = spring(
            stiffness = if (reduceAnimations.value == true) 0f else Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioLowBouncy,
        ),
        label = "filterChipCaretRotation",
    )

    Column(Modifier.wrapContentWidth(unbounded = true)) {
        FilterChip(
            selected = false,
            leadingIcon = {
                Icon(
                    leadingIconPainter,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(dimensionResource(R.dimen.small_icon))
                )
            },
            label = {
                Text(
                    getDisplayedString(currentSelection),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                Icon(
                    painter = painterResource(R.drawable.caret_down),
                    contentDescription = expandContentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(dimensionResource(R.dimen.small_icon))
                        .rotate(caretRotation.value)
                )
            },
            onClick = {
                expanded = !expanded
                if (SDK_INT >= Build.VERSION_CODES.R)
                    view.performHapticFeedback(HapticFeedbackConstants.TOGGLE_ON)
                      },
            colors =
                FilterChipDefaults.filterChipColors().copy(
                    containerColor =
                        if (expanded) MaterialTheme.colorScheme.surfaceContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow
                ),
            modifier = Modifier.onGloballyPositioned { coordinates ->
                with (density) {
                    filterChipWidth = coordinates.size.width.toDp()
                }
            }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                if (SDK_INT >= Build.VERSION_CODES.R)
                    view.performHapticFeedback(HapticFeedbackConstants.TOGGLE_OFF)
                               },
            border = FilterChipDefaults.filterChipBorder(true, false),
            shape = FilterChipDefaults.shape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.width(filterChipWidth)
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
                    text = { Text(
                        getDisplayedString(menuItem),
                        style = MaterialTheme.typography.labelLarge,
                    ) },
                    onClick = {
                        expanded = !expanded
                        onSelectionChange(menuItem)
                    },
                    enabled = menuItem != currentSelection
                )
            }
        }
    }
}