/*
 * SPDX-FileCopyrightText: 2025 Loowiz <loowiz@envs.net>
 *
 *  SPDX-License-Identifier: AGPL-3.0-only
 */

package com.example.photoswooper.ui.components

import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.photoswooper.R
import com.example.photoswooper.data.BooleanPreference
import com.example.photoswooper.data.StringPreference
import com.example.photoswooper.data.models.MediaFilter
import com.example.photoswooper.data.models.MediaSortField
import com.example.photoswooper.data.models.MediaType
import com.example.photoswooper.dataStore
import com.example.photoswooper.ui.components.tiny.AnimatedExpandCollapseIcon
import com.example.photoswooper.ui.viewmodels.FileSize
import com.example.photoswooper.ui.viewmodels.FilterDialogViewModel
import com.example.photoswooper.utils.DataStoreInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.RoundingMode
import kotlin.math.pow


// TODO: Change to a scrollable horizontal list of filter chip menus?
@Composable
fun FilterDialog(
    onDismiss: () -> Unit,
    onConfirm: (newFilter: MediaFilter, setFilterAsDefault: Boolean) -> Unit,
    filterDialogViewModel: FilterDialogViewModel,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val reduceAnimations by DataStoreInterface(context.dataStore)
        .getBooleanSettingValue(BooleanPreference.REDUCE_ANIMATIONS.setting).collectAsState(false)

    val newFilters by filterDialogViewModel.newFilters.collectAsState()
    var setFilterAsDefault by remember { mutableStateOf(false) }
    var currentSizeMultiplier by remember {
        mutableStateOf(
            when {
                newFilters.sizeRange.first / FileSize.GB.multiplier > 1 -> FileSize.GB
                newFilters.sizeRange.first / FileSize.MB.multiplier > 1 -> FileSize.MB
                else -> FileSize.KB
            }
        )
    }
    var displayedMinSize by remember {
        mutableStateOf(
            newFilters.sizeRange.first.div(currentSizeMultiplier.multiplier).toString()
        )
    }

    fun getMinSizeFromDisplayedString(string: String = displayedMinSize) =
        ((string.toFloatOrNull() ?: 0f) * (currentSizeMultiplier.multiplier))

    // Activity launcher to request user to select directory / album
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { result ->
        if (result != null) {
            val decodedDirectoryUri = Uri.decode(result.toString())
            Log.i("Filters", "User has chosen album to filter. directory is ${decodedDirectoryUri.substringAfterLast(":")}")
            filterDialogViewModel.updateDirectory(decodedDirectoryUri.substringAfterLast(":"))
        }
    }

    val sortIconRotation by animateFloatAsState(if (newFilters.sortAscending) 180f else 0f)
    val titleStyle = MaterialTheme.typography.titleMedium

    var tapToDismissWarningEnabled by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = {
            if (tapToDismissWarningEnabled) {
                tapToDismissWarningEnabled = false
                Toast.makeText(
                    context,
                    R.string.tap_again_to_dismiss,
                    Toast.LENGTH_SHORT
                ).show()
                // Enable warning again after 3 seconds
                CoroutineScope(Dispatchers.Main).launch {
                    delay(2000)
                    tapToDismissWarningEnabled = true
                }
            }
            else {
                onDismiss()
            }
        },
    ) {
        Card(
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensionResource(R.dimen.padding_medium)),
        ) {
            Column(
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(dimensionResource(R.dimen.padding_medium)),
            ) {
                // Filtering & sorting title Row
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .padding(bottom = dimensionResource(R.dimen.padding_medium))
                        .fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(R.drawable.funnel),
                        contentDescription = null,
                        modifier = Modifier.padding(end = dimensionResource(R.dimen.padding_xsmall))
                    )
                    Text(
                        "Filters",
                        style = MaterialTheme.typography.titleLarge.copy(),
                    )
                }
                // Type
                Text("Include:", style = titleStyle)
                Row(
                    horizontalArrangement = Arrangement.SpaceAround,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = dimensionResource(R.dimen.padding_medium))
                ) {
                    for (mediaType in MediaType.entries) {
                        FilterChip(
                            selected = newFilters.mediaTypes.contains(mediaType),
                            onClick = {
                                filterDialogViewModel.toggleMediaType(
                                    mediaType,
                                    onSuccess = {
                                        if (Build.VERSION.SDK_INT >= 34) {
                                            if (newFilters.mediaTypes.contains(mediaType))
                                                view.performHapticFeedback(HapticFeedbackConstants.TOGGLE_OFF)
                                            else
                                                view.performHapticFeedback(HapticFeedbackConstants.TOGGLE_ON)
                                        }
                                    },
                                    onError = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                                            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                                        Toast.makeText(
                                            context,
                                            "Select at least one of: photos or videos",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                            },
                            label = {
                                Text(
                                    mediaType.toString().lowercase().replaceFirstChar { it.uppercase() } + "s"
                                )
                            },
                            leadingIcon = {
                                newFilters.sizeRange.first.div(currentSizeMultiplier.multiplier).toString()
                                Icon(
                                    painterResource(if (mediaType == MediaType.VIDEO) R.drawable.video else R.drawable.image),
                                    contentDescription = null,
                                    tint = // Change colour based on whether is selected
                                        if (newFilters.mediaTypes.contains(mediaType)) MaterialTheme.colorScheme.onSecondaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(dimensionResource(R.dimen.small_icon))
                                )
                            },
                            trailingIcon = {
                                if (newFilters.mediaTypes.contains(mediaType)) Icon(
                                    painter = painterResource(R.drawable.check),
                                    contentDescription = null,
                                    tint = // Change colour based on whether is selected
                                        if (newFilters.mediaTypes.contains(mediaType)) MaterialTheme.colorScheme.onSecondaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(dimensionResource(R.dimen.xsmall_icon))
                                )
                            },
                        )
                    }
                }
                // Sort by
                Text("Sort by:", style = titleStyle)
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = dimensionResource(R.dimen.padding_medium))
                ) {
                    Column {
                            DropdownFilterChip(
                                leadingIconPainter = painterResource(newFilters.sortField.iconDrawableId),
                                selectedMenuItem = newFilters.sortField.toString().lowercase(),
                                filterChipSelected = newFilters.sortField != MediaSortField.RANDOM,
                                menuItemsDescription = "Fields to sort media by",
                                menuItems = MediaSortField.entries.map { it.toString().lowercase() }.toTypedArray(),
                                menuItemIcons = MediaSortField.entries.map { painterResource(it.iconDrawableId) }
                                    .toTypedArray(),
                                onSelectionChange = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    filterDialogViewModel.updateSortField(MediaSortField.valueOf(it.uppercase()))
                                }
                            )
                            AnimatedVisibility(
                                newFilters.sortField != MediaSortField.RANDOM,
                                enter = if (reduceAnimations) fadeIn()
                                else fadeIn() + expandVertically(
                                    animationSpec = spring(
                                        stiffness = Spring.StiffnessMediumLow,
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                    ),
                                ),
                                exit = if (reduceAnimations) fadeOut()
                                else fadeOut() + shrinkVertically (
                                    animationSpec = spring(
                                        stiffness = Spring.StiffnessMediumLow,
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                    ),
                                ),
                                label = "sort ascending"
                            ) {
                                DropdownFilterChip(
                                    leadingIconPainter = if (newFilters.sortAscending) painterResource(R.drawable.sort_descending) else painterResource(
                                        R.drawable.sort_ascending
                                    ),
                                    selectedMenuItem = if (newFilters.sortAscending) "Ascending" else "Descending",
                                    menuItemsDescription = "Sort order options (ascending/descending)",
                                    menuItems = arrayOf("Ascending", "Descending"),
                                    menuItemIcons = arrayOf(
                                        painterResource(R.drawable.sort_descending),
                                        painterResource(R.drawable.sort_ascending)
                                    ),
                                    onSelectionChange = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                        filterDialogViewModel.toggleSortAscending()
                                    }
                                )
                            }
                    }
                    AnimatedVisibility(newFilters.sortField.sortOrderString != StringPreference.FILTER_SORT_FIELD.default) {
                        IconButton(onClick = {
                            filterDialogViewModel.updateSortField(MediaSortField.entries.first { it.sortOrderString == StringPreference.FILTER_SORT_FIELD.default })
                            filterDialogViewModel.toggleSortAscending(BooleanPreference.FILTER_SORT_ASCENDING.default)
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.undo),
                                contentDescription = "Reset sort field & direction to default",
                                modifier = Modifier.size(dimensionResource(R.dimen.small_icon))
                            )
                        }
                    }
                }
                // Album or file path
                Text("Album:", style = titleStyle)
                Row(modifier = Modifier.padding(bottom = dimensionResource(R.dimen.padding_medium))) {
                    InputChip(
                        selected = newFilters.directory != "",
                        onClick = {
                            launcher.launch(null)
                        },
                        label = {
                            Text(
                                if (newFilters.directory != "") newFilters.directory
                                    .substringAfterLast("/")
                                else "Any album"
                            )
                        },
                        leadingIcon = {
                            Icon(
                                painterResource(R.drawable.books),
                                null,
                                Modifier.size(dimensionResource(R.dimen.small_icon))
                            )
                        }
                    )
                    AnimatedVisibility(newFilters.directory != StringPreference.FILTER_DIRECTORIES.default) {
                        IconButton(onClick = { filterDialogViewModel.updateDirectory(StringPreference.FILTER_DIRECTORIES.default) }) {
                            Icon(
                                painter = painterResource(R.drawable.undo),
                                contentDescription = "Reset album filter to default",
                                modifier = Modifier.size(dimensionResource(R.dimen.small_icon))
                            )
                        }
                    }
                }
                // File` larger than:
                Text("Minimum file size:", style = titleStyle)
                OutlinedTextField(
                    value = displayedMinSize,
                    onValueChange = { input ->
                        if ((input.toFloatOrNull() != null && getMinSizeFromDisplayedString(input) < 10f.pow(11)) || input == "")
                            displayedMinSize = input
                    },
                    leadingIcon = {
                        Icon(
                            painterResource(R.drawable.hard_drives),
                            null,
                            Modifier.size(dimensionResource(R.dimen.small_icon))
                        )
                    },
                    trailingIcon = {
                        var showSizeMenu by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.medium)
                                .clickable { showSizeMenu = true }
                                .padding(dimensionResource(R.dimen.padding_xsmall))
                        ) {
                            Text(
                                text = currentSizeMultiplier.toString(),
                                modifier = Modifier.padding(
                                    start = dimensionResource(R.dimen.padding_xsmall),
                                    top = dimensionResource(R.dimen.padding_xsmall),
                                    end = 0.dp,
                                    bottom = dimensionResource(R.dimen.padding_xsmall)
                                )
                            )
                            AnimatedExpandCollapseIcon(
                                expanded = showSizeMenu,
                                contentDescription = "${if (showSizeMenu) "Hide" else "Show"} other sizes",
                                modifier = Modifier.padding(
                                    start = 0.dp,
                                    top = dimensionResource(R.dimen.padding_xsmall),
                                    end = dimensionResource(R.dimen.padding_xsmall),
                                    bottom = dimensionResource(R.dimen.padding_xsmall)
                                )
                            )
                        }
                        DropdownMenu(
                            expanded = showSizeMenu,
                            onDismissRequest = { showSizeMenu = false },
                            border = FilterChipDefaults.filterChipBorder(enabled = true, selected = false),
                            shape = FilterChipDefaults.shape,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            for (size in FileSize.entries) {
                                DropdownMenuItem(
                                    text = { Text(size.toString()) },
                                    onClick = {
                                        val previousSize = currentSizeMultiplier
                                        currentSizeMultiplier = size
                                        displayedMinSize = ((displayedMinSize.toFloatOrNull()
                                            ?: 0f) * (previousSize.multiplier / currentSizeMultiplier.multiplier))
                                            .toBigDecimal().setScale(6, RoundingMode.HALF_UP).stripTrailingZeros()
                                            .toPlainString()
                                        showSizeMenu = false
                                    },
                                    enabled = currentSizeMultiplier != size
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Number
                    ),
                    modifier = Modifier
                        .padding(bottom = dimensionResource(R.dimen.padding_medium))
                )
                // Contains text:
                Text("Text search:", style = titleStyle)
                OutlinedTextField(
                    value = newFilters.containsText,
                    onValueChange = { filterDialogViewModel.updateContainsText(it) },
                    leadingIcon = {
                        Icon(
                            painterResource(R.drawable.magnifying_glass),
                            null,
                            Modifier.size(dimensionResource(R.dimen.small_icon))
                        )
                    },
                    modifier = Modifier
                        .padding(bottom = dimensionResource(R.dimen.padding_large))
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { setFilterAsDefault = !setFilterAsDefault }
                ) {
                    Checkbox(
                        setFilterAsDefault,
                        onCheckedChange = { setFilterAsDefault = it },
                    )
                    Text(
                        stringResource(R.string.use_filter_by_default),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(end = dimensionResource(R.dimen.padding_small))
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    OutlinedButton(
                        onClick = { onDismiss() },
                        modifier = Modifier.padding(dimensionResource(R.dimen.padding_small)),
                    ) {
                        Text(
                            stringResource(R.string.cancel),
                        )
                    }
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            onConfirm(
                                newFilters.copy(
                                    sizeRange =
                                        getMinSizeFromDisplayedString().toLong()..newFilters.sizeRange.last
                                ),
                                setFilterAsDefault
                            )
                        },
                        modifier = Modifier.padding(dimensionResource(R.dimen.padding_small)),
                    ) {
                        Text(
                            stringResource(R.string.confirm),
                        )
                    }
                }
            }
        }
    }
}