package com.example.photoswooper.experimental.viewmodel

import android.net.Uri
import java.io.File
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.photoswooper.data.models.MediaStatus
import com.example.photoswooper.experimental.SwipeController
import com.example.photoswooper.experimental.data.Document
import com.example.photoswooper.experimental.data.DocumentFilter
import com.example.photoswooper.experimental.data.defaultDocumentFilter
import com.example.photoswooper.experimental.data.database.DocumentStatusDao
import com.example.photoswooper.experimental.utils.DocumentResolverInterface
import com.example.photoswooper.experimental.utils.safTreeUriToFilePath
import com.example.photoswooper.utils.DataStoreInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DocumentSwipeUiState(
    val documents: MutableList<Document> = mutableListOf(),
    val currentIndex: Int = 0,
    val numUnset: Int = 0,
    val fetchingDocuments: Boolean = false,
    val documentReady: Boolean = false,
    val scanFolderUris: List<String> = emptyList(),
    val excludedExtensions: Set<String> = emptySet(),
    val isSwipeMode: Boolean = false,
    val spaceSaved: Long = 0,
)

class DocumentSwipeViewModel(
    private val documentResolverInterface: DocumentResolverInterface,
    private val dao: DocumentStatusDao,
    private val dataStoreInterface: DataStoreInterface,
    private val uiCoroutineScope: CoroutineScope,
) : SwipeController {
    private val _uiState = MutableStateFlow(DocumentSwipeUiState())
    val uiState: StateFlow<DocumentSwipeUiState> = _uiState

    var filter by mutableStateOf(defaultDocumentFilter)
        private set

    // SwipeController implementation
    override val animatedMediaScale = Animatable(0f)

    private val _showFloatingActions = MutableStateFlow(false)
    val showFloatingActions: StateFlow<Boolean> = _showFloatingActions

    private val _showDocumentInfo = MutableStateFlow(false)
    val showDocumentInfo: StateFlow<Boolean> = _showDocumentInfo

    override fun toggleInfoAndFloatingActionsRow() {
        _showFloatingActions.update { !it }
    }

    fun toggleDocumentInfo() {
        _showDocumentInfo.update { !it }
    }

    override fun onMediaLoaded(mediaAspectRatio: Float) {
        onDocumentReady()
        uiCoroutineScope.launch {
            animatedMediaScale.animateTo(1f)
        }
    }

    override fun onMediaError(errorMessage: String?) {
        onDocumentReady()
        uiCoroutineScope.launch {
            animatedMediaScale.animateTo(1f)
        }
    }

    override fun getCurrentItemSize(): Long? = getCurrentDocument()?.size

    /** DataStore keys for persistence */
    private val folderUrisKey = "document_scan_folders"
    private val excludedExtKey = "document_excluded_extensions"
    private val spaceSavedKey = "document_space_saved"

    init {
        uiCoroutineScope.launch(Dispatchers.IO) {
            // Load saved folder URIs — only keep file:// URIs (filter legacy SAF content:// URIs)
            try {
                val value = dataStoreInterface.getStringSettingValue(folderUrisKey).first()
                if (value.isNotEmpty()) {
                    val allUris = value.split(";").filter { it.isNotEmpty() }
                    val fileOnlyUris = allUris.filter { it.startsWith("file://") }
                    if (fileOnlyUris.size != allUris.size) saveFolderUris(fileOnlyUris)
                    _uiState.update { state ->
                        state.copy(scanFolderUris = fileOnlyUris)
                    }
                }
            } catch (_: Exception) {}

            // Load excluded extensions
            try {
                val value = dataStoreInterface.getStringSettingValue(excludedExtKey).first()
                if (value.isNotEmpty()) {
                    _uiState.update { state ->
                        state.copy(excludedExtensions = value.split(";").filter { it.isNotEmpty() }.toSet())
                    }
                }
            } catch (_: Exception) {}

            // Load persisted space saved
            try {
                val saved = dataStoreInterface.getLongSettingValue(spaceSavedKey).first()
                _uiState.update { state -> state.copy(spaceSaved = saved) }
            } catch (_: Exception) {}

        }
    }

    fun addExcludedExtension(ext: String) {
        val cleaned = ext.removePrefix(".").lowercase().trim()
        if (cleaned.isEmpty()) return
        _uiState.update { state ->
            val updated = state.excludedExtensions + cleaned
            saveExcludedExtensions(updated)
            state.copy(excludedExtensions = updated)
        }
    }

    fun removeExcludedExtension(ext: String) {
        _uiState.update { state ->
            val updated = state.excludedExtensions - ext
            saveExcludedExtensions(updated)
            state.copy(excludedExtensions = updated)
        }
    }

    private fun saveExcludedExtensions(exts: Set<String>) {
        uiCoroutineScope.launch(Dispatchers.IO) {
            dataStoreInterface.setStringSettingValue(exts.joinToString(";"), excludedExtKey)
        }
    }

    /** Add a folder from the SAF picker — converts to file:// path */
    fun addScanFolder(treeUri: Uri) {
        val filePath = safTreeUriToFilePath(treeUri) ?: return
        addFileFolder(filePath)
    }

    /** Add a file-system path folder (requires MANAGE_EXTERNAL_STORAGE) */
    fun addFileFolder(path: String) {
        val fileUri = Uri.fromFile(File(path)).toString()
        _uiState.update { state ->
            if (fileUri in state.scanFolderUris) state
            else {
                val updated = state.scanFolderUris + fileUri
                saveFolderUris(updated)
                state.copy(scanFolderUris = updated)
            }
        }
    }

    fun removeScanFolder(uri: String) {
        _uiState.update { state ->
            val updated = state.scanFolderUris.filter { it != uri }
            saveFolderUris(updated)
            state.copy(scanFolderUris = updated)
        }
    }

    private fun saveFolderUris(uris: List<String>) {
        uiCoroutineScope.launch(Dispatchers.IO) {
            dataStoreInterface.setStringSettingValue(uris.joinToString(";"), folderUrisKey)
        }
    }

    fun resetMatches() {
        uiCoroutineScope.launch(Dispatchers.IO) {
            dao.resetAllStatuses()
        }
    }

    fun scanDocuments() {
        _uiState.update { it.copy(
            documents = mutableListOf(),
            currentIndex = 0,
            numUnset = 0,
            fetchingDocuments = true,
            documentReady = false,
        ) }

        uiCoroutineScope.launch(Dispatchers.IO) {
            val allDocs = mutableListOf<Document>()
            val seen = mutableSetOf<String>()
            val excluded = _uiState.value.excludedExtensions

            for (folderUri in _uiState.value.scanFolderUris) {
                try {
                    val parsed = Uri.parse(folderUri)
                    val dir = File(parsed.path!!)
                    documentResolverInterface.scanDocumentsFromFilePath(
                        directory = dir,
                        filter = filter,
                        onAddDocument = { doc -> allDocs.add(doc) },
                        maxToAdd = 100,
                        documentsAdded = seen,
                        excludedExtensions = excluded
                    )
                } catch (e: Exception) {
                    // Folder may not exist or lack permissions
                }
            }

            val sorted = documentResolverInterface.sortDocuments(allDocs, filter)

            _uiState.update { state ->
                state.copy(
                    documents = sorted,
                    numUnset = sorted.size,
                    fetchingDocuments = false,
                    documentReady = sorted.isNotEmpty(),
                )
            }
        }
    }

    fun getCurrentDocument(): Document? {
        val state = _uiState.value
        return state.documents.getOrNull(state.currentIndex)
    }

    override fun markItem(status: MediaStatus) {
        val state = _uiState.value
        val doc = state.documents.getOrNull(state.currentIndex) ?: return
        val updatedDoc = doc.copy(status = status)

        _uiState.update { currentState ->
            currentState.documents[currentState.currentIndex] = updatedDoc
            currentState.copy(
                numUnset = currentState.numUnset - 1
            )
        }

        // Persist to database
        uiCoroutineScope.launch(Dispatchers.IO) {
            val entity = updatedDoc.toDocumentEntity(true)
            dao.insert(entity)
        }
    }

    fun undoLastSwipe() {
        val state = _uiState.value
        if (state.currentIndex <= 0) return

        val prevIndex = state.currentIndex - 1
        val prevDoc = state.documents.getOrNull(prevIndex) ?: return
        val restoredDoc = prevDoc.copy(status = MediaStatus.UNSET)

        uiCoroutineScope.launch {
            // Exit animation — scale current item down
            animatedMediaScale.animateTo(
                0f,
                spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)
            )
            delay(100)

            // Restore previous item
            _uiState.update { currentState ->
                currentState.documents[prevIndex] = restoredDoc
                currentState.copy(
                    currentIndex = prevIndex,
                    numUnset = currentState.numUnset + 1,
                    documentReady = true,
                )
            }

            // Entry animation — scale new item up with bounce
            animatedMediaScale.snapTo(0f)
            animatedMediaScale.animateTo(
                1f,
                spring(
                    stiffness = Spring.StiffnessMediumLow,
                    dampingRatio = Spring.DampingRatioLowBouncy
                )
            )
        }

        uiCoroutineScope.launch(Dispatchers.IO) {
            dao.insert(restoredDoc.toDocumentEntity(true))
        }
    }

    override fun next() {
        _uiState.update { state ->
            state.copy(
                currentIndex = state.currentIndex + 1,
                documentReady = (state.currentIndex + 1) < state.documents.size,
            )
        }
    }

    fun onDocumentReady() {
        _uiState.update { it.copy(documentReady = true) }
    }

    fun enterSwipeMode() {
        _uiState.update { it.copy(isSwipeMode = true) }
    }

    fun exitSwipeMode() {
        _showFloatingActions.update { false }
        _showDocumentInfo.update { false }
        _uiState.update { it.copy(isSwipeMode = false) }
    }

    fun getDocumentsToDelete(): List<Document> {
        return _uiState.value.documents.filter { it.status == MediaStatus.DELETE }
    }

    /**
     * Show the system trash dialog for media files. Nothing is deleted yet —
     * actual deletion happens in onDocumentDeletion() after the user presses Allow.
     * If there are no media files (only non-media), triggers onDocumentDeletion directly.
     */
    fun deleteMarkedDocuments() {
        val docsToDelete = getDocumentsToDelete()
        if (docsToDelete.isEmpty()) return

        uiCoroutineScope.launch(Dispatchers.IO) {
            val dialogShown = documentResolverInterface.showTrashDialog(docsToDelete)
            // If no system dialog was shown (no media files or API < 30),
            // trigger deletion directly since there's no callback coming
            if (!dialogShown) {
                onDocumentDeletion(approved = true)
            }
        }
    }

    /**
     * Called from MainActivity.onActivityResult when the user presses Allow/Deny,
     * or called directly if there were no media files to show a dialog for.
     * Deletes non-media files and marks everything as HIDE.
     */
    fun onDocumentDeletion(approved: Boolean) {
        if (!approved) return

        val docsToDelete = getDocumentsToDelete()
        if (docsToDelete.isEmpty()) return

        uiCoroutineScope.launch(Dispatchers.IO) {
            // Delete non-media files now (media files were already trashed by the system)
            documentResolverInterface.deleteNonMediaFiles(docsToDelete)
            markDocumentsAsHidden(docsToDelete)
        }
    }

    private suspend fun markDocumentsAsHidden(docs: List<Document>) {
        val deletedSize = docs.sumOf { it.size }
        val uriStrings = docs.map { it.uri.toString() }.toSet()

        for (doc in docs) {
            val entity = dao.findByUri(doc.uri.toString())
            if (entity != null) {
                dao.update(entity.copy(status = MediaStatus.HIDE))
            }
        }

        _uiState.update { state ->
            val updatedDocs = state.documents.map { doc ->
                if (doc.uri.toString() in uriStrings) doc.copy(status = MediaStatus.HIDE)
                else doc
            }.toMutableList()
            val newTotal = state.spaceSaved + deletedSize
            state.copy(
                documents = updatedDocs,
                spaceSaved = newTotal
            )
        }

        dataStoreInterface.setLongSettingValue(
            _uiState.value.spaceSaved,
            spaceSavedKey
        )
    }

    fun unswipeDocuments(documents: Set<Document>) {
        _uiState.update { state ->
            val updatedDocs = state.documents.map { doc ->
                if (doc in documents) doc.copy(status = MediaStatus.UNSET)
                else doc
            }.toMutableList()
            state.copy(documents = updatedDocs)
        }
        uiCoroutineScope.launch(Dispatchers.IO) {
            for (doc in documents) {
                val entity = dao.findByUri(doc.uri.toString())
                if (entity != null) {
                    dao.update(entity.copy(status = MediaStatus.UNSET))
                }
            }
        }
    }

    fun updateFilter(newFilter: DocumentFilter) {
        filter = newFilter
    }

    fun snoozeItem(snoozeDurationMillis: Long = 14L * 24 * 60 * 60 * 1000) {
        val state = _uiState.value
        val doc = state.documents.getOrNull(state.currentIndex) ?: return
        val updatedDoc = doc.copy(status = MediaStatus.SNOOZE)

        _uiState.update { currentState ->
            currentState.documents[currentState.currentIndex] = updatedDoc
            currentState.copy(numUnset = currentState.numUnset - 1)
        }

        uiCoroutineScope.launch(Dispatchers.IO) {
            val entity = updatedDoc.toDocumentEntity(true).copy(
                snoozedUntil = System.currentTimeMillis() + snoozeDurationMillis
            )
            dao.insert(entity)
        }
    }
}
