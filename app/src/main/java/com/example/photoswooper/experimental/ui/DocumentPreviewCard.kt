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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import coil3.ImageLoader
import coil3.compose.AsyncImage
import com.example.photoswooper.R
import com.example.photoswooper.experimental.ConversionPipelineDocView.DocRenderMethod
import com.example.photoswooper.experimental.ConversionPipelineDocView.WebViewDocPreview
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
    docRenderMethod: DocRenderMethod = DocRenderMethod.PLAIN_TEXT,
    modifier: Modifier = Modifier
) {
    when (document.documentType) {
        DocumentType.PDF -> PdfPreview(document, modifier)
        DocumentType.TEXT, DocumentType.CODE -> {
            if (document.extension.lowercase() == "csv" && docRenderMethod == DocRenderMethod.WEBVIEW) {
                WebViewDocPreview(document, modifier)
            } else {
                TextPreview(document, modifier)
            }
        }
        DocumentType.IMAGE -> ImagePreview(document, imageLoader, modifier)
        DocumentType.APK -> ApkPreview(document, modifier)
        DocumentType.EPUB -> EpubPreview(document, modifier)
        DocumentType.AUDIO -> AudioPreview(document, exoPlayer, modifier)
        DocumentType.VIDEO -> VideoPreview(document, exoPlayer, modifier)
        DocumentType.WORD, DocumentType.EXCEL, DocumentType.POWERPOINT -> {
            when (docRenderMethod) {
                DocRenderMethod.WEBVIEW -> WebViewDocPreview(document, modifier)
                DocRenderMethod.PLAIN_TEXT -> {
                    when (document.documentType) {
                        DocumentType.WORD -> WordPreview(document, modifier)
                        DocumentType.EXCEL -> ExcelPreview(document, modifier)
                        else -> PowerPointPreview(document, modifier)
                    }
                }
            }
        }
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

    var videoAspectRatio by remember(document.uri) { mutableFloatStateOf(0f) }
    var playbackFailed by remember(document.uri) { mutableStateOf(false) }

    DisposableEffect(document.uri) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.height > 0) {
                    videoAspectRatio = videoSize.width / videoSize.height.toFloat()
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "VideoPreview error for ${document.name}: ${error.message}", error)
                playbackFailed = true
            }
        }
        exoPlayer.addListener(listener)
        // Build MediaItem with explicit MIME type for reliable format detection
        val mimeType = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(document.extension.lowercase())
        val mediaItem = MediaItem.Builder()
            .setUri(document.uri)
            .apply { if (mimeType != null) setMimeType(mimeType) }
            .build()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.pause()
            exoPlayer.clearMediaItems()
        }
    }

    if (playbackFailed) {
        // Show file info card when video codec is unsupported
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            modifier = modifier.fillMaxSize().padding(32.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize().padding(24.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.film_strip),
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
                Text(
                    text = "Unsupported video codec",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(16.dp))
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
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.fillMaxSize()
        ) {
            PlayerSurface(
                player = exoPlayer,
                surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                modifier = if (videoAspectRatio > 0f)
                    Modifier.aspectRatio(videoAspectRatio).fillMaxSize()
                else
                    Modifier.fillMaxSize()
            )
        }
    }
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
    var firstPageBitmap by remember(document.uri) { mutableStateOf<Bitmap?>(null) }
    var allPageBitmaps by remember(document.uri) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var pageCount by remember(document.uri) { mutableStateOf(0) }
    var loadFailed by remember(document.uri) { mutableStateOf(false) }
    var showInteractive by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Render page 1 immediately for preview, then render remaining pages in background
    LaunchedEffect(document.uri) {
        withContext(Dispatchers.IO) {
            try {
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
                            pageCount = r.pageCount
                            val bitmaps = mutableListOf<Bitmap>()
                            val maxPages = minOf(r.pageCount, 50)
                            for (i in 0 until maxPages) {
                                val page = r.openPage(i)
                                page.use { p ->
                                    val scale = 2
                                    val bmp = Bitmap.createBitmap(
                                        p.width * scale, p.height * scale, Bitmap.Config.ARGB_8888
                                    )
                                    Canvas(bmp).drawColor(AndroidColor.WHITE)
                                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    bitmaps.add(bmp)
                                }
                                // Show first page as soon as it's ready
                                if (i == 0) firstPageBitmap = bitmaps[0]
                            }
                            allPageBitmaps = bitmaps
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

    when {
        loadFailed -> GenericFileCard(document, modifier)
        firstPageBitmap != null -> {
            // Card preview showing page 1 — tap to open interactive view
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = modifier
                    .fillMaxSize()
                    .padding(32.dp)
            ) {
                Box(Modifier.fillMaxSize()) {
                    Image(
                        bitmap = firstPageBitmap!!.asImageBitmap(),
                        contentDescription = "PDF preview: ${document.name}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )

                    // Transparent overlay to capture taps
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { showInteractive = true }
                    )
                }
            }

            // Fullscreen interactive dialog
            if (showInteractive && allPageBitmaps.isNotEmpty()) {
                InteractivePdfDialog(
                    pageBitmaps = allPageBitmaps,
                    pageCount = pageCount,
                    onDismiss = { showInteractive = false }
                )
            }
        }
        else -> {
            // Loading state
            Box(
                contentAlignment = Alignment.Center,
                modifier = modifier.fillMaxSize().padding(32.dp)
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Fullscreen dialog showing all PDF pages stacked vertically, scrollable.
 */
@Composable
private fun InteractivePdfDialog(
    pageBitmaps: List<Bitmap>,
    pageCount: Int,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(8.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        pageBitmaps.forEachIndexed { idx, bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "PDF page ${idx + 1}",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.fillMaxWidth()
                            )
                            // Thin divider between pages
                            if (idx < pageBitmaps.size - 1) {
                                Spacer(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(2.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant)
                                )
                            }
                        }
                        // Page count indicator at bottom
                        if (pageCount > pageBitmaps.size) {
                            Text(
                                text = "${pageBitmaps.size} of $pageCount pages shown",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    // Close button
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f))
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.x),
                            contentDescription = "Close",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
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
            .padding(32.dp)
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
private fun WordPreview(document: Document, modifier: Modifier) {
    var textContent by remember(document.uri) { mutableStateOf<String?>(null) }
    var loadFailed by remember(document.uri) { mutableStateOf(false) }
    val context = LocalContext.current
    val ext = document.extension.lowercase()

    LaunchedEffect(document.uri) {
        withContext(Dispatchers.IO) {
            try {
                val inputStream = if (document.uri.scheme == "file") {
                    FileInputStream(File(document.uri.path!!))
                } else {
                    context.contentResolver.openInputStream(document.uri)
                }
                inputStream?.use { stream ->
                    when (ext) {
                        "docx" -> {
                            // .docx is a ZIP containing word/document.xml
                            val zipStream = ZipInputStream(stream)
                            var entry = zipStream.nextEntry
                            var documentXml: ByteArray? = null
                            while (entry != null) {
                                if (entry.name == "word/document.xml") {
                                    documentXml = zipStream.readBytes()
                                    break
                                }
                                zipStream.closeEntry()
                                entry = zipStream.nextEntry
                            }
                            zipStream.close()

                            if (documentXml != null) {
                                val sb = StringBuilder()
                                val parser = XmlPullParserFactory.newInstance().newPullParser()
                                parser.setInput(documentXml.inputStream().reader())
                                var eventType = parser.eventType
                                var inParagraph = false
                                while (eventType != XmlPullParser.END_DOCUMENT) {
                                    when (eventType) {
                                        XmlPullParser.START_TAG -> {
                                            if (parser.name == "p" &&
                                                parser.namespace?.contains("wordprocessingml") != false) {
                                                inParagraph = true
                                            }
                                        }
                                        XmlPullParser.TEXT -> {
                                            val text = parser.text?.trim()
                                            if (!text.isNullOrEmpty()) {
                                                sb.append(text)
                                            }
                                        }
                                        XmlPullParser.END_TAG -> {
                                            if (parser.name == "p" && inParagraph) {
                                                sb.append("\n")
                                                inParagraph = false
                                            }
                                        }
                                    }
                                    if (sb.length > 3000) break
                                    eventType = parser.next()
                                }
                                textContent = sb.toString().take(3000)
                            } else {
                                loadFailed = true
                            }
                        }
                        "doc" -> {
                            // .doc is binary OLE2 format
                            val bytes = stream.readBytes()
                            // Try Apache POI first
                            try {
                                val extractor = org.apache.poi.hwpf.extractor.WordExtractor(
                                    java.io.ByteArrayInputStream(bytes)
                                )
                                textContent = extractor.text.take(3000)
                                extractor.close()
                            } catch (poiError: Throwable) {
                                Log.w(TAG, "POI failed for .doc, using manual extraction: ${poiError.message}")
                                textContent = extractTextFromOle2(bytes, "WordDocument")
                            }
                        }
                        else -> loadFailed = true
                    }
                } ?: run { loadFailed = true }
            } catch (e: Exception) {
                Log.e(TAG, "WordPreview failed for ${document.name}: ${e.message}", e)
                loadFailed = true
            }
        }
    }

    if (loadFailed || (textContent != null && textContent!!.isBlank())) {
        GenericFileCard(document, modifier)
    } else {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                if (textContent != null) {
                    Text(
                        text = textContent!!,
                        style = MaterialTheme.typography.bodySmall,
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
}

@Composable
private fun ExcelPreview(document: Document, modifier: Modifier) {
    var textContent by remember(document.uri) { mutableStateOf<String?>(null) }
    var loadFailed by remember(document.uri) { mutableStateOf(false) }
    val context = LocalContext.current
    val ext = document.extension.lowercase()

    LaunchedEffect(document.uri) {
        withContext(Dispatchers.IO) {
            try {
                val inputStream = if (document.uri.scheme == "file") {
                    FileInputStream(File(document.uri.path!!))
                } else {
                    context.contentResolver.openInputStream(document.uri)
                }
                inputStream?.use { stream ->
                    when (ext) {
                        "xlsx" -> {
                            // .xlsx is a ZIP containing xl/sharedStrings.xml + xl/worksheets/sheet*.xml
                            val entries = mutableMapOf<String, ByteArray>()
                            val zipStream = ZipInputStream(stream)
                            var entry = zipStream.nextEntry
                            while (entry != null) {
                                if (!entry.isDirectory) entries[entry.name] = zipStream.readBytes()
                                zipStream.closeEntry()
                                entry = zipStream.nextEntry
                            }
                            zipStream.close()

                            // Parse shared strings table
                            val sharedStrings = mutableListOf<String>()
                            entries["xl/sharedStrings.xml"]?.let { bytes ->
                                val parser = XmlPullParserFactory.newInstance().newPullParser()
                                parser.setInput(bytes.inputStream().reader())
                                var eventType = parser.eventType
                                var inT = false
                                val current = StringBuilder()
                                while (eventType != XmlPullParser.END_DOCUMENT) {
                                    when (eventType) {
                                        XmlPullParser.START_TAG -> if (parser.name == "t") inT = true
                                        XmlPullParser.TEXT -> if (inT) current.append(parser.text)
                                        XmlPullParser.END_TAG -> {
                                            if (parser.name == "t") inT = false
                                            if (parser.name == "si") {
                                                sharedStrings.add(current.toString())
                                                current.clear()
                                            }
                                        }
                                    }
                                    eventType = parser.next()
                                }
                            }

                            // Parse first worksheet
                            val sb = StringBuilder()
                            val sheetEntry = entries.keys
                                .filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
                                .minOrNull()
                            if (sheetEntry != null) {
                                entries[sheetEntry]?.let { bytes ->
                                    val parser = XmlPullParserFactory.newInstance().newPullParser()
                                    parser.setInput(bytes.inputStream().reader())
                                    var eventType = parser.eventType
                                    var cellType: String? = null
                                    var inV = false
                                    while (eventType != XmlPullParser.END_DOCUMENT) {
                                        when (eventType) {
                                            XmlPullParser.START_TAG -> when (parser.name) {
                                                "c" -> cellType = parser.getAttributeValue(null, "t")
                                                "v" -> inV = true
                                            }
                                            XmlPullParser.TEXT -> if (inV) {
                                                val value = parser.text
                                                if (cellType == "s") {
                                                    val idx = value.toIntOrNull()
                                                    if (idx != null && idx < sharedStrings.size) {
                                                        sb.append(sharedStrings[idx])
                                                    }
                                                } else {
                                                    sb.append(value)
                                                }
                                                sb.append("\t")
                                            }
                                            XmlPullParser.END_TAG -> {
                                                if (parser.name == "v") inV = false
                                                if (parser.name == "row") sb.append("\n")
                                            }
                                        }
                                        if (sb.length > 3000) break
                                        eventType = parser.next()
                                    }
                                }
                            }
                            textContent = sb.toString().take(3000)
                        }
                        "xls" -> {
                            val bytes = stream.readBytes()
                            try {
                                val extractor = org.apache.poi.hssf.extractor.ExcelExtractor(
                                    org.apache.poi.hssf.usermodel.HSSFWorkbook(
                                        java.io.ByteArrayInputStream(bytes)
                                    )
                                )
                                textContent = extractor.text.take(3000)
                                extractor.close()
                            } catch (poiError: Throwable) {
                                Log.w(TAG, "POI failed for .xls: ${poiError.message}")
                                textContent = extractTextFromOle2(bytes, "Workbook", "Book")
                            }
                        }
                        else -> loadFailed = true
                    }
                } ?: run { loadFailed = true }
            } catch (e: Exception) {
                Log.e(TAG, "ExcelPreview failed for ${document.name}: ${e.message}", e)
                loadFailed = true
            }
        }
    }

    if (loadFailed || (textContent != null && textContent!!.isBlank())) {
        GenericFileCard(document, modifier)
    } else {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            modifier = modifier.fillMaxSize().padding(32.dp)
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
}

@Composable
private fun PowerPointPreview(document: Document, modifier: Modifier) {
    var textContent by remember(document.uri) { mutableStateOf<String?>(null) }
    var loadFailed by remember(document.uri) { mutableStateOf(false) }
    val context = LocalContext.current
    val ext = document.extension.lowercase()

    LaunchedEffect(document.uri) {
        withContext(Dispatchers.IO) {
            try {
                val inputStream = if (document.uri.scheme == "file") {
                    FileInputStream(File(document.uri.path!!))
                } else {
                    context.contentResolver.openInputStream(document.uri)
                }
                inputStream?.use { stream ->
                    when (ext) {
                        "pptx" -> {
                            // .pptx is a ZIP containing ppt/slides/slide*.xml
                            val zipStream = ZipInputStream(stream)
                            var entry = zipStream.nextEntry
                            val slideTexts = mutableListOf<Pair<String, String>>()
                            while (entry != null) {
                                if (entry.name.startsWith("ppt/slides/slide") &&
                                    entry.name.endsWith(".xml")
                                ) {
                                    val slideXml = zipStream.readBytes()
                                    val sb = StringBuilder()
                                    val parser = XmlPullParserFactory.newInstance().newPullParser()
                                    parser.setInput(slideXml.inputStream().reader())
                                    var eventType = parser.eventType
                                    var inText = false
                                    while (eventType != XmlPullParser.END_DOCUMENT) {
                                        when (eventType) {
                                            XmlPullParser.START_TAG ->
                                                if (parser.name == "t") inText = true
                                            XmlPullParser.TEXT ->
                                                if (inText) sb.append(parser.text)
                                            XmlPullParser.END_TAG -> {
                                                if (parser.name == "t") inText = false
                                                if (parser.name == "p" || parser.name == "a:p") {
                                                    if (sb.isNotEmpty() && sb.last() != '\n')
                                                        sb.append("\n")
                                                }
                                            }
                                        }
                                        eventType = parser.next()
                                    }
                                    slideTexts.add(entry.name to sb.toString())
                                }
                                zipStream.closeEntry()
                                entry = zipStream.nextEntry
                            }
                            zipStream.close()
                            // Sort slides by name (slide1, slide2, ...)
                            slideTexts.sortBy { it.first }
                            val result = slideTexts.mapIndexed { idx, (_, text) ->
                                "--- Slide ${idx + 1} ---\n$text"
                            }.joinToString("\n")
                            textContent = result.take(3000)
                        }
                        "ppt" -> {
                            val bytes = stream.readBytes()
                            try {
                                val extractor = org.apache.poi.hslf.extractor.PowerPointExtractor(
                                    java.io.ByteArrayInputStream(bytes)
                                )
                                textContent = extractor.text.take(3000)
                                extractor.close()
                            } catch (poiError: Throwable) {
                                Log.w(TAG, "POI failed for .ppt: ${poiError.message}")
                                textContent = extractTextFromOle2(bytes, "PowerPoint Document")
                            }
                        }
                        else -> loadFailed = true
                    }
                } ?: run { loadFailed = true }
            } catch (e: Exception) {
                Log.e(TAG, "PowerPointPreview failed for ${document.name}: ${e.message}", e)
                loadFailed = true
            }
        }
    }

    if (loadFailed || (textContent != null && textContent!!.isBlank())) {
        GenericFileCard(document, modifier)
    } else {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            modifier = modifier.fillMaxSize().padding(32.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                if (textContent != null) {
                    Text(
                        text = textContent!!,
                        style = MaterialTheme.typography.bodySmall,
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
}

/** Manually parse OLE2 compound binary format to extract text from binary Office files. */
private fun extractTextFromOle2(data: ByteArray, vararg streamNames: String): String? {
    if (data.size < 512) return null
    // Verify OLE2/CFBF signature
    if (data[0] != 0xD0.toByte() || data[1] != 0xCF.toByte() ||
        data[2] != 0x11.toByte() || data[3] != 0xE0.toByte()
    ) return null

    fun readInt(buf: ByteArray, off: Int): Int =
        (buf[off].toInt() and 0xFF) or
                ((buf[off + 1].toInt() and 0xFF) shl 8) or
                ((buf[off + 2].toInt() and 0xFF) shl 16) or
                ((buf[off + 3].toInt() and 0xFF) shl 24)

    fun readShort(buf: ByteArray, off: Int): Int =
        (buf[off].toInt() and 0xFF) or ((buf[off + 1].toInt() and 0xFF) shl 8)

    val sectorSize = 1 shl readShort(data, 30)
    val dirStartSec = readInt(data, 48)
    val maxSector = (data.size - 512) / sectorSize
    if (maxSector <= 0) return null

    fun sectorOffset(sec: Int) = 512 + sec * sectorSize

    // Read DIFAT entries from header (up to 109)
    val difat = mutableListOf<Int>()
    for (i in 0 until 109) {
        val v = readInt(data, 76 + i * 4)
        if (v < 0 || v >= maxSector) break
        difat.add(v)
    }

    // Build FAT table from DIFAT sectors
    val fat = IntArray(maxSector) { -1 }
    var fi = 0
    for (fatSec in difat) {
        val off = sectorOffset(fatSec)
        for (j in 0 until sectorSize / 4) {
            if (fi >= fat.size) break
            if (off + j * 4 + 3 < data.size) {
                fat[fi] = readInt(data, off + j * 4)
            }
            fi++
        }
    }

    // Read a chain of sectors into a ByteArray
    fun readChain(start: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var sec = start
        val visited = mutableSetOf<Int>()
        while (sec in 0 until maxSector && sec !in visited) {
            visited.add(sec)
            val off = sectorOffset(sec)
            val len = minOf(sectorSize, data.size - off)
            if (len > 0) out.write(data, off, len)
            sec = if (sec < fat.size) fat[sec] else -2
        }
        return out.toByteArray()
    }

    // Read directory and find the target stream
    val dirData = readChain(dirStartSec)
    var streamStart = -1
    for (i in 0 until dirData.size / 128) {
        val off = i * 128
        val nameSize = readShort(dirData, off + 64)
        if (nameSize <= 0 || nameSize > 64) continue
        val name = String(dirData, off, nameSize, Charsets.UTF_16LE).trimEnd('\u0000')
        if (name in streamNames) {
            streamStart = readInt(dirData, off + 116)
            break
        }
    }
    if (streamStart < 0) return null

    // Read the stream data
    val streamData = readChain(streamStart)
    if (streamData.size < 16) return null

    // Extract printable text (skip first 0x200 bytes for header/FIB)
    val sb = StringBuilder()
    val run = StringBuilder()
    val startOffset = minOf(0x200, streamData.size)
    for (i in startOffset until streamData.size) {
        val b = streamData[i].toInt() and 0xFF
        when {
            b in 0x20..0x7E -> run.append(b.toChar())
            b == 0x0D || b == 0x0A -> {
                if (run.isNotEmpty()) {
                    sb.append(run); sb.append('\n'); run.clear()
                }
            }
            b == 0x09 -> run.append('\t')
            else -> {
                if (run.length >= 4) { sb.append(run); sb.append(' ') }
                run.clear()
            }
        }
        if (sb.length > 3000) break
    }
    if (run.length >= 4) sb.append(run)
    val result = sb.toString().trim()
    return if (result.length > 20) result.take(3000) else null
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
