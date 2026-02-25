package com.example.photoswooper.experimental.ConversionPipelineDocView

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.photoswooper.R
import com.example.photoswooper.experimental.data.Document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "LibreOfficeRenderer"
private const val CANARY_FILE = "lo_init_canary"
private const val SUCCESS_FILE = "lo_init_success"

/**
 * Crash-safe helper that uses reflection to call LibreOfficeSDK methods.
 *
 * Two safety layers:
 * 1. Reflection: avoids eagerly loading native .so at class-load time
 * 2. Canary files: if a previous init attempt caused a native crash (SIGABRT),
 *    we detect it on next launch and refuse to try again, showing an error instead.
 */
private object LOBridge {
    private var sdkClass: Class<*>? = null
    private var initFailed = false
    var lastError: String? = null
        private set

    /** Check if a previous init crashed the process */
    fun didPreviousInitCrash(context: Context): Boolean {
        val canary = File(context.cacheDir, CANARY_FILE)
        val success = File(context.cacheDir, SUCCESS_FILE)
        return canary.exists() && !success.exists()
    }

    /** Clear the crash canary so the user can retry */
    fun clearCrashCanary(context: Context) {
        File(context.cacheDir, CANARY_FILE).delete()
        File(context.cacheDir, SUCCESS_FILE).delete()
        initFailed = false
        lastError = null
    }

    fun initialize(activity: Activity): Boolean {
        if (initFailed) return false
        val context = activity.applicationContext

        // Check if a previous attempt crashed the process
        if (didPreviousInitCrash(context)) {
            initFailed = true
            lastError = "LibreOffice SDK failed to initialize"
            return false
        }

        try {
            // Write canary BEFORE calling native code — if we crash, canary remains without success file
            File(context.cacheDir, CANARY_FILE).writeText("init_started")
            File(context.cacheDir, SUCCESS_FILE).delete()

            val clazz = Class.forName("org.libreoffice.sdk.LibreOfficeSDK")
            sdkClass = clazz
            clazz.getMethod("initialize", Activity::class.java).invoke(null, activity)

            // If we get here, init succeeded — write success marker
            File(context.cacheDir, SUCCESS_FILE).writeText("ok")
            return true
        } catch (e: Throwable) {
            initFailed = true
            lastError = e.cause?.message ?: e.message
            Log.e(TAG, "LibreOffice SDK init failed", e)
            // Clean up canary on Java-level failure (process survived)
            File(context.cacheDir, CANARY_FILE).delete()
            return false
        }
    }

    fun isReady(): Boolean {
        val clazz = sdkClass ?: return false
        return try {
            clazz.getMethod("isReady").invoke(null) as Boolean
        } catch (_: Throwable) { false }
    }

    fun openDocument(filePath: String): Any? {
        val clazz = sdkClass ?: return null
        return try {
            clazz.getMethod("openDocument", String::class.java).invoke(null, filePath)
        } catch (e: Throwable) {
            lastError = e.cause?.message ?: e.message
            null
        }
    }

    fun getLastSDKError(): String? {
        val clazz = sdkClass ?: return null
        return try {
            clazz.getMethod("getLastError").invoke(null) as? String
        } catch (_: Throwable) { null }
    }

    fun renderPage(loDoc: Any, width: Int, height: Int, pageIndex: Int): Bitmap? {
        return try {
            loDoc.javaClass.getMethod("renderPage", Int::class.java, Int::class.java, Int::class.java)
                .invoke(loDoc, width, height, pageIndex) as? Bitmap
        } catch (e: Throwable) {
            lastError = e.cause?.message ?: e.message
            null
        }
    }

    fun closeDoc(loDoc: Any) {
        try {
            loDoc.javaClass.getMethod("close").invoke(loDoc)
        } catch (_: Throwable) {}
    }
}

@Composable
fun LibreOfficeDocPreview(
    document: Document,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var bitmap by remember(document.uri) { mutableStateOf<Bitmap?>(null) }
    var error by remember(document.uri) { mutableStateOf<String?>(null) }
    var loading by remember(document.uri) { mutableStateOf(true) }
    var containerWidth by remember { mutableIntStateOf(0) }
    var containerHeight by remember { mutableIntStateOf(0) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(document.uri, containerWidth, containerHeight, retryTrigger) {
        if (containerWidth <= 0 || containerHeight <= 0) return@LaunchedEffect

        loading = true
        error = null
        bitmap = null

        withContext(Dispatchers.IO) {
            try {
                // Lazy initialization — only loads native libs when user actually uses this feature
                if (!LOBridge.isReady()) {
                    if (activity == null) {
                        error = "Cannot initialize: no Activity context"
                        loading = false
                        return@withContext
                    }
                    if (!LOBridge.initialize(activity)) {
                        error = LOBridge.lastError ?: "LibreOffice SDK failed to initialize"
                        loading = false
                        return@withContext
                    }
                }

                // Copy SAF URI to a temp file (SDK needs a file path)
                val tempFile = File(context.cacheDir, "lo_render_${document.name}")
                context.contentResolver.openInputStream(document.uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: run {
                    error = "Could not read document"
                    loading = false
                    return@withContext
                }

                val loDoc = LOBridge.openDocument(tempFile.absolutePath)
                if (loDoc == null) {
                    val sdkError = LOBridge.getLastSDKError() ?: LOBridge.lastError
                    error = "Failed to open document${if (sdkError != null) ": $sdkError" else ""}"
                    loading = false
                    tempFile.delete()
                    return@withContext
                }

                val rendered = LOBridge.renderPage(loDoc, containerWidth, containerHeight, 0)
                LOBridge.closeDoc(loDoc)
                tempFile.delete()

                if (rendered != null) {
                    bitmap = rendered
                } else {
                    error = "Failed to render page: ${LOBridge.lastError}"
                }
                loading = false
            } catch (e: Throwable) {
                Log.e(TAG, "LibreOffice render failed", e)
                error = "Render failed: ${e.message}"
                loading = false
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged {
                containerWidth = it.width
                containerHeight = it.height
            },
        contentAlignment = Alignment.Center
    ) {
        when {
            loading -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Rendering with LibreOffice...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            error != null -> {
                ErrorCard(
                    document = document,
                    errorMessage = error!!,
                    onRetry = {
                        LOBridge.clearCrashCanary(context)
                        retryTrigger++
                    }
                )
            }

            bitmap != null -> {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = document.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    contentScale = ContentScale.FillWidth
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(
    document: Document,
    errorMessage: String,
    onRetry: () -> Unit = {}
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = Modifier
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
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = document.name,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "LibreOffice SDK failed",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onRetry) {
                Text("Retry: log files")
            }
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
}
