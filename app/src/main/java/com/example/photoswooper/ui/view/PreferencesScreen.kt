/*
 * SPDX-FileCopyrightText: 2025 Loowiz <loowiz@envs.net>
 *
 *  SPDX-License-Identifier: AGPL-3.0-only
 */

package com.example.photoswooper.ui.view

import android.content.Intent
import android.icu.util.Calendar
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import com.example.photoswooper.MainActivity
import com.example.photoswooper.R
import com.example.photoswooper.data.uistates.BooleanPreference
import com.example.photoswooper.data.uistates.IntPreference
import com.example.photoswooper.data.uistates.LongPreference
import com.example.photoswooper.dataStore
import com.example.photoswooper.ui.components.DropdownFilterChip
import com.example.photoswooper.utils.DataStoreInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.system.exitProcess


enum class PreferencesCategory(@param:StringRes val titleStringId: Int, @param:StringRes val descriptionStringId: Int, @param:DrawableRes val iconDrawableId: Int) {
    BEHAVIOUR(R.string.prefs_behaviour_title, R.string.prefs_behaviour_desc, R.drawable.navigation_arrow),
    APPEARANCE(R.string.prefs_appearance_title, R.string.prefs_appearance_desc, R.drawable.paint_brush_broad),
    STATISTICS(R.string.statistics, R.string.prefs_statistics_desc, R.drawable.chart),
//    BACKUP_RESTORE(R.string.prefs_backup_restore_title, R.string.prefs_backup_restore_desc, R.drawable.clock_counter_clockwise),
    // TODO("Backup & restore")
}

// TODO("Add titles to each category page")

@Composable
fun PreferencesScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val dataStoreInterface = DataStoreInterface(context.dataStore)

    val statisticsEnabled by DataStoreInterface(context.dataStore)
        .getBooleanSettingValue(BooleanPreference.STATISTICS_ENABLED.setting).collectAsState(true)
    val reduceAnimations by DataStoreInterface(context.dataStore)
        .getBooleanSettingValue(BooleanPreference.REDUCE_ANIMATIONS.setting).collectAsState(false)

    var currentCategory by rememberSaveable { mutableStateOf<PreferencesCategory?>(null) }
    BackHandler(enabled = currentCategory != null) { currentCategory = null }
    @Composable
    fun BackButtonListItem() {
        ListItem(
            headlineContent = { Text("Back") },
            leadingContent = { Icon(painterResource(R.drawable.arrow_left), null) },
            modifier = Modifier.clickable {
                currentCategory = null
                if (SDK_INT >= 30)
                   view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
            }
        )
    }

    var showRestartRequiredDialog by remember { mutableStateOf(false) }
    if (showRestartRequiredDialog)
        RestartRequiredDialog { showRestartRequiredDialog = false }
    var showConfirmRestartTutorialDialog by remember { mutableStateOf(false) }
    if (showConfirmRestartTutorialDialog)
        ConfirmRestartTutorialDialog(
            onDismissRequest = { showConfirmRestartTutorialDialog = false },
            onConfirm = {
                CoroutineScope(Dispatchers.IO).launch {
                    dataStoreInterface.setIntSettingValue(
                        newValue = 1,
                        setting = IntPreference.TUTORIAL_INDEX.setting
                    )
                    dataStoreInterface.setLongSettingValue(
                        newValue = Calendar.getInstance().timeInMillis,
                        setting = LongPreference.TUTORIAL_START_TIME.setting
                    )
                    val intent: Intent = Intent(context, MainActivity::class.java)
                    context.startActivity(intent)
                    exitProcess(0)
                }
            }
        )
    var showDonationDialog by remember { mutableStateOf(false) }
    if (showDonationDialog)
        DonationDialog(
            onDismissRequest = { showDonationDialog = false },
        )


    AnimatedContent(
        currentCategory,
        transitionSpec = {
            if (reduceAnimations) fadeIn() togetherWith fadeOut()
            else if (currentCategory != null)
                slideInHorizontally(
                    spring(
                        Spring.DampingRatioNoBouncy,
                        Spring.StiffnessMedium
                    ),
                    { it }
                ).togetherWith(
                    slideOutHorizontally(
                        spring(
                            Spring.DampingRatioNoBouncy,
                            Spring.StiffnessMedium
                        ),
                        targetOffsetX = {-it}
                    ) + fadeOut()
                )
            else
                (slideInHorizontally(
                    spring(
                        Spring.DampingRatioNoBouncy,
                        Spring.StiffnessMedium
                    ),
                    initialOffsetX = {-it/2}
                ) + fadeIn()).togetherWith(
                    slideOutHorizontally(
                        spring(
                            Spring.DampingRatioNoBouncy,
                            Spring.StiffnessMedium
                        ),
                        { it }
                    )
                )
        },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier
                .verticalScroll(rememberScrollState())
                .fillMaxSize()
        ) {
            when (it) {
                PreferencesCategory.APPEARANCE -> {
                    BackButtonListItem()
                    /* System font preference */
                    BooleanPreferenceEditor(
                        dataStoreInterface = dataStoreInterface,
                        preference = BooleanPreference.SYSTEM_FONT,
                    )
                    /* Dynamic theme preference */
                    if (SDK_INT >= Build.VERSION_CODES.S)
                        BooleanPreferenceEditor(
                            dataStoreInterface = dataStoreInterface,
                            preference = BooleanPreference.DYNAMIC_THEME,
                        )
                    /* Reduce animations preference */
                    BooleanPreferenceEditor(
                        dataStoreInterface = dataStoreInterface,
                        preference = BooleanPreference.REDUCE_ANIMATIONS,
                    )
                }

                PreferencesCategory.BEHAVIOUR -> {
                    BackButtonListItem()
                    /* Media items per stack preference */
                    IntPreferenceEditorSlider(
                        dataStoreInterface = dataStoreInterface,
                        preference = IntPreference.NUM_PHOTOS_PER_STACK,
                        acceptedValueRange = 1..100,
                    )
                    // Forget swipes after x days preference
                    IntPreferenceEditorDropdownOptions(
                        dataStoreInterface = dataStoreInterface,
                        preference = IntPreference.NO_DAYS_TO_REMEMBER_SWIPES,
                        preferenceUnits = "Days",
                        readOnlyReason = if (statisticsEnabled) null else stringResource(R.string.statistics_required),
                        acceptedValueRange = 1..365,
                        choiceTitles = arrayOf("Forever", "1 Year", "1 Month", "1 Week"),
                        choiceValues = intArrayOf(0, 365, 28, 7)
                    )
                    /* Permanently delete preference */
                    if (SDK_INT >= Build.VERSION_CODES.R)
                        BooleanPreferenceEditor(
                            dataStoreInterface = dataStoreInterface,
                            preference = BooleanPreference.PERMANENTLY_DELETE,
                        )
                    /* Skip review screen */
                    BooleanPreferenceEditor(
                        dataStoreInterface = dataStoreInterface,
                        preference = BooleanPreference.SKIP_REVIEW,
                    )
                    /* Pause background media when video starts playing */
                    BooleanPreferenceEditor(
                        dataStoreInterface = dataStoreInterface,
                        preference = BooleanPreference.PAUSE_BACKGROUND_MEDIA,
                        onUpdate = { showRestartRequiredDialog = true }
                    )
                    /* Loop videos */
                    BooleanPreferenceEditor(
                        dataStoreInterface = dataStoreInterface,
                        preference = BooleanPreference.LOOP_VIDEOS,
                        onUpdate = { showRestartRequiredDialog = true }
                    )
                }

                PreferencesCategory.STATISTICS -> {
                    BackButtonListItem()
                    // Statistics Enabled
                    BooleanPreferenceEditor(
                        dataStoreInterface = dataStoreInterface,
                        preference = BooleanPreference.STATISTICS_ENABLED,
                    )
                    // Start week on monday
                    BooleanPreferenceEditor(
                        dataStoreInterface = dataStoreInterface,
                        preference = BooleanPreference.START_WEEK_ON_MONDAY,
                        readOnlyReason = if (statisticsEnabled) null else stringResource(R.string.statistics_required)
                    )
                    // Clear statistics (always enabled)
                }
//                PreferencesCategory.BACKUP_RESTORE -> {
//                    BackButtonListItem()
//
//                }
                null -> {
                    Column(
                        verticalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column {
                            for (category in PreferencesCategory.entries) {

                                ListItem(
                                    headlineContent = {
                                        Text(stringResource(category.titleStringId))
                                    },
                                    supportingContent = { Text(stringResource(category.descriptionStringId)) },
                                    leadingContent = {
                                        Icon(
                                            painterResource(category.iconDrawableId),
                                            null
                                        )
                                    },
                                    trailingContent = {
                                        Icon(
                                            painterResource(R.drawable.caret_right),
                                            contentDescription = null
                                        )
                                    },
                                    modifier = Modifier.clickable {
                                        currentCategory = category
                                        if (SDK_INT >= Build.VERSION_CODES.R)
                                            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
                                    }
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(dimensionResource(R.dimen.padding_medium))
                        ) {
                            FooterItem(
                                icon = painterResource(R.drawable.rewind),
                                title = stringResource(R.string.restart_tutorial),
                                onClick = {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        showConfirmRestartTutorialDialog = true
                                        if (SDK_INT >= Build.VERSION_CODES.R)
                                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    }
                                }
                            )
                            FooterItem(
                                icon = painterResource(R.drawable.heart),
                                title = stringResource(R.string.donate),
                                onClick = {
                                    showDonationDialog = true
                                }
                            )
                            FooterItem(
                                icon = painterResource(R.drawable.bug),
                                title = stringResource(R.string.report_bug),
                                onClick = {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            "https://codeberg.org/Loowiz/PhotoSwooper/issues/new".toUri()
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DonationDialog(
    onDismissRequest: () -> Unit
) {
    val view = LocalView.current
    val context = LocalContext.current

    fun onDonate() {
        Toast.makeText(context, R.string.thanks_for_donating, Toast.LENGTH_SHORT).show()
    }

    Dialog(onDismissRequest = { onDismissRequest() }) {
        Card(
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensionResource(R.dimen.padding_medium)),
        ) {
            Row(
                Modifier
                    .padding(
                        top = dimensionResource(R.dimen.padding_medium),
                        bottom = dimensionResource(R.dimen.padding_small),
                    )
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.heart),
                    contentDescription = null,
                    modifier = Modifier
                        .size(dimensionResource(R.dimen.small_icon))
                )
                Text(
                    stringResource(R.string.donate_to_loowiz),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(
                        start = dimensionResource(R.dimen.padding_small),
                    ),
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                stringResource(R.string.donate_to_loowiz_desc),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(
                    top = dimensionResource(R.dimen.padding_small),
                    bottom = dimensionResource(R.dimen.padding_small),
                    start = dimensionResource(R.dimen.padding_medium),
                    end = dimensionResource(R.dimen.padding_medium)
                ),
                textAlign = TextAlign.Center
            )
            Row(horizontalArrangement = Arrangement.SpaceAround, modifier = Modifier.fillMaxWidth()) {
                FooterItem(
                    icon = painterResource(R.drawable.kofi),
                    title = stringResource(id = R.string.kofi),
                    colouredIcon = true
                ) {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            "https://ko-fi.com/loowiz".toUri()
                        )
                    )
                    onDonate()
                }
                FooterItem(
                    icon = painterResource(R.drawable.liberapay_logo_black_on_yellow),
                    title = stringResource(R.string.liberapay),
                    colouredIcon = true
                ) {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            "https://liberapay.com/loowiz".toUri()
                        )
                    )
                    onDonate()
                }
            }
            OutlinedButton(
                onClick = {
                    if (SDK_INT >= Build.VERSION_CODES.R)
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    onDismissRequest()
                },
                modifier = Modifier
                    .padding(dimensionResource(R.dimen.padding_small))
                    .align(Alignment.CenterHorizontally),
            ) {
                Icon(
                    painter = painterResource(R.drawable.x),
                    contentDescription = null,
                    modifier = Modifier
                        .size(dimensionResource(R.dimen.xsmall_icon))
                )
                Text(stringResource(R.string.dismiss), Modifier.padding(start = dimensionResource(R.dimen.padding_small)),)
            }
        }
    }
}

@Composable
fun ConfirmRestartTutorialDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit
) {
    val view = LocalView.current
    val context = LocalContext.current

    Dialog(onDismissRequest = { onDismissRequest() }) {
        Card(
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensionResource(R.dimen.padding_medium)),
        ) {
            Text(
                stringResource(R.string.confirm_restart_tutorial),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(
                    top = dimensionResource(R.dimen.padding_medium),
                    bottom = dimensionResource(R.dimen.padding_small),
                    start = dimensionResource(R.dimen.padding_medium),
                    end = dimensionResource(R.dimen.padding_medium)
                ),
                textAlign = TextAlign.Center
            )
            Text(
                stringResource(R.string.this_will_discard_items_marked_as_deleted),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(
                    top = dimensionResource(R.dimen.padding_small),
                    bottom = dimensionResource(R.dimen.padding_small),
                    start = dimensionResource(R.dimen.padding_medium),
                    end = dimensionResource(R.dimen.padding_medium)
                ),
                textAlign = TextAlign.Center
            )
            FlowRow (
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimensionResource(R.dimen.padding_medium)),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                OutlinedButton(
                    onClick = {
                        if (SDK_INT >= Build.VERSION_CODES.R)
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        onDismissRequest()
                    },
                    modifier = Modifier.padding(dimensionResource(R.dimen.padding_small)),
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = {
                        if (SDK_INT >= Build.VERSION_CODES.R)
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        onConfirm()
                    },
                    modifier = Modifier.padding(dimensionResource(R.dimen.padding_small)),
                ) {
                    Text(stringResource(R.string.confirm))
                }
            }
        }
    }
}

@Composable
private fun RestartRequiredDialog(
    onDismissRequest: () -> Unit
) {
    val view = LocalView.current
    val context = LocalContext.current

    Dialog(
        onDismissRequest = {}
    ) {
        Card(
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensionResource(R.dimen.padding_medium)),
        ) {
            Text(
                stringResource(R.string.restart_required_for_preference),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(
                    top = dimensionResource(R.dimen.padding_medium),
                    bottom = dimensionResource(R.dimen.padding_small),
                    start = dimensionResource(R.dimen.padding_medium),
                    end = dimensionResource(R.dimen.padding_medium)
                ),
                textAlign = TextAlign.Center
            )
            Text(
                stringResource(R.string.this_will_discard_items_marked_as_deleted),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(
                    top = dimensionResource(R.dimen.padding_small),
                    bottom = dimensionResource(R.dimen.padding_small),
                    start = dimensionResource(R.dimen.padding_medium),
                    end = dimensionResource(R.dimen.padding_medium)
                ),
                textAlign = TextAlign.Center
            )

            FlowRow (
                modifier = Modifier
                    .fillMaxWidth(),
//                    .padding(horizontal = dimensionResource(R.dimen.padding_medium)),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                OutlinedButton(
                    onClick = {
                        if (SDK_INT >= Build.VERSION_CODES.R)
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        onDismissRequest()
                    },
                    modifier = Modifier.padding(dimensionResource(R.dimen.padding_small)),
                ) {
                    Text(stringResource(R.string.restart_later))
                }
                Button(
                    onClick = {
                        if (SDK_INT >= Build.VERSION_CODES.R)
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        val intent: Intent = Intent(context, MainActivity::class.java)
                        context.startActivity(intent)
                        exitProcess(0)
                    },
                    modifier = Modifier.padding(dimensionResource(R.dimen.padding_small)),
                ) {
                    Text(stringResource(R.string.restart_now))
                }
            }
        }
    }
}

/** Composable containing a clickable icon & text, used as footer links in [PreferencesScreen] */
@Composable
private fun FooterItem(
    icon: Painter,
    title: String,
    colouredIcon: Boolean = false,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.large)
            .clickable { onClick() }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(dimensionResource(R.dimen.padding_small))
        ) {
            Icon(
                painter = icon,
                tint = if (colouredIcon) Color.Unspecified else LocalContentColor.current,
                contentDescription = null,
                modifier = Modifier.size(dimensionResource(R.dimen.small_icon))
            )
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/** Composable function containing a list item that displays info about a boolean preference, and a switch to toggle it
 *
 * @param readOnlyReason Reason to display to the user if the setting is read-only. Value is null when not read-only
 * */
@Composable
fun BooleanPreferenceEditor(
    dataStoreInterface: DataStoreInterface,
    preference: BooleanPreference,
    onUpdate: (newValue: Boolean) -> Unit = {},
    readOnlyReason: String? = null
) {
    val view = LocalView.current
    val context = LocalContext.current

    val preferenceValue by dataStoreInterface.getBooleanSettingValue(preference.setting).collectAsState(false)

    fun togglePreference() = CoroutineScope(Dispatchers.IO).launch {
        onUpdate(!preferenceValue)
        dataStoreInterface.setBooleanSettingValue(
            setting = preference.setting,
            newValue = !preferenceValue
        )
    }

    fun performSwitchHapticFeedback(toggledOn: Boolean) {
        if (SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (toggledOn)
                view.performHapticFeedback(HapticFeedbackConstants.TOGGLE_ON)
            else
                view.performHapticFeedback(HapticFeedbackConstants.TOGGLE_OFF)
        } else if (SDK_INT >= Build.VERSION_CODES.R)
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    // UI
    ListItem(
        leadingContent = {
            Icon(
                painter = painterResource(preference.icon),
                contentDescription = null // Described in adjacent text
            )
        },
        headlineContent = {
            Text(
                stringResource(preference.title)
            )
        },
        trailingContent = {
            Switch(
                checked = preferenceValue,
                enabled = readOnlyReason == null,
                onCheckedChange = {
                    togglePreference()
                    performSwitchHapticFeedback(it)
                }
            )
        },
        supportingContent = {
            if (preference.description != null)
                Text(
                    stringResource(preference.description)
                )
        },
        modifier = Modifier.clickable { // Allows user to click on the whole row to toggle
            if (readOnlyReason == null) {
                togglePreference()
                performSwitchHapticFeedback(preferenceValue)
            } else {
                if (SDK_INT >= Build.VERSION_CODES.R)
                    view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                Toast.makeText(
                    context,
                    readOnlyReason,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    )

}

/** Composable function containing a list item that displays info about a integer preference, with a text box & slider to adjust
 *
 * @param readOnlyReason Reason to display to the user if the setting is read-only. Value is null when not read-only
 * */
@Composable
fun IntPreferenceEditorSlider(
    dataStoreInterface: DataStoreInterface,
    preference: IntPreference,
    acceptedValueRange: IntRange,
    readOnlyReason: String? = null
) {
    val context = LocalContext.current
    val view = LocalView.current

    val readOnly = readOnlyReason != null

    val preferenceValue by dataStoreInterface.getIntSettingValue(preference.setting).collectAsState(0)
    var displayedPreferenceValue by remember { mutableStateOf(preferenceValue.toString()) }
    // Update displayed values when the preference's value changes (value on start-up is not correct so needs to be updated)
    LaunchedEffect(preferenceValue) {
        displayedPreferenceValue = preferenceValue.toString()
    }

    fun onUpdatePreference(newValue: Int) = CoroutineScope(Dispatchers.IO).launch {
        dataStoreInterface.setIntSettingValue(
            setting = preference.setting,
            newValue = newValue
        )
    }

    fun validateInputAndUpdate(input: String) {
        val inputAsInt = input.toIntOrNull()
        when {
            (inputAsInt != null) -> {
                displayedPreferenceValue = input
                if (inputAsInt in acceptedValueRange)
                    onUpdatePreference(inputAsInt)
                else Toast.makeText(
                    context,
                    "Value must be within ${acceptedValueRange.first}-${acceptedValueRange.endInclusive}",
                    Toast.LENGTH_SHORT
                ).show()
            }

            (input == "") -> displayedPreferenceValue = input
        }
    }

    fun displayReadOnlyReasoning() {
        if (SDK_INT >= Build.VERSION_CODES.R)
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        Toast.makeText(context, readOnlyReason, Toast.LENGTH_SHORT).show()
    }

    ListItem(
        leadingContent = {
            Icon(
                painter = painterResource(preference.icon),
                contentDescription = null, // Described in adjacent text
                modifier = Modifier.padding(end = dimensionResource(R.dimen.padding_medium))
            )
        },
        headlineContent = {
            Text(
                text = stringResource(preference.title),
                modifier = Modifier.padding(bottom = dimensionResource(R.dimen.padding_xsmall))
            )
        },
        supportingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = displayedPreferenceValue,
                    isError = (displayedPreferenceValue.toIntOrNull() ?: -1) !in acceptedValueRange,
                    onValueChange = { input -> // Update UI value & dataStore only if valid
                        validateInputAndUpdate(input)
                    },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Number
                    ),
                    enabled = !readOnly,
                    modifier = Modifier
                        .weight(0.15f)
                )
                Slider(
                    value = displayedPreferenceValue.toFloatOrNull()
                        ?: IntPreference.NUM_PHOTOS_PER_STACK.default.toFloat(),
                    onValueChange = {
                        if (it.roundToInt() != displayedPreferenceValue.toIntOrNull()) {
                            displayedPreferenceValue = it.roundToInt().toString()
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                    },
                    valueRange = 10f..100f,
                    onValueChangeFinished = {
                        onUpdatePreference(displayedPreferenceValue.toInt())
                    },
                    steps = 8,
                    enabled = !readOnly,
                    modifier = Modifier
                        .weight(0.5f)
                        .padding(horizontal = dimensionResource(R.dimen.padding_small))
                )
            }
        },
        modifier = Modifier.clickable(
            enabled = readOnly,
        ) {
            displayReadOnlyReasoning()
        }
    )
}

/** Composable function containing a list item that displays info about a integer preference, with a dropdown menu
 * containing choices for this setting, along with a custom choice which creates a text box underneath the setting
 *
 * @param readOnlyReason Reason to display to the user if the setting is read-only. Value is null when not read-only
 * */
@Composable
fun IntPreferenceEditorDropdownOptions(
    dataStoreInterface: DataStoreInterface,
    preference: IntPreference,
    preferenceUnits: String,
    choiceTitles: Array<String>,
    choiceValues: IntArray,
    acceptedValueRange: IntRange,
    readOnlyReason: String? = null
) {
    val context = LocalContext.current
    val view = LocalView.current

    val preferenceValue by dataStoreInterface.getIntSettingValue(preference.setting).collectAsState(0)
    var selectedChoice by remember { mutableStateOf(
        if (preferenceValue in choiceValues)
            choiceTitles[choiceValues.indexOf(preferenceValue)]
        else
            "Custom"
    ) }
    var customValue by remember { mutableStateOf(preferenceValue.toString()) }

    // Update displayed values when the preference's value changes (value on start-up is not correct so needs to be updated)
    LaunchedEffect(preferenceValue) {
        customValue = preferenceValue.toString()
        selectedChoice =
            if (preferenceValue in choiceValues)
                choiceTitles[choiceValues.indexOf(preferenceValue)]
            else
                "Custom"
    }

    fun updatePreference(newValue: Int) = CoroutineScope(Dispatchers.IO).launch {
        dataStoreInterface.setIntSettingValue(
            setting = preference.setting,
            newValue = newValue
        )
    }

    fun validateInputAndUpdate(input: String) {
        val inputAsInt = input.toIntOrNull()
        when {
            (inputAsInt != null) -> {
                customValue = input
                if (inputAsInt in acceptedValueRange)
                    updatePreference(inputAsInt)
                else {
                    if (SDK_INT >= Build.VERSION_CODES.R)
                        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                    Toast.makeText(context, "Value must be within $acceptedValueRange", Toast.LENGTH_SHORT).show()
                }
            }

            (input == "") -> customValue = input
        }
    }

    Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
        ListItem(
            leadingContent = { Icon(painterResource(preference.icon), contentDescription = null) },
            headlineContent = { Text(stringResource(preference.title)) },
            trailingContent = {
                DropdownFilterChip(
                    selectedMenuItem = selectedChoice,
                    menuItemsDescription = stringResource(R.string.options_for_current_preference),
                    menuItems = (choiceTitles + "Custom"),
                    onSelectionChange = {
                        selectedChoice = it
                        updatePreference(
                            if (it == "Custom") customValue.toInt()
                            else choiceValues[choiceTitles.indexOf(it)]
                        )
                        if (SDK_INT >= Build.VERSION_CODES.R)
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    },
                )
            },
            modifier = Modifier.clickable(
                enabled = readOnlyReason != null,
            ) {
                Toast.makeText(context, readOnlyReason, Toast.LENGTH_SHORT).show()
            }
        )
        AnimatedVisibility(selectedChoice == "Custom", modifier = Modifier.padding(start = 56.dp)) {
            OutlinedTextField(
                value = customValue,
                isError = (customValue.toIntOrNull() ?: -1) !in acceptedValueRange,
                onValueChange = { input -> // Update UI value & dataStore only if valid
                    validateInputAndUpdate(input)
                },
//                label = { Text(stringResource(R.string.value_for_above_preference)) },
                suffix = { Text(preferenceUnits) },
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Number
                ),
                modifier = Modifier
            )
        }
    }}
