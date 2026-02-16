package com.example.photoswooper.experimental.utils

import android.app.Activity
import android.content.ContentResolver
import android.content.ContentUris
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.app.ActivityCompat.startIntentSenderForResult
import com.example.photoswooper.data.models.MediaStatus
import com.example.photoswooper.experimental.data.Document
import com.example.photoswooper.experimental.data.DocumentFilter
import com.example.photoswooper.experimental.data.DocumentSortField
import com.example.photoswooper.experimental.data.DocumentType
import com.example.photoswooper.experimental.data.database.DocumentStatusDao
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

const val DOCUMENT_DELETE_REQUEST_CODE = 200

class DocumentResolverInterface(
    private val dao: DocumentStatusDao,
    private val contentResolver: ContentResolver,
    private val activity: Activity,
) {
    /**
     * Scan a directory using the File API (requires MANAGE_EXTERNAL_STORAGE).
     * This bypasses SAF restrictions and can access Downloads, etc.
     */
    suspend fun scanDocumentsFromFilePath(
        directory: File,
        filter: DocumentFilter,
        onAddDocument: (Document) -> Unit,
        maxToAdd: Int = 100,
        documentsAdded: MutableSet<String> = mutableSetOf(),
        excludedExtensions: Set<String> = emptySet()
    ): Int {
        if (!directory.exists() || !directory.isDirectory) return 0

        var numAdded = 0
        try {
            val files = directory.listFiles() ?: return 0

            for (file in files) {
                if (numAdded >= maxToAdd) break

                // Recurse into subdirectories
                if (file.isDirectory) {
                    numAdded += scanDocumentsFromFilePath(
                        file, filter, onAddDocument,
                        maxToAdd - numAdded, documentsAdded, excludedExtensions
                    )
                    continue
                }

                val displayName = file.name
                val extension = displayName.substringAfterLast(".", "").lowercase()

                // Skip excluded extensions
                if (extension in excludedExtensions) continue

                // Skip files with no extension or hidden files
                if (extension.isEmpty() || displayName.startsWith(".")) continue

                val fileUri = Uri.fromFile(file)
                val uriString = fileUri.toString()

                // Skip if already seen
                if (uriString in documentsAdded) continue

                val size = file.length()
                val lastModified = file.lastModified()

                // Apply filters — use MIME type fallback for unknown extensions
                val fileMimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: ""
                val docType = DocumentType.fromExtension(extension).let { type ->
                    if (type == DocumentType.OTHER) DocumentType.fromMimeType(fileMimeType) ?: type
                    else type
                }
                if (docType !in filter.documentTypes) continue
                if (size !in filter.sizeRange) continue
                if (filter.containsText.isNotEmpty() &&
                    !displayName.contains(filter.containsText, ignoreCase = true)
                ) continue

                // Check database for existing status
                val existingEntity = dao.findByUri(uriString)
                if (existingEntity != null) {
                    when (existingEntity.status) {
                        MediaStatus.DELETE, MediaStatus.KEEP, MediaStatus.HIDE -> continue
                        MediaStatus.SNOOZE -> {
                            val snoozedUntil = existingEntity.snoozedUntil ?: 0
                            if (System.currentTimeMillis() < snoozedUntil) continue
                        }
                        MediaStatus.UNSET -> { /* Show it */ }
                    }
                }

                val document = Document(
                    uri = fileUri,
                    name = displayName,
                    size = size,
                    dateModified = lastModified,
                    extension = extension,
                    documentType = docType,
                    status = MediaStatus.UNSET,
                    mimeType = fileMimeType,
                )

                onAddDocument(document)
                documentsAdded.add(uriString)
                numAdded++
                dao.insert(document.toDocumentEntity(false))
            }
        } catch (e: Exception) {
            Log.e("DocumentResolver", "Error scanning file path: ${e.message}", e)
        }

        return numAdded
    }

    /**
     * Show the system trash dialog for media files (image/audio/video).
     * Does NOT delete anything — just launches the system confirmation dialog.
     * Returns true if the system dialog was shown, false if there were no media files.
     */
    suspend fun showTrashDialog(documents: List<Document>): Boolean {
        if (documents.isEmpty()) return false

        val mediaTypes = setOf(DocumentType.IMAGE, DocumentType.AUDIO, DocumentType.VIDEO)
        val mediaDocs = documents.filter { it.documentType in mediaTypes }
        if (mediaDocs.isEmpty()) return false

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false

        val contentUris = mutableListOf<Uri>()
        val unresolvedDocs = mutableListOf<Document>()

        withContext(Dispatchers.IO) {
            for (doc in mediaDocs) {
                val path = doc.uri.path ?: continue
                val uri = getMediaStoreUriForFile(path, doc.documentType)
                if (uri != null) contentUris.add(uri)
                else unresolvedDocs.add(doc)
            }
        }

        if (unresolvedDocs.isNotEmpty()) {
            try {
                val unresolvedPaths = unresolvedDocs.mapNotNull { it.uri.path }
                withTimeoutOrNull(15_000) {
                    scanFilesIntoMediaStore(unresolvedPaths)
                }
                delay(500)
                withContext(Dispatchers.IO) {
                    for (doc in unresolvedDocs) {
                        val path = doc.uri.path ?: continue
                        val uri = getMediaStoreUriForFile(path, doc.documentType)
                        if (uri != null) contentUris.add(uri)
                    }
                }
            } catch (e: Exception) {
                Log.e("DocumentResolver", "Error scanning files: ${e.message}", e)
            }
        }

        if (contentUris.isEmpty()) return false

        withContext(Dispatchers.Main) {
            try {
                val trashRequest = MediaStore.createTrashRequest(
                    contentResolver,
                    contentUris,
                    true
                )
                startIntentSenderForResult(
                    activity,
                    trashRequest.intentSender,
                    DOCUMENT_DELETE_REQUEST_CODE,
                    null, 0, 0, 0,
                    Bundle.EMPTY
                )
            } catch (e: Exception) {
                Log.e("DocumentResolver", "Error creating trash request: ${e.message}", e)
                Toast.makeText(activity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        return true
    }

    /**
     * Delete non-media files directly using File.delete().
     * Called after user approves deletion (from onDocumentDeletion).
     */
    suspend fun deleteNonMediaFiles(documents: List<Document>): Int {
        val mediaTypes = setOf(DocumentType.IMAGE, DocumentType.AUDIO, DocumentType.VIDEO)
        val nonMediaDocs = documents.filter { it.documentType !in mediaTypes }
        if (nonMediaDocs.isEmpty()) return 0

        var deleted = 0
        withContext(Dispatchers.IO) {
            for (doc in nonMediaDocs) {
                try {
                    val file = File(doc.uri.path!!)
                    if (file.delete()) deleted++
                } catch (e: Exception) {
                    Log.e("DocumentResolver", "Error deleting file: ${e.message}", e)
                }
            }
        }
        return deleted
    }

    /**
     * Force-scan files into MediaStore so they appear in content URI queries.
     * Suspends until all files have been scanned.
     */
    private suspend fun scanFilesIntoMediaStore(filePaths: List<String>) {
        if (filePaths.isEmpty()) return
        suspendCancellableCoroutine { cont ->
            val remaining = AtomicInteger(filePaths.size)
            val paths = filePaths.toTypedArray()
            val mimeTypes = filePaths.map { path ->
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                    path.substringAfterLast(".", "")
                )
            }.toTypedArray()

            MediaScannerConnection.scanFile(
                activity,
                paths,
                mimeTypes
            ) { _, _ ->
                if (remaining.decrementAndGet() <= 0 && cont.isActive) {
                    cont.resume(Unit)
                }
            }
        }
    }

    /**
     * Query the specific MediaStore media table (Images/Audio/Video) to find
     * the content URI for a given absolute file path.
     * createTrashRequest requires URIs from media-specific tables, NOT MediaStore.Files.
     */
    private fun getMediaStoreUriForFile(filePath: String, docType: DocumentType): Uri? {
        val collectionUri = when (docType) {
            DocumentType.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            DocumentType.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            DocumentType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else -> return null // Non-media types can't go through MediaStore trash
        }
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DATA} = ?"
        val selectionArgs = arrayOf(filePath)

        try {
            contentResolver.query(
                collectionUri, projection, selection, selectionArgs, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(
                        cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    )
                    return ContentUris.withAppendedId(collectionUri, id)
                }
            }
        } catch (e: Exception) {
            Log.e("DocumentResolver", "Error querying MediaStore for $filePath: ${e.message}", e)
        }
        return null
    }

    /**
     * Sort a list of documents according to the filter.
     */
    fun sortDocuments(documents: MutableList<Document>, filter: DocumentFilter): MutableList<Document> {
        when (filter.sortField) {
            DocumentSortField.RANDOM -> documents.shuffle()
            DocumentSortField.DATE -> {
                if (filter.sortAscending) documents.sortBy { it.dateModified ?: 0 }
                else documents.sortByDescending { it.dateModified ?: 0 }
            }
            DocumentSortField.SIZE -> {
                if (filter.sortAscending) documents.sortBy { it.size }
                else documents.sortByDescending { it.size }
            }
            DocumentSortField.NAME -> {
                if (filter.sortAscending) documents.sortBy { it.name.lowercase() }
                else documents.sortByDescending { it.name.lowercase() }
            }
        }
        return documents
    }
}

/**
 * Convert a SAF tree URI to an absolute file path.
 * e.g. content://...externalstorage.../tree/primary%3ADownloads → /storage/emulated/0/Downloads
 */
fun safTreeUriToFilePath(treeUri: Uri): String? {
    return try {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val parts = docId.split(":")
        if (parts.size != 2) return null
        val volume = parts[0]
        val relativePath = parts[1]

        when (volume) {
            "primary" -> "${Environment.getExternalStorageDirectory().absolutePath}/$relativePath"
            else -> "/storage/$volume/$relativePath"
        }
    } catch (e: Exception) {
        Log.e("DocumentResolver", "Error converting SAF URI to file path: ${e.message}", e)
        null
    }
}
