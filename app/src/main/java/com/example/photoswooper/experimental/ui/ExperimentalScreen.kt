package com.example.photoswooper.experimental.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.photoswooper.R
import com.example.photoswooper.experimental.viewmodel.DocumentSwipeViewModel
import java.io.File

@Composable
fun ExperimentalScreen(
    viewModel: DocumentSwipeViewModel,
    onStartSwiping: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Check if MANAGE_EXTERNAL_STORAGE is granted
    var hasAllFilesAccess by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()
        )
    }

    // Folder picker — converts SAF tree URI to file:// path
    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.addScanFolder(it) }
    }

    // Permission request result — re-check after returning from settings
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasAllFilesAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()
    }

    MainExpContent(
        viewModel = viewModel,
        uiState = uiState,
        hasAllFilesAccess = hasAllFilesAccess,
        onAddFolder = { folderPickerLauncher.launch(null) },
        onFilesAccessClick = {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            permissionLauncher.launch(intent)
        },
        onRemoveFolder = { viewModel.removeScanFolder(it) },
        onStartSwiping = {
            viewModel.scanDocuments()
            viewModel.enterSwipeMode()
            onStartSwiping()
        },
        onStopSwiping = { viewModel.exitSwipeMode() },
        onAddExcludedExtension = { viewModel.addExcludedExtension(it) },
        onRemoveExcludedExtension = { viewModel.removeExcludedExtension(it) },
        onResetMatches = {
            viewModel.resetMatches()
            Toast.makeText(context, "File swipes have been reset", Toast.LENGTH_SHORT).show()
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun MainExpContent(
    viewModel: DocumentSwipeViewModel,
    uiState: com.example.photoswooper.experimental.viewmodel.DocumentSwipeUiState,
    hasAllFilesAccess: Boolean,
    onAddFolder: () -> Unit,
    onFilesAccessClick: () -> Unit,
    onRemoveFolder: (String) -> Unit,
    onStartSwiping: () -> Unit,
    onStopSwiping: () -> Unit,
    onAddExcludedExtension: (String) -> Unit,
    onRemoveExcludedExtension: (String) -> Unit,
    onResetMatches: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Access all files toggle
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ListItem(
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.ic_file_copy),
                        contentDescription = null
                    )
                },
                headlineContent = {
                    Text("Access all files")
                },
                supportingContent = {
                    Text(
                        if (hasAllFilesAccess) "MANAGE_EXTERNAL_STORAGE grants access to all system files"
                        else "Required to scan Downloads and restricted folders"
                    )
                },
                trailingContent = {
                    Switch(
                        checked = hasAllFilesAccess,
                        onCheckedChange = { onFilesAccessClick() }
                    )
                },
                modifier = Modifier.clickable { onFilesAccessClick() }
            )
        }

        // Quick add folders — collapsible dropdown
        run {
            var quickAddExpanded by rememberSaveable { mutableStateOf(false) }

            ListItem(
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.ic_create_new_folder),
                        contentDescription = null
                    )
                },
                headlineContent = {
                    Text("Quick add folders")
                },
                supportingContent = {
                    Text("Add / remove folders from internal storage")
                },
                trailingContent = {
                    Icon(
                        painter = painterResource(
                            if (quickAddExpanded) R.drawable.caret_down else R.drawable.caret_right
                        ),
                        contentDescription = null
                    )
                },
                modifier = Modifier.clickable { quickAddExpanded = !quickAddExpanded }
            )

            AnimatedVisibility(
                visible = quickAddExpanded,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) + fadeIn(),
                exit = shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) + fadeOut()
            ) {
                Column {
                    if (hasAllFilesAccess) {
                        val storageDirs = remember {
                            val root = Environment.getExternalStorageDirectory()
                            (root.listFiles() ?: emptyArray())
                                .filter { it.isDirectory && !it.name.startsWith(".") }
                                .sortedBy { it.name.lowercase() }
                        }

                        storageDirs.forEach { dir ->
                            val fileUri = Uri.fromFile(dir).toString()
                            val isChecked = fileUri in uiState.scanFolderUris

                            ListItem(
                                leadingContent = {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = {
                                            if (it) viewModel.addFileFolder(dir.absolutePath)
                                            else viewModel.removeScanFolder(fileUri)
                                        }
                                    )
                                },
                                headlineContent = {
                                    Text(dir.name)
                                },
                                modifier = Modifier.clickable {
                                    if (isChecked) viewModel.removeScanFolder(fileUri)
                                    else viewModel.addFileFolder(dir.absolutePath)
                                }
                            )
                        }
                    } else {
                        Text(
                            text = "Enable \"Access all files\" to browse folders here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        // Folders to swipe — collapsible with chips
        run {
            var foldersExpanded by rememberSaveable { mutableStateOf(false) }

            ListItem(
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.ic_folder),
                        contentDescription = null
                    )
                },
                headlineContent = {
                    Text("Folders to swipe")
                },
                supportingContent = {
                    Text("Tap to manage folders")
                },
                trailingContent = {
                    Icon(
                        painter = painterResource(
                            if (foldersExpanded) R.drawable.caret_down else R.drawable.caret_right
                        ),
                        contentDescription = null
                    )
                },
                modifier = Modifier.clickable { foldersExpanded = !foldersExpanded }
            )

            AnimatedVisibility(
                visible = foldersExpanded,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) + fadeIn(),
                exit = shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) + fadeOut()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onAddFolder,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_create_new_folder),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(" Add folder")
                    }

                    if (uiState.scanFolderUris.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            uiState.scanFolderUris.forEach { uriString ->
                                InputChip(
                                    selected = true,
                                    onClick = { onRemoveFolder(uriString) },
                                    label = { Text(decodeFolderName(uriString)) },
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    trailingIcon = {
                                        Icon(
                                            painter = painterResource(R.drawable.x),
                                            contentDescription = "Remove",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        // Excluded file extensions — collapsible
        run {
            var excludedExpanded by rememberSaveable { mutableStateOf(false) }
            var textFieldValue by rememberSaveable { mutableStateOf("") }

            ListItem(
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.ic_block),
                        contentDescription = null
                    )
                },
                headlineContent = {
                    Text("Excluded file extensions")
                },
                supportingContent = {
                    Text("Tap to manage excluded types")
                },
                trailingContent = {
                    Icon(
                        painter = painterResource(
                            if (excludedExpanded) R.drawable.caret_down else R.drawable.caret_right
                        ),
                        contentDescription = null
                    )
                },
                modifier = Modifier.clickable { excludedExpanded = !excludedExpanded }
            )

            AnimatedVisibility(
                visible = excludedExpanded,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) + fadeIn(),
                exit = shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) + fadeOut()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Spacer(Modifier.height(8.dp))
                    // Input field
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = textFieldValue,
                            onValueChange = { textFieldValue = it },
                            placeholder = { Text(".tmp, .log, .bak") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                        Spacer(Modifier.size(8.dp))
                        Button(
                            onClick = {
                                textFieldValue.split(",", " ").forEach { ext ->
                                    val trimmed = ext.trim()
                                    if (trimmed.isNotEmpty()) onAddExcludedExtension(trimmed)
                                }
                                textFieldValue = ""
                            },
                            enabled = textFieldValue.isNotBlank()
                        ) {
                            Text("Add")
                        }
                    }

                    // Show current excluded extensions as removable chips
                    if (uiState.excludedExtensions.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            uiState.excludedExtensions.sorted().forEach { ext ->
                                InputChip(
                                    selected = false,
                                    onClick = { onRemoveExcludedExtension(ext) },
                                    label = { Text(".$ext") },
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    trailingIcon = {
                                        Icon(
                                            painter = painterResource(R.drawable.x),
                                            contentDescription = "Remove",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Start / Stop swiping button
        if (uiState.isSwipeMode) {
            OutlinedButton(
                onClick = onStopSwiping,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_experiment),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Text(" Stop swiping")
            }
        } else {
            Button(
                onClick = onStartSwiping,
                enabled = uiState.scanFolderUris.isNotEmpty() && hasAllFilesAccess,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_experiment),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Text(" Start swiping")
            }
        }
        Text(
            text = "${uiState.numUnset} files remaining in your area",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, start = 16.dp, end = 16.dp)
        )

        OutlinedButton(
            onClick = onResetMatches,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, start = 16.dp, end = 16.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_folder_match),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Text(" Reset file swipes")
        }
    }
}

/** Decode a SAF folder URI or file:// URI to a human-readable folder name */
private fun decodeFolderName(uriString: String): String {
    if (uriString.startsWith("file://")) {
        return uriString.removePrefix("file://").substringAfterLast("/")
    }
    val decoded = Uri.decode(uriString)
    return decoded.substringAfterLast(":").ifEmpty {
        decoded.substringAfterLast("/")
    }
}

