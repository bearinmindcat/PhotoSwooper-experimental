package com.example.photoswooper.experimental.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.material3.Card
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil3.ImageLoader
import coil3.compose.AsyncImage
import com.example.photoswooper.R
import com.example.photoswooper.data.models.MediaStatus
import com.example.photoswooper.experimental.data.Document
import com.example.photoswooper.experimental.data.DocumentType
import com.example.photoswooper.experimental.viewmodel.DocumentSwipeViewModel
import com.example.photoswooper.ui.components.FloatingAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

private const val TAG = "DocReviewScreen"

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileReviewScreen(
    viewModel: DocumentSwipeViewModel,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedDocuments by remember { mutableStateOf(setOf<Document>()) }
    var selectionMode by remember { mutableStateOf(false) }
    var currentFilter by remember { mutableStateOf(MediaStatus.DELETE) }
    val filteredDocs = uiState.documents.filter { it.status == currentFilter }
    val context = LocalContext.current
    val view = LocalView.current
    val showNonMediaDialog by viewModel.showNonMediaDeleteDialog.collectAsState()
    val appName = remember { context.applicationInfo.loadLabel(context.packageManager).toString() }

    // Confirmation popup for non-media files (mimics system trash dialog)
    if (showNonMediaDialog) {
        val docsToDelete = viewModel.getDocumentsToDelete()
        val fileCount = docsToDelete.size

        Dialog(
            onDismissRequest = { viewModel.dismissNonMediaDeleteDialog() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                contentAlignment = Alignment.BottomCenter,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 20.dp)
                    .padding(horizontal = 16.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(
                            start = 24.dp,
                            end = 24.dp,
                            top = 24.dp,
                            bottom = 16.dp
                        )
                    ) {
                        Text(
                            text = if (fileCount == 1)
                                "Allow $appName to move this file to trash?"
                            else
                                "Allow $appName to move these $fileCount files to trash?",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Normal
                            ),
                            color = Color.White,
                            textAlign = TextAlign.Start,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 32.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(80.dp, Alignment.CenterHorizontally),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            TextButton(onClick = { viewModel.dismissNonMediaDeleteDialog() }) {
                                Text(
                                    "Deny",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                            TextButton(onClick = { viewModel.confirmNonMediaDelete() }) {
                                Text(
                                    "Allow",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Filter chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                listOf(MediaStatus.DELETE, MediaStatus.KEEP, MediaStatus.SNOOZE).forEach { status ->
                    val count = uiState.documents.count { it.status == status }
                    Button(
                        onClick = { currentFilter = status },
                        colors = if (currentFilter == status)
                            ButtonDefaults.buttonColors()
                        else
                            ButtonDefaults.outlinedButtonColors(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            painter = painterResource(status.iconDrawableId),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = " $count",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            if (filteredDocs.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = "No ${currentFilter.name.lowercase()} files",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(3),
                    verticalItemSpacing = 4.dp,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
                    items(filteredDocs, key = { it.uri.toString() }) { doc ->
                        DocumentGridItem(
                            document = doc,
                            imageLoader = imageLoader,
                            isSelected = doc in selectedDocuments,
                            onTap = {
                                if (selectionMode) {
                                    selectedDocuments = if (doc in selectedDocuments)
                                        selectedDocuments - doc
                                    else
                                        selectedDocuments + doc
                                    if (selectedDocuments.isEmpty()) selectionMode = false
                                } else {
                                    val mimeType = doc.mimeType.ifEmpty {
                                        MimeTypeMap.getSingleton()
                                            .getMimeTypeFromExtension(doc.extension) ?: "*/*"
                                    }
                                    try {
                                        val shareUri = if (doc.uri.scheme == "file") {
                                            val file = File(doc.uri.path!!)
                                            FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                file
                                            )
                                        } else {
                                            doc.uri
                                        }
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(shareUri, mimeType)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Open document failed: ${e.message}")
                                        Toast.makeText(context, "No suitable app found", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onLongPress = {
                                selectionMode = true
                                selectedDocuments = selectedDocuments + doc
                            }
                        )
                    }
                }
            }

        }

        // Selection action bar (Cancel, Select all, Unswipe) — matches main ReviewScreen
        AnimatedVisibility(
            visible = selectionMode,
            enter = slideInVertically(
                animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow,
                    dampingRatio = Spring.DampingRatioLowBouncy,
                ),
                initialOffsetY = { it * 2 }
            ),
            exit = slideOutVertically(
                animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow,
                    dampingRatio = Spring.DampingRatioLowBouncy,
                ),
                targetOffsetY = { it * 2 }
            ),
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.BottomStart)
        ) {
            Box(contentAlignment = Alignment.BottomStart) {
                Row(
                    modifier = Modifier
                        .dropShadow(
                            shape = MaterialTheme.shapes.medium,
                            shadow = Shadow(
                                radius = 128.dp,
                                alpha = 0.6f
                            )
                        )
                        .padding(dimensionResource(R.dimen.padding_medium))
                ) {
                    FloatingAction(
                        drawableIconId = R.drawable.x,
                        actionTitle = stringResource(R.string.cancel),
                        actionDescription = stringResource(R.string.cancel_selection),
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            selectionMode = false
                            selectedDocuments = emptySet()
                        },
                    )
                    FloatingAction(
                        drawableIconId = R.drawable.selection_all,
                        actionTitle = stringResource(R.string.select_all),
                        actionDescription = null,
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            selectedDocuments = filteredDocs.toSet()
                        },
                    )
                    FloatingAction(
                        drawableIconId = R.drawable.undo,
                        actionTitle = stringResource(R.string.unswipe),
                        actionDescription = stringResource(R.string.unswipe_selected_photos),
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            viewModel.unswipeDocuments(selectedDocuments)
                            selectionMode = false
                            selectedDocuments = emptySet()
                        },
                    )
                }
            }
        }

        // Delete button floating at bottom-end (matches regular ReviewScreen)
        AnimatedVisibility(
            visible = currentFilter == MediaStatus.DELETE
                    && filteredDocs.isNotEmpty()
                    && !selectionMode,
            enter = slideInHorizontally(
                animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow,
                    dampingRatio = Spring.DampingRatioLowBouncy,
                ),
                initialOffsetX = { it * 2 }
            ),
            exit = slideOutHorizontally(
                animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow,
                    dampingRatio = Spring.DampingRatioLowBouncy,
                ),
                targetOffsetX = { it * 2 }
            ),
            modifier = Modifier
                .padding(dimensionResource(R.dimen.padding_medium))
                .align(Alignment.BottomEnd)
        ) {
            ExtendedFloatingActionButton(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    viewModel.deleteMarkedDocuments()
                }
            ) {
                Icon(painterResource(MediaStatus.DELETE.iconDrawableId), null)
                Spacer(Modifier.width(dimensionResource(R.dimen.padding_small)))
                Text("Delete ${filteredDocs.size} items")
            }
        }

    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocumentGridItem(
    document: Document,
    imageLoader: ImageLoader,
    isSelected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            )
    ) {
        when (document.documentType) {
            DocumentType.IMAGE -> ImageThumbnail(document, imageLoader)
            DocumentType.VIDEO -> ImageThumbnail(document, imageLoader) // Coil VideoFrameDecoder handles video thumbnails
            DocumentType.PDF -> PdfThumbnail(document)
            DocumentType.APK -> ApkThumbnail(document)
            DocumentType.EPUB -> EpubThumbnail(document)
            else -> IconThumbnail(document)
        }

        // Type icon overlay (bottom-start)
        Icon(
            painter = painterResource(document.documentType.iconResId),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp)
                .size(16.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                    shape = CircleShape
                )
                .padding(2.dp)
        )

        // Selection indicator
        AnimatedVisibility(
            visible = isSelected,
            enter = expandIn(expandFrom = Alignment.Center),
            exit = shrinkOut(shrinkTowards = Alignment.Center)
        ) {
            Icon(
                painter = painterResource(R.drawable.check),
                tint = MaterialTheme.colorScheme.onSurface,
                contentDescription = "Selected",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                        shape = CircleShape
                    )
                    .padding(4.dp)
                    .size(20.dp)
            )
        }
    }
}

@Composable
private fun ImageThumbnail(document: Document, imageLoader: ImageLoader) {
    var loadFailed by remember(document.uri) { mutableStateOf(false) }

    if (!loadFailed) {
        AsyncImage(
            model = document.uri,
            imageLoader = imageLoader,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onError = { loadFailed = true },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        )
    } else {
        IconThumbnail(document)
    }
}

@Composable
private fun PdfThumbnail(document: Document) {
    var bitmap by remember(document.uri) { mutableStateOf<Bitmap?>(null) }
    var loadAttempted by remember(document.uri) { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(document.uri) {
        withContext(Dispatchers.IO) {
            try {
                val pfd = if (document.uri.scheme == "file") {
                    val path = document.uri.path
                    if (path != null) ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
                    else null
                } else {
                    context.contentResolver.openFileDescriptor(document.uri, "r")
                }
                pfd?.use { fd ->
                    val renderer = PdfRenderer(fd)
                    renderer.use { r ->
                        if (r.pageCount > 0) {
                            val page = r.openPage(0)
                            page.use { p ->
                                val bmp = Bitmap.createBitmap(p.width, p.height, Bitmap.Config.ARGB_8888)
                                Canvas(bmp).drawColor(AndroidColor.WHITE)
                                p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                bitmap = bmp
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "PdfThumbnail failed for ${document.name}: ${e.message}")
            }
            loadAttempted = true
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
        )
    } else {
        IconThumbnail(document)
    }
}

@Composable
private fun ApkThumbnail(document: Document) {
    var appIcon by remember(document.uri) { mutableStateOf<Bitmap?>(null) }
    var loadAttempted by remember(document.uri) { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(document.uri) {
        withContext(Dispatchers.IO) {
            var tempFile: File? = null
            try {
                val filePath = when (document.uri.scheme) {
                    "file" -> document.uri.path
                    else -> {
                        tempFile = File.createTempFile("apk_thumb", ".apk", context.cacheDir)
                        context.contentResolver.openInputStream(document.uri)?.use { input ->
                            tempFile!!.outputStream().use { output ->
                                input.copyTo(output, bufferSize = 8192)
                            }
                        }
                        tempFile!!.absolutePath
                    }
                }
                if (filePath != null) {
                    val pm = context.packageManager
                    @Suppress("DEPRECATION")
                    val packageInfo = pm.getPackageArchiveInfo(filePath, 0)
                    packageInfo?.applicationInfo?.let { appInfo ->
                        appInfo.sourceDir = filePath
                        appInfo.publicSourceDir = filePath
                        appIcon = appInfo.loadIcon(pm)?.toBitmap()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "ApkThumbnail failed for ${document.name}: ${e.message}")
            } finally {
                tempFile?.delete()
                loadAttempted = true
            }
        }
    }

    if (appIcon != null) {
        Image(
            bitmap = appIcon!!.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(8.dp)
        )
    } else {
        IconThumbnail(document)
    }
}

@Composable
private fun EpubThumbnail(document: Document) {
    var coverBitmap by remember(document.uri) { mutableStateOf<Bitmap?>(null) }
    var loadAttempted by remember(document.uri) { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(document.uri) {
        withContext(Dispatchers.IO) {
            try {
                val inputStream = if (document.uri.scheme == "file") {
                    FileInputStream(File(document.uri.path!!))
                } else {
                    context.contentResolver.openInputStream(document.uri)
                }
                inputStream?.use { stream ->
                    var opfPath: String? = null
                    var coverHref: String? = null
                    val itemHrefs = mutableMapOf<String, String>()
                    val itemMediaTypes = mutableMapOf<String, String>()
                    var coverMetaContent: String? = null

                    val entries = mutableMapOf<String, ByteArray>()
                    val zipStream = ZipInputStream(stream)
                    var entry = zipStream.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            entries[entry.name] = zipStream.readBytes()
                        }
                        zipStream.closeEntry()
                        entry = zipStream.nextEntry
                    }
                    zipStream.close()

                    // Find OPF path
                    entries["META-INF/container.xml"]?.let { bytes ->
                        val parser = XmlPullParserFactory.newInstance().newPullParser()
                        parser.setInput(bytes.inputStream().reader())
                        var eventType = parser.eventType
                        while (eventType != XmlPullParser.END_DOCUMENT) {
                            if (eventType == XmlPullParser.START_TAG && parser.name == "rootfile") {
                                opfPath = parser.getAttributeValue(null, "full-path")
                            }
                            eventType = parser.next()
                        }
                    }

                    val opfDir = opfPath?.substringBeforeLast("/", "") ?: ""
                    if (opfPath != null) {
                        entries[opfPath]?.let { opfBytes ->
                            val parser = XmlPullParserFactory.newInstance().newPullParser()
                            parser.setInput(opfBytes.inputStream().reader())
                            var eventType = parser.eventType
                            while (eventType != XmlPullParser.END_DOCUMENT) {
                                if (eventType == XmlPullParser.START_TAG) {
                                    when (parser.name) {
                                        "meta" -> {
                                            if (parser.getAttributeValue(null, "name") == "cover") {
                                                coverMetaContent = parser.getAttributeValue(null, "content")
                                            }
                                        }
                                        "item" -> {
                                            val id = parser.getAttributeValue(null, "id") ?: ""
                                            val href = parser.getAttributeValue(null, "href") ?: ""
                                            val mediaType = parser.getAttributeValue(null, "media-type") ?: ""
                                            itemHrefs[id] = href
                                            itemMediaTypes[id] = mediaType
                                            val props = parser.getAttributeValue(null, "properties") ?: ""
                                            if (props.contains("cover-image")) coverHref = href
                                        }
                                    }
                                }
                                eventType = parser.next()
                            }
                        }
                    }

                    if (coverHref == null && coverMetaContent != null) {
                        coverHref = itemHrefs[coverMetaContent]
                    }
                    if (coverHref == null) {
                        for ((id, mediaType) in itemMediaTypes) {
                            if (mediaType.startsWith("image/")) {
                                coverHref = itemHrefs[id]
                                break
                            }
                        }
                    }

                    if (coverHref != null) {
                        val coverPath = if (opfDir.isNotEmpty()) "$opfDir/$coverHref" else coverHref!!
                        val imageBytes = entries[coverPath]
                            ?: entries[coverHref!!]
                            ?: entries.entries.firstOrNull { it.key.endsWith(coverHref!!) }?.value
                        if (imageBytes != null) {
                            coverBitmap = android.graphics.BitmapFactory.decodeByteArray(
                                imageBytes, 0, imageBytes.size
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "EpubThumbnail failed for ${document.name}: ${e.message}")
            }
            loadAttempted = true
        }
    }

    if (coverBitmap != null) {
        Image(
            bitmap = coverBitmap!!.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.67f) // book cover ratio
        )
    } else {
        IconThumbnail(document)
    }
}

@Composable
private fun IconThumbnail(document: Document) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(8.dp)
    ) {
        Icon(
            painter = painterResource(document.documentType.iconResId),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = document.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            text = document.getFormattedSize(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
