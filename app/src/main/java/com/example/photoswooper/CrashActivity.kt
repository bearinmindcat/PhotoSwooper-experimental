/*
 * SPDX-FileCopyrightText: 2025 Loowiz <loowiz@envs.net>
 *  
 *  SPDX-License-Identifier: AGPL-3.0-only
 */

package com.example.photoswooper

import android.content.ClipData
import android.content.Intent
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardColors
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri
import com.example.photoswooper.data.BooleanPreference
import com.example.photoswooper.data.StringPreference
import com.example.photoswooper.ui.components.tiny.AnimatedExpandCollapseIcon
import com.example.photoswooper.ui.theme.PhotoSwooperTheme
import com.example.photoswooper.ui.view.FooterItem
import com.example.photoswooper.utils.DataStoreInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

/** Activity invoked on uncaught exemption */
class CrashActivity : AppCompatActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dataStoreInterface = DataStoreInterface(this.dataStore)

        setContent {
            val crashLog by dataStoreInterface.getStringSettingValue(StringPreference.CRASH_LOG.setting)
                .collectAsState("")

            val systemFont by dataStoreInterface.getBooleanSettingValue(BooleanPreference.SYSTEM_FONT.setting)
                .collectAsState(!BooleanPreference.SYSTEM_FONT.default)
            val dynamicTheme by dataStoreInterface.getBooleanSettingValue(BooleanPreference.DYNAMIC_THEME.setting)
                .collectAsState(BooleanPreference.DYNAMIC_THEME.default)

            PhotoSwooperTheme(
                systemFont = systemFont,
                dynamicColor = dynamicTheme
            ) {
                ModalBottomSheet(onDismissRequest = {
                    // Restart app on dismiss
                    this.startActivity(Intent(this, MainActivity::class.java))
                    exitProcess(0)
                }) {
                    CrashScreen(crashLog)
                }
            }
        }
    }
}

@Composable
fun CrashScreen(log: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current
    val clipboard = LocalClipboard.current

    // Log text with the first line emboldened
    val formattedLog = buildAnnotatedString {
        val lines = log.lines()
        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
            appendLine(lines.first())
        }
        lines.drop(1).forEach { line ->
            appendLine(line)
        }
    }

    var crashLogExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .padding(horizontal = dimensionResource(R.dimen.padding_medium))
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            stringResource(R.string.photoswooper_has_crashed),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(dimensionResource(R.dimen.padding_small))
        )
        // Container for crash log and actions
        ElevatedCard(
            colors = CardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier
                .clickable {
                    crashLogExpanded = !crashLogExpanded
                }
        ) {
            // Crash log actions
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimensionResource(R.dimen.padding_xsmall))
            ) {
                Row {
                    AnimatedExpandCollapseIcon(
                        expanded = crashLogExpanded,
                        contentDescription = stringResource(R.string.crash_log),
                        modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.padding_xsmall))
                    )
                    Text(
                        stringResource(R.string.crash_log),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Row {
                    // Copy to clipboard button
                    IconButton(
                        onClick = {
                            CoroutineScope(Dispatchers.Main).launch {
                                clipboard.setClipEntry(
                                    ClipEntry(
                                        ClipData(
                                            ClipData.newPlainText(context.getString(R.string.photoswooper_crash_log), log)
                                        )
                                    )
                                )
                            }
                        }
                    ) {
                        Icon(
                            painterResource(R.drawable.copy),
                            contentDescription = stringResource(R.string.copy_crash_log_contents),
                            modifier = Modifier.size(dimensionResource(R.dimen.small_icon))
                        )
                    }
                    // Share button
                    IconButton(
                        onClick = {
                            val shareIntent: Intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, log)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(shareIntent,
                                context.getString(R.string.share_crash_log)))
                        }
                    ) {
                        Icon(
                            painterResource(R.drawable.share_network),
                            contentDescription = stringResource(R.string.share_crash_log),
                            modifier = Modifier.size(dimensionResource(R.dimen.small_icon))
                        )
                    }
                }
            }
            // Full crash log
            AnimatedVisibility(crashLogExpanded) {
                OutlinedCard(
                    Modifier
                        .padding(
                            start = dimensionResource(R.dimen.padding_small),
                            end = dimensionResource(R.dimen.padding_small),
                            bottom = dimensionResource(R.dimen.padding_small),
                        )
                        // Prevent clicks on log text being registered as clicks intended to collapse the log
                        .combinedClickable(
                            interactionSource = MutableInteractionSource(),
                            indication = null,
                            onClick = {}
                        )
                ) {
                    SelectionContainer {
                        Text(
                            text = formattedLog,
                            softWrap = false,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .padding(dimensionResource(R.dimen.padding_xsmall))
                                .horizontalScroll(rememberScrollState(0))
                        )
                    }
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = dimensionResource(R.dimen.padding_medium)))
        // Details on how to report crash
        Text(
            stringResource(R.string.crash_reporting_options),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium
        )
        // Buttons to report crash via codeberg and via email
        Row(
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensionResource(R.dimen.padding_medium))
        ) {
            FooterItem(
                icon = painterResource(R.drawable.globe),
                title = stringResource(R.string.online_bug_report),
                onClick = {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            context.getString(R.string.create_issue_url).toUri()
                        )
                    )
                }
            )
            FooterItem(
                icon = painterResource(R.drawable.paper_plane_tilt),
                title = stringResource(R.string.email_bug_report),
                onClick = {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_SENDTO,
                        ).apply {
                            data = "mailto:loowiz@envs.net".toUri()
                            putExtra(Intent.EXTRA_EMAIL, "loowiz@envs.net")
                            putExtra(Intent.EXTRA_SUBJECT, "PhotoSwooper crash report")
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "**What were you doing just prior to the crash?**\n\n" +
                                        "[Insert answer]" +
                                        "\n\n--------\n\n" +
                                        "**Log**\n\n" +
                                        "$formattedLog"
                            )
                        }
                    )
                }
            )
        }
        Spacer(Modifier.size(dimensionResource(R.dimen.padding_small)))
        // Restart app button
        ElevatedButton(
            onClick = {
                if (SDK_INT >= Build.VERSION_CODES.R)
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                val intent = Intent(context, MainActivity::class.java)
                context.startActivity(intent)
                exitProcess(0)
            },
            modifier = Modifier.safeContentPadding()
        ) {
            Icon(painterResource(R.drawable.arrows_counter_clockwise), contentDescription = null)
            Spacer(Modifier.size(dimensionResource(R.dimen.padding_small)))
            Text(stringResource(R.string.restart_photoswooper))
        }
    }
}

@Preview
@Composable
fun CrashScreenPreview() {
    CrashScreen(
        log = "" +
                "java.lang.RuntimeException: Unable to start activity ComponentInfo{com.example.photoswooper.debug/com.example.photoswooper.MainActivity}: java.lang.IllegalStateException: uh oh\n" +
                "\tat android.app.ActivityThread.performLaunchActivity(ActivityThread.java:4298)\n" +
                "\tat android.app.ActivityThread.handleLaunchActivity(ActivityThread.java:4485)\n" +
                "\tat android.app.servertransaction.LaunchActivityItem.execute(LaunchActivityItem.java:222)\n" +
                "\tat android.app.servertransaction.TransactionExecutor.executeNonLifecycleItem(TransactionExecutor.java:133)\n" +
                "\tat android.app.servertransaction.TransactionExecutor.executeTransactionItems(TransactionExecutor.java:103)\n" +
                "\tat android.app.servertransaction.TransactionExecutor.execute(TransactionExecutor.java:80)\n" +
                "\tat android.app.ActivityThread\$H.handleMessage(ActivityThread.java:2832)\n" +
                "\tat android.os.Handler.dispatchMessage(Handler.java:110)\n" +
                "\tat android.os.Looper.loopOnce(Looper.java:248)\n" +
                "\tat android.os.Looper.loop(Looper.java:338)\n" +
                "\tat android.app.ActivityThread.main(ActivityThread.java:9111)\n" +
                "\tat java.lang.reflect.Method.invoke(Native Method)\n" +
                "\tat com.android.internal.os.RuntimeInit\$MethodAndArgsCaller.run(RuntimeInit.java:593)\n" +
                "\tat com.android.internal.os.ZygoteInit.main(ZygoteInit.java:948)\n" +
                "Caused by: java.lang.IllegalStateException: uh oh\n" +
                "\tat com.example.photoswooper.MainActivity.onCreate(MainActivity.kt:118)\n" +
                "\tat android.app.Activity.performCreate(Activity.java:9208)\n" +
                "\tat android.app.Activity.performCreate(Activity.java:9186)\n" +
                "\tat android.app.Instrumentation.callActivityOnCreate(Instrumentation.java:1531)\n" +
                "\tat android.app.ActivityThread.performLaunchActivity(ActivityThread.java:4280)\n" +
                "\t... 13 more\n"
    )
}