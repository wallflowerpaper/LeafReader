package com.leaf.reader.library

import kotlinx.serialization.Serializable

/**
 * Represents a book in the user's library.
 *
 * [filePath] is stored as a URI string (content:// scheme) so we can
 * reopen the file across app sessions without holding a persisted
 * file descriptor. The user must grant persistent read permission
 * (handled via FLAG_GRANT_READ_URI_PERMISSION in the file picker).
 */
@Serializable
data class Book(
    val id: String,
    val title: String,
    val author: String,
    val filePath: String,
    val addedAt: Long = System.currentTimeMillis(),
    /** Fractional scroll position (0.0–1.0) for resuming reading. */
    val lastScrollFraction: Double = 0.0,
    val lastReadAt: Long? = null
)
