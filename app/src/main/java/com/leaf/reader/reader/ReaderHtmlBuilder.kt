package com.leaf.reader.reader

import com.leaf.reader.epub.EpubParser
import com.leaf.reader.epub.ParsedEpub
import com.leaf.reader.settings.ReaderFont
import com.leaf.reader.settings.ReaderSettings
import java.io.File

/**
 * Assembles a [ParsedEpub] into a single self-contained HTML document.
 *
 * All chapters are concatenated in spine order inside <section> elements.
 * A master stylesheet overrides epub-specific styles with our reading aesthetic,
 * inspired by Standard Ebooks' online reader.
 *
 * Font size, font family, and dark/light theme can be changed at runtime
 * via the [buildJs] bridge without reloading the document.
 */
class ReaderHtmlBuilder {

    fun build(
        parsedEpub: ParsedEpub,
        parser: EpubParser,
        settings: ReaderSettings
    ): String {
        val chapterBodies = parsedEpub.spineItems.mapIndexedNotNull { index, spineItem ->
            val file = File(parsedEpub.extractedDir, spineItem.absolutePath)
            if (!file.exists()) return@mapIndexedNotNull null
            val html = file.readText(Charsets.UTF_8)
            Pair(index, parser.extractBody(html, spineItem, parsedEpub.extractedDir))
        }

        val chaptersHtml = buildString {
            chapterBodies.forEachIndexed { listIndex, (originalIndex, body) ->
                val isFirst = listIndex == 0
                appendLine(
                    """<section class="chapter${if (isFirst) " chapter-start" else ""}" """ +
                    """id="chapter-$originalIndex">"""
                )
                appendLine(body)
                appendLine("</section>")
                if (listIndex < chapterBodies.lastIndex) {
                    appendLine("""<div class="chapter-divider" aria-hidden="true"></div>""")
                }
            }
        }

        return assembleDocument(
            chaptersHtml = chaptersHtml,
            settings     = settings,
            title        = parsedEpub.metadata.title,
            language     = parsedEpub.metadata.language
        )
    }

    private fun assembleDocument(
        chaptersHtml: String,
        settings: ReaderSettings,
        title: String,
        language: String
    ): String {
        val theme     = if (settings.isDarkMode) "dark" else "light"
        val fontSize  = "${settings.textSizeSp}px"
        val fontValue = settings.font.cssValue

        return """<!DOCTYPE html>
<html lang="${escHtml(language)}" data-theme="$theme">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0">
<title>${escHtml(title)}</title>
<style>
${buildCss(fontValue, fontSize)}
</style>
</head>
<body>
<div class="book-content" id="book-content">
$chaptersHtml
</div>
<script>
${buildJs()}
</script>
</body>
</html>"""
    }

    // ── Master CSS ────────────────────────────────────────────────────────────

    private fun buildCss(fontFamily: String, fontSize: String) = """
:root {
    --bg:        #faf8f4;
    --text:      #1c1b18;
    --muted:     #7a7870;
    --accent:    #8b7355;
    --divider:   rgba(124, 120, 112, 0.22);
    --font:      $fontFamily;
    --size:      $fontSize;
    --leading:   1.70;
    --measure:   68ch;
    --indent:    1.5em;
}

[data-theme="dark"] {
    --bg:      #181816;
    --text:    #e4e0d8;
    --muted:   #8a8880;
    --accent:  #c8a878;
    --divider: rgba(138, 136, 128, 0.18);
}

*, *::before, *::after { box-sizing: border-box; }

html {
    font-size: var(--size);
    /* Disable browser font-scaling so our size slider is the single source of truth */
    -webkit-text-size-adjust: none;
    text-size-adjust: none;
}

body {
    background-color: var(--bg);
    color: var(--text);
    font-family: var(--font);
    line-height: var(--leading);
    padding: 3rem 1.5rem 14rem;
    -webkit-font-smoothing: antialiased;
    /* Smooth only bg/color transitions; scrolling must never animate */
    transition: background-color 0.18s ease, color 0.18s ease;
}

.book-content {
    max-width: var(--measure);
    margin: 0 auto;
}

/* ── Paragraphs ─────────────────────────────────────────────────── */

p {
    margin: 0;
    text-indent: var(--indent);
    -webkit-hyphens: auto;
    hyphens: auto;
}

/* No indent after any heading or on the first para of a chapter */
h1 + p, h2 + p, h3 + p, h4 + p, h5 + p, h6 + p,
.chapter-start p:first-of-type,
p.no-indent, p.first, p.poem {
    text-indent: 0;
}

/* ── Headings ───────────────────────────────────────────────────── */

h1, h2, h3, h4, h5, h6 {
    font-weight: normal;
    line-height: 1.28;
    text-indent: 0 !important;
    text-align: center;
}

h1 { font-size: 1.55em; margin: 3em 0 1.5em;  letter-spacing: 0.01em; }
h2 { font-size: 1.20em; margin: 2.5em 0 1.2em; }
h3 { font-size: 1.05em; margin: 2em 0 1em; font-style: italic; }
h4, h5, h6 { font-size: 1em; margin: 1.8em 0 0.8em; font-style: italic; }

/* The very first heading (usually the book title) gets less top margin */
.chapter-start > h1:first-child,
.chapter-start > h2:first-child {
    margin-top: 0.5em;
}

/* ── Chapter divider ────────────────────────────────────────────── */

.chapter-divider {
    display: block;
    height: 1px;
    background: var(--divider);
    margin: 5.5rem auto;
    max-width: 6rem;
    border: none;
}

/* ── Drop-cap on the first paragraph of the whole book ─────────── */

.chapter-start p:first-of-type::first-letter {
    float: left;
    font-size: 3.2em;
    line-height: 0.82;
    margin: 0.08em 0.12em 0 0;
}

/* ── Inline ─────────────────────────────────────────────────────── */

em, i    { font-style: italic; }
strong, b{ font-weight: bold; }
small    { font-size: 0.85em; }
abbr     { letter-spacing: 0.04em; }
sup, sub { font-size: 0.75em; line-height: 0; position: relative; vertical-align: baseline; }
sup      { top: -0.4em; }
sub      { bottom: -0.25em; }

/* ── Links ──────────────────────────────────────────────────────── */

a { color: var(--accent); text-decoration: none; }

/* ── Blockquotes ────────────────────────────────────────────────── */

blockquote {
    margin: 1.8em 0 1.8em 1.8em;
    color: var(--muted);
    font-style: italic;
    padding-left: 1em;
    border-left: 2px solid var(--divider);
}
blockquote p { text-indent: 0; }

/* ── Rules / scene breaks ───────────────────────────────────────── */

hr {
    border: none;
    text-align: center;
    color: var(--muted);
    margin: 2.5em 0;
    height: auto;
}
hr::after {
    content: '✦';
    letter-spacing: 0.4em;
    font-size: 0.85em;
}

/* ── Images ─────────────────────────────────────────────────────── */

img {
    max-width: 100%;
    height: auto;
    display: block;
    margin: 2.5em auto;
}
figure     { margin: 2.5em 0; text-align: center; }
figcaption { font-size: 0.85em; color: var(--muted); margin-top: 0.6em; font-style: italic; }

/* ── Lists ──────────────────────────────────────────────────────── */

ul, ol { margin: 1.2em 0 1.2em 1.5em; padding: 0; }
li     { margin-bottom: 0.35em; text-indent: 0; }

/* ── Tables ─────────────────────────────────────────────────────── */

table  { width: 100%; border-collapse: collapse; margin: 2em 0; font-size: 0.88em; }
th, td { text-align: left; padding: 0.6em 0.4em; border-bottom: 1px solid var(--divider); text-indent: 0; }
th     { font-weight: bold; color: var(--muted); font-size: 0.78em; letter-spacing: 0.05em; text-transform: uppercase; }

/* ── Code ───────────────────────────────────────────────────────── */

pre, code { font-family: 'Courier New', monospace; font-size: 0.88em; }
code      { background: var(--divider); padding: 0.1em 0.3em; border-radius: 3px; }
pre       { background: var(--divider); padding: 1em 1.2em; overflow-x: auto; line-height: 1.5; border-radius: 4px; }
pre code  { background: none; padding: 0; }

/* ── Poem / verse ───────────────────────────────────────────────── */

.poem, .verse { margin: 1.5em 0 1.5em 2em; }
.poem p, .verse p { text-indent: 0; margin-bottom: 0.2em; }

/* ── EPUB nav elements should never render as content ───────────── */

nav { display: none; }
""".trimIndent()

    // ── JavaScript bridge ─────────────────────────────────────────────────────

    private fun buildJs() = """
window.LeafReader = {
    setTheme: function(dark) {
        document.documentElement.setAttribute('data-theme', dark ? 'dark' : 'light');
    },
    setFontSize: function(px) {
        document.documentElement.style.fontSize = px + 'px';
    },
    setFont: function(fontFamily) {
        document.documentElement.style.setProperty('--font', fontFamily);
    },
    getScrollFraction: function() {
        var el = document.documentElement;
        var max = el.scrollHeight - el.clientHeight;
        return max > 0 ? (el.scrollTop / max) : 0;
    },
    scrollToFraction: function(fraction) {
        var el = document.documentElement;
        var max = el.scrollHeight - el.clientHeight;
        el.scrollTop = Math.round(fraction * max);
    },
    scrollToChapter: function(index) {
        var el = document.getElementById('chapter-' + index);
        if (el) el.scrollIntoView({ behavior: 'smooth' });
    }
};
""".trimIndent()

    private fun escHtml(text: String) = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
