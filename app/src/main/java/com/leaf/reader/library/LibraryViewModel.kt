package com.leaf.reader.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.leaf.reader.epub.EpubParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

sealed class ImportState {
    object Idle    : ImportState()
    object Loading : ImportState()
    data class Failure(val message: String) : ImportState()
}

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LibraryRepository(application)
    private val parser     = EpubParser(application)

    val books: StateFlow<List<Book>> = repository.books
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    fun importEpub(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _importState.value = ImportState.Loading
            try {
                val bookId = UUID.randomUUID().toString()
                val parsed = parser.parse(uri, bookId)
                val book = Book(
                    id       = bookId,
                    title    = parsed.metadata.title,
                    author   = parsed.metadata.author,
                    filePath = uri.toString()
                )
                repository.addBook(book)
                _importState.value = ImportState.Idle
            } catch (e: Exception) {
                e.printStackTrace()
                _importState.value = ImportState.Failure(e.message ?: "Unknown error")
            }
        }
    }

    fun dismissImportError() {
        _importState.value = ImportState.Idle
    }

    fun removeBook(bookId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeBook(bookId)
        }
    }
}
