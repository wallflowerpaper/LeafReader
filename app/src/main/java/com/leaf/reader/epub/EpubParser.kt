package com.leaf.reader.epub

import android.content.Context
import android.net.Uri
import android.util.Log
import org.w3c.dom.Element
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses an EPUB file (given as a content URI) into a [ParsedEpub].
 *
 * Strategy:
 *   1. Extract all zip entries into a per-book cache directory.
 *   2. Read META-INF/container.xml to locate the OPF file.
 *   3. Parse the OPF for metadata, manifest, and spine order.
 *   4. Return a [ParsedEpub] ready for HTML assembly.
 *
 * The extracted directory is retained so the WebView can resolve
 * relative asset URLs (images, fonts) via a file:// base URL.
 */
class EpubParser(private val context: Context) {

    companion object {
        private const val TAG = "EpubParser"
        private const val CONTAINER_XML = "META-INF/container.xml"
    }

    fun parse(uri: Uri, bookId: String): ParsedEpub {
        val extractDir = File(context.cacheDir, "epub_$bookId").apply {
            deleteRecursively()
            mkdirs()
        }

        extractZip(uri, extractDir)

        val containerFile = File(extractDir, CONTAINER_XML)
        val opfPath = parseContainerXml(containerFile)
        // e.g. "OEBPS/content.opf" → opfDir = "OEBPS"
        val opfDir = opfPath.substringBeforeLast("/", missingDelimiterValue = "")

        val opfFile = File(extractDir, opfPath)
        val (metadata, spineItems) = parseOpf(opfFile, opfDir, extractDir)

        return ParsedEpub(
            metadata = metadata,
            spineItems = spineItems,
            extractedDir = extractDir
        )
    }

    // ── Zip extraction ───────────────────────────────────────────────────────

    private fun extractZip(uri: Uri, destDir: File) {
        val stream: InputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open input stream for URI: $uri")

        ZipInputStream(stream.buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val destFile = File(destDir, entry.name)
                    // Guard against zip-slip path traversal attacks
                    if (destFile.canonicalPath.startsWith(destDir.canonicalPath + File.separator)) {
                        destFile.parentFile?.mkdirs()
                        FileOutputStream(destFile).use { out -> zis.copyTo(out) }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    // ── container.xml → OPF path ─────────────────────────────────────────────

    private fun parseContainerXml(file: File): String {
        val doc = parseXmlFile(file)
        val rootfiles = doc.getElementsByTagName("rootfile")
        if (rootfiles.length == 0) throw IllegalStateException("No <rootfile> in container.xml")
        val rootfile = rootfiles.item(0) as Element
        return rootfile.getAttribute("full-path")
            .ifEmpty { throw IllegalStateException("Empty full-path in container.xml") }
    }

    // ── OPF → metadata + spine ───────────────────────────────────────────────

    private fun parseOpf(
        opfFile: File,
        opfDir: String,
        extractDir: File
    ): Pair<EpubMetadata, List<SpineItem>> {

        val doc = parseXmlFile(opfFile)

        // --- Metadata ---
        fun firstText(localName: String): String? =
            doc.getElementsByTagNameNS("*", localName).item(0)?.textContent?.trim()
                ?: doc.getElementsByTagName("dc:$localName").item(0)?.textContent?.trim()

        val title  = firstText("title")   ?: "Untitled"
        val author = firstText("creator") ?: "Unknown Author"
        val lang   = firstText("language") ?: "en"

        // --- Manifest: id → href ---
        val manifest = mutableMapOf<String, String>()
        val manifestItems = doc.getElementsByTagName("item")
        for (i in 0 until manifestItems.length) {
            val el = manifestItems.item(i) as? Element ?: continue
            val id   = el.getAttribute("id")
            val href = el.getAttribute("href")
            if (id.isNotEmpty() && href.isNotEmpty()) manifest[id] = href
        }

        // --- Spine: ordered list of idrefs ---
        val spineItems = mutableListOf<SpineItem>()
        val itemRefs = doc.getElementsByTagName("itemref")
        for (i in 0 until itemRefs.length) {
            val el = itemRefs.item(i) as? Element ?: continue
            if (el.getAttribute("linear") == "no") continue

            val idref = el.getAttribute("idref")
            val href  = manifest[idref] ?: run {
                Log.w(TAG, "Spine idref '$idref' not found in manifest, skipping")
                continue
            }

            // Resolve to a path relative to the zip root
            val absolutePath = if (opfDir.isEmpty()) href else "$opfDir/$href"
            // Directory containing the spine HTML file (for image resolution)
            val chapterDir = absolutePath.substringBeforeLast("/", missingDelimiterValue = "")

            spineItems.add(
                SpineItem(
                    id           = idref,
                    href         = href,
                    absolutePath = absolutePath,
                    chapterDir   = chapterDir
                )
            )
        }

        if (spineItems.isEmpty()) Log.w(TAG, "No spine items found in $opfFile")

        return Pair(EpubMetadata(title, author, lang), spineItems)
    }

    // ── HTML body extraction ─────────────────────────────────────────────────

    /**
     * Strips everything outside <body> … </body> and rewrites relative
     * image src attributes to absolute file:// paths so the WebView's
     * single base URL doesn't need to match each chapter's directory.
     */
    fun extractBody(html: String, spineItem: SpineItem, extractedDir: File): String {
        val bodyStart = html.indexOf("<body", ignoreCase = true)
        val bodyEnd   = html.lastIndexOf("</body>", ignoreCase = true)

        val raw = when {
            bodyStart == -1 -> html
            else -> {
                val contentStart = html.indexOf('>', bodyStart) + 1
                val contentEnd   = if (bodyEnd != -1) bodyEnd else html.length
                html.substring(contentStart, contentEnd)
            }
        }.trim()

        return resolveImagePaths(raw, spineItem, extractedDir)
    }

    /**
     * Rewrites relative src="…" values to absolute file:// paths.
     * This lets all chapters share the same WebView base URL.
     */
    private fun resolveImagePaths(html: String, spineItem: SpineItem, extractedDir: File): String {
        val chapterDirFile = if (spineItem.chapterDir.isEmpty()) {
            extractedDir
        } else {
            File(extractedDir, spineItem.chapterDir)
        }

        return html.replace(Regex("""(?i)(src=["'])([^"']+)(["'])""")) { match ->
            val src = match.groupValues[2]
            when {
                // Already absolute — leave untouched
                src.startsWith("http", ignoreCase = true) -> match.value
                src.startsWith("data:",  ignoreCase = true) -> match.value
                src.startsWith("file:",  ignoreCase = true) -> match.value
                else -> {
                    val resolved = File(chapterDirFile, src).canonicalPath
                    "${match.groupValues[1]}file://$resolved${match.groupValues[3]}"
                }
            }
        }
    }

    // ── XML helper ───────────────────────────────────────────────────────────

    private fun parseXmlFile(file: File) =
        DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(file)
}
