/*
 * SPDX-FileCopyrightText: 2025 Loowiz <loowiz@envs.net>
 *
 *  SPDX-License-Identifier: AGPL-3.0-only
 */

package com.example.photoswooper.ui.view

import android.icu.util.Calendar
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.util.Log
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.photoswooper.R
import com.example.photoswooper.data.MAX_TUTORIAL_INDEX
import com.example.photoswooper.data.uistates.BooleanPreference
import com.example.photoswooper.data.uistates.IntPreference
import com.example.photoswooper.data.uistates.LongPreference
import com.example.photoswooper.utils.DataStoreInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


@Composable
fun Onboardingcreen(dataStoreInterface: DataStoreInterface) {
    val context = LocalContext.current
    val view = LocalView.current

    val drawable = context.packageManager.getApplicationIcon(context.packageName)

    Scaffold { paddingValues ->
        Log.d("UI", "Loading Onboarding")
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .widthIn(max = 512.dp)
                    .fillMaxSize()
            ) {
                // Container for the welcome text & common settings
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(dimensionResource(R.dimen.padding_medium))
                ) {
                    // Container for welcome text
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(dimensionResource(R.dimen.padding_medium))
                    ) {
                        Image(
                            drawable.toBitmap().asImageBitmap(),
                            contentDescription = "Image",
                            modifier = Modifier
                                .padding(dimensionResource(R.dimen.padding_medium))
                        )
                        Text(
                            stringResource(R.string.onboarding_title),
                            style = MaterialTheme.typography.headlineLarge,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            stringResource(R.string.onboarding_app_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(Modifier.height(dimensionResource(R.dimen.padding_large)))
                    // Common settings
                    CommonSettings(dataStoreInterface)
                }
                // Skip tutorial & Start tutorial buttons
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(dimensionResource(R.dimen.padding_medium))
                ) {
                    TextButton(
                        onClick = {
                            if (SDK_INT >= Build.VERSION_CODES.R)
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            CoroutineScope(Dispatchers.IO).launch {
                                dataStoreInterface.setIntSettingValue(
                                    newValue = MAX_TUTORIAL_INDEX + 1,
                                    setting = IntPreference.TUTORIAL_INDEX.setting
                                )
                            }
                        },
                    ) {
                        Text("Skip tutorial")
                    }
                    Button(
                        onClick = {
                            if (SDK_INT >= Build.VERSION_CODES.R)
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            CoroutineScope(Dispatchers.IO).launch {
                                dataStoreInterface.setIntSettingValue(
                                    newValue = 1,
                                    setting = IntPreference.TUTORIAL_INDEX.setting
                                )
                            }
                            CoroutineScope(Dispatchers.IO).launch {
                                dataStoreInterface.setLongSettingValue(
                                    newValue = Calendar.getInstance().timeInMillis,
                                    setting = LongPreference.TUTORIAL_START_TIME.setting
                                )
                            }
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.start_tutorial),
                            modifier = Modifier.padding(end = dimensionResource(R.dimen.padding_xsmall))
                        )
                        Icon(
                            painterResource(R.drawable.caret_right),
                            null,
                            Modifier.size(dimensionResource(R.dimen.small_icon))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommonSettings(dataStoreInterface: DataStoreInterface, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = stringResource(R.string.common_settings),
            style = MaterialTheme.typography.titleLarge
        )
        BooleanPreferenceEditor(
            dataStoreInterface,
            preference = BooleanPreference.STATISTICS_ENABLED,
        )
        BooleanPreferenceEditor(
            dataStoreInterface = dataStoreInterface,
            preference = BooleanPreference.SYSTEM_FONT,
        )
        BooleanPreferenceEditor(
            dataStoreInterface = dataStoreInterface,
            preference = BooleanPreference.REDUCE_ANIMATIONS,
        )
        Text(
            text = stringResource(R.string.common_settings_desc),
            style = MaterialTheme.typography.labelMedium
        )
    }
}