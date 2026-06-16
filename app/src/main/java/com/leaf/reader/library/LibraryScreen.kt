package com.leaf.reader.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private val BgColor   = Color(0xFFFAF8F4)
private val TextColor = Color(0xFF1C1B18)
private val MutedColor = Color(0xFF7A7870)
private val AccentColor = Color(0xFF8B7355)
private val DividerColor = Color(0x1A1C1B18)

@Composable
fun LibraryScreen(
    onBookOpen: (String) -> Unit,
    viewModel: LibraryViewModel = viewModel()
) {
    val books       by viewModel.books.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.importEpub(it) } }

    // Show error snackbar if import fails
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(importState) {
        if (importState is ImportState.Failure) {
            snackbarHostState.showSnackbar(
                message = "Couldn't open that file — is it a valid EPUB?",
                duration = SnackbarDuration.Short
            )
            viewModel.dismissImportError()
        }
    }

    Scaffold(
        containerColor = BgColor,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (books.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { filePicker.launch("application/epub+zip") },
                    containerColor = TextColor,
                    contentColor = BgColor,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 2.dp
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Import EPUB")
                }
            }
        }
    ) { innerPadding ->
        if (importState is ImportState.Loading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AccentColor, strokeWidth = 2.dp)
            }
            return@Scaffold
        }

        if (books.isEmpty()) {
            EmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                onImport = { filePicker.launch("application/epub+zip") }
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    top    = 64.dp,
                    bottom = 96.dp,
                    start  = 24.dp,
                    end    = 24.dp
                )
            ) {
                item {
                    Text(
                        text       = "Library",
                        fontSize   = 30.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = (-0.5).sp,
                        color      = TextColor,
                        modifier   = Modifier.padding(bottom = 28.dp)
                    )
                }
                items(
                    items = books.sortedByDescending { it.lastReadAt ?: it.addedAt },
                    key   = { it.id }
                ) { book ->
                    BookRow(
                        book        = book,
                        onClick     = { onBookOpen(book.id) },
                        onDelete    = { viewModel.removeBook(book.id) }
                    )
                    HorizontalDivider(color = DividerColor, thickness = 1.dp)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookRow(
    book: Book,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick     = onClick,
                onLongClick = { showDialog = true }
            )
            .padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Reading-progress bar: thin vertical accent on the left
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(46.dp)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(DividerColor, RoundedCornerShape(2.dp))
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(book.lastScrollFraction.toFloat())
                    .align(Alignment.BottomStart)
                    .background(AccentColor, RoundedCornerShape(2.dp))
            )
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = book.title,
                fontSize = 16.sp,
                color    = TextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text      = book.author,
                fontSize  = 13.sp,
                fontStyle = FontStyle.Italic,
                color     = MutedColor,
                maxLines  = 1,
                overflow  = TextOverflow.Ellipsis
            )
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor   = Color(0xFFFAF8F4),
            title = { Text("Remove "${book.title}"?", color = TextColor) },
            text  = { Text("It will be removed from your library.", color = MutedColor) },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDialog = false }) {
                    Text("Remove", color = AccentColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel", color = MutedColor)
                }
            }
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier, onImport: () -> Unit) {
    Column(
        modifier            = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text          = "Leaf",
            fontSize      = 40.sp,
            fontWeight    = FontWeight.Light,
            letterSpacing = 3.sp,
            color         = TextColor
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text      = "your epub reader",
            fontSize  = 15.sp,
            fontStyle = FontStyle.Italic,
            color     = MutedColor
        )
        Spacer(Modifier.height(40.dp))
        OutlinedButton(
            onClick = onImport,
            colors  = ButtonDefaults.outlinedButtonColors(contentColor = TextColor),
            border  = ButtonDefaults.outlinedButtonBorder
        ) {
            Text("Open an EPUB", letterSpacing = 0.5.sp)
        }
    }
}
