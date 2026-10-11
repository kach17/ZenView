package com.example

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.BackHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.*
import java.util.*
import java.util.zip.ZipInputStream
import com.example.ui.theme.*

// ═══════════════════════════════════════════════════════════════
// DATA & SEALED CLASSES
// ═══════════════════════════════════════════════════════════════

data class AppColors(
    val bg: Color,
    val text: Color,
    val subText: Color,
    val border: Color,
    val surface: Color,
    val accent: Color = AccentPeach,
    val onAccent: Color = OnAccentPeach
)

val LocalAppColors = staticCompositionLocalOf {
    AppColors(bg = CleanBgDark, text = CleanTextDark, subText = CleanSubTextDark, border = BorderDark, surface = CleanSurfaceDark)
}

sealed class DocumentContent {
    data class Pdf(val renderer: PdfRenderer, val pfd: ParcelFileDescriptor, val pageCount: Int) : DocumentContent()
    data class Html(val content: String) : DocumentContent()
}

data class DocumentContext(val uri: Uri, val name: String, val type: FileType)

sealed interface UiState {
    object Idle : UiState
    data class Loading(val fileName: String) : UiState
    data class RenderTarget(val docContext: DocumentContext, val content: DocumentContent) : UiState
    data class Error(val message: String, val docContext: DocumentContext? = null) : UiState
}

enum class FileType {
    PDF, TXT, JSON, CSV, DOCX, XLSX, MD, XML, HTML_SYS, LOG, UNKNOWN
}

class SearchState {
    val query = MutableStateFlow("")
    val isActive = MutableStateFlow(false)
    val activeMatch = MutableStateFlow(0)
    val totalMatches = MutableStateFlow(0)
    val triggerFindNext = MutableStateFlow(0)
    val triggerFindPrev = MutableStateFlow(0)
}

// ═══════════════════════════════════════════════════════════════
// HTML RENDERER
// ═══════════════════════════════════════════════════════════════

object HtmlRenderer {
    private const val BASE_STYLE = """
        :root {
            --bg-color: #F4F5F8; --surface-color: #FFFFFF; --text-color: #1A1C20;
            --subtext-color: #646975; --accent-color: #F7DACF; --accent-hover: rgba(247,218,207,0.25);
            --border-color: #E2E4EB; --row-alt: #EAEDF3;
        }
        html.dark-theme :root, html.dark-theme {
            --bg-color: #1A1A1E; --surface-color: #222328; --text-color: #B5B8C2;
            --subtext-color: #7A7D8A; --accent-color: #F7DACF; --accent-hover: rgba(247,218,207,0.1);
            --border-color: #2B2D35; --row-alt: #25262C;
        }
        html.light-theme :root, html.light-theme {
            --bg-color: #F4F5F8; --surface-color: #FFFFFF; --text-color: #1A1C20;
            --subtext-color: #646975; --accent-color: #F7DACF; --accent-hover: rgba(247,218,207,0.25);
            --border-color: #E2E4EB; --row-alt: #EAEDF3;
        }
        html { overscroll-behavior: auto; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            margin: 0; padding: 96px 16px; color: var(--text-color); background-color: var(--bg-color);
            line-height: 1.6; display: flex; justify-content: center; transition: background-color 0.3s ease, color 0.3s ease;
        }
        .paper { background-color: var(--surface-color); box-shadow: 0 1px 3px rgba(0,0,0,0.3);
            border-radius: 4px; padding: 24px; width: 100%; max-width: 800px; }
        .scroll-container { overflow-x: auto; width: 100%; background: var(--surface-color);
            border: 1px solid var(--border-color); border-radius: 4px; box-shadow: 0 1px 3px rgba(0,0,0,0.3); max-width: 100%; }
        a { color: var(--accent-color); text-decoration: none; transition: background-color 0.2s ease; }
        a:hover { background-color: var(--accent-hover); }
        .table-wrapper { overflow-x: auto; margin: 16px 0; border: 1px solid var(--border-color);
            border-radius: 6px; background-color: var(--surface-color); }
        .table-wrapper table { margin: 0; border-collapse: collapse; width: 100%; min-width: 500px; }
        .page-break { margin: 32px 0; position: relative; text-align: center; }
        .page-break-line { border: none; border-top: 2px dashed var(--border-color); margin: 0; }
        .page-break-text { display: inline-block; position: absolute; top: -10px; left: 50%;
            transform: translateX(-50%); background-color: var(--surface-color); padding: 0 12px;
            font-size: 11px; font-weight: 600; text-transform: uppercase; }
        .list-item { display: block; margin-top: 4px; margin-bottom: 8px; padding-left: calc(24px * var(--level, 1)); }
        .list-item::before { content: "•"; color: var(--accent-color); font-weight: bold; position: absolute; }
        .doc-image-container { display: block; text-align: center; margin: 24px auto; }
        .doc-image { max-width: 100%; height: auto; border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.15); }
        h1, h2, h3, h4, h5, h6 { margin-top: 24px; margin-bottom: 16px; color: var(--text-color); }
        p { margin-bottom: 16px; }
        code { background-color: var(--row-alt); padding: 2px 4px; border-radius: 4px; font-family: monospace; }
        pre { background-color: var(--row-alt); padding: 12px; border-radius: 4px; overflow-x: auto; }
        blockquote { border-left: 4px solid var(--accent-color); margin: 0; padding-left: 16px; color: var(--subtext-color); }
        table { border-collapse: collapse; width: 100%; margin-bottom: 16px; }
        th, td { border: 1px solid var(--border-color); padding: 8px 12px; text-align: left; }
        th { background-color: var(--bg-color); font-weight: 600; color: var(--accent-color); position: sticky; top: 0; z-index: 10; }
        tr:nth-child(even) { background-color: var(--row-alt); }
        td.row-header { background-color: var(--bg-color); font-weight: 600; color: var(--accent-color);
            position: sticky; left: 0; text-align: center; width: 40px; z-index: 20; }
    """

    fun wrap(content: String, isTable: Boolean = false): String {
        val wrapStart = if (isTable) "<div class=\"scroll-container\"><table>" else "<div class=\"paper\">"
        val wrapEnd = if (isTable) "</table></div>" else "</div>"
        val viewportMeta = if (isTable || content.contains("<table")) {
            "<meta name=\"viewport\" content=\"minimum-scale=0.1, maximum-scale=5.0\" />"
        } else {
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, minimum-scale=0.5, maximum-scale=5.0\" />"
        }
        return """<!DOCTYPE html><html><head>$viewportMeta<style>$BASE_STYLE</style></head>
            |<body${if (isTable) " style=\"display: block; padding-top: 96px; padding-bottom: 96px;\"" else ""}>$wrapStart$content$wrapEnd</body></html>""".trimMargin()
    }
}

// ═══════════════════════════════════════════════════════════════
// TABLE TO HTML CONVERTER
// ═══════════════════════════════════════════════════════════════

object TableToHtmlConverter {
    fun csvToHtml(context: Context, uri: Uri): String {
        val stream = context.contentResolver.openInputStream(uri) ?: throw Exception("Failed to open stream")
        val reader = BufferedReader(InputStreamReader(stream, "UTF-8"))
        val rows = StringBuilder("<tbody>")
        var line = reader.readLine()
        var isHeader = true
        var rowCount = 0
        val MAX_ROWS = 1500

        while (line != null && rowCount < MAX_ROWS) {
            val columns = parseCsvFields(line)
            rows.append("<tr>")
            columns.forEach { col ->
                val escaped = col.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                rows.append(if (isHeader) "<th>$escaped</th>" else "<td>$escaped</td>")
            }
            rows.append("</tr>")
            isHeader = false
            rowCount++
            line = reader.readLine()
        }
        rows.append("</tbody>")
        if (line != null) rows.append("<div style='margin-top: 10px; font-size: 11px; color: var(--subtext-color);'>⚡ Preview limited to $MAX_ROWS rows.</div>")
        reader.close()
        return rows.toString()
    }

    fun xlsxToHtml(context: Context, uri: Uri, sharedStrings: List<String>): String {
        val MAX_ROWS = 1000
        val MAX_COLS = 50
        val sheetMatrix = TreeMap<Int, TreeMap<Int, String>>()

        ZipProcessor.processZipEntry(context, uri, { it.contains("worksheets/sheet") }) { parser ->
            var eventType = parser.eventType
            var currentCellRef = ""
            var currentCellType = ""
            var inCell = false
            var inV = false
            var inT = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name
                        if (name == "c") {
                            inCell = true
                            currentCellRef = parser.getAttributeValue(null, "r") ?: ""
                            currentCellType = parser.getAttributeValue(null, "t") ?: ""
                        } else if (name == "v") inV = true
                        else if (name == "t") inT = true
                    }
                    XmlPullParser.TEXT -> {
                        if (inCell && inV) {
                            val vVal = parser.text ?: ""
                            if (currentCellRef.isNotEmpty()) {
                                val (cIdx, rIdx) = parseCellRef(currentCellRef)
                                if (rIdx < MAX_ROWS && cIdx < MAX_COLS) {
                                    var cellVal = vVal
                                    if (currentCellType == "s") {
                                        val sIdx = vVal.toIntOrNull() ?: -1
                                        if (sIdx in sharedStrings.indices) cellVal = sharedStrings[sIdx]
                                    } else if (currentCellType == "b") {
                                        cellVal = if (vVal == "1") "TRUE" else "FALSE"
                                    }
                                    val colsMap = sheetMatrix.getOrPut(rIdx) { TreeMap() }
                                    colsMap[cIdx] = cellVal
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "v" -> inV = false
                            "t" -> inT = false
                            "c" -> { inCell = false; currentCellRef = ""; currentCellType = "" }
                        }
                    }
                }
                eventType = parser.next()
            }
        }

        val maxRow = sheetMatrix.lastKey()
        var maxCol = 0
        sheetMatrix.values.forEach { cols -> if (cols.isNotEmpty()) maxCol = maxOf(maxCol, cols.lastKey()) }

        val rows = StringBuilder("<thead><tr><th></th>")
        (0..maxCol).forEach { c -> rows.append("<th>${colIndexToName(c)}</th>") }
        rows.append("</tr></thead><tbody>")
        (0..maxRow).forEach { r ->
            rows.append("<tr><td class='row-header'>${r + 1}</td>")
            val colsMap = sheetMatrix[r]
            (0..maxCol).forEach { c ->
                val valStr = colsMap?.get(c) ?: ""
                val escaped = valStr.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                rows.append("<td>$escaped</td>")
            }
            rows.append("</tr>")
        }
        rows.append("</tbody>")
        if (maxRow >= MAX_ROWS - 1) rows.append("<div style='margin-top: 10px; font-size: 11px; color: var(--subtext-color);'>⚡ Heavy worksheet: only first $MAX_ROWS rows loaded.</div>")
        return rows.toString()
    }

    private fun parseCsvFields(line: String): List<String> {
        val result = ArrayList<String>()
        val curVal = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                inQuotes && ch == '\"' && i + 1 < line.length && line[i + 1] == '\"' -> {
                    curVal.append('\"'); i++
                }
                inQuotes && ch == '\"' -> inQuotes = false
                inQuotes -> curVal.append(ch)
                ch == '\"' -> inQuotes = true
                ch == ',' -> { result.add(curVal.toString().trim()); curVal.clear() }
                else -> curVal.append(ch)
            }
            i++
        }
        result.add(curVal.toString().trim())
        return result
    }

    private fun parseCellRef(ref: String): Pair<Int, Int> {
        val colName = ref.takeWhile { it.isLetter() }.uppercase()
        val rowNum = ref.dropWhile { it.isLetter() }
        return Pair(colNameToIndex(colName), (rowNum.toIntOrNull() ?: 1) - 1)
    }

    private fun colNameToIndex(name: String): Int {
        var idx = 0
        name.forEach { char -> idx = idx * 26 + (char - 'A' + 1) }
        return idx - 1
    }

    private fun colIndexToName(index: Int): String {
        var temp = index
        val name = StringBuilder()
        while (temp >= 0) {
            name.insert(0, ('A' + (temp % 26)))
            temp = temp / 26 - 1
        }
        return name.toString()
    }
}

// ═══════════════════════════════════════════════════════════════
// ZIP PROCESSOR
// ═══════════════════════════════════════════════════════════════

object ZipProcessor {
    fun processZipEntry(context: Context, uri: Uri, predicate: (String) -> Boolean, block: (XmlPullParser) -> Unit) {
        val stream = context.contentResolver.openInputStream(uri) ?: throw Exception("Failed to open stream")
        val zipStream = ZipInputStream(stream)
        try {
            var entry = zipStream.nextEntry
            var targetStream: InputStream? = null
            while (entry != null) {
                if (predicate(entry.name)) { targetStream = zipStream; break }
                entry = zipStream.nextEntry
            }
            if (targetStream == null) throw Exception("Not a valid archive")
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(targetStream, "UTF-8")
            block(parser)
        } finally {
            zipStream.close()
            stream.close()
        }
    }

    fun extractMedia(context: Context, uri: Uri) {
        val mediaDir = File(context.cacheDir, "docx_media")
        if (mediaDir.exists()) mediaDir.deleteRecursively()
        mediaDir.mkdirs()

        val stream = context.contentResolver.openInputStream(uri) ?: return
        val zipStream = ZipInputStream(stream)
        try {
            var entry = zipStream.nextEntry
            val buffer = ByteArray(4096)
            while (entry != null) {
                if (entry.name.startsWith("word/media/")) {
                    val fileName = entry.name.substringAfterLast('/')
                    val outFile = File(mediaDir, fileName)
                    val outStream = FileOutputStream(outFile)
                    try {
                        var len = zipStream.read(buffer)
                        while (len > 0) {
                            outStream.write(buffer, 0, len)
                            len = zipStream.read(buffer)
                        }
                    } finally { outStream.close() }
                }
                entry = zipStream.nextEntry
            }
        } catch (e: Exception) { e.printStackTrace() }
        finally { zipStream.close(); stream.close() }
    }

    fun loadSharedStrings(context: Context, uri: Uri): List<String> {
        val stream = context.contentResolver.openInputStream(uri) ?: return emptyList()
        val zipStream = ZipInputStream(stream)
        var sharedStrings: List<String> = emptyList()
        try {
            var entry = zipStream.nextEntry
            while (entry != null) {
                if (entry.name == "xl/sharedStrings.xml" || entry.name.endsWith("sharedStrings.xml")) {
                    sharedStrings = parseSharedStringsXml(zipStream)
                    break
                }
                entry = zipStream.nextEntry
            }
        } catch (e: Exception) { e.printStackTrace() }
        finally { zipStream.close() }
        return sharedStrings
    }

    fun loadRelationships(context: Context, uri: Uri): Map<String, String> {
        val relsMap = HashMap<String, String>()
        try {
            processZipEntry(context, uri, { it == "word/_rels/document.xml.rels" }) { parser ->
                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && parser.name == "Relationship") {
                        val id = parser.getAttributeValue(null, "Id")
                        val type = parser.getAttributeValue(null, "Type")
                        val target = parser.getAttributeValue(null, "Target")
                        if (id != null && target != null && type != null && type.contains("image")) {
                            relsMap[id] = target.substringAfterLast('/')
                        }
                    }
                    eventType = parser.next()
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return relsMap
    }

    private fun parseSharedStringsXml(inStream: InputStream): List<String> {
        val list = ArrayList<String>()
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(inStream, "UTF-8")
        var eventType = parser.eventType
        val currentStr = StringBuilder()
        var inSi = false
        var inT = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "si") { inSi = true; currentStr.clear() }
                    else if (parser.name == "t" && inSi) inT = true
                }
                XmlPullParser.TEXT -> { if (inT && inSi) currentStr.append(parser.text) }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "t") inT = false
                    else if (parser.name == "si") { inSi = false; list.add(currentStr.toString()) }
                }
            }
            eventType = parser.next()
        }
        return list
    }
}

// ═══════════════════════════════════════════════════════════════
// SIMPLE TEXT PARSER
// ═══════════════════════════════════════════════════════════════

object SimpleTextParser {
    fun readPlainText(context: Context, uri: Uri): String {
        val stream = context.contentResolver.openInputStream(uri) ?: throw Exception("Failed to open stream")
        val reader = BufferedReader(InputStreamReader(stream, "UTF-8"))
        val builder = StringBuilder()
        var line = reader.readLine()
        var lineCount = 0
        val MAX_LINES = 8000
        while (line != null && lineCount < MAX_LINES) {
            builder.append(line).append("\n")
            lineCount++
            line = reader.readLine()
        }
        reader.close()
        if (line != null) builder.append("\n⚡ [Preview limited to $MAX_LINES lines for peak speed]")
        return builder.toString()
    }

    fun prettyPrintJson(rawJson: String): String {
        return try {
            val json = rawJson.trim()
            when {
                json.startsWith("{") -> JSONObject(json).toString(4)
                json.startsWith("[") -> JSONArray(json).toString(4)
                else -> rawJson
            }
        } catch (e: Exception) { rawJson }
    }

    fun parseMarkdownToHtml(markdown: String): String {
        var html = markdown
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace(Regex("(?m)^([-*_\\s]){3,}\\s*$"), "<div class=\"page-break\"><hr class=\"page-break-line\"/><div class=\"page-break-text\">Page Break</div></div>")
            .replace(Regex("\\*\\*(.*?)\\*\\*"), "<strong>$1</strong>")
            .replace(Regex("(?m)^# (.*?)$"), "<h1>$1</h1>")
            .replace(Regex("(?m)^## (.*?)$"), "<h2>$1</h2>")
            .replace(Regex("(?m)^### (.*?)$"), "<h3>$1</h3>")
            .replace(Regex("\\[(.*?)\\]\\((.*?)\\)"), "<a href=\"$2\" style=\"color:var(--accent-color);\">$1</a>")
            .replace(Regex("`([^`]+)`"), "<code>$1</code>")
            .replace(Regex("(?m)^> (.*?)$"), "<blockquote>$1</blockquote>")
            .replace(Regex("(?m)^[*-] (.*?)$"), "<div class=\"list-item\" style=\"--level: 1;\">$1</div>")
            .replace(Regex("(?m)$"), "<br>")
        html = html.replace("<br><br>", "<br>")
        return html
    }
}

// ═══════════════════════════════════════════════════════════════
// DOCUMENT PARSER
// ═══════════════════════════════════════════════════════════════

object DocumentParser {
    fun getFileName(resolver: ContentResolver, uri: Uri): String {
        var name = ""
        if (uri.scheme == "content") {
            val cursor = resolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) name = cursor.getString(index)
                }
            } catch (e: Exception) { e.printStackTrace() }
            finally { cursor?.close() }
        }
        if (name.isEmpty()) {
            name = uri.path ?: ""
            val cut = name.lastIndexOf('/')
            if (cut != -1) name = name.substring(cut + 1)
        }
        return if (name.isEmpty()) "Document" else name
    }

    fun getFileType(context: Context, uri: Uri, fileName: String): FileType {
        val mimeType = context.contentResolver.getType(uri)?.lowercase() ?: ""
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when {
            mimeType == "application/pdf" || extension == "pdf" -> FileType.PDF
            mimeType == "text/plain" || extension == "txt" -> FileType.TXT
            mimeType == "text/csv" || extension == "csv" -> FileType.CSV
            mimeType == "application/json" || extension == "json" -> FileType.JSON
            mimeType == "text/markdown" || extension in listOf("md", "markdown") -> FileType.MD
            mimeType == "text/xml" || extension == "xml" -> FileType.XML
            mimeType == "text/html" || extension in listOf("html", "htm") -> FileType.HTML_SYS
            extension == "log" -> FileType.LOG
            mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" || extension == "docx" -> FileType.DOCX
            mimeType == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" || extension == "xlsx" -> FileType.XLSX
            else -> FileType.UNKNOWN
        }
    }

    fun getMimeType(context: Context, uri: Uri, fileName: String): String {
        val mime = context.contentResolver.getType(uri)
        if (mime != null) return mime
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "csv" -> "text/csv"
            "json" -> "application/json"
            "md", "markdown" -> "text/markdown"
            "xml" -> "text/xml"
            "html", "htm" -> "text/html"
            "log" -> "text/plain"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            else -> "*/*"
        }
    }

    fun openWithSystemApp(context: Context, uri: Uri, mimeType: String) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Document..."))
        } catch (e: Exception) {
            Toast.makeText(context, "No supportive app found to share this document type.", Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun loadDocument(context: Context, uri: Uri, fileName: String, type: FileType): DocumentContent {
        return withContext(Dispatchers.IO) {
            when (type) {
                FileType.PDF -> {
                    val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: throw Exception("Failed to open file descriptor")
                    val renderer = PdfRenderer(pfd)
                    DocumentContent.Pdf(renderer, pfd, renderer.pageCount)
                }
                FileType.CSV -> {
                    val html = TableToHtmlConverter.csvToHtml(context, uri)
                    DocumentContent.Html(HtmlRenderer.wrap(html, isTable = true))
                }
                FileType.XLSX -> {
                    val sharedStrings = ZipProcessor.loadSharedStrings(context, uri)
                    val html = TableToHtmlConverter.xlsxToHtml(context, uri, sharedStrings)
                    DocumentContent.Html(HtmlRenderer.wrap(html, isTable = true))
                }
                FileType.DOCX -> {
                    val html = parseDocxToHtml(context, uri)
                    DocumentContent.Html(HtmlRenderer.wrap(html))
                }
                FileType.MD -> {
                    val text = SimpleTextParser.readPlainText(context, uri)
                    val html = SimpleTextParser.parseMarkdownToHtml(text)
                    DocumentContent.Html(HtmlRenderer.wrap(html))
                }
                FileType.JSON -> {
                    val text = SimpleTextParser.readPlainText(context, uri)
                    val formatted = SimpleTextParser.prettyPrintJson(text)
                    val escaped = formatted.replace("<", "&lt;").replace(">", "&gt;")
                    DocumentContent.Html(HtmlRenderer.wrap("<pre><code>$escaped</code></pre>"))
                }
                FileType.XML, FileType.LOG -> {
                    val text = SimpleTextParser.readPlainText(context, uri)
                    val escaped = text.replace("<", "&lt;").replace(">", "&gt;")
                    DocumentContent.Html(HtmlRenderer.wrap("<pre><code>$escaped</code></pre>"))
                }
                FileType.HTML_SYS -> {
                    val text = SimpleTextParser.readPlainText(context, uri)
                    DocumentContent.Html(HtmlRenderer.wrap(text))
                }
                else -> {
                    val text = SimpleTextParser.readPlainText(context, uri)
                    DocumentContent.Html(HtmlRenderer.wrap("<pre>$text</pre>"))
                }
            }
        }
    }

    private suspend fun parseDocxToHtml(context: Context, uri: Uri): String {
        return withContext(Dispatchers.IO) {
            val rels = ZipProcessor.loadRelationships(context, uri)
            ZipProcessor.extractMedia(context, uri)
            val html = StringBuilder()

            ZipProcessor.processZipEntry(context, uri, { it == "word/document.xml" }) { parser ->
                var eventType = parser.eventType
                var inParagraph = false
                var paragraphStartedInHtml = false
                var inRun = false
                var isBold = false
                var isItalic = false
                var isUnderline = false
                var isListItem = false
                var listLevel = 0
                var headingLevel = 0

                fun ensureParagraphOpen() {
                    if (inParagraph && !paragraphStartedInHtml) {
                        paragraphStartedInHtml = true
                        when {
                            headingLevel > 0 -> html.append("<h$headingLevel>")
                            isListItem -> html.append("<div class=\"list-item\" style=\"--level: ${listLevel + 1};\">")
                            else -> html.append("<p>")
                        }
                    }
                }

                fun closeParagraph() {
                    if (paragraphStartedInHtml) {
                        html.append(when {
                            headingLevel > 0 -> "</h$headingLevel>"
                            isListItem -> "</div>"
                            else -> "</p>"
                        })
                        paragraphStartedInHtml = false
                    } else if (inParagraph) {
                        html.append("<p>&nbsp;</p>")
                    }
                }

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            when (parser.name) {
                                "tbl" -> html.append("<div class=\"table-wrapper\"><table>")
                                "tr" -> html.append("<tr>")
                                "tc" -> html.append("<td>")
                                "p" -> { inParagraph = true; paragraphStartedInHtml = false; isListItem = false; listLevel = 0; headingLevel = 0 }
                                "pStyle" -> {
                                    val styleVal = parser.getAttributeValue(null, "val")
                                    if (styleVal != null && styleVal.startsWith("Heading", ignoreCase = true)) {
                                        headingLevel = styleVal.filter { it.isDigit() }.toIntOrNull() ?: 1
                                    }
                                }
                                "numPr" -> isListItem = true
                                "ilvl" -> {
                                    val valAttr = parser.getAttributeValue(null, "val")
                                    if (valAttr != null) listLevel = valAttr.toIntOrNull() ?: 0
                                }
                                "br" -> {
                                    val typeAttr = parser.getAttributeValue(null, "type")
                                    if (typeAttr == "page") {
                                        html.append("<div class=\"page-break\"><hr class=\"page-break-line\"/><div class=\"page-break-text\">Page Break</div></div>")
                                    } else {
                                        ensureParagraphOpen()
                                        html.append("<br/>")
                                    }
                                }
                                "lastRenderedPageBreak" -> html.append("<div class=\"page-break\"><hr class=\"page-break-line\"/><div class=\"page-break-text\">Page Break</div></div>")
                                "blip" -> {
                                    val embedId = parser.getAttributeValue(null, "embed") ?: parser.getAttributeValue(null, "link")
                                    if (embedId != null) {
                                        val fileName = rels[embedId]
                                        if (fileName != null) {
                                            ensureParagraphOpen()
                                            html.append("<div class=\"doc-image-container\"><img class=\"doc-image\" src=\"$fileName\" alt=\"Image\" /></div>")
                                        }
                                    }
                                }
                                "r" -> { inRun = true; isBold = false; isItalic = false; isUnderline = false }
                                "b" -> if (inRun) isBold = true
                                "i" -> if (inRun) isItalic = true
                                "u" -> if (inRun) isUnderline = true
                                "t" -> {
                                    if (inRun) {
                                        ensureParagraphOpen()
                                        val textContent = parser.nextText()
                                        val escaped = textContent.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                                        if (isBold) html.append("<strong>")
                                        if (isItalic) html.append("<em>")
                                        if (isUnderline) html.append("<u>")
                                        html.append(escaped)
                                        if (isUnderline) html.append("</u>")
                                        if (isItalic) html.append("</em>")
                                        if (isBold) html.append("</strong>")
                                    }
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            when (parser.name) {
                                "tbl" -> html.append("</table></div>")
                                "tr" -> html.append("</tr>")
                                "tc" -> html.append("</td>")
                                "p" -> { closeParagraph(); inParagraph = false }
                                "r" -> inRun = false
                            }
                        }
                    }
                    eventType = parser.next()
                }
            }
            html.toString()
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// RECENT FILES MANAGER
// ═══════════════════════════════════════════════════════════════

data class RecentFile(val uri: Uri, val name: String, val type: FileType, val timestamp: Long, val lastOffset: Int = 0)

object RecentFilesManager {
    private const val PREFS_NAME = "zenview_recent_files"
    private const val KEY_RECENTS = "recents_json"

    fun addRecentFile(context: Context, docContext: DocumentContext) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentJson = prefs.getString(KEY_RECENTS, "[]")
        val array = JSONArray(currentJson)
        val newList = mutableListOf<JSONObject>()
        
        var existingOffset = 0
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            if (obj.getString("name") != docContext.name) {
                newList.add(obj)
            } else {
                existingOffset = obj.optInt("lastOffset", 0)
            }
        }
        
        val newObj = JSONObject().apply {
            put("uri", docContext.uri.toString())
            put("name", docContext.name)
            put("type", docContext.type.name)
            put("time", System.currentTimeMillis())
            put("lastOffset", existingOffset)
        }
        
        newList.add(0, newObj)
        val finalArray = JSONArray()
        newList.take(10).forEach { finalArray.put(it) }
        
        prefs.edit().putString(KEY_RECENTS, finalArray.toString()).apply()
        
        try {
            context.contentResolver.takePersistableUriPermission(docContext.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
            // Might fail for some providers, ignore safely
        }
    }
    
    fun updateLastOffset(context: Context, uri: Uri, name: String, offset: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentJson = prefs.getString(KEY_RECENTS, "[]")
        val array = JSONArray(currentJson)
        val finalArray = JSONArray()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            if (obj.getString("name") == name) {
                obj.put("lastOffset", offset)
                obj.put("uri", uri.toString()) // Keep URI updated
            }
            finalArray.put(obj)
        }
        prefs.edit().putString(KEY_RECENTS, finalArray.toString()).apply()
    }

    fun getRecentFiles(context: Context): List<RecentFile> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentJson = prefs.getString(KEY_RECENTS, "[]")
        val array = JSONArray(currentJson)
        val list = mutableListOf<RecentFile>()
        for (i in 0 until array.length()) {
            try {
                val obj = array.getJSONObject(i)
                list.add(RecentFile(
                    uri = Uri.parse(obj.getString("uri")),
                    name = obj.getString("name"),
                    type = FileType.valueOf(obj.getString("type")),
                    timestamp = obj.getLong("time"),
                    lastOffset = obj.optInt("lastOffset", 0)
                ))
            } catch (e: Exception) {}
        }
        return list
    }
    
    fun removeRecentFile(context: Context, uri: Uri) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentJson = prefs.getString(KEY_RECENTS, "[]")
        val array = JSONArray(currentJson)
        val finalArray = JSONArray()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            if (obj.getString("uri") != uri.toString()) {
                finalArray.put(obj)
            }
        }
        prefs.edit().putString(KEY_RECENTS, finalArray.toString()).apply()
    }
}

// ═══════════════════════════════════════════════════════════════
// VIEW MODEL
// ═══════════════════════════════════════════════════════════════

class ViewerViewModel : ViewModel() {
    var isExternalLaunch: Boolean = false
    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState = _uiState.asStateFlow()
    val searchState = SearchState()
    val loadProgress = MutableStateFlow(0f)

    fun handleIntent(context: Context, intent: Intent?) {
        if (intent == null) return
        var targetUri: Uri? = null
        when (intent.action) {
            Intent.ACTION_VIEW -> targetUri = intent.data
            Intent.ACTION_SEND -> targetUri = intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        targetUri?.let { 
            isExternalLaunch = true
            loadFile(context, it) 
        }
    }

    fun loadFile(context: Context, uri: Uri) {
        closeActiveResources()
        val resolvedName = DocumentParser.getFileName(context.contentResolver, uri)
        val resolvedType = DocumentParser.getFileType(context, uri, resolvedName)
        val docContext = DocumentContext(uri, resolvedName, resolvedType)
        _uiState.value = UiState.Loading(resolvedName)
        loadProgress.value = 0f
        viewModelScope.launch {
            try {
                val content = DocumentParser.loadDocument(context, uri, resolvedName, resolvedType)
                loadProgress.value = 1f
                _uiState.value = UiState.RenderTarget(docContext, content)
                RecentFilesManager.addRecentFile(context, docContext)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.localizedMessage ?: "An error occurred.", docContext)
            }
        }
    }

    fun resetToIdle() {
        closeActiveResources()
        _uiState.value = UiState.Idle
        loadProgress.value = 0f
    }

    fun closeActiveResources() {
        val current = _uiState.value
        if (current is UiState.RenderTarget && current.content is DocumentContent.Pdf) {
            try { current.content.renderer.close() } catch (e: Exception) {}
            try { current.content.pfd.close() } catch (e: Exception) {}
        }
    }

    override fun onCleared() {
        super.onCleared()
        closeActiveResources()
    }
}

// ═══════════════════════════════════════════════════════════════
// MAIN ACTIVITY
// ═══════════════════════════════════════════════════════════════

class ViewerActivity : ComponentActivity() {
    private val activityIntent = mutableStateOf<Intent?>(null)

    @androidx.compose.foundation.ExperimentalFoundationApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        activityIntent.value = intent

        setContent {
            val viewModel: ViewerViewModel = viewModel()
            val context = LocalContext.current
            val currentIntent by activityIntent
            LaunchedEffect(currentIntent) {
                currentIntent?.let {
                    viewModel.handleIntent(context, it)
                    activityIntent.value = null
                }
            }

            val systemTheme = isSystemInDarkTheme()
            var isDarkMode by remember { mutableStateOf(systemTheme) }
            var isImmersive by remember { mutableStateOf(false) }
            val view = LocalView.current
            val window = androidx.activity.compose.LocalActivity.current?.window

            LaunchedEffect(isImmersive, isDarkMode) {
                if (window != null) {
                    val windowInsetsController = WindowCompat.getInsetsController(window, view)
                    if (isImmersive) {
                        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    } else {
                        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                        windowInsetsController.isAppearanceLightStatusBars = !isDarkMode
                        windowInsetsController.isAppearanceLightNavigationBars = !isDarkMode
                    }
                }
            }

            val appColors = remember(isDarkMode) {
                if (isDarkMode) AppColors(bg = CleanBgDark, text = CleanTextDark, subText = CleanSubTextDark, border = BorderDark, surface = CleanSurfaceDark)
                else AppColors(bg = CleanBgLight, text = CleanTextLight, subText = CleanSubTextLight, border = BorderLight, surface = CleanSurfaceLight)
            }

            MyApplicationTheme(darkTheme = isDarkMode) {
                CompositionLocalProvider(
                    LocalOverscrollConfiguration provides null,
                    LocalAppColors provides appColors
                ) {
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                    val searchQuery by viewModel.searchState.query.collectAsStateWithLifecycle()
                    val searchActive by viewModel.searchState.isActive.collectAsStateWithLifecycle()
                    val loadProgress by viewModel.loadProgress.collectAsStateWithLifecycle()
                    
                    val handleBack: () -> Unit = {
                        if (viewModel.isExternalLaunch) {
                            (context as? ComponentActivity)?.finish()
                        } else {
                            viewModel.resetToIdle()
                        }
                    }

                    BackHandler(enabled = uiState !is UiState.Idle || viewModel.isExternalLaunch) {
                        handleBack()
                    }

                    Box(modifier = Modifier.fillMaxSize().background(appColors.bg)) {
                        when (val state = uiState) {
                            is UiState.Idle -> WelcomeDashboard(onFilePick = { uri -> 
                                viewModel.isExternalLaunch = false
                                viewModel.loadFile(context, uri) 
                            })
                            is UiState.Loading -> {
                                Box(modifier = Modifier.fillMaxSize().background(appColors.bg), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        BasicText(state.fileName, style = TextStyle(fontSize = 14.sp, color = appColors.text, fontWeight = FontWeight.Medium))
                                        Spacer(modifier = Modifier.height(24.dp))
                                        Box(
                                            modifier = Modifier.width(200.dp).height(2.dp).background(appColors.border)
                                        ) {
                                            Box(
                                                modifier = Modifier.fillMaxHeight().fillMaxWidth(loadProgress).background(AccentPeach)
                                                    .animateContentSize(animationSpec = tween(300, easing = LinearEasing))
                                            )
                                        }
                                    }
                                }
                            }
                            is UiState.RenderTarget -> {
                                val rawStatusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                                var stableStatusBarHeight by remember { mutableStateOf(24.dp) }
                                if (rawStatusBarHeight > 0.dp && rawStatusBarHeight != stableStatusBarHeight) {
                                    stableStatusBarHeight = rawStatusBarHeight
                                }
                                val topContentPadding = stableStatusBarHeight + 56.dp

                                DocumentViewport(
                                    content = state.content,
                                    docContext = state.docContext,
                                    isImmersive = isImmersive,
                                    isDarkMode = isDarkMode,
                                    contentPaddingTop = topContentPadding,
                                    onSetImmersive = { isImmersive = it },
                                    searchState = viewModel.searchState
                                )
                            }
                            is UiState.Error -> {
                                ErrorStateView(
                                    message = state.message,
                                    onRetryClick = { state.docContext?.let { viewModel.loadFile(context, it.uri) } },
                                    onBackClick = handleBack,
                                    onOpenWithAlternative = {
                                        state.docContext?.let {
                                            val mime = DocumentParser.getMimeType(context, it.uri, it.name)
                                            DocumentParser.openWithSystemApp(context, it.uri, mime)
                                        }
                                    }
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = !isImmersive && uiState !is UiState.Idle && uiState !is UiState.Loading,
                            enter = slideInVertically { -it } + fadeIn(),
                            exit = slideOutVertically { -it } + fadeOut(),
                            modifier = Modifier.align(Alignment.TopCenter)
                        ) {
                            val docContext = (uiState as? UiState.RenderTarget)?.docContext ?: (uiState as? UiState.Error)?.docContext
                            var showInfoDialog by remember { mutableStateOf(false) }

                            if (showInfoDialog && docContext != null) {
                                androidx.compose.ui.window.Dialog(onDismissRequest = { showInfoDialog = false }) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().background(appColors.surface, RoundedCornerShape(12.dp)).padding(24.dp)
                                    ) {
                                        Column {
                                            BasicText("Document Info", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = appColors.text))
                                            Spacer(modifier = Modifier.height(16.dp))
                                            BasicText("Name", style = TextStyle(fontSize = 12.sp, color = appColors.subText, fontWeight = FontWeight.SemiBold))
                                            BasicText(docContext.name, style = TextStyle(fontSize = 15.sp, color = appColors.text))
                                            Spacer(modifier = Modifier.height(12.dp))
                                            BasicText("Format", style = TextStyle(fontSize = 12.sp, color = appColors.subText, fontWeight = FontWeight.SemiBold))
                                            BasicText(docContext.type.name, style = TextStyle(fontSize = 15.sp, color = appColors.text))
                                            Spacer(modifier = Modifier.height(12.dp))
                                            BasicText("MIME Type", style = TextStyle(fontSize = 12.sp, color = appColors.subText, fontWeight = FontWeight.SemiBold))
                                            BasicText(DocumentParser.getMimeType(context, docContext.uri, docContext.name), style = TextStyle(fontSize = 15.sp, color = appColors.text))
                                            Spacer(modifier = Modifier.height(24.dp))
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                                LiteInteractive(onClick = { showInfoDialog = false }) { BasicText("Close") }
                                            }
                                        }
                                    }
                                }
                            }

                            ViewerTopBar(
                                fileName = docContext?.name ?: "Error",
                                mimeType = docContext?.let { DocumentParser.getMimeType(context, it.uri, it.name) } ?: "",
                                onBackClick = handleBack,
                                onOpenWithClick = {
                                    docContext?.let {
                                        DocumentParser.openWithSystemApp(context, it.uri, DocumentParser.getMimeType(context, it.uri, it.name))
                                    } ?: Toast.makeText(context, "No document loaded", Toast.LENGTH_SHORT).show()
                                },
                                onInfoClick = { showInfoDialog = true },
                                isDarkMode = isDarkMode,
                                onThemeToggle = { isDarkMode = !isDarkMode }
                            )
                        }

                        AnimatedVisibility(
                            visible = (!isImmersive || searchActive) && uiState is UiState.RenderTarget && (uiState as UiState.RenderTarget).content is DocumentContent.Html,
                            enter = slideInVertically { it } + fadeIn(),
                            exit = slideOutVertically { it } + fadeOut(),
                            modifier = Modifier.align(Alignment.BottomCenter)
                        ) {
                            BottomSearchBanner(searchState = viewModel.searchState, onSetImmersive = { isImmersive = it })
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        activityIntent.value = intent
    }
}

// ═══════════════════════════════════════════════════════════════
// DOCUMENT VIEWPORT & VIEWERS
// ═══════════════════════════════════════════════════════════════

@Composable
fun DocumentViewport(
    content: DocumentContent,
    docContext: DocumentContext,
    isImmersive: Boolean,
    isDarkMode: Boolean,
    contentPaddingTop: Dp,
    onSetImmersive: (Boolean) -> Unit,
    searchState: SearchState
) {
    val context = LocalContext.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    
    val initialOffset = remember(docContext.uri) {
        RecentFilesManager.getRecentFiles(context).find { it.uri == docContext.uri }?.lastOffset ?: 0
    }

    Box(
        modifier = Modifier.fillMaxSize().onSizeChanged { size = it }.pointerInput(onSetImmersive, size, isImmersive) {
            awaitPointerEventScope {
                while (true) {
                    val down = awaitFirstDown(pass = PointerEventPass.Initial)
                    val startPos = down.position
                    val startTime = System.currentTimeMillis()
                    var isClick = true
                    var lastEvent = down
                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null) break
                        lastEvent = change
                        val dist = (change.position - startPos).getDistance()
                        if (dist > 15f || (System.currentTimeMillis() - startTime) > 250) isClick = false
                        if (!change.pressed) break
                    }
                    if (isClick && size.width > 0 && size.height > 0) {
                        val x = lastEvent.position.x
                        val y = lastEvent.position.y
                        val minX = size.width * 0.25f
                        val maxX = size.width * 0.75f
                        val minY = size.height * 0.15f
                        val maxY = size.height * 0.85f
                        if (x in minX..maxX && y in minY..maxY) {
                            onSetImmersive(!isImmersive)
                        }
                    }
                }
            }
        }
    ) {
        when (content) {
            is DocumentContent.Pdf -> PdfDocumentViewer(
                renderer = content.renderer,
                pageCount = content.pageCount,
                isDarkMode = isDarkMode,
                isImmersive = isImmersive,
                contentPaddingTop = contentPaddingTop,
                onSetImmersive = onSetImmersive,
                initialPage = initialOffset,
                onPageChange = { page ->
                    RecentFilesManager.updateLastOffset(context, docContext.uri, docContext.name, page)
                }
            )
            is DocumentContent.Html -> HtmlDocumentViewer(
                htmlContent = content.content,
                isDarkMode = isDarkMode,
                isImmersive = isImmersive,
                contentPaddingTop = contentPaddingTop,
                onSetImmersive = onSetImmersive,
                searchState = searchState,
                initialScrollY = initialOffset,
                onScrollChange = { scrollY ->
                    RecentFilesManager.updateLastOffset(context, docContext.uri, docContext.name, scrollY)
                }
            )
        }
    }
}

@Composable
fun PdfDocumentViewer(
    renderer: PdfRenderer,
    pageCount: Int,
    isDarkMode: Boolean,
    isImmersive: Boolean,
    contentPaddingTop: Dp,
    onSetImmersive: (Boolean) -> Unit,
    initialPage: Int = 0,
    onPageChange: (Int) -> Unit = {}
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage)
    val scope = rememberCoroutineScope()
    var lastOffset by remember { mutableStateOf(0) }
    val currentVisIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    
    LaunchedEffect(currentVisIndex) {
        onPageChange(currentVisIndex)
    }

    LaunchedEffect(currentVisIndex, listState.firstVisibleItemScrollOffset) {
        val currentOffset = currentVisIndex * 10000 + listState.firstVisibleItemScrollOffset
        if (listState.isScrollInProgress) {
            if (currentOffset > lastOffset + 15) {
                onSetImmersive(true)
            } else if (currentOffset < lastOffset - 15) {
                onSetImmersive(false)
            }
        }
        lastOffset = currentOffset
    }

    val appColors = LocalAppColors.current

    ZoomableContainer(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().background(appColors.bg).padding(vertical = if (pageCount == 1) 0.dp else 4.dp),
                contentPadding = if (pageCount == 1) PaddingValues(0.dp) else PaddingValues(top = contentPaddingTop, bottom = 32.dp),
                verticalArrangement = if (pageCount == 1) Arrangement.Center else Arrangement.Top
            ) {
                items(pageCount) { index ->
                    PdfPageRenderer(renderer = renderer, pageIndex = index, isDarkMode = isDarkMode, modifier = Modifier.fillMaxWidth())
                }
            }

            // Fast scroller
            FastScrollBar(
                listState = listState,
                itemCount = pageCount,
                isVisible = pageCount > 1,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
            )

            if (pageCount > 1) {
                AnimatedVisibility(
                    visible = !isImmersive || listState.isScrollInProgress,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(modifier = Modifier.background(Color(0x99000000), shape = RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                        BasicText("${currentVisIndex + 1} / $pageCount", style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium))
                    }
                }
            }

            val showFastScrollFab by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 && !isImmersive } }
            AnimatedVisibility(
                visible = showFastScrollFab,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                LiteFloatingActionButton(onClick = { scope.launch { listState.animateScrollToItem(0) } }, modifier = Modifier.size(48.dp)) {
                    Image(Icons.Default.KeyboardArrowUp, contentDescription = "Jump to Top", colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(LocalContentColor.current))
                }
            }
        }
    }
}

@Composable
fun FastScrollBar(
    listState: androidx.compose.foundation.lazy.LazyListState,
    itemCount: Int,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val scrollProgress = remember(listState) {
        derivedStateOf {
            val visibleIndex = listState.firstVisibleItemIndex
            val visibleOffset = listState.firstVisibleItemScrollOffset
            val totalScroll = visibleIndex + (visibleOffset.toFloat() / 1000f)
            (totalScroll / itemCount).coerceIn(0f, 1f)
        }
    }

    var isDragging by remember { mutableStateOf(false) }
    var showLabel by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.padding(end = 4.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxHeight().width(3.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        isDragging = true
                        showLabel = true
                        val clickProgress = it.y / size.height
                        scope.launch {
                            val targetIndex = (clickProgress * itemCount).toInt().coerceIn(0, itemCount - 1)
                            listState.animateScrollToItem(targetIndex)
                        }
                        isDragging = false
                        showLabel = false
                    })
                }
        ) {
            // Track
            Box(modifier = Modifier.fillMaxHeight().width(2.dp).background(appColors.border.copy(alpha = 0.3f)))

            // Thumb
            Box(
                modifier = Modifier.fillMaxHeight(0.08f).width(3.dp).offset(y = remember(scrollProgress.value) {
                    derivedStateOf { (scrollProgress.value * 92).dp }
                }.value).background(AccentPeach, shape = RoundedCornerShape(1.5.dp))
            )

            // Page number label
            if (showLabel) {
                Box(
                    modifier = Modifier.align(Alignment.CenterStart).offset(x = (-50).dp)
                        .background(Color.Black.copy(alpha = 0.8f), shape = RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    val currentPage = (scrollProgress.value * itemCount).toInt() + 1
                    BasicText("$currentPage", style = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium))
                }
            }
        }
    }
}

@Composable
fun PdfPageRenderer(renderer: PdfRenderer, pageIndex: Int, isDarkMode: Boolean, modifier: Modifier = Modifier) {
    var bitmapState by remember { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current

    LaunchedEffect(pageIndex) {
        withContext(Dispatchers.IO) {
            try {
                synchronized(renderer) {
                    val page = renderer.openPage(pageIndex)
                    val metrics = context.resources.displayMetrics
                    val targetWidth = minOf(metrics.widthPixels, 1280)
                    val scale = targetWidth.toFloat() / page.width.toFloat()
                    val targetHeight = (page.height * scale).toInt()
                    val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bitmapState = bitmap
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    Box(
        modifier = modifier.fillMaxWidth().aspectRatio(bitmapState?.let { it.width.toFloat() / it.height.toFloat() } ?: 0.707f)
            .padding(vertical = 8.dp, horizontal = 16.dp).shadow(1.dp, shape = RoundedCornerShape(2.dp))
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = bitmapState
        if (bitmap != null) {
            val dimMatrix = floatArrayOf(
                0.85f, 0f, 0f, 0f, 0f,
                0f, 0.85f, 0f, 0f, 0f,
                0f, 0f, 0.85f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                colorFilter = if (isDarkMode) androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(dimMatrix)) else null
            )
        } else {
            CircularProgressIndicator(color = AccentPeach, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun HtmlDocumentViewer(
    htmlContent: String,
    isDarkMode: Boolean,
    isImmersive: Boolean,
    contentPaddingTop: Dp,
    onSetImmersive: (Boolean) -> Unit,
    searchState: SearchState,
    initialScrollY: Int = 0,
    onScrollChange: (Int) -> Unit = {}
) {
    val themeClass = if (isDarkMode) "dark-theme" else "light-theme"
    val styledHtml = remember(htmlContent, themeClass) {
        htmlContent.replaceFirst("<html", "<html class=\"$themeClass\"")
    }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val query by searchState.query.collectAsStateWithLifecycle()
    val isActive by searchState.isActive.collectAsStateWithLifecycle()
    val triggerNext by searchState.triggerFindNext.collectAsStateWithLifecycle()
    val triggerPrev by searchState.triggerFindPrev.collectAsStateWithLifecycle()

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                overScrollMode = android.view.View.OVER_SCROLL_NEVER
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (initialScrollY > 0) {
                            view?.scrollTo(0, initialScrollY)
                        }
                    }
                    
                    override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                        val url = request?.url ?: return false
                        if (url.scheme in listOf("http", "https")) {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, url))
                                return true
                            } catch (e: Exception) {}
                        }
                        return false
                    }

                    override fun shouldInterceptRequest(view: WebView?, request: android.webkit.WebResourceRequest?): android.webkit.WebResourceResponse? {
                        val url = request?.url ?: return null
                        if (url.scheme == "doc-asset") {
                            val fileName = url.lastPathSegment ?: return null
                            val cacheFile = File(context.cacheDir, "docx_media/$fileName")
                            if (cacheFile.exists()) {
                                try {
                                    val stream = FileInputStream(cacheFile)
                                    val mimeType = when (fileName.substringAfterLast('.', "").lowercase()) {
                                        "png" -> "image/png"
                                        "jpg", "jpeg" -> "image/jpeg"
                                        "gif" -> "image/gif"
                                        "webp" -> "image/webp"
                                        else -> "image/*"
                                    }
                                    return android.webkit.WebResourceResponse(mimeType, "UTF-8", stream)
                                } catch (e: Exception) { e.printStackTrace() }
                            }
                        }
                        return super.shouldInterceptRequest(view, request)
                    }
                }
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                settings.apply {
                    javaScriptEnabled = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    builtInZoomControls = true
                    displayZoomControls = false
                    setSupportZoom(true)
                }
                setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                    onScrollChange(scrollY)
                    if (!isActive) {
                        val diff = scrollY - oldScrollY
                        if (diff > 15) onSetImmersive(true)
                        else if (diff < -15) onSetImmersive(false)
                    }
                }
                setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
                    if (isDoneCounting) {
                        searchState.totalMatches.value = numberOfMatches
                        searchState.activeMatch.value = if (numberOfMatches > 0) activeMatchOrdinal + 1 else 0
                    }
                }
                tag = styledHtml
                loadDataWithBaseURL("doc-asset://", styledHtml, "text/html", "UTF-8", null)
                webViewRef = this
            }
        },
        update = { webView ->
            if (webView.tag != styledHtml) {
                webView.tag = styledHtml
                webView.loadDataWithBaseURL("doc-asset://", styledHtml, "text/html", "UTF-8", null)
                if (isActive && query.isNotEmpty()) {
                    webView.findAllAsync(query)
                }
            }
            webViewRef = webView
        },
        modifier = Modifier.fillMaxSize()
    )

    LaunchedEffect(query, isActive, webViewRef) {
        val webView = webViewRef ?: return@LaunchedEffect
        if (isActive && query.isNotEmpty()) {
            webView.findAllAsync(query)
        } else {
            webView.clearMatches()
            searchState.activeMatch.value = 0
            searchState.totalMatches.value = 0
        }
    }

    LaunchedEffect(triggerNext, webViewRef) {
        val webView = webViewRef ?: return@LaunchedEffect
        if (triggerNext > 0 && isActive) webView.findNext(true)
    }

    LaunchedEffect(triggerPrev, webViewRef) {
        val webView = webViewRef ?: return@LaunchedEffect
        if (triggerPrev > 0 && isActive) webView.findNext(false)
    }
}

@Composable
fun ZoomableContainer(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val coroutineScope = rememberCoroutineScope()

    val transformableState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        if (scale > 1f) {
            val maxOffsetX = (containerSize.width * scale - containerSize.width) / 2f
            val maxOffsetY = (containerSize.height * scale - containerSize.height) / 2f
            offset = Offset(
                x = (offset.x + offsetChange.x * scale).coerceIn(-maxOffsetX, maxOffsetX),
                y = (offset.y + offsetChange.y * scale).coerceIn(-maxOffsetY, maxOffsetY)
            )
        } else {
            offset = Offset.Zero
        }
    }

    Box(
        modifier = modifier.onSizeChanged { containerSize = it }.pointerInput(Unit) {
            detectTapGestures(onDoubleTap = { tapOffset ->
                val startScale = scale
                val targetScale = if (scale > 1f) 1f else 2.5f
                val startOffset = offset
                val targetOffset = if (targetScale > 1f) {
                    val centerX = containerSize.width / 2f
                    val centerY = containerSize.height / 2f
                    val maxOffsetX = (containerSize.width * targetScale - containerSize.width) / 2f
                    val maxOffsetY = (containerSize.height * targetScale - containerSize.height) / 2f
                    Offset(
                        x = ((centerX - tapOffset.x) * (targetScale - 1f)).coerceIn(-maxOffsetX, maxOffsetX),
                        y = ((centerY - tapOffset.y) * (targetScale - 1f)).coerceIn(-maxOffsetY, maxOffsetY)
                    )
                } else {
                    Offset.Zero
                }
                coroutineScope.launch {
                    animate(0f, 1f, animationSpec = tween(280, easing = FastOutSlowInEasing)) { fraction, _ ->
                        scale = startScale + (targetScale - startScale) * fraction
                        offset = startOffset + (targetOffset - startOffset) * fraction
                    }
                }
            })
        }.transformable(state = transformableState).pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (scale > 1f && event.changes.size == 1) {
                        val change = event.changes.first()
                        if (change.pressed) {
                            val dragAmount = change.position - change.previousPosition
                            if (dragAmount != Offset.Zero) {
                                change.consume()
                                val maxOffsetX = (containerSize.width * scale - containerSize.width) / 2f
                                val maxOffsetY = (containerSize.height * scale - containerSize.height) / 2f
                                offset = Offset(
                                    x = (offset.x + dragAmount.x).coerceIn(-maxOffsetX, maxOffsetX),
                                    y = (offset.y + dragAmount.y).coerceIn(-maxOffsetY, maxOffsetY)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)) {
            content()
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// UI COMPONENTS
// ═══════════════════════════════════════════════════════════════

@Composable
fun LiteInteractive(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp? = null,
    containerColor: Color = AccentPeach,
    contentColor: Color = OnAccentPeach,
    isOutlined: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
    )
    val cornerRadius by animateDpAsState(
        targetValue = if (isPressed) 12.dp else 4.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
    )

    val appColors = LocalAppColors.current
    Box(
        modifier = modifier.then(if (size != null) Modifier.size(size) else Modifier).graphicsLayer {
            scaleX = scale
            scaleY = scale
            shape = RoundedCornerShape(cornerRadius)
            clip = true
        }.background(if (isOutlined) Color.Transparent else containerColor).drawBehind {
            if (isOutlined) {
                val strokeWidthPx = 1.dp.toPx()
                drawRect(color = appColors.border, style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidthPx))
            }
        }.clickable(interactionSource = interactionSource, indication = null, onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides if (isOutlined) appColors.text else contentColor) {
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                content()
            }
        }
    }
}

@Composable
fun LiteIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.88f else 1f, animationSpec = spring(stiffness = Spring.StiffnessLow))
    val cornerRadius by animateDpAsState(if (isPressed) 12.dp else 22.dp, animationSpec = spring(stiffness = Spring.StiffnessLow))
    val appColors = LocalAppColors.current
    val animatedBgColor by animateColorAsState(
        targetValue = if (isPressed) AccentPeach else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessLow)
    )
    val animatedContentColor by animateColorAsState(
        targetValue = if (!enabled) appColors.text.copy(alpha = 0.3f) else if (isPressed) OnAccentPeach else appColors.text,
        animationSpec = spring(stiffness = Spring.StiffnessMedium)
    )

    Box(
        modifier = modifier.size(44.dp).graphicsLayer { scaleX = scale; scaleY = scale; shape = RoundedCornerShape(cornerRadius); clip = true }
            .background(animatedBgColor).clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides animatedContentColor) {
            content()
        }
    }
}

@Composable
fun LiteFloatingActionButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.9f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    val cornerRadius by animateDpAsState(if (isPressed) 14.dp else 24.dp, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))

    Box(
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale; shape = RoundedCornerShape(cornerRadius); clip = true }
            .background(AccentPeach).clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides OnAccentPeach) {
            content()
        }
    }
}

@Composable
fun ViewerTopBar(
    fileName: String,
    mimeType: String,
    onBackClick: () -> Unit,
    onOpenWithClick: () -> Unit,
    onInfoClick: () -> Unit,
    isDarkMode: Boolean,
    onThemeToggle: () -> Unit
) {
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth().background(appColors.bg).statusBarsPadding().padding(horizontal = 4.dp, vertical = 8.dp)
            .drawBehind {
                val strokeWidthPx = 1.dp.toPx()
                drawLine(color = appColors.border, start = Offset(0f, size.height - strokeWidthPx / 2), end = Offset(size.width, size.height - strokeWidthPx / 2), strokeWidth = strokeWidthPx)
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        LiteIconButton(onClick = onBackClick) {
            Image(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(LocalContentColor.current))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f).clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onInfoClick
            ).padding(vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(fileName, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = appColors.text), modifier = Modifier.weight(1f, fill = false))
                Spacer(modifier = Modifier.width(4.dp))
                Image(Icons.Default.KeyboardArrowDown, contentDescription = "View Info", modifier = Modifier.size(16.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(appColors.subText))
            }
            BasicText("Local View • Read Only", maxLines = 1, style = TextStyle(fontSize = 11.sp, color = appColors.text.copy(alpha = 0.55f), fontWeight = FontWeight.Normal))
        }
        LiteIconButton(onClick = onThemeToggle) {
            Image(if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode, contentDescription = "Theme", colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(LocalContentColor.current))
        }
        LiteIconButton(onClick = onOpenWithClick) {
            Image(Icons.Default.Share, contentDescription = "Share", colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(LocalContentColor.current))
        }
    }
}

@Composable
fun BottomSearchBanner(searchState: SearchState, onSetImmersive: (Boolean) -> Unit) {
    val appColors = LocalAppColors.current
    val focusRequester = remember { FocusRequester() }
    val query by searchState.query.collectAsStateWithLifecycle()
    val isActive by searchState.isActive.collectAsStateWithLifecycle()
    val activeMatch by searchState.activeMatch.collectAsStateWithLifecycle()
    val totalMatches by searchState.totalMatches.collectAsStateWithLifecycle()

    LaunchedEffect(isActive) {
        if (isActive) focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier.fillMaxWidth().background(appColors.surface).drawBehind {
            drawLine(color = appColors.border, start = Offset(0f, 0f), end = Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
        }.navigationBarsPadding().imePadding()
    ) {
        Crossfade(targetState = isActive, label = "search_mode") { active ->
            if (active) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier.weight(1f).height(40.dp).background(appColors.bg, shape = RoundedCornerShape(8.dp))
                            .border(1.dp, appColors.border, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (query.isEmpty()) {
                            BasicText("Search in document...", style = TextStyle(fontSize = 14.sp, color = appColors.text.copy(alpha = 0.4f)))
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { searchState.query.value = it },
                            textStyle = TextStyle(fontSize = 14.sp, color = appColors.text),
                            singleLine = true,
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(appColors.accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { if (totalMatches > 0) searchState.triggerFindNext.value++ }),
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    if (totalMatches > 0) {
                        BasicText("$activeMatch / $totalMatches", style = TextStyle(fontSize = 13.sp, color = appColors.text.copy(alpha = 0.7f), fontWeight = FontWeight.Medium), modifier = Modifier.padding(horizontal = 4.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                    } else if (query.isNotEmpty()) {
                        BasicText("0 / 0", style = TextStyle(fontSize = 13.sp, color = appColors.text.copy(alpha = 0.4f)), modifier = Modifier.padding(horizontal = 4.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LiteIconButton(onClick = { searchState.triggerFindPrev.value++ }, enabled = totalMatches > 0, modifier = Modifier.size(36.dp)) {
                            Image(Icons.Default.KeyboardArrowUp, contentDescription = "Prev", colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(if (totalMatches > 0) appColors.text else appColors.text.copy(alpha = 0.3f)))
                        }
                        LiteIconButton(onClick = { searchState.triggerFindNext.value++ }, enabled = totalMatches > 0, modifier = Modifier.size(36.dp)) {
                            Image(Icons.Default.KeyboardArrowDown, contentDescription = "Next", colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(if (totalMatches > 0) appColors.text else appColors.text.copy(alpha = 0.3f)))
                        }
                        LiteIconButton(onClick = { searchState.query.value = ""; searchState.isActive.value = false }, modifier = Modifier.size(36.dp)) {
                            Image(Icons.Default.Close, contentDescription = "Close", colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(appColors.text))
                        }
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText("⚡", style = TextStyle(fontSize = 14.sp, color = AccentPeach))
                        Spacer(modifier = Modifier.width(6.dp))
                        BasicText("Preview Mode", style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = appColors.text.copy(alpha = 0.61f), letterSpacing = 0.2.sp))
                    }
                    LiteIconButton(onClick = { searchState.isActive.value = true; onSetImmersive(false) }) {
                        Image(Icons.Default.Search, contentDescription = "Search", colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(appColors.text))
                    }
                }
            }
        }
    }
}

@Composable
fun WelcomeDashboard(onFilePick: (Uri) -> Unit) {
    val context = LocalContext.current
    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { onFilePick(it) }
    }
    val appColors = LocalAppColors.current
    val recentFiles = remember { RecentFilesManager.getRecentFiles(context).take(3) }

    Box(modifier = Modifier.fillMaxSize().background(appColors.bg)) {
        Column(
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 40.dp, end = 40.dp, bottom = 80.dp),
            horizontalAlignment = Alignment.Start
        ) {
            BasicText("Simply", style = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.Light, color = appColors.text))
            BasicText("open", style = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.Light, color = appColors.text))
            BasicText("a file.", style = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.Light, color = appColors.accent))
            
            if (recentFiles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(48.dp))
                BasicText("Continue reading", style = TextStyle(fontSize = 12.sp, color = appColors.subText, fontWeight = FontWeight.Medium), modifier = Modifier.padding(start = 4.dp))
                Spacer(modifier = Modifier.height(12.dp))
                
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    recentFiles.forEach { file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onFilePick(file.uri) }
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BasicText("→", style = TextStyle(fontSize = 15.sp, color = appColors.accent))
                            Spacer(modifier = Modifier.width(16.dp))
                            BasicText(
                                file.name, 
                                maxLines = 1, 
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, 
                                style = TextStyle(fontSize = 15.sp, color = appColors.text, fontWeight = FontWeight.Light, letterSpacing = 0.5.sp)
                            )
                        }
                    }
                }
            }
        }

        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(if (isPressed) 0.9f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        val cornerRadius by animateDpAsState(if (isPressed) 28.dp else 48.dp, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        
        Box(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 32.dp, bottom = 48.dp).size(80.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale; shape = RoundedCornerShape(cornerRadius); clip = true }
                .background(appColors.accent)
                .clickable(interactionSource = interactionSource, indication = null, onClick = {
                    pickerLauncher.launch(arrayOf("application/pdf", "text/*", "application/json", "application/xml",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                }),
            contentAlignment = Alignment.Center
        ) {
            Image(Icons.Default.Add, contentDescription = "Open File", colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(appColors.onAccent), modifier = Modifier.size(36.dp))
        }
    }
}

@Composable
fun ErrorStateView(
    message: String,
    onRetryClick: () -> Unit,
    onBackClick: () -> Unit,
    onOpenWithAlternative: () -> Unit
) {
    val appColors = LocalAppColors.current
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(Icons.Default.Warning, contentDescription = "Alert", colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFFFFA07A)), modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(16.dp))
        BasicText("Unable to View Document", style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = appColors.text))
        Spacer(modifier = Modifier.height(8.dp))
        BasicText(message, style = TextStyle(fontSize = 14.sp, color = appColors.text.copy(alpha = 0.6f), textAlign = TextAlign.Center), modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(modifier = Modifier.height(32.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LiteInteractive(onClick = onBackClick, modifier = Modifier.weight(1f), isOutlined = true) { BasicText("Back") }
            LiteInteractive(onClick = onRetryClick, modifier = Modifier.weight(1f), isOutlined = true) { BasicText("Retry") }
            LiteInteractive(onClick = onOpenWithAlternative, containerColor = Color(0xFF552222), contentColor = Color.White, modifier = Modifier.weight(1.5f)) {
                BasicText("System App", style = TextStyle(textAlign = TextAlign.Center, fontSize = 12.sp))
            }
        }
    }
}