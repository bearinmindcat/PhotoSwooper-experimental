package com.example.photoswooper.experimental.ConversionPipelineDocView

import android.util.Log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

private const val TAG = "HtmlGenerators"

private fun htmlWrapper(title: String, bodyContent: String): String = """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>$title</title>
<style>
  body {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    padding: 8px;
    margin: 0;
    color: #1a1a1a;
    background: #ffffff;
    font-size: 14px;
    line-height: 1.6;
    word-wrap: break-word;
  }
  h1, h2, h3, h4, h5, h6 { color: #333; margin-top: 1em; margin-bottom: 0.5em; }
  h1 { font-size: 1.5em; }
  h2 { font-size: 1.3em; }
  h3 { font-size: 1.15em; }
  table { border-collapse: collapse; width: 100%; margin: 8px 0; }
  th, td { border: 1px solid #ddd; padding: 6px 10px; text-align: left; font-size: 13px; }
  th { background: #f0f0f0; font-weight: 600; }
  tr:nth-child(even) { background: #f9f9f9; }
  p { margin: 0.4em 0; }
  body > *:first-child { margin-top: 0; }
  body > *:last-child { margin-bottom: 0; }
  .slide { border: 1px solid #ccc; border-radius: 8px; padding: 16px; margin: 12px 0; background: #fff; }
  .slide-header { font-weight: 600; color: #555; margin-bottom: 8px; font-size: 12px; text-transform: uppercase; }
  b, strong { font-weight: 600; }
  i, em { font-style: italic; }
  u { text-decoration: underline; }
  pre { background: #f4f4f4; padding: 12px; border-radius: 4px; overflow-x: auto; font-size: 13px; }
</style>
</head>
<body>
$bodyContent
</body>
</html>
""".trimIndent()

/**
 * Generate formatted HTML from a .docx file (ZIP containing word/document.xml).
 * Parses paragraphs, text runs with bold/italic/underline, and tables.
 */
fun generateDocxHtml(bytes: ByteArray): String {
    val entries = readZipEntries(bytes)
    val documentXml = entries["word/document.xml"] ?: return htmlWrapper("Document", "<p>Could not read document content</p>")

    val sb = StringBuilder()
    val parser = XmlPullParserFactory.newInstance().newPullParser()
    parser.setInput(documentXml.inputStream().reader())

    var eventType = parser.eventType
    var inParagraph = false
    var inRun = false
    var isBold = false
    var isItalic = false
    var isUnderline = false
    var inTable = false
    var inTableRow = false
    var inTableCell = false
    var fontSize: String? = null

    while (eventType != XmlPullParser.END_DOCUMENT) {
        when (eventType) {
            XmlPullParser.START_TAG -> {
                val name = localName(parser.name)
                when {
                    // Paragraph
                    name == "p" -> {
                        inParagraph = true
                        if (!inTableCell) sb.append("<p>")
                    }
                    // Paragraph style — check for headings
                    name == "pStyle" -> {
                        val styleVal = parser.getAttributeValue(null, "val")
                            ?: parser.getAttributeValue("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "val")
                            ?: ""
                        if (styleVal.startsWith("Heading") || styleVal.startsWith("heading")) {
                            val level = styleVal.filter { it.isDigit() }.take(1).ifEmpty { "3" }
                            if (!inTableCell) {
                                val lastP = sb.lastIndexOf("<p>")
                                if (lastP >= 0) sb.replace(lastP, lastP + 3, "<h$level>")
                            }
                        }
                    }
                    // Run
                    name == "r" -> {
                        inRun = true
                        isBold = false
                        isItalic = false
                        isUnderline = false
                        fontSize = null
                    }
                    // Run properties
                    name == "b" && inRun -> isBold = true
                    name == "i" && inRun -> isItalic = true
                    name == "u" && inRun -> isUnderline = true
                    name == "sz" && inRun -> fontSize = parser.getAttributeValue(null, "val")
                    // Text content
                    name == "t" -> { /* handled in TEXT event */ }
                    // Table elements
                    name == "tbl" -> { inTable = true; sb.append("<table>") }
                    name == "tr" && inTable -> { inTableRow = true; sb.append("<tr>") }
                    name == "tc" && inTableRow -> { inTableCell = true; sb.append("<td>") }
                }
            }
            XmlPullParser.TEXT -> {
                val text = parser.text
                if (text != null && text.isNotBlank() && (inParagraph || inTableCell)) {
                    if (isBold) sb.append("<b>")
                    if (isItalic) sb.append("<i>")
                    if (isUnderline) sb.append("<u>")
                    sb.append(escapeHtml(text))
                    if (isUnderline) sb.append("</u>")
                    if (isItalic) sb.append("</i>")
                    if (isBold) sb.append("</b>")
                }
            }
            XmlPullParser.END_TAG -> {
                val name = localName(parser.name)
                when {
                    name == "p" && inParagraph -> {
                        inParagraph = false
                        if (!inTableCell) {
                            val lastOpen = findLastOpenTag(sb)
                            if (lastOpen != null && lastOpen.startsWith("h")) {
                                sb.append("</$lastOpen>")
                            } else {
                                sb.append("</p>")
                            }
                        } else {
                            sb.append("<br>")
                        }
                    }
                    name == "r" -> inRun = false
                    name == "tc" && inTableCell -> { inTableCell = false; sb.append("</td>") }
                    name == "tr" && inTableRow -> { inTableRow = false; sb.append("</tr>") }
                    name == "tbl" && inTable -> { inTable = false; sb.append("</table>") }
                }
            }
        }
        if (sb.length > 50000) break
        eventType = parser.next()
    }

    return htmlWrapper("Document", sb.toString())
}

/**
 * Generate formatted HTML from a .xlsx file (ZIP containing xl/worksheets + xl/sharedStrings).
 * Renders as an HTML table with shared string resolution.
 */
fun generateXlsxHtml(bytes: ByteArray): String {
    val entries = readZipEntries(bytes)

    // Parse shared strings
    val sharedStrings = mutableListOf<String>()
    entries["xl/sharedStrings.xml"]?.let { ssBytes ->
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(ssBytes.inputStream().reader())
        var eventType = parser.eventType
        var inT = false
        val current = StringBuilder()
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> if (localName(parser.name) == "t") inT = true
                XmlPullParser.TEXT -> if (inT) current.append(parser.text)
                XmlPullParser.END_TAG -> {
                    if (localName(parser.name) == "t") inT = false
                    if (localName(parser.name) == "si") {
                        sharedStrings.add(current.toString())
                        current.clear()
                    }
                }
            }
            eventType = parser.next()
        }
    }

    // Find first worksheet
    val sheetEntry = entries.keys
        .filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
        .minOrNull()

    if (sheetEntry == null) return htmlWrapper("Spreadsheet", "<p>No worksheet found</p>")

    val sb = StringBuilder()
    sb.append("<table>")

    entries[sheetEntry]?.let { sheetBytes ->
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(sheetBytes.inputStream().reader())
        var eventType = parser.eventType
        var cellType: String? = null
        var inV = false
        var inRow = false
        var rowCount = 0

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> when (localName(parser.name)) {
                    "row" -> {
                        inRow = true
                        rowCount++
                        sb.append("<tr>")
                    }
                    "c" -> cellType = parser.getAttributeValue(null, "t")
                    "v" -> inV = true
                }
                XmlPullParser.TEXT -> if (inV) {
                    val value = parser.text
                    val displayValue = if (cellType == "s") {
                        val idx = value.toIntOrNull()
                        if (idx != null && idx < sharedStrings.size) sharedStrings[idx]
                        else value
                    } else value
                    val tag = if (rowCount == 1) "th" else "td"
                    sb.append("<$tag>${escapeHtml(displayValue)}</$tag>")
                }
                XmlPullParser.END_TAG -> {
                    if (localName(parser.name) == "v") inV = false
                    if (localName(parser.name) == "row") {
                        inRow = false
                        sb.append("</tr>")
                    }
                }
            }
            if (sb.length > 50000) break
            eventType = parser.next()
        }
    }

    sb.append("</table>")
    return htmlWrapper("Spreadsheet", sb.toString())
}

/**
 * Generate formatted HTML from a .pptx file (ZIP containing ppt/slides/slide*.xml).
 * Each slide is rendered as a styled card with its text content.
 */
fun generatePptxHtml(bytes: ByteArray): String {
    val entries = readZipEntries(bytes)

    // Find and sort slide entries
    val slideEntries = entries.keys
        .filter { it.startsWith("ppt/slides/slide") && it.endsWith(".xml") }
        .sorted()

    if (slideEntries.isEmpty()) return htmlWrapper("Presentation", "<p>No slides found</p>")

    val sb = StringBuilder()

    slideEntries.forEachIndexed { idx, slidePath ->
        sb.append("<div class='slide'>")
        sb.append("<div class='slide-header'>Slide ${idx + 1}</div>")

        entries[slidePath]?.let { slideBytes ->
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(slideBytes.inputStream().reader())
            var eventType = parser.eventType
            var inText = false
            var paragraphHasText = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (localName(parser.name) == "t") inText = true
                    }
                    XmlPullParser.TEXT -> {
                        if (inText) {
                            val text = parser.text
                            if (text.isNotBlank()) {
                                sb.append(escapeHtml(text))
                                paragraphHasText = true
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (localName(parser.name) == "t") inText = false
                        if (localName(parser.name) == "p") {
                            if (paragraphHasText) {
                                sb.append("<br>")
                                paragraphHasText = false
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        }

        sb.append("</div>")
        if (sb.length > 50000) return@forEachIndexed
    }

    return htmlWrapper("Presentation", sb.toString())
}

/**
 * Generate formatted HTML table from a CSV file.
 * Handles quoted fields, escaped quotes, and newlines within quotes.
 * First row is rendered as table headers.
 */
fun generateCsvHtml(text: String): String {
    val rows = parseCsvRows(text, maxRows = 500)
    if (rows.isEmpty()) return htmlWrapper("CSV", "<p>Empty file</p>")

    val sb = StringBuilder()
    sb.append("<table>")
    rows.forEachIndexed { rowIdx, cells ->
        sb.append("<tr>")
        val tag = if (rowIdx == 0) "th" else "td"
        for (cell in cells) {
            sb.append("<$tag>${escapeHtml(cell.trim())}</$tag>")
        }
        sb.append("</tr>")
        if (sb.length > 50000) return@forEachIndexed
    }
    sb.append("</table>")
    return htmlWrapper("CSV", sb.toString())
}

/** Simple RFC 4180 CSV parser — handles quoted fields with commas and newlines. */
private fun parseCsvRows(text: String, maxRows: Int): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    val cells = mutableListOf<String>()
    val cell = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < text.length && rows.size < maxRows) {
        val c = text[i]
        when {
            inQuotes -> {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        cell.append('"')
                        i++ // skip escaped quote
                    } else {
                        inQuotes = false
                    }
                } else {
                    cell.append(c)
                }
            }
            c == '"' -> inQuotes = true
            c == ',' -> {
                cells.add(cell.toString())
                cell.clear()
            }
            c == '\n' || (c == '\r' && (i + 1 >= text.length || text[i + 1] == '\n')) -> {
                if (c == '\r') i++ // skip \r in \r\n
                cells.add(cell.toString())
                cell.clear()
                if (cells.any { it.isNotEmpty() }) rows.add(cells.toList())
                cells.clear()
            }
            else -> cell.append(c)
        }
        i++
    }
    // Last row (no trailing newline)
    if (cell.isNotEmpty() || cells.isNotEmpty()) {
        cells.add(cell.toString())
        if (cells.any { it.isNotEmpty() }) rows.add(cells.toList())
    }
    return rows
}

/**
 * Wrap plain text (from binary .doc/.xls/.ppt) in styled HTML.
 * Used when WebView rendering is enabled but the format is binary
 * and can't be parsed for formatting.
 */
fun wrapPlainTextAsHtml(text: String, title: String): String {
    return htmlWrapper(title, "<pre>${escapeHtml(text)}</pre>")
}

// --- Utility functions ---

private fun readZipEntries(bytes: ByteArray): Map<String, ByteArray> {
    val entries = mutableMapOf<String, ByteArray>()
    try {
        val zipStream = ZipInputStream(ByteArrayInputStream(bytes))
        var entry = zipStream.nextEntry
        while (entry != null) {
            if (!entry.isDirectory) {
                entries[entry.name] = zipStream.readBytes()
            }
            zipStream.closeEntry()
            entry = zipStream.nextEntry
        }
        zipStream.close()
    } catch (e: Exception) {
        Log.e(TAG, "Failed to read ZIP entries: ${e.message}")
    }
    return entries
}

private fun escapeHtml(text: String): String =
    text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

/** Strip namespace prefix from XML element name (e.g. "w:p" -> "p", "a:t" -> "t") */
private fun localName(name: String): String = if (":" in name) name.substringAfter(":") else name

private fun findLastOpenTag(sb: StringBuilder): String? {
    val str = sb.toString()
    val lastH = str.lastIndexOf("<h")
    val lastP = str.lastIndexOf("<p>")
    return when {
        lastH > lastP -> {
            val end = str.indexOf(">", lastH)
            if (end > lastH) str.substring(lastH + 1, end) else null
        }
        else -> null
    }
}
