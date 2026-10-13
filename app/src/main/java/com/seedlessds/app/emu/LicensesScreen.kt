package com.seedlessds.app.emu

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.seedlessds.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val close = { if (selected == null) onBack() else selected = null }
    BackHandler(onBack = close)
    val documents by produceState<List<String>?>(null, context) {
        value = withContext(Dispatchers.IO) {
            try { context.assets.list("licenses")?.filter { it.endsWith(".txt") }?.sorted().orEmpty() }
            catch (_: java.io.IOException) { emptyList() }
        }
    }
    val paragraphs by produceState<List<String>?>(null, selected, context) {
        value = null
        val file = selected
        if (file != null) value = withContext(Dispatchers.IO) {
            try { context.assets.open("licenses/$file").bufferedReader().use { it.readText() }.split(Regex("\\r?\\n\\r?\\n")) }
            catch (_: java.io.IOException) { listOf(context.getString(R.string.licenses_load_error)) }
        }
    }
    val listState = rememberLazyListState()
    val cursor = rememberMenuCursor(
        count = if (selected == null) documents?.size ?: 0 else 0,
        onAccept = { selected = documents?.getOrNull(it) },
        onCancel = close
    )
    cursor.KeepVisible(listState)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    SeedlessScreenScaffold(
        title = selected?.removeSuffix(".txt") ?: stringResource(R.string.licenses_title),
        subtitle = stringResource(R.string.licenses_title),
        onBack = close,
        modifier = Modifier.fillMaxSize().focusRequester(focus)
            .onPreviewKeyEvent { event ->
                event.type == KeyEventType.KeyDown && MenuKeyRouter.dispatch(event.nativeKeyEvent.keyCode)
            }.focusable()
    ) { padding ->
        if (selected == null) {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(18.dp), state = listState) {
                if (documents == null) item { CircularProgressIndicator() }
                else if (documents!!.isEmpty()) item { Text(stringResource(R.string.licenses_load_error)) }
                itemsIndexed(documents.orEmpty(), key = { _, file -> file }) { index, file ->
                    TextButton(
                        onClick = { cursor.select(index); selected = file },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = if (cursor.index == index) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
                        )
                    ) {
                        Text(file.removeSuffix(".txt"))
                    }
                }
            }
        } else {
            SelectionContainer {
                LazyColumn(Modifier.fillMaxSize().padding(padding).padding(18.dp)) {
                    if (paragraphs == null) item { CircularProgressIndicator() }
                    items(paragraphs.orEmpty()) { paragraph -> Text(paragraph, modifier = Modifier.padding(bottom = 12.dp)) }
                }
            }
        }
    }
}
