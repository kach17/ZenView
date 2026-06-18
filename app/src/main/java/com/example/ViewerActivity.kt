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
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.app.Activity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.lang.StringBuilder
import java.util.*
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import kotlin.collections.ArrayList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppColors(
    val bg: Color,
    val text: Color,
    val subText: Color,
    val border: Color,
    val surface: Color,
    val accent: Color = com.example.ui.theme.AccentPeach,
    val onAccent: Color = com.example.ui.theme.OnAccentPeach
)

val LocalAppColors = staticCompositionLocalOf {
    AppColors(
        bg = com.example.ui.theme.CleanBgDark,
        text = com.example.ui.theme.CleanTextDark,
        subText = com.example.ui.theme.CleanSubTextDark,
        border = com.example.ui.theme.BorderDark,
        surface = com.example.ui.theme.CleanSurfaceDark
    )
}

enum class FileType {
    PDF {
        override suspend fun render(context: Context, uri: Uri): ViewState {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw Exception("Failed to open file descriptor")
            val renderer = PdfRenderer(pfd)
            return ViewState.PdfSuccess(renderer, pfd, renderer.pageCount)
        }
    },
    TXT { override suspend fun render(context: Context, uri: Uri) = ViewState.TextSuccess(DocumentParser.readPlainTxt(context, uri), isJson = false) },
    JSON { override suspend fun render(context: Context, uri: Uri) = ViewState.TextSuccess(DocumentParser.prettyPrintJson(DocumentParser.readPlainTxt(context, uri)), isJson = true) },
    CSV { override suspend fun render(context: Context, uri: Uri) = ViewState.HtmlSuccess(DocumentParser.parseCsvToHtml(context, uri)) },
    DOCX { override suspend fun render(context: Context, uri: Uri) = ViewState.HtmlSuccess(DocumentParser.parseDocxToHtml(context, uri)) },
    XLSX { override suspend fun render(context: Context, uri: Uri) = ViewState.HtmlSuccess(DocumentParser.parseWorksheetToHtml(context, uri, DocumentParser.loadSharedStrings(context, uri))) },
    MD { override suspend fun render(context: Context, uri: Uri) = ViewState.HtmlSuccess(DocumentParser.parseMarkdownToHtml(DocumentParser.readPlainTxt(context, uri))) },
    XML { override suspend fun render(context: Context, uri: Uri) = ViewState.TextSuccess(DocumentParser.readPlainTxt(context, uri), isJson = true) },
    HTML_SYS { override suspend fun render(context: Context, uri: Uri) = ViewState.HtmlSuccess(DocumentParser.readPlainTxt(context, uri)) },
    LOG { override suspend fun render(context: Context, uri: Uri) = ViewState.TextSuccess(DocumentParser.readPlainTxt(context, uri), isJson = true) },
    UNKNOWN { override suspend fun render(context: Context, uri: Uri) = ViewState.TextSuccess(DocumentParser.readPlainTxt(context, uri), isJson = false) };

    abstract suspend fun render(context: Context, uri: Uri): ViewState
}

sealed class ViewState {
    object Idle : ViewState()
    object Loading : ViewState()
    data class PdfSuccess(val renderer: PdfRenderer, val pfd: ParcelFileDescriptor, val pageCount: Int) : ViewState()
    data class TextSuccess(val content: String, val isJson: Boolean = false) : ViewState()
    data class HtmlSuccess(val htmlContent: String) : ViewState()
    data class Error(val message: String) : ViewState()
}

data class DocumentContext(
    val uri: Uri,
    val name: String,
    val type: FileType
)

sealed interface UiState {
    object Idle : UiState
    object Loading : UiState
    data class RenderTarget(
        val docContext: DocumentContext,
        val viewState: ViewState
    ) : UiState
    data class Error(val message: String, val docContext: DocumentContext? = null) : UiState
}

class ViewerViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState = _uiState.asStateFlow()

    fun handleIntent(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        var targetUri: Uri? = null

        if (Intent.ACTION_VIEW == action) {
            targetUri = intent.data
        } else if (Intent.ACTION_SEND == action) {
            @Suppress("DEPRECATION")
            targetUri = intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }
        targetUri?.let { loadFile(context, it) }
    }

    fun loadFile(context: Context, uri: Uri) {
        closeActiveResources()
        
        val resolvedName = DocumentParser.getFileName(context.contentResolver, uri)
        val resolvedType = DocumentParser.getFileType(context, uri, resolvedName)
        val docContext = DocumentContext(uri, resolvedName, resolvedType)

        _uiState.value = UiState.RenderTarget(docContext, ViewState.Loading)

        viewModelScope.launch {
            try {
                val newState = withContext(Dispatchers.IO) {
                    resolvedType.render(context, uri)
                }
                _uiState.value = UiState.RenderTarget(docContext, newState)
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = UiState.Error(e.localizedMessage ?: "An error occurred while loading this document.", docContext)
            }
        }
    }

    fun resetToIdle() {
        closeActiveResources()
        _uiState.value = UiState.Idle
    }

    fun closeActiveResources() {
        val current = _uiState.value
        if (current is UiState.RenderTarget && current.viewState is ViewState.PdfSuccess) {
            try { current.viewState.renderer.close() } catch (e: Exception) {}
            try { current.viewState.pfd.close() } catch (e: Exception) {}
        }
    }

    override fun onCleared() {
        super.onCleared()
        closeActiveResources()
    }
}

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
            
            // Handle incoming trigger intents
            val currentIntent by activityIntent
            LaunchedEffect(currentIntent) {
                currentIntent?.let {
                    viewModel.handleIntent(context, it)
                    activityIntent.value = null
                    try {
                        setIntent(null)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
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
                if (isDarkMode) {
                    AppColors(
                        bg = com.example.ui.theme.CleanBgDark,
                        text = com.example.ui.theme.CleanTextDark,
                        subText = com.example.ui.theme.CleanSubTextDark,
                        border = com.example.ui.theme.BorderDark,
                        surface = com.example.ui.theme.CleanSurfaceDark
                    )
                } else {
                    AppColors(
                        bg = com.example.ui.theme.CleanBgLight,
                        text = com.example.ui.theme.CleanTextLight,
                        subText = com.example.ui.theme.CleanSubTextLight,
                        border = com.example.ui.theme.BorderLight,
                        surface = com.example.ui.theme.CleanSurfaceLight
                    )
                }
            }

            MyApplicationTheme(darkTheme = isDarkMode) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.foundation.LocalOverscrollConfiguration provides null,
                    LocalAppColors provides appColors
                ) {
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                    val searchState = remember(uiState) { SearchState() }

                    // Intercept system back button/gesture to go back to dashboard
                    androidx.activity.compose.BackHandler(enabled = uiState !is UiState.Idle) {
                        viewModel.resetToIdle()
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(LocalAppColors.current.bg)
                    ) {
                        when (val state = uiState) {
                            is UiState.Idle -> WelcomeDashboard(onFilePick = { uri -> viewModel.loadFile(context, uri) })
                            is UiState.Loading -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(color = com.example.ui.theme.AccentPeach, strokeWidth = 4.dp)
                                        Spacer(modifier = Modifier.height(16.dp))
                                        androidx.compose.foundation.text.BasicText(
                                            text = "Parsing document...",
                                            style = androidx.compose.ui.text.TextStyle(
                                                fontSize = 14.sp,
                                                color = LocalAppColors.current.text.copy(alpha=0.7f)
                                            )
                                        )
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
                                    viewState = state.viewState,
                                    docContext = state.docContext,
                                    isImmersive = isImmersive,
                                    isDarkMode = isDarkMode,
                                    contentPaddingTop = topContentPadding,
                                    onSetImmersive = { isImmersive = it }, searchState = searchState
                                )
                            }
                            is UiState.Error -> {
                                ErrorStateView(
                                    message = state.message,
                                    onRetryClick = { state.docContext?.let { viewModel.loadFile(context, it.uri) } },
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
                                    enter = androidx.compose.animation.slideInVertically { -it } + fadeIn(),
                                    exit = androidx.compose.animation.slideOutVertically { -it } + fadeOut(),
                                    modifier = Modifier.align(Alignment.TopCenter)
                                ) {
                                    val docContext = when (uiState) {
                                        is UiState.RenderTarget -> (uiState as UiState.RenderTarget).docContext
                                        is UiState.Error -> (uiState as UiState.Error).docContext
                                        else -> null
                                    }
                                    val currentMimeType = if (docContext != null) {
                                        DocumentParser.getMimeType(context, docContext.uri, docContext.name)
                                    } else {
                                        ""
                                    }
                                    ViewerTopBar(
                                        fileName = docContext?.name ?: "Error",
                                        mimeType = currentMimeType,
                                        onBackClick = { viewModel.resetToIdle() },
                                        onOpenWithClick = {
                                            docContext?.let {
                                                val mimeType = DocumentParser.getMimeType(context, it.uri, it.name)
                                                DocumentParser.openWithSystemApp(context, it.uri, mimeType)
                                            } ?: Toast.makeText(context, "No document loaded to share", Toast.LENGTH_SHORT).show()
                                        },
                                        isDarkMode = isDarkMode,
                                        onThemeToggle = { isDarkMode = !isDarkMode }
                                    )
                                }

                                AnimatedVisibility(
                                    visible = (!isImmersive || searchState.isActive) && uiState is UiState.RenderTarget &&
                                              (uiState as UiState.RenderTarget).viewState is ViewState.HtmlSuccess,
                                    enter = androidx.compose.animation.slideInVertically { it } + fadeIn(),
                                    exit = androidx.compose.animation.slideOutVertically { it } + fadeOut(),
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                ) {
                                    BottomFormatBanner(searchState, onSetImmersive = { isImmersive = it })
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

object DocumentParser {

    // --- HTML Templates ---
    object HtmlTemplates {
        private const val BASE_STYLE = """
            :root {
                /* fallback to light theme if no class */
                --bg-color: #F4F5F8;
                --surface-color: #FFFFFF;
                --text-color: #1A1C20;
                --subtext-color: #646975;
                --accent-color: #F7DACF;
                --accent-hover: rgba(247, 218, 207, 0.25);
                --border-color: #E2E4EB;
                --row-alt: #EAEDF3;
            }

            html.dark-theme :root, html.dark-theme {
                --bg-color: #0C0D0F;
                --surface-color: #14161B;
                --text-color: #E6E8ED;
                --subtext-color: #8C919D;
                --accent-color: #F7DACF;
                --accent-hover: rgba(247, 218, 207, 0.1);
                --border-color: #20232B;
                --row-alt: #17181D;
            }

            html.light-theme :root, html.light-theme {
                --bg-color: #F4F5F8;
                --surface-color: #FFFFFF;
                --text-color: #1A1C20;
                --subtext-color: #646975;
                --accent-color: #F7DACF;
                --accent-hover: rgba(247, 218, 207, 0.25);
                --border-color: #E2E4EB;
                --row-alt: #EAEDF3;
            }

            html {
                overscroll-behavior: auto;
            }

            body {
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                margin: 0;
                padding: 96px 16px 96px 16px;
                color: var(--text-color);
                background-color: var(--bg-color);
                line-height: 1.6;
                display: flex;
                justify-content: center;
                transition: background-color 0.3s ease, color 0.3s ease;
                box-sizing: border-box;
            }
            .paper {
                background-color: var(--surface-color);
                box-shadow: 0 1px 3px rgba(0,0,0,0.3);
                border-radius: 4px;
                padding: 24px;
                width: 100%;
                max-width: 800px;
                box-sizing: border-box;
                transition: background-color 0.3s ease;
            }
            .scroll-container {
                overflow-x: auto;
                width: 100%;
                background: var(--surface-color);
                border: 1px solid var(--border-color);
                border-radius: 4px;
                box-shadow: 0 1px 3px rgba(0,0,0,0.3);
                box-sizing: border-box;
                max-width: 100%;
            }

            /* Interactive Elements */
            a {
                color: var(--accent-color);
                text-decoration: none;
                transition: background-color 0.2s ease, outline 0.2s ease;
                border-radius: 2px;
            }
            a:hover {
                background-color: var(--accent-hover);
            }
            a:focus-visible {
                outline: 2px solid var(--accent-color);
                outline-offset: 2px;
            }

            /* Responsive table wrapper for standard doc tables */
            .table-wrapper {
                overflow-x: auto;
                margin: 16px 0;
                border: 1px solid var(--border-color);
                border-radius: 6px;
                background-color: var(--surface-color);
                -webkit-overflow-scrolling: touch;
            }
            .table-wrapper table {
                margin: 0;
                border-collapse: collapse;
                width: 100%;
                min-width: 500px;
            }
            table p {
                margin: 2px 0;
            }

            /* Smart Custom Page Breaks */
            .page-break {
                margin: 32px 0;
                position: relative;
                text-align: center;
            }
            .page-break-line {
                border: none;
                border-top: 2px dashed var(--border-color);
                margin: 0;
            }
            .page-break-text {
                display: inline-block;
                position: absolute;
                top: -10px;
                left: 50%;
                transform: translateX(-50%);
                background-color: var(--surface-color);
                padding: 0 12px;
                font-size: 11px;
                font-weight: 600;
                text-transform: uppercase;
                letter-spacing: 1px;
                color: var(--subtext-color);
            }

            /* Responsive and Custom Indent List Items */
            .list-item {
                display: block;
                margin-top: 4px;
                margin-bottom: 8px;
                padding-left: calc(24px * var(--level, 1));
                position: relative;
            }
            .list-item::before {
                content: "•";
                color: var(--accent-color);
                font-weight: bold;
                font-size: 1.25em;
                position: absolute;
                left: calc(24px * var(--level, 1) - 16px);
                top: -2px;
            }

            /* Ultra-clean Image Styling */
            .doc-image-container {
                display: block;
                text-align: center;
                margin: 24px auto;
                width: 100%;
                max-width: 100%;
            }
            .doc-image {
                max-width: 100%;
                height: auto;
                border-radius: 8px;
                box-shadow: 0 4px 12px rgba(0,0,0,0.15);
            }
        """

        private const val DOCUMENT_STYLE = """
            h1, h2, h3, h4, h5, h6 { margin-top: 24px; margin-bottom: 16px; color: var(--text-color); }
            p { margin-bottom: 16px; }
            code { background-color: var(--row-alt); padding: 2px 4px; border-radius: 4px; font-family: monospace; }
            pre { background-color: var(--row-alt); padding: 12px; border-radius: 4px; overflow-x: auto; }
            pre code { padding: 0; }
            blockquote { border-left: 4px solid var(--accent-color); margin: 0; padding-left: 16px; color: var(--subtext-color); }
            table { border-collapse: collapse; width: 100%; margin-bottom: 16px; }
            th, td { border: 1px solid var(--border-color); padding: 8px 12px; text-align: left; transition: background-color 0.2s ease; }
            th { background-color: var(--bg-color); font-weight: 600; color: var(--accent-color); }
            
            td:hover, th:hover {
                background-color: var(--accent-hover);
            }
            td:focus-visible, th:focus-visible {
                outline: 2px solid var(--accent-color);
                outline-offset: -2px;
            }
        """

        private const val TABLE_STYLE = """
            table { border-collapse: collapse; width: 100%; min-width: 600px; }
            th, td { border: 1px solid var(--border-color); padding: 8px 12px; text-align: left; font-size: 14px; transition: background-color 0.2s ease; }
            th { background-color: var(--bg-color); font-weight: 600; color: var(--accent-color); position: sticky; top: 0; z-index: 10; }
            tr:nth-child(even) { background-color: var(--row-alt); }
            td.row-header { background-color: var(--bg-color); font-weight: 600; color: var(--accent-color); position: sticky; left: 0; text-align: center; width: 40px; z-index: 20; }
            
            td:hover, th:hover {
                background-color: var(--accent-hover);
            }
            td:focus-visible, th:focus-visible {
                outline: 2px solid var(--accent-color);
                outline-offset: -2px;
            }
        """

        fun buildHtml(bodyContent: String, injectTableCss: Boolean = false): String {
            val styleExt = if (injectTableCss) TABLE_STYLE else DOCUMENT_STYLE
            val wrapperStart = if (injectTableCss) "<div class=\"scroll-container\">\n<table>\n" else "<div class=\"paper\">\n"
            val wrapperEnd = if (injectTableCss) "</table>\n</div>" else "</div>"

            val viewportMeta = if (injectTableCss || bodyContent.contains("<table")) {
                "<meta name=\"viewport\" content=\"minimum-scale=0.1, maximum-scale=5.0\" />"
            } else {
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, minimum-scale=0.5, maximum-scale=5.0\" />"
            }

            return """
            <!DOCTYPE html>
            <html>
            <head>
                $viewportMeta
                <style>
                    $BASE_STYLE
                    $styleExt
                </style>
            </head>
            <body${if (injectTableCss) " style=\"display: block; padding-top: 96px; padding-bottom: 96px;\"" else ""}>
                $wrapperStart$bodyContent$wrapperEnd
            </body>
            </html>
            """.trimIndent()
        }
    }

    // --- Parser Helpers ---

    internal fun getFileName(resolver: ContentResolver, uri: Uri): String {
        var name = ""
        if (uri.scheme == "content") {
            val cursor = resolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        name = cursor.getString(index)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                cursor?.close()
            }
        }
        if (name.isEmpty()) {
            name = uri.path ?: ""
            val cut = name.lastIndexOf('/')
            if (cut != -1) {
                name = name.substring(cut + 1)
            }
        }
        return if (name.isEmpty()) "Document" else name
    }

    internal fun getFileType(context: Context, uri: Uri, fileName: String): FileType {
        val mimeType = context.contentResolver.getType(uri)?.lowercase() ?: ""
        val extension = fileName.substringAfterLast('.', "").lowercase()

        return when {
            mimeType == "application/pdf" || extension == "pdf" -> FileType.PDF
            mimeType == "text/plain" || extension == "txt" -> FileType.TXT
            mimeType == "text/csv" || mimeType == "text/comma-separated-values" || extension == "csv" -> FileType.CSV
            mimeType == "application/json" || extension == "json" -> FileType.JSON
            mimeType == "text/markdown" || extension == "md" || extension == "markdown" -> FileType.MD
            mimeType == "text/xml" || mimeType == "application/xml" || extension == "xml" -> FileType.XML
            mimeType == "text/html" || extension == "html" || extension == "htm" -> FileType.HTML_SYS
            extension == "log" -> FileType.LOG
            mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" || extension == "docx" -> FileType.DOCX
            mimeType == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" || extension == "xlsx" -> FileType.XLSX
            else -> FileType.UNKNOWN
        }
    }

    internal fun getMimeType(context: Context, uri: Uri, fileName: String): String {
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

    internal fun openWithSystemApp(context: Context, uri: Uri, mimeType: String) {
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

    internal fun parseMarkdownToHtml(markdown: String): String {
        var html = markdown
            // Escapes to avoid regex issues (basic protection)
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        // Page Breaks / Horizontal rules (---, ***, ___) before appending `<br>` and standard list conversions
        html = html.replace(Regex("(?m)^([-*_\\s]){3,}\\s*$"), "<div class=\"page-break\"><hr class=\"page-break-line\"/><div class=\"page-break-text\">Page Break</div></div>")

        // Bold
        html = html.replace(Regex("\\*\\*(.*?)\\*\\*"), "<strong>$1</strong>")
        
        // Headers
        html = html.replace(Regex("(?m)^# (.*?)$"), "<h1>$1</h1>")
        html = html.replace(Regex("(?m)^## (.*?)$"), "<h2>$1</h2>")
        html = html.replace(Regex("(?m)^### (.*?)$"), "<h3>$1</h3>")
        
        // Links
        html = html.replace(Regex("\\[(.*?)\\]\\((.*?)\\)"), "<a href=\"$2\" style=\"color:var(--accent-color);\">$1</a>")
        
        // Inline Code
        html = html.replace(Regex("`([^`]+)`"), "<code>$1</code>")
        
        // Blockquotes
        html = html.replace(Regex("(?m)^> (.*?)$"), "<blockquote>$1</blockquote>")
        
        // Basic list items (converts to modern custom list item blocks)
        html = html.replace(Regex("(?m)^[*-] (.*?)$"), "<div class=\"list-item\" style=\"--level: 1;\">$1</div>")

        // Newlines to <br> for regular text breaks (skipping list item blocks or divisions so style isn't disrupted)
        html = html.replace(Regex("(?m)$"), "<br>")
        html = html.replace("<br><br>", "<br>")
        
        return HtmlTemplates.buildHtml(html)
    }

    internal fun readPlainTxt(context: Context, uri: Uri): String {
        val stream = context.contentResolver.openInputStream(uri) ?: throw Exception("Failed to open file stream")
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
        if (line != null) {
            builder.append("\n⚡ [Preview limited to $MAX_LINES lines for peak speed]")
        }
        return builder.toString()
    }

    internal fun prettyPrintJson(rawJson: String): String {
        try {
            val json = rawJson.trim()
            if (json.startsWith("{")) {
                val jsonObject = org.json.JSONObject(json)
                return jsonObject.toString(4)
            } else if (json.startsWith("[")) {
                val jsonArray = org.json.JSONArray(json)
                return jsonArray.toString(4)
            }
        } catch (e: Exception) {
            // Raw return if format fails
        }
        return rawJson
    }

    internal fun parseCsvToHtml(context: Context, uri: Uri): String {
        val stream = context.contentResolver.openInputStream(uri) ?: throw Exception("Failed to open file stream")
        val reader = BufferedReader(InputStreamReader(stream, "UTF-8"))
        val rows = StringBuilder()
        rows.append("<tbody>")

        var line = reader.readLine()
        var isHeader = true
        var rowCount = 0
        val MAX_ROWS = 1500

        while (line != null && rowCount < MAX_ROWS) {
            val columns = parseCsvFields(line)
            rows.append("<tr>")
            for (col in columns) {
                val escaped = col.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                if (isHeader) {
                    rows.append("<th>$escaped</th>")
                } else {
                    rows.append("<td>$escaped</td>")
                }
            }
            rows.append("</tr>")
            isHeader = false
            rowCount++
            line = reader.readLine()
        }

        rows.append("</tbody>")
        if (line != null) {
            rows.append("<div style='margin-top: 10px; font-size: 11px; color: var(--subtext-color); text-align: center; font-family: sans-serif;'>⚡ Preview limited to $MAX_ROWS rows.</div>")
        }
        reader.close()
        return HtmlTemplates.buildHtml(rows.toString(), injectTableCss = true)
    }

    internal fun parseCsvFields(line: String): List<String> {
        val result = ArrayList<String>()
        val curVal = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (inQuotes) {
                if (ch == '\"') {
                    if (i + 1 < line.length && line[i + 1] == '\"') {
                        curVal.append('\"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    curVal.append(ch)
                }
            } else {
                if (ch == '\"') {
                    inQuotes = true
                } else if (ch == ',') {
                    result.add(curVal.toString().trim())
                    curVal.clear()
                } else {
                    curVal.append(ch)
                }
            }
            i++
        }
        result.add(curVal.toString().trim())
        return result
    }

    internal fun processXmlZipEntry(
        context: Context,
        uri: Uri,
        predicate: (String) -> Boolean,
        block: (XmlPullParser) -> Unit
    ) {
        val stream = context.contentResolver.openInputStream(uri) ?: throw Exception("Failed to open file stream")
        val zipStream = java.util.zip.ZipInputStream(stream)
        try {
            var entry = zipStream.nextEntry
            var targetStream: InputStream? = null
            while (entry != null) {
                if (predicate(entry.name)) {
                    targetStream = zipStream
                    break
                }
                entry = zipStream.nextEntry
            }
            
            if (targetStream == null) throw Exception("Not a valid archive: missing matching file")

            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(targetStream, "UTF-8")
            block(parser)
        } finally {
            try { zipStream.close() } catch(e: Exception) {}
            try { stream.close() } catch(e: Exception) {}
        }
    }

    private fun getParserAttrValue(parser: XmlPullParser, attrName: String): String? {
        for (i in 0 until parser.attributeCount) {
            if (parser.getAttributeName(i) == attrName) {
                return parser.getAttributeValue(i)
            }
        }
        return null
    }

    private fun loadRelationships(context: Context, uri: Uri): Map<String, String> {
        val relsMap = HashMap<String, String>()
        try {
            processXmlZipEntry(context, uri, { it == "word/_rels/document.xml.rels" }) { parser ->
                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && parser.name == "Relationship") {
                        val id = getParserAttrValue(parser, "Id")
                        val type = getParserAttrValue(parser, "Type")
                        val target = getParserAttrValue(parser, "Target")
                        if (id != null && target != null && type != null && type.contains("image")) {
                            val fileName = target.substringAfterLast('/')
                            relsMap[id] = fileName
                        }
                    }
                    eventType = parser.next()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return relsMap
    }

    private fun extractMediaFiles(context: Context, uri: Uri) {
        val mediaDir = java.io.File(context.cacheDir, "docx_media")
        if (mediaDir.exists()) {
            mediaDir.deleteRecursively()
        }
        mediaDir.mkdirs()

        var stream: java.io.InputStream? = null
        var zipStream: java.util.zip.ZipInputStream? = null
        try {
            stream = context.contentResolver.openInputStream(uri) ?: return
            zipStream = java.util.zip.ZipInputStream(stream)
            var entry = zipStream.nextEntry
            val buffer = ByteArray(4096)
            while (entry != null) {
                if (entry.name.startsWith("word/media/")) {
                    val fileName = entry.name.substringAfterLast('/')
                    val outFile = java.io.File(mediaDir, fileName)
                    val outStream = java.io.FileOutputStream(outFile)
                    try {
                        var len = zipStream.read(buffer)
                        while (len > 0) {
                            outStream.write(buffer, 0, len)
                            len = zipStream.read(buffer)
                        }
                    } finally {
                        outStream.close()
                    }
                }
                entry = zipStream.nextEntry
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { zipStream?.close() } catch(e: Exception) {}
            try { stream?.close() } catch(e: Exception) {}
        }
    }

    internal fun parseDocxToHtml(context: Context, uri: Uri): String {
        val rels = loadRelationships(context, uri)
        extractMediaFiles(context, uri)
        val html = StringBuilder()

        processXmlZipEntry(context, uri, { it == "word/document.xml" }) { parser ->
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

            // Helper to ensure the opening tag of a paragraph/heading/list item is written
            fun ensureParagraphOpen() {
                if (inParagraph && !paragraphStartedInHtml) {
                    paragraphStartedInHtml = true
                    if (headingLevel > 0) {
                        html.append("<h$headingLevel>")
                    } else if (isListItem) {
                        html.append("<div class=\"list-item\" style=\"--level: ${listLevel + 1};\">")
                    } else {
                        html.append("<p>")
                    }
                }
            }

            // Helper to close current paragraph/heading/list item
            fun closeParagraph() {
                if (paragraphStartedInHtml) {
                    if (headingLevel > 0) {
                        html.append("</h$headingLevel>")
                    } else if (isListItem) {
                        html.append("</div>")
                    } else {
                        html.append("</p>")
                    }
                    paragraphStartedInHtml = false
                } else if (inParagraph) {
                    // Empty paragraph, preserve spacing
                    html.append("<p>&nbsp;</p>")
                }
            }

            // Helper to extract attribute values safely
            fun getAttrValue(attrName: String): String? {
                for (i in 0 until parser.attributeCount) {
                    if (parser.getAttributeName(i) == attrName) {
                        return parser.getAttributeValue(i)
                    }
                }
                return null
            }

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val localName = parser.name
                        when (localName) {
                            "tbl" -> {
                                html.append("<div class=\"table-wrapper\"><table>")
                            }
                            "tr" -> {
                                html.append("<tr>")
                            }
                            "tc" -> {
                                html.append("<td>")
                            }
                            "p" -> {
                                inParagraph = true
                                paragraphStartedInHtml = false
                                isListItem = false
                                listLevel = 0
                                headingLevel = 0
                            }
                            "pStyle" -> {
                                val styleVal = getAttrValue("val")
                                if (styleVal != null) {
                                    if (styleVal.startsWith("Heading", ignoreCase = true)) {
                                        val digit = styleVal.filter { it.isDigit() }.toIntOrNull()
                                        headingLevel = digit ?: 1
                                    }
                                }
                            }
                            "numPr" -> {
                                isListItem = true
                            }
                            "ilvl" -> {
                                val valAttr = getAttrValue("val")
                                if (valAttr != null) {
                                    listLevel = valAttr.toIntOrNull() ?: 0
                                }
                            }
                            "br" -> {
                                val typeAttr = getAttrValue("type")
                                if (typeAttr == "page") {
                                    html.append("<div class=\"page-break\"><hr class=\"page-break-line\"/><div class=\"page-break-text\">Page Break</div></div>")
                                } else {
                                    ensureParagraphOpen()
                                    html.append("<br/>")
                                }
                            }
                            "lastRenderedPageBreak" -> {
                                html.append("<div class=\"page-break\"><hr class=\"page-break-line\"/><div class=\"page-break-text\">Page Break</div></div>")
                            }
                            "blip" -> {
                                val embedId = getAttrValue("embed") ?: getAttrValue("link")
                                if (embedId != null) {
                                    val fileName = rels[embedId]
                                    if (fileName != null) {
                                        ensureParagraphOpen()
                                        html.append("<div class=\"doc-image-container\"><img class=\"doc-image\" src=\"$fileName\" alt=\"Image\" /></div>")
                                    }
                                }
                            }
                            "r" -> {
                                inRun = true
                                isBold = false
                                isItalic = false
                                isUnderline = false
                            }
                            "b" -> if (inRun) isBold = true
                            "i" -> if (inRun) isItalic = true
                            "u" -> if (inRun) isUnderline = true
                            "t" -> {
                                if (inRun) {
                                    ensureParagraphOpen()
                                    val textContent = parser.nextText()
                                    val escaped = textContent.replace("&", "&amp;")
                                        .replace("<", "&lt;")
                                        .replace(">", "&gt;")
                                    
                                    val wraps = StringBuilder()
                                    if (isBold) wraps.append("<strong>")
                                    if (isItalic) wraps.append("<em>")
                                    if (isUnderline) wraps.append("<u>")
                                    
                                    html.append(wraps.toString())
                                    html.append(escaped)
                                    
                                    val unwraps = StringBuilder()
                                    if (isUnderline) unwraps.append("</u>")
                                    if (isItalic) unwraps.append("</em>")
                                    if (isBold) unwraps.append("</strong>")
                                    
                                    html.append(unwraps.toString())
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val localName = parser.name
                        when (localName) {
                            "tbl" -> {
                                html.append("</table></div>")
                            }
                            "tr" -> {
                                html.append("</tr>")
                            }
                            "tc" -> {
                                html.append("</td>")
                            }
                            "p" -> {
                                closeParagraph()
                                inParagraph = false
                            }
                            "r" -> {
                                inRun = false
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        }

        return HtmlTemplates.buildHtml(html.toString())
    }

    internal fun loadSharedStrings(context: Context, uri: Uri): List<String> {
        val stream = context.contentResolver.openInputStream(uri) ?: return emptyList()
        val zipStream = java.util.zip.ZipInputStream(stream)
        var sharedStrings: List<String> = emptyList()
        try {
            var entry = zipStream.nextEntry
            while (entry != null) {
                if (entry.name == "xl/sharedStrings.xml" || entry.name.endsWith("sharedStrings.xml")) {
                    sharedStrings = parseSharedStrings(zipStream)
                    break
                }
                entry = zipStream.nextEntry
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            zipStream.close()
        }
        return sharedStrings
    }

    internal fun parseSharedStrings(inStream: InputStream): List<String> {
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
                    val name = parser.name
                    if (name == "si") {
                        inSi = true
                        currentStr.clear()
                    } else if (name == "t" && inSi) {
                        inT = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inT && inSi) {
                        currentStr.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    val name = parser.name
                    if (name == "t") {
                        inT = false
                    } else if (name == "si") {
                        inSi = false
                        list.add(currentStr.toString())
                    }
                }
            }
            eventType = parser.next()
        }
        return list
    }

    internal fun parseWorksheetToHtml(context: Context, uri: Uri, sharedStrings: List<String>): String {
        val MAX_ROWS = 1000
        val MAX_COLS = 50
        val sheetMatrix = TreeMap<Int, TreeMap<Int, String>>()

        processXmlZipEntry(context, uri, { it.contains("worksheets/sheet") }) { parser ->
            var eventType = parser.eventType
            var currentCellRef = ""
            var currentCellType = ""
            var inCell = false
            var inV = false
            var inInlineStr = false
            var inT = false
            val inlineStrVal = StringBuilder()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name
                        if (name == "c") {
                            inCell = true
                            currentCellRef = parser.getAttributeValue(null, "r") ?: ""
                            currentCellType = parser.getAttributeValue(null, "t") ?: ""
                            inlineStrVal.clear()
                        } else if (name == "v") {
                            inV = true
                        } else if (name == "is") {
                            inInlineStr = true
                        } else if (name == "t" && inInlineStr) {
                            inT = true
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inCell) {
                            if (inV) {
                                val vVal = parser.text ?: ""
                                if (currentCellRef.isNotEmpty()) {
                                    val (cIdx, rIdx) = parseCellRef(currentCellRef)
                                    if (rIdx < MAX_ROWS && cIdx < MAX_COLS) {
                                        var cellVal = vVal
                                        if (currentCellType == "s") {
                                            val sIdx = vVal.toIntOrNull() ?: -1
                                            if (sIdx in sharedStrings.indices) {
                                                cellVal = sharedStrings[sIdx]
                                            }
                                        } else if (currentCellType == "b") {
                                            cellVal = if (vVal == "1") "TRUE" else "FALSE"
                                        }
                                        val colsMap = sheetMatrix.getOrPut(rIdx) { TreeMap() }
                                        colsMap[cIdx] = cellVal
                                    }
                                }
                            } else if (inT && inInlineStr) {
                                inlineStrVal.append(parser.text ?: "")
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val name = parser.name
                        if (name == "v") {
                            inV = false
                        } else if (name == "t" && inInlineStr) {
                            inT = false
                        } else if (name == "is") {
                            inInlineStr = false
                            if (currentCellRef.isNotEmpty() && inlineStrVal.isNotEmpty()) {
                                val (cIdx, rIdx) = parseCellRef(currentCellRef)
                                if (rIdx < MAX_ROWS && cIdx < MAX_COLS) {
                                    val colsMap = sheetMatrix.getOrPut(rIdx) { TreeMap() }
                                    colsMap[cIdx] = inlineStrVal.toString()
                                }
                            }
                        } else if (name == "c") {
                            inCell = false
                            currentCellRef = ""
                            currentCellType = ""
                        }
                    }
                }
                eventType = parser.next()
            }
        }

        val maxRow = if (sheetMatrix.isNotEmpty()) sheetMatrix.lastKey() else 0
        var maxCol = 0
        for ((_, cols) in sheetMatrix) {
            if (cols.isNotEmpty()) {
                val colMax = cols.lastKey()
                if (colMax > maxCol) {
                    maxCol = colMax
                }
            }
        }
        
        val rows = StringBuilder()
        rows.append("<thead>\n<tr>\n<th></th>")

        for (c in 0..maxCol) {
            rows.append("<th>${getColNameFromIndex(c)}</th>")
        }
        rows.append("</tr>\n</thead>\n<tbody>\n")

        for (r in 0..maxRow) {
            rows.append("<tr>")
            rows.append("<td class='row-header'>${r + 1}</td>")
            val colsMap = sheetMatrix[r]
            for (c in 0..maxCol) {
                val valStr = colsMap?.get(c) ?: ""
                val escaped = valStr.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                rows.append("<td>$escaped</td>")
            }
            rows.append("</tr>\n")
        }

        rows.append("</tbody>\n")
        if (maxRow >= MAX_ROWS - 1) {
            rows.append("<div style='margin-top: 10px; font-size: 11px; color: var(--subtext-color); text-align: center;'>⚡ Heavy worksheet: only first $MAX_ROWS rows loaded.</div>")
        }
        return HtmlTemplates.buildHtml(rows.toString(), injectTableCss = true)
    }

    private fun parseCellRef(ref: String): Pair<Int, Int> {
        val colName = ref.takeWhile { it.isLetter() }.uppercase()
        val rowNum = ref.dropWhile { it.isLetter() }
        val colIndex = colNameToIndex(colName)
        val rowIndex = (rowNum.toIntOrNull() ?: 1) - 1
        return Pair(colIndex, rowIndex)
    }

    private fun colNameToIndex(name: String): Int {
        var idx = 0
        for (i in name.indices) {
            val char = name[i]
            if (char in 'A'..'Z') {
                idx = idx * 26 + (char - 'A' + 1)
            }
        }
        return idx - 1
    }

    private fun getColNameFromIndex(index: Int): String {
        var temp = index
        val name = StringBuilder()
        while (temp >= 0) {
            name.insert(0, ('A' + (temp % 26)))
            temp = temp / 26 - 1
        }
        return name.toString()
    }
}

// --- UI Components ---

@Composable
fun ViewerTopBar(
    fileName: String,
    mimeType: String,
    onBackClick: () -> Unit,
    onOpenWithClick: () -> Unit,
    isDarkMode: Boolean,
    onThemeToggle: () -> Unit
) {
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(appColors.bg)
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 8.dp)
            .drawBehind {
                val strokeWidthPx = 1.dp.toPx()
                val y = size.height - strokeWidthPx / 2
                drawLine(
                    color = appColors.border,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = strokeWidthPx
                )
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        LiteIconButton(onClick = onBackClick) {
            Image(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Navigate Back",
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(androidx.compose.material3.LocalContentColor.current)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            androidx.compose.foundation.text.BasicText(
                text = fileName,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                style = androidx.compose.ui.text.TextStyle(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = appColors.text
                )
            )
            androidx.compose.foundation.text.BasicText(
                text = "Local View • Read Only",
                maxLines = 1,
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 11.sp,
                    color = appColors.text.copy(alpha = 0.55f),
                    fontWeight = FontWeight.Normal
                )
            )
        }
        
        LiteIconButton(onClick = onThemeToggle) {
            Image(
                imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                contentDescription = "Toggle Theme",
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(androidx.compose.material3.LocalContentColor.current)
            )
        }
        LiteIconButton(onClick = onOpenWithClick) {
            Image(
                imageVector = Icons.Default.Share,
                contentDescription = "Open With System App",
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(androidx.compose.material3.LocalContentColor.current)
            )
        }
    }
}

class SearchState {
    var query by mutableStateOf("")
    var isActive by mutableStateOf(false)
    var activeMatch by mutableStateOf(0)
    var totalMatches by mutableStateOf(0)
    var triggerFindNext by mutableStateOf(0)
    var triggerFindPrev by mutableStateOf(0)
}

@Composable
fun BottomFormatBanner(
    searchState: SearchState,
    onSetImmersive: (Boolean) -> Unit
) {
    val appColors = LocalAppColors.current
    val focusRequester = remember { FocusRequester() }
    
    LaunchedEffect(searchState.isActive) {
        if (searchState.isActive) {
            focusRequester.requestFocus()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(appColors.surface)
            .drawBehind {
                val strokeWidthPx = 1.dp.toPx()
                drawLine(
                    color = appColors.border,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = strokeWidthPx
                )
            }
            .navigationBarsPadding()
            .imePadding()
    ) {
        androidx.compose.animation.Crossfade(targetState = searchState.isActive, label = "SearchCrossfade") { active ->
            if (active) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .background(
                                color = appColors.bg,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = appColors.border,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (searchState.query.isEmpty()) {
                            androidx.compose.foundation.text.BasicText(
                                text = "Search in document...",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = 14.sp,
                                    color = appColors.text.copy(alpha = 0.4f)
                                )
                            )
                        }
                        
                        androidx.compose.foundation.text.BasicTextField(
                            value = searchState.query,
                            onValueChange = { searchState.query = it },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 14.sp,
                                color = appColors.text
                            ),
                            singleLine = true,
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(appColors.accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    if (searchState.totalMatches > 0) {
                                        searchState.triggerFindNext++
                                    }
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    if (searchState.totalMatches > 0) {
                        androidx.compose.foundation.text.BasicText(
                            text = "${searchState.activeMatch} / ${searchState.totalMatches}",
                            style = androidx.compose.ui.text.TextStyle(
                                fontSize = 13.sp,
                                color = appColors.text.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Medium
                            ),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else if (searchState.query.isNotEmpty()) {
                        androidx.compose.foundation.text.BasicText(
                            text = "0 / 0",
                            style = androidx.compose.ui.text.TextStyle(
                                fontSize = 13.sp,
                                color = appColors.text.copy(alpha = 0.4f),
                                fontWeight = FontWeight.Normal
                            ),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LiteIconButton(
                            onClick = { searchState.triggerFindPrev++ },
                            enabled = searchState.totalMatches > 0,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Image(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Previous Match",
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                                    if (searchState.totalMatches > 0) appColors.text else appColors.text.copy(alpha = 0.3f)
                                )
                            )
                        }
                        
                        LiteIconButton(
                            onClick = { searchState.triggerFindNext++ },
                            enabled = searchState.totalMatches > 0,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Image(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Next Match",
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                                    if (searchState.totalMatches > 0) appColors.text else appColors.text.copy(alpha = 0.3f)
                                )
                            )
                        }
                        
                        LiteIconButton(
                            onClick = {
                                searchState.query = ""
                                searchState.isActive = false
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Image(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Search",
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(appColors.text)
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.text.BasicText(
                            text = "⚡",
                            style = androidx.compose.ui.text.TextStyle(
                                fontSize = 14.sp,
                                color = com.example.ui.theme.AccentPeach
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        androidx.compose.foundation.text.BasicText(
                            text = "Preview Mode",
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = appColors.text.copy(alpha = 0.61f),
                                letterSpacing = 0.2.sp
                            )
                        )
                    }
                    
                    LiteIconButton(
                        onClick = { 
                            searchState.isActive = true 
                            onSetImmersive(false)
                        }
                    ) {
                        Image(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search in document",
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(appColors.text)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WelcomeDashboard(
    onFilePick: (Uri) -> Unit
) {
    val context = LocalContext.current
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { onFilePick(it) } }

    val expressive = rememberExpressiveState(
        pressedScale = 0.9f,
        baseCorner = 48.dp,
        pressedCorner = 32.dp
    )

    val appColors = LocalAppColors.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.bg)
    ) {
        // Bold Typography
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 40.dp, end = 40.dp, top = 80.dp),
            horizontalAlignment = Alignment.Start
        ) {
            androidx.compose.foundation.text.BasicText(
                text = "Simply",
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Light,
                    color = appColors.text
                )
            )
            androidx.compose.foundation.text.BasicText(
                text = "open",
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Light,
                    color = appColors.text
                )
            )
            androidx.compose.foundation.text.BasicText(
                text = "a file.",
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Light,
                    color = appColors.accent // Peach accent
                )
            )
        }

        // Large bottom FAB (Expressive Blob)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
                .size(96.dp)
                .graphicsLayer {
                    scaleX = expressive.scale
                    scaleY = expressive.scale
                    shape = RoundedCornerShape(expressive.cornerSize)
                    clip = true
                }
                .background(com.example.ui.theme.AccentPeach)
                .clickable(
                    interactionSource = expressive.interactionSource,
                    indication = null,
                    onClick = {
                        pickerLauncher.launch(
                            arrayOf(
                                "application/pdf",
                                "text/*",
                                "application/json",
                                "application/xml",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                            )
                        )
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                imageVector = Icons.Default.Add,
                contentDescription = "Open File",
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(com.example.ui.theme.OnAccentPeach),
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

@Composable
fun DocumentViewport(
    viewState: ViewState,
    docContext: DocumentContext,
    isImmersive: Boolean,
    isDarkMode: Boolean,
    contentPaddingTop: androidx.compose.ui.unit.Dp,
    onSetImmersive: (Boolean) -> Unit,
    searchState: SearchState
) {
    val appColors = LocalAppColors.current
    var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(onSetImmersive, size, isImmersive) {
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
                            // If user moved their finger more than 15 pixels or held it for more than 250ms, it is scaled or scrolled!
                            if (dist > 15f || (System.currentTimeMillis() - startTime) > 250) {
                                isClick = false
                            }
                            
                            if (!change.pressed) {
                                break
                            }
                        }
                        
                        if (isClick && size.width > 0 && size.height > 0) {
                            val x = lastEvent.position.x
                            val y = lastEvent.position.y
                            
                            val minX = size.width * 0.25f
                            val maxX = size.width * 0.75f
                            val minY = size.height * 0.15f
                            val maxY = size.height * 0.85f
                            
                            // Check if the single tap is within the center 50% width and center 70% height
                            if (x in minX..maxX && y in minY..maxY) {
                                onSetImmersive(!isImmersive)
                            }
                        }
                    }
                }
            }
    ) {
        when (viewState) {
            is ViewState.Loading, ViewState.Idle -> {
                TactileDocumentShimmer(
                    docType = docContext.type,
                    contentPaddingTop = contentPaddingTop
                )
            }
            is ViewState.PdfSuccess -> {
                PdfDocViewer(
                    pdfRenderer = viewState.renderer,
                    pageCount = viewState.pageCount,
                    isDarkMode = isDarkMode,
                    isImmersive = isImmersive,
                    contentPaddingTop = contentPaddingTop,
                    onSetImmersive = onSetImmersive
                )
            }
            is ViewState.TextSuccess -> {
                TextDocViewer(
                    text = viewState.content,
                    isJson = viewState.isJson,
                    isImmersive = isImmersive,
                    contentPaddingTop = contentPaddingTop,
                    onSetImmersive = onSetImmersive
                )
            }
            is ViewState.HtmlSuccess -> {
                HtmlDocViewer(
                    htmlContent = viewState.htmlContent,
                    isDarkMode = isDarkMode,
                    isImmersive = isImmersive,
                    contentPaddingTop = contentPaddingTop,
                    onSetImmersive = onSetImmersive,
                    searchState = searchState
                )
            }
            else -> Unit
        }
    }
}

@Composable
fun FauxShimmerItem(
    alpha: Float,
    widthPercent: Float,
    height: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp = 4.dp
) {
    val appColors = LocalAppColors.current
    Box(
        modifier = modifier
            .fillMaxWidth(widthPercent)
            .height(height)
            .graphicsLayer { this.alpha = alpha }
            .background(appColors.border.copy(alpha = 0.5f), shape = RoundedCornerShape(cornerRadius))
    )
}

@Composable
fun TactileDocumentShimmer(
    docType: FileType,
    contentPaddingTop: androidx.compose.ui.unit.Dp
) {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        )
    )

    val appColors = LocalAppColors.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.bg)
            .padding(top = contentPaddingTop, start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        when (docType) {
            FileType.PDF -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .shadow(4.dp, shape = RoundedCornerShape(12.dp))
                        .background(appColors.surface, shape = RoundedCornerShape(12.dp))
                        .padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FauxShimmerItem(alpha, 0.4f, 20.dp)
                            FauxShimmerItem(alpha, 0.15f, 20.dp)
                        }

                        Divider(color = appColors.border, thickness = 1.dp)

                        FauxShimmerItem(alpha, 1.0f, 14.dp)
                        FauxShimmerItem(alpha, 0.95f, 14.dp)
                        FauxShimmerItem(alpha, 0.9f, 14.dp)
                        FauxShimmerItem(alpha, 0.85f, 14.dp)
                        FauxShimmerItem(alpha, 0.4f, 14.dp)

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .graphicsLayer { this.alpha = alpha }
                                    .background(appColors.border.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp))
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                FauxShimmerItem(alpha, 0.8f, 12.dp)
                                FauxShimmerItem(alpha, 0.6f, 12.dp)
                                FauxShimmerItem(alpha, 0.4f, 12.dp)
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        
                        FauxShimmerItem(alpha, 1.0f, 14.dp)
                        FauxShimmerItem(alpha, 0.95f, 14.dp)
                        FauxShimmerItem(alpha, 0.9f, 14.dp)
                        FauxShimmerItem(alpha, 0.7f, 14.dp)
                    }
                }
            }
            FileType.XLSX, FileType.CSV -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .shadow(4.dp, shape = RoundedCornerShape(12.dp))
                        .background(appColors.surface, shape = RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(appColors.border.copy(alpha = 0.3f))
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        repeat(5) {
                            Box(
                                modifier = Modifier
                                    .width(60.dp)
                                    .height(16.dp)
                                    .graphicsLayer { this.alpha = alpha }
                                    .background(appColors.border.copy(alpha = 0.7f), shape = RoundedCornerShape(4.dp))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(15) { rowIndex ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                repeat(5) { colIndex ->
                                    val width = when (colIndex) {
                                        0 -> 40.dp
                                        1 -> 70.dp
                                        2 -> 55.dp
                                        3 -> 65.dp
                                        else -> 50.dp
                                    }
                                    Box(
                                        modifier = Modifier
                                            .width(width)
                                            .height(18.dp)
                                            .graphicsLayer { this.alpha = alpha }
                                            .background(appColors.border.copy(alpha = 0.4f), shape = RoundedCornerShape(4.dp))
                                    )
                                }
                            }
                            Divider(color = appColors.border, thickness = 0.5.dp)
                        }
                    }
                }
            }
            FileType.DOCX, FileType.MD -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .shadow(4.dp, shape = RoundedCornerShape(12.dp))
                        .background(appColors.surface, shape = RoundedCornerShape(12.dp))
                        .padding(20.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        FauxShimmerItem(alpha, 0.65f, 24.dp)
                        FauxShimmerItem(alpha, 0.4f, 16.dp)
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = appColors.border, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(12.dp))

                        FauxShimmerItem(alpha, 1.0f, 14.dp)
                        FauxShimmerItem(alpha, 0.98f, 14.dp)
                        FauxShimmerItem(alpha, 0.96f, 14.dp)
                        FauxShimmerItem(alpha, 0.92f, 14.dp)
                        FauxShimmerItem(alpha, 0.85f, 14.dp)
                        FauxShimmerItem(alpha, 0.45f, 14.dp)

                        Spacer(modifier = Modifier.height(16.dp))

                        FauxShimmerItem(alpha, 0.5f, 20.dp)
                        Spacer(modifier = Modifier.height(4.dp))

                        FauxShimmerItem(alpha, 1.0f, 14.dp)
                        FauxShimmerItem(alpha, 0.95f, 14.dp)
                        FauxShimmerItem(alpha, 0.9f, 14.dp)
                        FauxShimmerItem(alpha, 0.35f, 14.dp)
                    }
                }
            }
            FileType.TXT, FileType.JSON, FileType.XML, FileType.LOG -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(appColors.surface, shape = RoundedCornerShape(8.dp))
                        .padding(16.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        repeat(16) { index ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(24.dp)
                                        .height(12.dp)
                                        .graphicsLayer { this.alpha = alpha }
                                        .background(appColors.border.copy(alpha = 0.3f), shape = RoundedCornerShape(2.dp))
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                
                                val indent = when (index % 5) {
                                    1 -> 16.dp
                                    2 -> 32.dp
                                    3 -> 48.dp
                                    4 -> 16.dp
                                    else -> 0.dp
                                }
                                if (indent > 0.dp) {
                                    Spacer(modifier = Modifier.width(indent))
                                }

                                val widthPct = when (index % 4) {
                                    0 -> 0.6f
                                    1 -> 0.75f
                                    2 -> 0.45f
                                    else -> 0.85f
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(widthPct)
                                        .height(12.dp)
                                        .graphicsLayer { this.alpha = alpha }
                                        .background(
                                            if (index % 5 == 1 || index % 5 == 2) {
                                                com.example.ui.theme.AccentPeach.copy(alpha = 0.25f)
                                            } else {
                                                appColors.border.copy(alpha = 0.5f)
                                            }, 
                                            shape = RoundedCornerShape(3.dp)
                                        )
                                )
                            }
                        }
                    }
                }
            }
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .shadow(4.dp, shape = RoundedCornerShape(12.dp))
                        .background(appColors.surface, shape = RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Image(
                            imageVector = Icons.Default.Description,
                            contentDescription = "Loading Doc",
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(com.example.ui.theme.AccentPeach),
                            modifier = Modifier
                                .size(64.dp)
                                .graphicsLayer { this.alpha = alpha }
                        )
                        FauxShimmerItem(alpha, 0.5f, 16.dp)
                        FauxShimmerItem(alpha, 0.3f, 12.dp)
                    }
                }
            }
        }
    }
}

@Composable
fun PdfDocViewer(
    pdfRenderer: PdfRenderer,
    pageCount: Int,
    isDarkMode: Boolean,
    isImmersive: Boolean,
    contentPaddingTop: androidx.compose.ui.unit.Dp,
    onSetImmersive: (Boolean) -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var lastOffset by remember { mutableStateOf(0) }
    val currentVisIndex by remember { androidx.compose.runtime.derivedStateOf { listState.firstVisibleItemIndex } }
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

    ZoomableContainer(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(appColors.bg)
                    .padding(vertical = if (pageCount == 1) 0.dp else 4.dp),
                contentPadding = if (pageCount == 1) PaddingValues(0.dp) else PaddingValues(top = contentPaddingTop, bottom = 32.dp),
                verticalArrangement = if (pageCount == 1) Arrangement.Center else Arrangement.Top
            ) {
                items(pageCount) { index ->
                    PdfPage(
                        pdfRenderer = pdfRenderer,
                        pageIndex = index,
                        isDarkMode = isDarkMode,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            // Page Indicator
            if (pageCount > 1) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = !isImmersive || listState.isScrollInProgress,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color(0x99000000), shape = RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        androidx.compose.foundation.text.BasicText(
                            text = "${currentVisIndex + 1} / $pageCount",
                            style = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }

            // Fast scroll
            val isLongDocument = pageCount > 3
            val scrollFraction by remember {
                androidx.compose.runtime.derivedStateOf {
                    if (pageCount <= 1) 0f else {
                        val firstIndex = listState.firstVisibleItemIndex
                        val offset = listState.firstVisibleItemScrollOffset
                        val itemSize = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1
                        val exactPage = firstIndex + (offset.toFloat() / itemSize)
                        (exactPage / pageCount).coerceIn(0f, 1f)
                    }
                }
            }
            val targetPage = (scrollFraction * pageCount).toInt().coerceIn(0, pageCount - 1)
            
            MinimalFastScroller(
                scrollFraction = scrollFraction,
                isScrollInProgress = listState.isScrollInProgress,
                onScrollToFraction = { frac ->
                    val page = (frac * pageCount).toInt().coerceIn(0, pageCount - 1)
                    scope.launch { listState.scrollToItem(page) }
                },
                thumbLabel = "${targetPage + 1} / $pageCount",
                isLongDocument = isLongDocument && !isImmersive,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
fun PdfPage(
    pdfRenderer: PdfRenderer,
    pageIndex: Int,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
) {
    var bitmapState by remember { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current

    LaunchedEffect(pageIndex) {
        withContext(Dispatchers.IO) {
            try {
                // Ensure thread-safe rendering on PdfRenderer instance
                synchronized(pdfRenderer) {
                    val page = pdfRenderer.openPage(pageIndex)
                    val metrics = context.resources.displayMetrics
                    // Cap width for extreme memory efficiency
                    val targetWidth = minOf(metrics.widthPixels, 1280)
                    val scale = targetWidth.toFloat() / page.width.toFloat()
                    val targetHeight = (page.height * scale).toInt()

                    val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bitmapState = bitmap
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(
                bitmapState?.let { it.width.toFloat() / it.height.toFloat() } ?: 0.707f
            )
            .padding(vertical = 8.dp, horizontal = 16.dp)
            .shadow(1.dp, shape = RoundedCornerShape(2.dp))
            .background(if (isDarkMode) Color.Black else Color.White),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = bitmapState
        if (bitmap != null) {
            val invertMatrix = floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Document Page ${pageIndex + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                colorFilter = if (isDarkMode) androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(invertMatrix)) else null
            )
        } else {
            CircularProgressIndicator(
                color = com.example.ui.theme.AccentPeach,
                strokeWidth = 2.dp,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun TextDocViewer(
    text: String,
    isJson: Boolean,
    isImmersive: Boolean,
    contentPaddingTop: androidx.compose.ui.unit.Dp,
    onSetImmersive: (Boolean) -> Unit
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    var lastValue by remember { mutableStateOf(0) }
    LaunchedEffect(scrollState.value) {
        if (scrollState.isScrollInProgress) {
            if (scrollState.value > lastValue + 15) {
                onSetImmersive(true)
            } else if (scrollState.value < lastValue - 15) {
                onSetImmersive(false)
            }
        }
        lastValue = scrollState.value
    }

    val appColors = LocalAppColors.current

    ZoomableContainer(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(appColors.bg)
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
                Spacer(modifier = Modifier.height(contentPaddingTop))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .shadow(4.dp, shape = RoundedCornerShape(8.dp))
                        .background(appColors.surface, shape = RoundedCornerShape(8.dp))
                        .drawBehind {
                            val strokeWidthPx = 1.dp.toPx()
                            drawRoundRect(
                                color = appColors.border,
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidthPx)
                            )
                        }
                        .padding(if (isJson) 16.dp else 24.dp)
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxWidth(),
                        factory = { context ->
                            android.widget.TextView(context).apply {
                                autoLinkMask = android.text.util.Linkify.WEB_URLS
                                setTextIsSelectable(true)
                            }
                        },
                        update = { textView ->
                            textView.text = text
                            textView.setTextColor(appColors.text.toArgb())
                            textView.textSize = if (isJson) 13f else 15.1f
                            textView.typeface = if (isJson) android.graphics.Typeface.MONOSPACE else android.graphics.Typeface.DEFAULT
                            textView.setLineSpacing(0f, if (isJson) 1.2f else 1.3f)
                        }
                    )
                }
            }

            // Fast scroll
            val isLongDocument by remember {
                androidx.compose.runtime.derivedStateOf {
                    scrollState.maxValue > 3000
                }
            }
            val scrollFraction by remember {
                androidx.compose.runtime.derivedStateOf {
                    if (scrollState.maxValue > 0) scrollState.value.toFloat() / scrollState.maxValue else 0f
                }
            }

            MinimalFastScroller(
                scrollFraction = scrollFraction,
                isScrollInProgress = scrollState.isScrollInProgress,
                onScrollToFraction = { frac ->
                    scope.launch { scrollState.scrollTo((frac * scrollState.maxValue).toInt()) }
                },
                thumbLabel = "${(scrollFraction * 100).toInt()}%",
                isLongDocument = isLongDocument && !isImmersive,
                modifier = Modifier.align(Alignment.CenterEnd).padding(top = contentPaddingTop)
            )
        }
    }
}

// Native wrap of Compose SelectionContainer to keep text selectable
@Composable
fun SelectionContainer(
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.text.selection.SelectionContainer {
        content()
    }
}

@Composable
fun HtmlDocViewer(
    htmlContent: String,
    isDarkMode: Boolean,
    isImmersive: Boolean,
    contentPaddingTop: androidx.compose.ui.unit.Dp,
    onSetImmersive: (Boolean) -> Unit,
    searchState: SearchState
) {
    val themeClass = if (isDarkMode) "dark-theme" else "light-theme"
    val styledHtml = remember(htmlContent, themeClass) {
        htmlContent
            .replaceFirst("<html>", "<html class=\"$themeClass\">")
    }
    
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                overScrollMode = View.OVER_SCROLL_NEVER // Disable overscroll glow to match compose LocalOverscrollConfiguration provides null
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                        val url = request?.url ?: return false
                        if (url.scheme == "http" || url.scheme == "https") {
                            try {
                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, url))
                                return true
                            } catch (e: Exception) {
                                // fallback
                            }
                        }
                        return false
                    }

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?
                    ): android.webkit.WebResourceResponse? {
                        val url = request?.url ?: return null
                        if (url.scheme == "doc-asset") {
                            val fileName = url.lastPathSegment ?: return null
                            val cacheFile = java.io.File(context.cacheDir, "docx_media/$fileName")
                            if (cacheFile.exists()) {
                                try {
                                    val stream = java.io.FileInputStream(cacheFile)
                                    val mimeType = when (fileName.substringAfterLast('.', "").lowercase()) {
                                        "png" -> "image/png"
                                        "jpg", "jpeg" -> "image/jpeg"
                                        "gif" -> "image/gif"
                                        "webp" -> "image/webp"
                                        else -> "image/*"
                                    }
                                    return android.webkit.WebResourceResponse(mimeType, "UTF-8", stream)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                        return super.shouldInterceptRequest(view, request)
                    }
                }
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                settings.apply {
                    javaScriptEnabled = false // No scripting required for lightweight documents
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    builtInZoomControls = true
                    displayZoomControls = false
                    setSupportZoom(true)
                }
                
                setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                    if (!searchState.isActive) {
                        val diff = scrollY - oldScrollY
                        if (diff > 15) {
                            onSetImmersive(true)
                        } else if (diff < -15) {
                            onSetImmersive(false)
                        }
                    }
                }
                
                setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
                    if (isDoneCounting) {
                        searchState.totalMatches = numberOfMatches
                        searchState.activeMatch = if (numberOfMatches > 0) activeMatchOrdinal + 1 else 0
                    }
                }
                
                tag = styledHtml
                loadDataWithBaseURL("doc-asset://", styledHtml, "text/html", "UTF-8", null)
                webViewRef = this
            }
        },
        update = { webView ->
            // Only reload if the content string is different (e.g. theme toggle) to avoid resetting scroll position on minor recompositions.
            if (webView.tag != styledHtml) {
                webView.tag = styledHtml
                webView.loadDataWithBaseURL("doc-asset://", styledHtml, "text/html", "UTF-8", null)
                if (searchState.isActive) {
                    webView.clearMatches()
                    searchState.activeMatch = 0
                    searchState.totalMatches = 0
                    if (searchState.query.isNotEmpty()) {
                        webView.findAllAsync(searchState.query)
                    }
                }
            }
            webViewRef = webView
        },
        modifier = Modifier.fillMaxSize()
    )

    // Handle Search Queries
    LaunchedEffect(searchState.query, searchState.isActive, webViewRef) {
        val webView = webViewRef ?: return@LaunchedEffect
        if (searchState.isActive && searchState.query.isNotEmpty()) {
            webView.findAllAsync(searchState.query)
        } else {
            webView.clearMatches()
            searchState.activeMatch = 0
            searchState.totalMatches = 0
        }
    }
    
    // Handle Find Next (Forward)
    LaunchedEffect(searchState.triggerFindNext, webViewRef) {
        val webView = webViewRef ?: return@LaunchedEffect
        if (searchState.triggerFindNext > 0 && searchState.isActive) {
            webView.findNext(true)
        }
    }
    
    // Handle Find Prev (Backward)
    LaunchedEffect(searchState.triggerFindPrev, webViewRef) {
        val webView = webViewRef ?: return@LaunchedEffect
        if (searchState.triggerFindPrev > 0 && searchState.isActive) {
            webView.findNext(false)
        }
    }
}

@Composable
fun ZoomableContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    
    val coroutineScope = rememberCoroutineScope()

    val transformableState = rememberTransformableState { zoomChange, offsetChange, _ ->
        val oldScale = scale
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
        modifier = modifier
            .onSizeChanged { containerSize = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
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
                            androidx.compose.animation.core.animate(
                                initialValue = 0f,
                                targetValue = 1f,
                                animationSpec = androidx.compose.animation.core.tween(
                                    durationMillis = 280,
                                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                                )
                            ) { fraction, _ ->
                                scale = startScale + (targetScale - startScale) * fraction
                                offset = startOffset + (targetOffset - startOffset) * fraction
                            }
                        }
                    }
                )
            }
            .transformable(state = transformableState)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        ) {
            content()
        }
    }
}

@Composable
fun ErrorStateView(
    message: String,
    onRetryClick: () -> Unit,
    onOpenWithAlternative: () -> Unit
) {
    val appColors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            imageVector = Icons.Default.Warning,
            contentDescription = "Alert",
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFFFFA07A)), // generic error accent
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        androidx.compose.foundation.text.BasicText(
            text = "Unable to View Document",
            style = androidx.compose.ui.text.TextStyle(
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = appColors.text
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        androidx.compose.foundation.text.BasicText(
            text = message,
            style = androidx.compose.ui.text.TextStyle(
                fontSize = 14.sp,
                color = appColors.text.copy(alpha=0.6f),
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LiteButton(
                onClick = onRetryClick,
                modifier = Modifier.weight(1f),
                isOutlined = true
            ) {
                androidx.compose.foundation.text.BasicText(text = "Retry")
            }
            LiteButton(
                onClick = onOpenWithAlternative,
                containerColor = Color(0xFF552222),
                contentColor = Color.White,
                modifier = Modifier.weight(1.5f)
            ) {
                androidx.compose.foundation.text.BasicText(text = "Open with System App", style = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center))
            }
        }
    }
}

// --- CUSTOM MINIMALIST THEME COMPONENTS ---

data class ExpressiveState(
    val interactionSource: MutableInteractionSource,
    val scale: Float,
    val cornerSize: androidx.compose.ui.unit.Dp
)

@Composable
fun rememberExpressiveState(
    pressedScale: Float = 0.9f,
    baseCorner: androidx.compose.ui.unit.Dp = 0.dp,
    pressedCorner: androidx.compose.ui.unit.Dp = 0.dp
): ExpressiveState {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val currentScale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "expressive_scale"
    )

    val currentCorner by animateDpAsState(
        targetValue = if (isPressed) pressedCorner else baseCorner,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "expressive_corner"
    )

    return ExpressiveState(interactionSource, currentScale, currentCorner)
}

@Composable
fun LiteButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: androidx.compose.ui.graphics.Color = com.example.ui.theme.AccentPeach,
    contentColor: androidx.compose.ui.graphics.Color = com.example.ui.theme.OnAccentPeach,
    isOutlined: Boolean = false,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    val expressive = rememberExpressiveState(
        pressedScale = 0.95f,
        baseCorner = 4.dp,
        pressedCorner = 12.dp
    )

    val actualBorderColor = if (isOutlined) LocalAppColors.current.border else contentColor
    val drawModifiers = modifier
        .graphicsLayer {
            scaleX = expressive.scale
            scaleY = expressive.scale
            shape = RoundedCornerShape(expressive.cornerSize)
            clip = true
        }
        .drawBehind {
            if (isOutlined) {
                val strokeWidthPx = 1.dp.toPx()
                drawRect(
                    color = actualBorderColor,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidthPx)
                )
            }
        }
        .background(if (isOutlined) androidx.compose.ui.graphics.Color.Transparent else containerColor)
        .clickable(
            interactionSource = expressive.interactionSource,
            indication = null,
            onClick = onClick
        )
        .padding(horizontal = 24.dp, vertical = 12.dp)

    Row(
        modifier = drawModifiers,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

@Composable
fun LiteIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val expressive = rememberExpressiveState(
        pressedScale = 0.88f,
        baseCorner = 22.dp,
        pressedCorner = 12.dp
    )
    val isPressed by expressive.interactionSource.collectIsPressedAsState()
    val appColors = LocalAppColors.current
    
    val animatedBgColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isPressed) {
            com.example.ui.theme.AccentPeach
        } else {
            Color.Transparent
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "btn_bg_anim"
    )
    
    val animatedContentColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (!enabled) {
            appColors.text.copy(alpha = 0.3f)
        } else if (isPressed) {
            com.example.ui.theme.OnAccentPeach
        } else {
            appColors.text
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "btn_content_tint"
    )
    
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .size(44.dp)
            .graphicsLayer {
                scaleX = expressive.scale
                scaleY = expressive.scale
                shape = RoundedCornerShape(expressive.cornerSize)
                clip = true
            }
            .background(animatedBgColor)
            .clickable(
                interactionSource = expressive.interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides animatedContentColor
        ) {
            content()
        }
    }
}

@Composable
fun LiteFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val expressive = rememberExpressiveState(
        pressedScale = 0.9f,
        baseCorner = 24.dp,
        pressedCorner = 14.dp
    )

    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = expressive.scale
                scaleY = expressive.scale
                shape = RoundedCornerShape(expressive.cornerSize)
                clip = true
            }
            .background(com.example.ui.theme.AccentPeach)
            .clickable(
                interactionSource = expressive.interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides com.example.ui.theme.OnAccentPeach
        ) {
            content()
        }
    }
}

@Composable
fun LiteCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val appColors = LocalAppColors.current
    val outlineColor = appColors.border
    Column(
        modifier = modifier
            .background(appColors.bg) // Used background directly
            .drawBehind {
                val strokeWidthPx = 1.dp.toPx()
                drawRect(
                    color = outlineColor,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidthPx)
                )
            }
            .fillMaxWidth()
    ) {
        content()
    }
}

@Composable
fun MinimalFastScroller(
    scrollFraction: Float,
    isScrollInProgress: Boolean,
    onScrollToFraction: (Float) -> Unit,
    thumbLabel: String,
    isLongDocument: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isLongDocument) return

    val density = androidx.compose.ui.platform.LocalDensity.current.density
    var layoutHeight by remember { mutableStateOf(0) }
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableStateOf(0f) }

    var isVisible by remember { mutableStateOf(false) }

    val currentFraction = if (isDragging) dragProgress else scrollFraction

    LaunchedEffect(isScrollInProgress, isDragging) {
        if (isScrollInProgress || isDragging) {
            isVisible = true
        } else {
            kotlinx.coroutines.delay(1500)
            isVisible = false
        }
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = isVisible,
        enter = androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.fadeOut(),
        modifier = modifier.fillMaxHeight().width(200.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Interactive scrollbar area on the right edge
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(36.dp)
                    .onSizeChanged { layoutHeight = it.height }
                    .pointerInput(layoutHeight) {
                        awaitPointerEventScope {
                            while (true) {
                                val down = awaitFirstDown()
                                isDragging = true
                                if (layoutHeight > 0) {
                                    val frac = (down.position.y / layoutHeight).coerceIn(0f, 1f)
                                    dragProgress = frac
                                    onScrollToFraction(frac)
                                }
                                var pointer = down
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == pointer.id }
                                    if (change != null) {
                                        if (change.pressed) {
                                            if (layoutHeight > 0) {
                                                val frac = (change.position.y / layoutHeight).coerceIn(0f, 1f)
                                                dragProgress = frac
                                                onScrollToFraction(frac)
                                            }
                                            pointer = change
                                        } else {
                                            isDragging = false
                                            break
                                        }
                                    } else {
                                        isDragging = false
                                        break
                                    }
                                }
                            }
                        }
                    }
            ) {
                val trackColor = LocalAppColors.current.subText.copy(alpha = 0.2f)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(4.dp)
                        .fillMaxHeight()
                        .padding(vertical = 16.dp)
                        .graphicsLayer { translationX = -(12 * density) }
                        .background(trackColor, RoundedCornerShape(2.dp))
                )

                if (layoutHeight > 0) {
                    val availableHeight = layoutHeight - (32 * density)
                    val thumbY = (16 * density) + (currentFraction * availableHeight)
                    val thumbColor = LocalAppColors.current.accent

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .graphicsLayer {
                                translationY = thumbY - (10 * density)
                                translationX = -(4 * density)
                            }
                            .size(20.dp)
                            .shadow(2.dp, androidx.compose.foundation.shape.CircleShape)
                            .background(thumbColor, androidx.compose.foundation.shape.CircleShape)
                            .border(1.dp, LocalAppColors.current.bg, androidx.compose.foundation.shape.CircleShape)
                    )
                }
            }

            // Floating indicator label (positioned to the left of the scroll track)
            if (isDragging && layoutHeight > 0) {
                val availableHeight = layoutHeight - (32 * density)
                val thumbY = (16 * density) + (currentFraction * availableHeight)
                val labelBg = LocalAppColors.current.surface
                val labelBorder = LocalAppColors.current.border
                val labelColor = LocalAppColors.current.text

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 44.dp)
                        .graphicsLayer {
                            translationY = thumbY - (18 * density)
                        }
                        .shadow(4.dp, RoundedCornerShape(8.dp))
                        .background(labelBg, RoundedCornerShape(8.dp))
                        .border(1.dp, labelBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    androidx.compose.foundation.text.BasicText(
                        text = thumbLabel,
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 14.sp, 
                            color = labelColor, 
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }
}