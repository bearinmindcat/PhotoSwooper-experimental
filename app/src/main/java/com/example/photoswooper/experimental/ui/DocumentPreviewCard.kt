package com.example.photoswooper.experimental.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import android.webkit.MimeTypeMap
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import coil3.ImageLoader
import coil3.compose.AsyncImage
import com.example.photoswooper.R
import com.example.photoswooper.experimental.data.Document
import com.example.photoswooper.experimental.data.DocumentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

private const val TAG = "DocPreviewCard"

@Composable
fun DocumentPreviewCard(
    document: Document,
    imageLoader: ImageLoader? = null,
    exoPlayer: ExoPlayer? = null,
    modifier: Modifier = Modifier
) {
    when (document.documentType) {
        DocumentType.PDF -> PdfPreview(document, modifier)
        DocumentType.TEXT, DocumentType.CODE -> TextPreview(document, modifier)
        DocumentType.IMAGE -> ImagePreview(document, imageLoader, modifier)
        DocumentType.APK -> ApkPreview(document, modifier)
        DocumentType.EPUB -> EpubPreview(document, modifier)
        DocumentType.AUDIO -> AudioPreview(document, exoPlayer, modifier)
        DocumentType.VIDEO -> VideoPreview(document, exoPlayer, modifier)
        DocumentType.WORD, DocumentType.EXCEL, DocumentType.POWERPOINT ->
            ThumbnailPreview(document, modifier)
        else -> GenericFileCard(document, modifier)
    }
}

@Composable
private fun ImagePreview(
    document: Document,
    imageLoader: ImageLoader?,
    modifier: Modifier
) {
    if (imageLoader != null) {
        var loadFailed by remember(document.uri) { mutableStateOf(false) }

        if (!loadFailed) {
            AsyncImage(
                model = document.uri,
                imageLoader = imageLoader,
                contentDescription = "Image preview: ${document.name}",
                contentScale = ContentScale.Fit,
                onError = { error ->
                    Log.e(TAG, "ImagePreview failed for ${document.name}: ${error.result.throwable}")
                    loadFailed = true
                },
                modifier = modifier
                    .fillMaxSize()
            )
        } else {
            GenericFileCard(document, modifier)
        }
    } else {
        GenericFileCard(document, modifier)
    }
}

@Composable
private fun AudioPreview(
    document: Document,
    exoPlayer: ExoPlayer?,
    modifier: Modifier
) {
    if (exoPlayer == null) {
        GenericFileCard(document, modifier)
        return
    }

    // Set up the media item when this composable enters
    DisposableEffect(document.uri) {
        // Use file:// URI directly — ExoPlayer's FileDataSource handles it
        exoPlayer.setMediaItem(MediaItem.fromUri(document.uri))
        exoPlayer.prepare()

        onDispose {
            exoPlayer.pause()
            exoPlayer.clearMediaItems()
        }
    }

    // Display card (controls are in DocumentFloatingActionsRow)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_file_audio),
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = document.name,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = document.getFormattedSize(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = document.getFormattedDate(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun VideoPreview(
    document: Document,
    exoPlayer: ExoPlayer?,
    modifier: Modifier
) {
    if (exoPlayer == null) {
        GenericFileCard(document, modifier)
        return
    }

    DisposableEffect(document.uri) {
        // Use file:// URI directly — ExoPlayer's FileDataSource handles it
        // with MANAGE_EXTERNAL_STORAGE permission (no FileProvider needed)
        exoPlayer.setMediaItem(MediaItem.fromUri(document.uri))
        exoPlayer.prepare()
        exoPlayer.play()

        onDispose {
            exoPlayer.pause()
            exoPlayer.clearMediaItems()
        }
    }

    PlayerSurface(
        player = exoPlayer,
        surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
        modifier = modifier.fillMaxSize()
    )
}

@Composable
private fun ApkPreview(document: Document, modifier: Modifier) {
    var appIcon by remember(document.uri) { mutableStateOf<Bitmap?>(null) }
    var appLabel by remember(document.uri) { mutableStateOf<String?>(null) }
    var loadAttempted by remember(document.uri) { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(document.uri) {
        withContext(Dispatchers.IO) {
            var tempFile: File? = null
            try {
                val filePath = when (document.uri.scheme) {
                    "file" -> document.uri.path
                    else -> {
                        // Copy to temp file for APK info extraction
                        tempFile = File.createTempFile("apk_preview", ".apk", context.cacheDir)
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
                    if (packageInfo != null) {
                        packageInfo.applicationInfo?.let { appInfo ->
                            appInfo.sourceDir = filePath
                            appInfo.publicSourceDir = filePath
                            appIcon = appInfo.loadIcon(pm)?.toBitmap()
                            appLabel = appInfo.loadLabel(pm)?.toString()
                        }
                    } else {
                        Log.w(TAG, "ApkPreview: getPackageArchiveInfo returned null for ${document.name}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "ApkPreview failed for ${document.name}: ${e.message}", e)
            } finally {
                tempFile?.delete()
                loadAttempted = true
            }
        }
    }

    if (appIcon != null) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Image(
                    bitmap = appIcon!!.asImageBitmap(),
                    contentDescription = "APK icon: ${document.name}",
                    modifier = Modifier.size(128.dp)
                )
                Spacer(Modifier.height(16.dp))
                if (appLabel != null) {
                    Text(
                        text = appLabel!!,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    text = document.name,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = document.getFormattedSize(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = document.getFormattedDate(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    } else {
        GenericFileCard(document, modifier)
    }
}

@Composable
private fun EpubPreview(document: Document, modifier: Modifier) {
    var coverBitmap by remember(document.uri) { mutableStateOf<Bitmap?>(null) }
    var title by remember(document.uri) { mutableStateOf<String?>(null) }
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
                    // Parse EPUB (ZIP archive) to find cover image and title
                    var opfPath: String? = null
                    var coverHref: String? = null
                    var epubTitle: String? = null
                    val imageEntries = mutableMapOf<String, ByteArray>()
                    val itemHrefs = mutableMapOf<String, String>() // id -> href
                    val itemMediaTypes = mutableMapOf<String, String>() // id -> media-type
                    var coverMetaContent: String? = null

                    val zipStream = ZipInputStream(stream)
                    var entry = zipStream.nextEntry
                    // First pass: read all entries into memory (EPUBs are small)
                    val entries = mutableMapOf<String, ByteArray>()
                    while (entry != null) {
                        val name = entry.name
                        if (!entry.isDirectory) {
                            entries[name] = zipStream.readBytes()
                        }
                        zipStream.closeEntry()
                        entry = zipStream.nextEntry
                    }
                    zipStream.close()

                    // Find OPF path from container.xml
                    entries["META-INF/container.xml"]?.let { containerBytes ->
                        val parser = XmlPullParserFactory.newInstance().newPullParser()
                        parser.setInput(containerBytes.inputStream().reader())
                        var eventType = parser.eventType
                        while (eventType != XmlPullParser.END_DOCUMENT) {
                            if (eventType == XmlPullParser.START_TAG && parser.name == "rootfile") {
                                opfPath = parser.getAttributeValue(null, "full-path")
                            }
                            eventType = parser.next()
                        }
                    }

                    // Parse OPF to find cover image reference and title
                    val opf = opfPath
                    val opfDir = opf?.substringBeforeLast("/", "") ?: ""
                    if (opf != null) {
                        entries[opf]?.let { opfBytes ->
                            val parser = XmlPullParserFactory.newInstance().newPullParser()
                            parser.setInput(opfBytes.inputStream().reader())
                            var eventType = parser.eventType
                            var inTitle = false
                            while (eventType != XmlPullParser.END_DOCUMENT) {
                                when (eventType) {
                                    XmlPullParser.START_TAG -> {
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
                                                // Check if this item has properties="cover-image" (EPUB3)
                                                val props = parser.getAttributeValue(null, "properties") ?: ""
                                                if (props.contains("cover-image")) {
                                                    coverHref = href
                                                }
                                            }
                                            "dc:title" -> inTitle = true
                                        }
                                    }
                                    XmlPullParser.TEXT -> {
                                        if (inTitle) epubTitle = parser.text
                                    }
                                    XmlPullParser.END_TAG -> {
                                        if (parser.name == "dc:title") inTitle = false
                                    }
                                }
                                eventType = parser.next()
                            }
                        }
                    }

                    title = epubTitle

                    // Resolve cover image href
                    if (coverHref == null && coverMetaContent != null) {
                        coverHref = itemHrefs[coverMetaContent]
                    }
                    // Fallback: find first image item if no cover specified
                    if (coverHref == null) {
                        for ((id, mediaType) in itemMediaTypes) {
                            if (mediaType.startsWith("image/")) {
                                coverHref = itemHrefs[id]
                                break
                            }
                        }
                    }

                    // Load the cover image
                    if (coverHref != null) {
                        val coverPath = if (opfDir.isNotEmpty()) "$opfDir/$coverHref" else coverHref!!
                        val imageBytes = entries[coverPath]
                            ?: entries[coverHref!!] // try without prefix
                            ?: entries.entries.firstOrNull {
                                it.key.endsWith(coverHref!!)
                            }?.value
                        if (imageBytes != null) {
                            coverBitmap = android.graphics.BitmapFactory.decodeByteArray(
                                imageBytes, 0, imageBytes.size
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "EpubPreview failed for ${document.name}: ${e.message}")
            }
            loadAttempted = true
        }
    }

    if (coverBitmap != null) {
        Image(
            bitmap = coverBitmap!!.asImageBitmap(),
            contentDescription = "EPUB cover: ${document.name}",
            contentScale = ContentScale.Fit,
            modifier = modifier.fillMaxSize()
        )
    } else if (loadAttempted) {
        // No cover found — show title card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_file_generic),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = title ?: document.name,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = document.getFormattedSize(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = document.getFormattedDate(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    } else {
        GenericFileCard(document, modifier)
    }
}

@Composable
private fun ThumbnailPreview(document: Document, modifier: Modifier) {
    var thumbnail by remember(document.uri) { mutableStateOf<Bitmap?>(null) }
    var loadAttempted by remember(document.uri) { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(document.uri) {
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    thumbnail = context.contentResolver.loadThumbnail(
                        document.uri,
                        android.util.Size(512, 512),
                        null
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "ThumbnailPreview failed for ${document.name}: ${e.message}")
                }
            }
            loadAttempted = true
        }
    }

    if (thumbnail != null) {
        Image(
            bitmap = thumbnail!!.asImageBitmap(),
            contentDescription = "Thumbnail: ${document.name}",
            contentScale = ContentScale.Fit,
            modifier = modifier.fillMaxSize()
        )
    } else {
        GenericFileCard(document, modifier)
    }
}

@Composable
private fun PdfPreview(document: Document, modifier: Modifier) {
    var bitmap by remember(document.uri) { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember(document.uri) { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(document.uri) {
        withContext(Dispatchers.IO) {
            try {
                // Use direct file access for file:// URIs, ContentResolver for content:// URIs
                val pfd = if (document.uri.scheme == "file") {
                    val path = document.uri.path
                    if (path != null) {
                        ParcelFileDescriptor.open(
                            File(path),
                            ParcelFileDescriptor.MODE_READ_ONLY
                        )
                    } else null
                } else {
                    context.contentResolver.openFileDescriptor(document.uri, "r")
                }

                if (pfd != null) {
                    pfd.use { fd ->
                        val renderer = PdfRenderer(fd)
                        renderer.use { r ->
                            if (r.pageCount > 0) {
                                val page = r.openPage(0)
                                page.use { p ->
                                    val scale = 2
                                    val bmp = Bitmap.createBitmap(
                                        p.width * scale, p.height * scale, Bitmap.Config.ARGB_8888
                                    )
                                    // Fill with white background before rendering
                                    Canvas(bmp).drawColor(AndroidColor.WHITE)
                                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    bitmap = bmp
                                }
                            }
                        }
                    }
                } else {
                    Log.w(TAG, "PdfPreview: Could not open file descriptor for ${document.name} (${document.uri})")
                    loadFailed = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "PdfPreview failed for ${document.name}: ${e.message}", e)
                loadFailed = true
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "PDF preview: ${document.name}",
            contentScale = ContentScale.Fit,
            modifier = modifier.fillMaxSize()
        )
    } else {
        GenericFileCard(document, modifier)
    }
}

@Composable
private fun TextPreview(document: Document, modifier: Modifier) {
    var textContent by remember(document.uri) { mutableStateOf<String?>(null) }
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
                    val reader = stream.bufferedReader()
                    textContent = reader.readText().take(3000)
                }
                if (inputStream == null) {
                    Log.w(TAG, "TextPreview: Could not open stream for ${document.name}")
                    textContent = "[Unable to read file]"
                }
            } catch (e: Exception) {
                Log.e(TAG, "TextPreview failed for ${document.name}: ${e.message}")
                textContent = "[Unable to read file]"
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            if (textContent != null) {
                Text(
                    text = textContent!!,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 40,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                )
            } else {
                Text(
                    text = "Loading...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun GenericFileCard(document: Document, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_file_generic),
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = document.name,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = ".${document.extension.uppercase()}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(24.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = document.getFormattedSize(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = document.getFormattedDate(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
