package com.leaf.reader.epub

import java.io.File

/** Bibliographic metadata extracted from the OPF <metadata> block. */
data class EpubMetadata(
    val title: String,
    val author: String,
    val language: String = "en"
)

/**
 * One item in the epub spine (i.e. one reading-order HTML document).
 *
 * @param id       The manifest id ("chapter01", "part2", etc.)
 * @param href     Path relative to the OPF directory ("Text/chapter01.xhtml")
 * @param absolutePath Path relative to the zip root ("OEBPS/Text/chapter01.xhtml")
 * @param chapterDir   Directory portion of absolutePath; used for image-path resolution
 */
data class SpineItem(
    val id: String,
    val href: String,
    val absolutePath: String,
    val chapterDir: String
)

/**
 * Everything we need to render the book: metadata, the ordered spine,
 * and the on-disk directory where all zip contents have been extracted.
 */
data class ParsedEpub(
    val metadata: EpubMetadata,
    val spineItems: List<SpineItem>,
    val extractedDir: File
)
