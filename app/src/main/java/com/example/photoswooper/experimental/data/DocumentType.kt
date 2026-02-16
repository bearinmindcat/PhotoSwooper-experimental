package com.example.photoswooper.experimental.data

import androidx.annotation.DrawableRes
import com.example.photoswooper.R

enum class DocumentType(val extensions: Set<String>, @param:DrawableRes val iconResId: Int) {
    PDF(setOf("pdf"), R.drawable.ic_file_pdf),
    TEXT(setOf("txt", "md", "csv", "log", "json", "xml", "yml", "yaml", "ini", "cfg", "conf"), R.drawable.ic_file_text),
    WORD(setOf("doc", "docx", "odt", "rtf"), R.drawable.ic_file_word),
    EXCEL(setOf("xls", "xlsx", "ods"), R.drawable.ic_file_excel),
    POWERPOINT(setOf("ppt", "pptx", "odp"), R.drawable.ic_file_powerpoint),
    ARCHIVE(setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "zst"), R.drawable.ic_file_archive),
    APK(setOf("apk", "xapk", "aab"), R.drawable.ic_file_apk),
    CODE(setOf("kt", "java", "py", "js", "ts", "c", "cpp", "h", "swift", "rs", "go", "rb", "php", "css", "scss", "sh", "bat"), R.drawable.ic_file_code),
    IMAGE(setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg", "ico", "tiff", "tif"), R.drawable.image),
    AUDIO(setOf("mp3", "m4a", "wav", "aac", "flac", "ogg", "opus", "wma", "amr", "mid", "midi"), R.drawable.ic_file_audio),
    VIDEO(setOf("mp4", "mkv", "avi", "mov", "3gp", "webm", "flv", "wmv", "m4v", "mpg", "mpeg", "mts", "m2ts", "vob", "ogv", "asf"), R.drawable.film_strip),
    EPUB(setOf("epub"), R.drawable.books),
    OTHER(emptySet(), R.drawable.ic_file_generic);

    companion object {
        fun fromExtension(ext: String): DocumentType =
            entries.firstOrNull { ext.lowercase() in it.extensions } ?: OTHER

        /** Fallback: determine type from MIME type when extension is unknown */
        fun fromMimeType(mimeType: String): DocumentType? = when {
            mimeType.startsWith("image/") -> IMAGE
            mimeType.startsWith("video/") -> VIDEO
            mimeType == "application/pdf" -> PDF
            mimeType == "text/html" -> null // HTML renders poorly as raw text
            mimeType.startsWith("text/") -> TEXT
            mimeType.contains("word") || mimeType.contains("opendocument.text") -> WORD
            mimeType.contains("spreadsheet") || mimeType.contains("excel") -> EXCEL
            mimeType.contains("presentation") || mimeType.contains("powerpoint") -> POWERPOINT
            mimeType.contains("zip") || mimeType.contains("rar") || mimeType.contains("compressed") -> ARCHIVE
            mimeType == "application/vnd.android.package-archive" -> APK
            mimeType == "application/epub+zip" -> EPUB
            mimeType.startsWith("audio/") -> AUDIO
            else -> null
        }
    }
}
