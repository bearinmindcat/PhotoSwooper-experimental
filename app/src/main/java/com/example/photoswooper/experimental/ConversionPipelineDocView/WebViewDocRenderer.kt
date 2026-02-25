package com.example.photoswooper.experimental.ConversionPipelineDocView

import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.photoswooper.R
import com.example.photoswooper.experimental.data.Document
import com.example.photoswooper.experimental.data.DocumentType
import com.example.photoswooper.experimental.ui.GenericFileCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

private const val TAG = "WebViewDocRenderer"

/**
 * Renders a document using Android WebView with formatted HTML.
 *
 * For XML-based formats (.docx, .xlsx, .pptx), generates HTML with
 * formatting from the document XML. For binary formats (.doc, .xls, .ppt),
 * wraps extracted plain text in styled HTML.
 *
 * Tap the preview to open a fullscreen interactive view where you can
 * scroll and pinch-zoom the document.
 */
@Composable
fun WebViewDocPreview(
    document: Document,
    modifier: Modifier = Modifier
) {
    var htmlContent by remember(document.uri) { mutableStateOf<String?>(null) }
    var loadFailed by remember(document.uri) { mutableStateOf(false) }
    var showInteractive by remember { mutableStateOf(false) }
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
                    val bytes = stream.readBytes()
                    val ext = document.extension.lowercase()

                    htmlContent = when (ext) {
                        "docx" -> generateDocxHtml(bytes)
                        "xlsx" -> generateXlsxHtml(bytes)
                        "pptx" -> generatePptxHtml(bytes)
                        "csv" -> generateCsvHtml(String(bytes, Charsets.UTF_8).take(100000))
                        "doc", "xls", "ppt" -> {
                            // Binary formats — extract plain text and wrap in HTML
                            val plainText = extractPlainTextForWebView(bytes, ext)
                            if (plainText != null) {
                                wrapPlainTextAsHtml(plainText, document.name)
                            } else null
                        }
                        else -> null
                    }

                    if (htmlContent == null) loadFailed = true
                } ?: run { loadFailed = true }
            } catch (e: Exception) {
                Log.e(TAG, "WebView generation failed for ${document.name}: ${e.message}", e)
                loadFailed = true
            }
        }
    }

    when {
        loadFailed -> GenericFileCard(document, modifier)
        htmlContent != null -> {
            // Card preview matching the style of TextPreview/WordPreview — tap to open interactive view
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = modifier
                    .fillMaxSize()
                    .padding(32.dp)
            ) {
                Box(Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewClient = WebViewClient()
                                settings.apply {
                                    javaScriptEnabled = false
                                    builtInZoomControls = false
                                    displayZoomControls = false
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                }
                                // Disable touch on preview — overlay handles tap
                                setOnTouchListener { _, _ -> true }
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            }
                        },
                        update = { webView ->
                            webView.loadDataWithBaseURL(
                                null,
                                htmlContent!!,
                                "text/html",
                                "UTF-8",
                                null
                            )
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )

                    // Transparent overlay on top of WebView to capture taps
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { showInteractive = true }
                    )
                }
            }

            // Fullscreen interactive dialog
            if (showInteractive) {
                InteractiveWebViewDialog(
                    htmlContent = htmlContent!!,
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
 * Fullscreen dialog with an interactive WebView where the user can
 * scroll, pinch-zoom, and pan the document freely.
 */
@Composable
private fun InteractiveWebViewDialog(
    htmlContent: String,
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
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewClient = WebViewClient()
                                settings.apply {
                                    javaScriptEnabled = false
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                    setSupportZoom(true)
                                }
                                setBackgroundColor(android.graphics.Color.WHITE)
                                isHorizontalScrollBarEnabled = true
                                isVerticalScrollBarEnabled = true
                                val thumbDrawable = android.graphics.drawable.GradientDrawable().apply {
                                    setColor(android.graphics.Color.argb(100, 0, 0, 0))
                                }
                                verticalScrollbarThumbDrawable = thumbDrawable
                                horizontalScrollbarThumbDrawable = thumbDrawable
                                setOnTouchListener { v, event ->
                                    when (event.actionMasked) {
                                        android.view.MotionEvent.ACTION_DOWN,
                                        android.view.MotionEvent.ACTION_POINTER_DOWN -> {
                                            v.parent?.requestDisallowInterceptTouchEvent(true)
                                        }
                                    }
                                    false
                                }
                                // Replace viewport for interactive mode: fixed width, no text reflow on zoom
                                val interactiveHtml = htmlContent
                                    .replace(
                                        """content="width=device-width, initial-scale=1.0"""",
                                        """content="width=900, initial-scale=0.5, user-scalable=yes, minimum-scale=0.25, maximum-scale=5.0""""
                                    )
                                    .replace("word-wrap: break-word;", "word-wrap: normal;")
                                loadDataWithBaseURL(null, interactiveHtml, "text/html", "UTF-8", null)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

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

/**
 * Extract plain text from binary Office formats for WebView wrapping.
 * Tries Apache POI first, falls back to OLE2 manual extraction.
 */
private fun extractPlainTextForWebView(bytes: ByteArray, ext: String): String? {
    return try {
        when (ext) {
            "doc" -> {
                try {
                    val extractor = org.apache.poi.hwpf.extractor.WordExtractor(
                        java.io.ByteArrayInputStream(bytes)
                    )
                    val text = extractor.text.take(5000)
                    extractor.close()
                    text
                } catch (e: Throwable) {
                    extractTextFromOle2ForWebView(bytes, "WordDocument")
                }
            }
            "xls" -> {
                try {
                    val extractor = org.apache.poi.hssf.extractor.ExcelExtractor(
                        org.apache.poi.hssf.usermodel.HSSFWorkbook(
                            java.io.ByteArrayInputStream(bytes)
                        )
                    )
                    val text = extractor.text.take(5000)
                    extractor.close()
                    text
                } catch (e: Throwable) {
                    extractTextFromOle2ForWebView(bytes, "Workbook", "Book")
                }
            }
            "ppt" -> {
                try {
                    val extractor = org.apache.poi.hslf.extractor.PowerPointExtractor(
                        java.io.ByteArrayInputStream(bytes)
                    )
                    val text = extractor.text.take(5000)
                    extractor.close()
                    text
                } catch (e: Throwable) {
                    extractTextFromOle2ForWebView(bytes, "PowerPoint Document")
                }
            }
            else -> null
        }
    } catch (e: Exception) {
        Log.w(TAG, "Plain text extraction failed for .$ext: ${e.message}")
        null
    }
}

/** Manual OLE2 text extraction — mirrors the one in DocumentPreviewCard but for WebView use. */
private fun extractTextFromOle2ForWebView(data: ByteArray, vararg streamNames: String): String? {
    if (data.size < 512) return null
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

    val difat = mutableListOf<Int>()
    for (i in 0 until 109) {
        val v = readInt(data, 76 + i * 4)
        if (v < 0 || v >= maxSector) break
        difat.add(v)
    }

    val fat = IntArray(maxSector) { -1 }
    var fi = 0
    for (fatSec in difat) {
        val off = sectorOffset(fatSec)
        for (j in 0 until sectorSize / 4) {
            if (fi >= fat.size) break
            if (off + j * 4 + 3 < data.size) fat[fi] = readInt(data, off + j * 4)
            fi++
        }
    }

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

    val streamData = readChain(streamStart)
    if (streamData.size < 16) return null

    val sb = StringBuilder()
    val run = StringBuilder()
    val startOffset = minOf(0x200, streamData.size)
    for (i in startOffset until streamData.size) {
        val b = streamData[i].toInt() and 0xFF
        when {
            b in 0x20..0x7E -> run.append(b.toChar())
            b == 0x0D || b == 0x0A -> {
                if (run.isNotEmpty()) { sb.append(run); sb.append('\n'); run.clear() }
            }
            b == 0x09 -> run.append('\t')
            else -> {
                if (run.length >= 4) { sb.append(run); sb.append(' ') }
                run.clear()
            }
        }
        if (sb.length > 5000) break
    }
    if (run.length >= 4) sb.append(run)
    val result = sb.toString().trim()
    return if (result.length > 20) result.take(5000) else null
}
