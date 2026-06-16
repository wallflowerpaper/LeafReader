package com.leaf.reader.library

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Scoped to this file to avoid naming conflicts. */
private val Context.libraryStore: DataStore<Preferences>
    by preferencesDataStore(name = "library")

class LibraryRepository(private val context: Context) {

    companion object {
        private val KEY_BOOKS = stringPreferencesKey("books_json")
        private val json = Json { ignoreUnknownKeys = true }
    }

    val books: Flow<List<Book>> = context.libraryStore.data.map { prefs ->
        decode(prefs[KEY_BOOKS])
    }

    suspend fun addBook(book: Book) {
        context.libraryStore.edit { prefs ->
            val current = decode(prefs[KEY_BOOKS])
            val updated = current.filter { it.id != book.id } + book
            prefs[KEY_BOOKS] = json.encodeToString(updated)
        }
    }

    suspend fun updateScrollPosition(bookId: String, fraction: Double) {
        context.libraryStore.edit { prefs ->
            val updated = decode(prefs[KEY_BOOKS]).map { book ->
                if (book.id == bookId) book.copy(
                    lastScrollFraction = fraction,
                    lastReadAt = System.currentTimeMillis()
                ) else book
            }
            prefs[KEY_BOOKS] = json.encodeToString(updated)
        }
    }

    suspend fun removeBook(bookId: String) {
        context.libraryStore.edit { prefs ->
            val updated = decode(prefs[KEY_BOOKS]).filter { it.id != bookId }
            prefs[KEY_BOOKS] = json.encodeToString(updated)
        }
    }

    private fun decode(raw: String?): List<Book> {
        if (raw.isNullOrBlank()) return emptyList()
        return try { json.decodeFromString(raw) } catch (e: Exception) { emptyList() }
    }
}
