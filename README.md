# Leaf — Minimalist EPUB Reader

A single-page offline EPUB reader for Android. Every chapter flows into one long, beautiful, scrollable document. No pages. No chapters as separate screens. Just the text.

---

## Design principles

- **One long page** — the entire book rendered as continuous scrollable HTML
- **Invisible UI** — tap anywhere to reveal controls, tap again to dismiss
- **Standard Ebooks aesthetic** — warm off-white paper, constrained measure (~68ch), generous leading, drop-cap on the opening paragraph
- **Zero stutter** — only `opacity` and `translate` animations; WebView native scrolling; no custom scroll logic
- **Offline first** — no accounts, no sync, no cloud

---

## Setup

### Requirements
- Android Studio Ladybug or newer
- Android SDK 35
- Kotlin 2.0+
- Gradle 8.9+

### Steps

1. **Clone / unzip** this project into a folder
2. Add your SDK path to `local.properties`:
   ```
   sdk.dir=/Users/yourname/Library/Android/sdk
   ```
   *(Android Studio creates this automatically if you open the project first.)*
3. **Open** the project root in Android Studio
4. Let Gradle sync finish
5. **Run** on a device or emulator (API 26+)

---

## Project structure

```
app/src/main/java/com/leaf/reader/
├── MainActivity.kt              Single Activity — edge-to-edge Compose host
├── navigation/
│   └── AppNavigation.kt         Library → Reader nav graph
├── epub/
│   ├── EpubModels.kt            Data classes for parsed epub content
│   └── EpubParser.kt            Unzip + OPF parsing + body extraction
├── reader/
│   ├── ReaderHtmlBuilder.kt     Assembles one long HTML doc with full CSS
│   ├── ReaderViewModel.kt       Book loading, settings, scroll persistence
│   └── ReaderScreen.kt          WebView + tap-to-reveal controls overlay
├── library/
│   ├── Book.kt                  Serializable book model
│   ├── LibraryRepository.kt     DataStore-backed book list (stored as JSON)
│   ├── LibraryViewModel.kt      Import, remove, list
│   └── LibraryScreen.kt         Clean list with per-book progress bars
└── settings/
    ├── ReaderSettings.kt        Font enum + settings data class
    └── SettingsDataStore.kt     Persists dark mode, font, text size
```

---

## How the reader works

1. User picks an `.epub` file via the system file picker
2. `EpubParser` extracts the zip to `context.cacheDir/epub_<id>/`
3. OPF is parsed to find the spine (reading order)
4. `ReaderHtmlBuilder` reads each spine HTML file, strips `<head>` and `<body>` tags, rewrites image paths to `file://` absolute URLs, and concatenates everything into one master HTML document
5. The document is loaded into a `WebView` via `loadDataWithBaseURL`, with the extracted epub directory as the base URL
6. A JavaScript bridge (`window.LeafReader`) lets Kotlin change theme, font, and size live without reloading

---

## Customising the aesthetic

All visual design is in `ReaderHtmlBuilder.kt` inside the `buildCss()` function. Key CSS variables:

| Variable     | Default         | Purpose                      |
|--------------|-----------------|------------------------------|
| `--bg`       | `#faf8f4`       | Page background (light mode) |
| `--text`     | `#1c1b18`       | Body text                    |
| `--measure`  | `68ch`          | Max line width               |
| `--leading`  | `1.70`          | Line height                  |
| `--indent`   | `1.5em`         | Paragraph indent             |

---

## Known limitations (v1)

- **No highlights or annotations** — planned for v2
- **No search** — planned for v2
- **Chapter jump** shows numbers only, not chapter titles — titles require parsing epub TOC (NCX/nav.xhtml)
- **DRM-protected EPUBs** will not open — by design
- **Very large books** (1000+ pages with many images) may take a moment to load on first open

---

## License

MIT
