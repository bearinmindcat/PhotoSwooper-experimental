package com.example.photoswooper.experimental.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

enum class DocumentSortField {
    RANDOM,
    DATE,
    SIZE,
    NAME
}

@Parcelize
data class DocumentFilter(
    val sizeRange: LongRange = 0L..Long.MAX_VALUE,
    val documentTypes: Set<DocumentType> = DocumentType.entries.toSet(),
    val containsText: String = "",
    val sortField: DocumentSortField = DocumentSortField.RANDOM,
    val sortAscending: Boolean = false,
) : Parcelable

val defaultDocumentFilter = DocumentFilter()
