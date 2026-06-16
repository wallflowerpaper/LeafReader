package com.leaf.reader.reader

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leaf.reader.settings.ReaderFont
import com.leaf.reader.settings.ReaderSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Color tokens (mirror CSS variables) ──────────────────────────────────────

private fun bgColor(dark: Boolean)     = if (dark) Color(0xFF181816) else Color(0xFFFAF8F4)
private fun textColor(dark: Boolean)   = if (dark) Color(0xFFE4E0D8) else Color(0xFF1C1B18)
private fun mutedColor(dark: Boolean)  = if (dark) Color(0xFF8A8880) else Color(0xFF7A7870)
private fun accentColor(dark: Boolean) = if (dark) Color(0xFFC8A878) else Color(0xFF8B7355)
private fun sheetBg(dark: Boolean)     = if (dark) Color(0xFF222220) else Color(0xFFFAF8F4)
private fun divColor(dark: Boolean)    = if (dark) Color(0x33E4E0D8) else Color(0x1A1C1B18)

// ── Entry point ───────────────────────────────────────────────────────────────

@Composable
fun ReaderScreen(
    onBack: () -> Unit,
    viewModel: ReaderViewModel = viewModel()
) {
    val uiState  by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    when (val s = uiState) {
        is ReaderUiState.Loading -> LoadingPlaceholder(settings.isDarkMode)
        is ReaderUiState.Error   -> ErrorPlaceholder(s.message, settings.isDarkMode, onBack)
        is ReaderUiState.Ready   -> ReaderContent(
            state    = s,
            settings = settings,
            viewModel = viewModel,
            onBack   = onBack
        )
    }
}

// ── Main reading surface ──────────────────────────────────────────────────────

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
private fun ReaderContent(
    state: ReaderUiState.Ready,
    settings: ReaderSettings,
    viewModel: ReaderViewModel,
    onBack: () -> Unit
) {
    var showControls by remember { mutableStateOf(false) }
    var webViewRef   by remember { mutableStateOf<WebView?>(null) }
    val scope        = rememberCoroutineScope()

    // Auto-dismiss controls after 4 s of inactivity
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(4_000)
            showControls = false
        }
    }

    BackHandler {
        // Save position before navigating away
        webViewRef?.evaluateJavascript("window.LeafReader.getScrollFraction();") { result ->
            viewModel.saveScrollPosition(result?.toDoubleOrNull() ?: 0.0)
            scope.launch { onBack() }
        } ?: onBack()
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // ── WebView ───────────────────────────────────────────────────────────

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory  = { context ->
                WebView(context).also { wv ->
                    wv.settings.apply {
                        javaScriptEnabled    = true
                        domStorageEnabled    = false   // we need no persistence in the WebView
                        setSupportZoom(true)
                        builtInZoomControls  = true
                        displayZoomControls  = false
                        textZoom             = 100     // disable system font-scale interference
                    }

                    wv.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            val fraction = viewModel.initialScrollFraction()
                            if (fraction > 0.0) {
                                view?.evaluateJavascript(
                                    "window.LeafReader.scrollToFraction($fraction);", null
                                )
                            }
                        }

                        // Block all navigation — this is a reader, not a browser
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ) = true
                    }

                    // Single tap anywhere on the reading surface toggles controls
                    wv.setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_UP) {
                            showControls = !showControls
                        }
                        false // must return false so scrolling still works
                    }

                    // Load the assembled book HTML.
                    // Base URL points at the extracted epub directory so that
                    // any remaining relative asset paths resolve correctly.
                    wv.loadDataWithBaseURL(
                        "file://${state.parsedEpub.extractedDir.absolutePath}/",
                        state.html,
                        "text/html",
                        "UTF-8",
                        null
                    )

                    webViewRef = wv
                }
            },
            update = { wv -> webViewRef = wv }
        )

        // ── 2 px progress bar at the very top ────────────────────────────────

        LinearProgressIndicator(
            progress     = { state.book.lastScrollFraction.toFloat() },
            modifier     = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .align(Alignment.TopCenter),
            color        = accentColor(settings.isDarkMode),
            trackColor   = Color.Transparent,
            strokeCap    = androidx.compose.ui.graphics.StrokeCap.Butt
        )

        // ── Controls sheet (bottom, fade in/out) ──────────────────────────────

        AnimatedVisibility(
            visible  = showControls,
            enter    = fadeIn(animationSpec = tween(120)) +
                       slideInVertically(animationSpec = tween(120)) { it / 4 },
            exit     = fadeOut(animationSpec = tween(120)) +
                       slideOutVertically(animationSpec = tween(120)) { it / 4 },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ControlsSheet(
                settings      = settings,
                chapterCount  = state.chapterCount,
                onBack        = {
                    webViewRef?.evaluateJavascript("window.LeafReader.getScrollFraction();") { result ->
                        viewModel.saveScrollPosition(result?.toDoubleOrNull() ?: 0.0)
                        scope.launch { onBack() }
                    } ?: onBack()
                },
                onToggleDark  = {
                    viewModel.toggleDarkMode()
                    val nowDark = !settings.isDarkMode
                    webViewRef?.evaluateJavascript(
                        "window.LeafReader.setTheme($nowDark);", null
                    )
                },
                onTextSize    = { size ->
                    viewModel.setTextSize(size)
                    webViewRef?.evaluateJavascript(
                        "window.LeafReader.setFontSize($size);", null
                    )
                },
                onFont        = { font ->
                    viewModel.setFont(font)
                    webViewRef?.evaluateJavascript(
                        "window.LeafReader.setFont('${font.cssValue}');", null
                    )
                },
                onChapter     = { index ->
                    webViewRef?.evaluateJavascript(
                        "window.LeafReader.scrollToChapter($index);", null
                    )
                    showControls = false
                }
            )
        }
    }

    // Save scroll position when the composable leaves the composition
    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.evaluateJavascript("window.LeafReader.getScrollFraction();") { result ->
                viewModel.saveScrollPosition(result?.toDoubleOrNull() ?: 0.0)
            }
        }
    }
}

// ── Controls sheet ────────────────────────────────────────────────────────────

@Composable
private fun ControlsSheet(
    settings: ReaderSettings,
    chapterCount: Int,
    onBack: () -> Unit,
    onToggleDark: () -> Unit,
    onTextSize: (Int) -> Unit,
    onFont: (ReaderFont) -> Unit,
    onChapter: (Int) -> Unit
) {
    val dark      = settings.isDarkMode
    val bg        = sheetBg(dark)
    val textClr   = textColor(dark)
    val mutedClr  = mutedColor(dark)
    val accentClr = accentColor(dark)
    val divClr    = divColor(dark)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Row 1: back arrow + dark-mode toggle ──────────────────────────────
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to library",
                    tint = textClr
                )
            }
            Text(
                "Reading options",
                fontSize = 14.sp,
                color    = mutedClr,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            )
            IconButton(onClick = onToggleDark) {
                Icon(
                    imageVector        = if (dark) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = if (dark) "Switch to light mode" else "Switch to dark mode",
                    tint               = textClr
                )
            }
        }

        HorizontalDivider(color = divClr)

        // ── Row 2: text size ──────────────────────────────────────────────────
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("A", fontSize = 13.sp, color = mutedClr)

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ReaderSettings.TEXT_SIZE_OPTIONS.forEach { size ->
                    val selected = settings.textSizeSp == size
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                color = if (selected) accentClr else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .then(
                                if (!selected) Modifier.border(
                                    BorderStroke(1.dp, divClr),
                                    RoundedCornerShape(10.dp)
                                ) else Modifier
                            )
                            .clickable(
                                indication           = null,
                                interactionSource    = remember { MutableInteractionSource() }
                            ) { onTextSize(size) }
                    ) {
                        Text(
                            text     = size.toString(),
                            fontSize = 12.sp,
                            color    = if (selected) Color.White else textClr
                        )
                    }
                }
            }

            Text("A", fontSize = 22.sp, color = mutedClr)
        }

        HorizontalDivider(color = divClr)

        // ── Row 3: font picker ────────────────────────────────────────────────
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReaderFont.entries.forEach { font ->
                val selected = settings.font == font
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .background(
                            color = if (selected) accentClr else Color.Transparent,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .then(
                            if (!selected) Modifier.border(
                                BorderStroke(1.dp, divClr),
                                RoundedCornerShape(10.dp)
                            ) else Modifier
                        )
                        .clickable(
                            indication        = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onFont(font) }
                ) {
                    Text(
                        text      = font.label,
                        fontSize  = 13.sp,
                        fontStyle = if (font == ReaderFont.GEORGIA || font == ReaderFont.LITERATA)
                            FontStyle.Italic else FontStyle.Normal,
                        color     = if (selected) Color.White else textClr
                    )
                }
            }
        }

        // ── Row 4: chapter jump (only if there's more than one chapter) ───────
        if (chapterCount > 1) {
            HorizontalDivider(color = divClr)
            ChapterJumpRow(
                count        = chapterCount,
                textClr      = textClr,
                mutedClr     = mutedClr,
                accentClr    = accentClr,
                divClr       = divClr,
                onChapter    = onChapter
            )
        }

        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ChapterJumpRow(
    count: Int,
    textClr: Color,
    mutedClr: Color,
    accentClr: Color,
    divClr: Color,
    onChapter: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Jump to chapter", fontSize = 12.sp, color = mutedClr, letterSpacing = 0.3.sp)
        // Simple horizontal scroll of chapter numbers
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(count) { index ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .border(BorderStroke(1.dp, divClr), RoundedCornerShape(8.dp))
                        .clickable(
                            indication        = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onChapter(index) }
                ) {
                    Text("${index + 1}", fontSize = 12.sp, color = textClr)
                }
            }
        }
    }
}

// ── Loading & error states ────────────────────────────────────────────────────

@Composable
private fun LoadingPlaceholder(dark: Boolean) {
    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(bgColor(dark)),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color       = accentColor(dark),
            strokeWidth = 2.dp
        )
    }
}

@Composable
private fun ErrorPlaceholder(message: String, dark: Boolean, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor(dark))
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Couldn't open this book",
                fontSize   = 17.sp,
                fontWeight = FontWeight.Light,
                color      = textColor(dark)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                fontSize  = 13.sp,
                color     = mutedColor(dark),
                fontStyle = FontStyle.Italic
            )
            Spacer(Modifier.height(28.dp))
            TextButton(onClick = onBack) {
                Text("Back to library", color = accentColor(dark))
            }
        }
    }
}
