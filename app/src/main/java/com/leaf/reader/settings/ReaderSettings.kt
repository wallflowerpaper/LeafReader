package com.leaf.reader.settings

enum class ReaderFont(val label: String, val cssValue: String) {
    GEORGIA(
        label    = "Georgia",
        cssValue = "'Georgia', 'Times New Roman', serif"
    ),
    LITERATA(
        label    = "Literata",
        // Literata ships with recent Android versions; fall back to Georgia
        cssValue = "'Literata', 'Georgia', serif"
    ),
    SANS(
        label    = "Sans",
        cssValue = "-apple-system, 'Roboto', 'Helvetica Neue', sans-serif"
    )
}

data class ReaderSettings(
    val isDarkMode: Boolean  = false,
    val textSizeSp: Int      = 19,
    val font: ReaderFont     = ReaderFont.GEORGIA
) {
    companion object {
        /** Discrete size steps shown in the controls panel. */
        val TEXT_SIZE_OPTIONS = listOf(15, 17, 19, 21, 24)
    }
}
