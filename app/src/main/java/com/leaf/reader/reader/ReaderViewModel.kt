package com.leaf.reader.reader

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.leaf.reader.epub.EpubParser
import com.leaf.reader.epub.ParsedEpub
import com.leaf.reader.library.Book
import com.leaf.reader.library.LibraryRepository
import com.leaf.reader.settings.ReaderFont
import com.leaf.reader.settings.ReaderSettings
import com.leaf.reader.settings.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ── UI state ─────────────────────────────────────────────────────────────────

sealed class ReaderUiState {
    object Loading : ReaderUiState()
    data class Ready(
        val html: String,
        val book: Book,
        val parsedEpub: ParsedEpub,
        val chapterCount: Int
    ) : ReaderUiState()
    data class Error(val message: String) : ReaderUiState()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class ReaderViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val bookId: String = checkNotNull(savedStateHandle["bookId"])

    private val parser      = EpubParser(application)
    private val htmlBuilder = ReaderHtmlBuilder()
    private val settingsDs  = SettingsDataStore(application)
    private val libraryRepo = LibraryRepository(application)

    // Settings stream — always available (defaults used before DataStore emits)
    val settings: StateFlow<ReaderSettings> = settingsDs.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderSettings())

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    init {
        loadBook()
    }

    // ── Book loading ──────────────────────────────────────────────────────────

    private fun loadBook() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val book = libraryRepo.books.first().find { it.id == bookId }
                    ?: throw IllegalStateException("Book not found in library")

                val parsed = parser.parse(Uri.parse(book.filePath), bookId)

                // Build the HTML with whatever settings are current at load time.
                // Runtime changes (dark mode, font, size) are applied via JS bridge
                // without rebuilding the full document.
                val html = htmlBuilder.build(parsed, parser, settings.value)

                _uiState.value = ReaderUiState.Ready(
                    html         = html,
                    book         = book,
                    parsedEpub   = parsed,
                    chapterCount = parsed.spineItems.size
                )
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = ReaderUiState.Error(e.message ?: "Failed to open book")
            }
        }
    }

    // ── Settings mutations (also applied live via JS in ReaderScreen) ─────────

    fun toggleDarkMode() {
        viewModelScope.launch {
            settingsDs.setDarkMode(!settings.value.isDarkMode)
        }
    }

    fun setTextSize(size: Int) {
        viewModelScope.launch { settingsDs.setTextSize(size) }
    }

    fun setFont(font: ReaderFont) {
        viewModelScope.launch { settingsDs.setFont(font) }
    }

    // ── Scroll persistence ────────────────────────────────────────────────────

    /**
     * Initial scroll position (0.0–1.0) to restore when the WebView finishes loading.
     * Returns 0 if the book hasn't been opened before.
     */
    fun initialScrollFraction(): Double =
        (_uiState.value as? ReaderUiState.Ready)?.book?.lastScrollFraction ?: 0.0

    /**
     * Called from the screen whenever we're about to leave the reader,
     * so the reading position is remembered for next time.
     */
    fun saveScrollPosition(fraction: Double) {
        viewModelScope.launch {
            libraryRepo.updateScrollPosition(bookId, fraction)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // We intentionally keep the extracted epub dir in cache so re-opening
        // the book is instant. Android will evict cache as needed.
    }
}
