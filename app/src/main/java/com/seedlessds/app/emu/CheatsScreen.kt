package com.seedlessds.app.emu

import com.seedlessds.app.R
import com.seedlessds.core.SeedlessCore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun jstr(b: ByteArray?): String {
    if (b == null) return ""
    val n = b.indexOf(0).let { if (it < 0) b.size else it }
    return String(b, 0, n, Charsets.UTF_8).trim()
}

private data class DbCheat(val idx: Int, val name: String, val note: String, val enabled: Boolean, val folderId: Int)
private data class DbFolder(val idx: Int, val name: String, val note: String, val multiSelect: Boolean)
private data class CustomCheat(val idx: Int, val name: String, val enabled: Boolean, val codes: IntArray)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheatsScreen(onBack: () -> Unit) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    if (editing != null) {
        CheatEditorScreen(
            editIdx = editing!!,
            onDone = { editing = null; refresh++ },
            onBack = { editing = null }
        )
        return
    }

    androidx.activity.compose.BackHandler { SeedlessCore.cheatUpdate(true); onBack() }

    val folders = remember(refresh) { (0 until SeedlessCore.cheatFolderCount()).map { f ->
        DbFolder(f, jstr(SeedlessCore.cheatFolderName(f)), jstr(SeedlessCore.cheatFolderNote(f)),
            SeedlessCore.cheatFolderMultiSelect(f))
    } }
    val cheats = remember(refresh) { (0 until SeedlessCore.cheatCount()).map { i ->
        DbCheat(i, jstr(SeedlessCore.cheatName(i)), jstr(SeedlessCore.cheatNote(i)),
            SeedlessCore.cheatEnabled(i), SeedlessCore.cheatFolderId(i))
    } }
    val customs = remember(refresh) { (0 until SeedlessCore.cheatCustomCount()).map { i ->
        CustomCheat(i, jstr(SeedlessCore.cheatCustomName(i)), SeedlessCore.cheatCustomEnabled(i),
            SeedlessCore.cheatCustomData(i) ?: IntArray(0))
    } }

    fun toggleDb(c: DbCheat, on: Boolean) {
        val folder = folders.getOrNull(c.folderId)
        if (on && folder != null && !folder.multiSelect) {
            cheats.filter { it.folderId == c.folderId && it.idx != c.idx && it.enabled }
                .forEach { SeedlessCore.cheatSetEnabled(it.idx, false) }
        }
        SeedlessCore.cheatSetEnabled(c.idx, on); refresh++
    }

    val colors = seedless
    val activeCount = cheats.count { it.enabled } + customs.count { it.enabled }

    val rows = remember(refresh, tab) {
        val out = ArrayList<CheatItem>()
        if (tab == 0) {
            folders.forEach { folder ->
                val inFolder = cheats.filter { it.folderId == folder.idx }
                if (inFolder.isNotEmpty()) {
                    out.add(CheatItem.Header(folder.name, if (folder.multiSelect) null else uiContext.getString(R.string.cheats_only_one)))
                    inFolder.forEach { out.add(CheatItem.Db(it)) }
                }
            }
            val ungrouped = cheats.filter { it.folderId < 0 || it.folderId >= folders.size }
            if (ungrouped.isNotEmpty()) {
                out.add(CheatItem.Header(uiContext.getString(R.string.cheats_other), null))
                ungrouped.forEach { out.add(CheatItem.Db(it)) }
            }
        } else {
            customs.forEach { out.add(CheatItem.Custom(it)) }
        }
        out
    }
    val selectable = remember(rows) { rows.indices.filter { rows[it] !is CheatItem.Header } }
    val listState = rememberLazyListState()
    val cursor = rememberMenuCursor(
        count = selectable.size,
        onAccept = { i ->
            when (val item = rows.getOrNull(selectable.getOrElse(i) { -1 })) {
                is CheatItem.Db -> toggleDb(item.cheat, !item.cheat.enabled)
                is CheatItem.Custom -> editing = item.cheat.idx
                else -> Unit
            }
        },
        onCancel = { SeedlessCore.cheatUpdate(true); onBack() },
        onTab = { dir -> tab = (tab + dir).coerceIn(0, 1) },
    )
    androidx.compose.runtime.LaunchedEffect(cursor.index, rows) {
        selectable.getOrNull(cursor.index)?.let { runCatching { listState.animateScrollToItem(it) } }
    }

    SeedlessScreenScaffold(
        title = uiContext.getString(R.string.common_cheats),
        subtitle = if (activeCount > 0) uiContext.getString(R.string.common_active, activeCount) else uiContext.getString(R.string.cheats_none_active),
        onBack = { SeedlessCore.cheatUpdate(true); onBack() },
        hints = listOf(
            GamepadHint(null, uiContext.getString(R.string.common_navigate)),
            GamepadHint(uiContext.getString(R.string.button_a), uiContext.getString(R.string.cheats_toggle)),
            GamepadHint(uiContext.getString(R.string.button_b), uiContext.getString(R.string.common_back)),
        ),
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            Column(Modifier.fillMaxSize()) {
                SeedlessSegmented(
                    options = listOf(uiContext.getString(R.string.cheats_database), uiContext.getString(R.string.cheats_custom)),
                    selected = tab,
                    onSelect = { tab = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
                Box(Modifier.weight(1f)) {
                    CheatList(
                        rows = rows,
                        state = listState,
                        selected = selectable.getOrNull(cursor.index) ?: -1,
                        emptyMsg = if (tab == 0) uiContext.getString(R.string.cheats_no_cheats_in_the_database_for_this_game)
                            else uiContext.getString(R.string.cheats_add_your_own_cheats_with_the_button),
                        onToggleDb = ::toggleDb,
                        onToggleCustom = { c, on ->
                            SeedlessCore.cheatSetCustomEnabled(c.idx, on); refresh++
                        },
                        onEditCustom = { editing = it.idx },
                    )
                }
                Text(
                    text = uiContext.getString(R.string.cheats_cheats_apply_when_you_resume),
                    color = colors.text3,
                    fontFamily = SeedlessMono,
                    fontSize = 8.5.sp,
                    modifier = Modifier.padding(start = 18.dp, bottom = 6.dp),
                )
            }
            if (tab == 1) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(20.dp)
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.green)
                        .clickable { editing = -1 },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Add, uiContext.getString(R.string.cheats_add_cheat), tint = Color.White, modifier = Modifier.size(24.dp)) }
            }
        }
    }
}

@Composable
fun SeedlessSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = seedless
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(11.dp))
            .padding(3.dp),
    ) {
        options.forEachIndexed { index, label ->
            val active = index == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (active) colors.green else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label.uppercase(),
                    color = if (active) Color.White else colors.text3,
                    fontFamily = SeedlessMono,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                )
            }
        }
    }
}

private sealed interface CheatItem {
    data class Header(val name: String, val note: String?) : CheatItem
    data class Db(val cheat: DbCheat) : CheatItem
    data class Custom(val cheat: CustomCheat) : CheatItem
}

@Composable
private fun CheatList(
    rows: List<CheatItem>,
    state: LazyListState,
    selected: Int,
    emptyMsg: String,
    onToggleDb: (DbCheat, Boolean) -> Unit,
    onToggleCustom: (CustomCheat, Boolean) -> Unit,
    onEditCustom: (CustomCheat) -> Unit,
) {
    if (rows.isEmpty()) {
        EmptyMsg(emptyMsg)
        return
    }
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize()
            .verticalScrollbar(state, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 24.dp),
    ) {
        itemsIndexed(rows) { i, row ->
            when (row) {
                is CheatItem.Header -> FolderHeader(row.name, row.note)
                is CheatItem.Db -> CheatRow(row.cheat.name, row.cheat.note, row.cheat.enabled, i == selected) {
                    onToggleDb(row.cheat, it)
                }
                is CheatItem.Custom -> CustomCheatRow(
                    c = row.cheat,
                    selected = i == selected,
                    onToggle = { onToggleCustom(row.cheat, it) },
                    onEdit = { onEditCustom(row.cheat) },
                )
            }
        }
    }
}

@Composable
private fun CustomCheatRow(
    c: CustomCheat,
    selected: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val colors = seedless
    SeedlessRow(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp),
        minHeight = 54.dp,
        selected = selected,
        onClick = onEdit,
    ) {
        IconBox(Icons.Rounded.Code)
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 12.dp)) {
            Text(
                text = c.name.ifEmpty { uiContext.getString(R.string.cheats_unnamed_cheat) },
                color = colors.text,
                fontFamily = Manrope,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = uiContext.resources.getQuantityString(R.plurals.cheats_lines_of_code, c.codes.size / 2, c.codes.size / 2),
                color = colors.text3,
                fontFamily = SeedlessMono,
                fontSize = 9.sp,
            )
        }
        SeedlessSwitch(checked = c.enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun IconBox(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val colors = seedless
    Box(
        Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(colors.greenDim),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = colors.green, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun FolderHeader(name: String, hint: String?) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val colors = seedless
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Folder, null, tint = colors.green, modifier = Modifier.size(14.dp))
        Text(
            text = name.uppercase(),
            color = colors.text3,
            fontFamily = SeedlessMono,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(start = 6.dp),
        )
        if (hint != null) Text(
            text = uiContext.getString(R.string.detail_suffix, hint).uppercase(),
            color = colors.red,
            fontFamily = SeedlessMono,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun CheatRow(name: String, note: String, enabled: Boolean, selected: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = seedless
    SeedlessRow(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        selected = selected,
        onClick = { onToggle(!enabled) },
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = name,
                color = colors.text,
                fontFamily = Manrope,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
            )
            if (note.isNotEmpty()) Text(
                text = note,
                color = colors.text3,
                fontFamily = Manrope,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        SeedlessSwitch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun EmptyMsg(msg: String) {
    val colors = seedless
    Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
        Text(
            text = msg,
            color = colors.text3,
            fontFamily = Manrope,
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun Group(title: String) = SeedlessSectionLabel(title, Modifier.fillMaxWidth())

private fun parseCheatCode(text: String): IntArray {
    val cleaned = text.replace("\n", " ").replace("\r", " ")
    val tokens = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (tokens.isEmpty() || tokens.size % 2 != 0) throw NumberFormatException("Invalid format")
    return IntArray(tokens.size) { i ->
        java.lang.Long.valueOf(tokens[i].replace(Regex("[^A-Za-z0-9]"), ""), 16).toInt()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheatEditorScreen(editIdx: Int, onDone: () -> Unit, onBack: () -> Unit) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val isNew = editIdx < 0
    val existing = if (!isNew) SeedlessCore.cheatCustomData(editIdx) ?: IntArray(0) else IntArray(0)
    var name by remember { mutableStateOf(if (isNew) "" else jstr(SeedlessCore.cheatCustomName(editIdx))) }
    var code by remember {
        mutableStateOf(if (isNew) "" else buildString {
            for (i in existing.indices step 2) {
                append(String.format("%08X %08X", existing[i], existing.getOrElse(i + 1) { 0 }))
                if (i < existing.size - 2) append("\n")
            }
        })
    }
    var enabled by remember { mutableStateOf(if (isNew) false else SeedlessCore.cheatCustomEnabled(editIdx)) }
    var error by remember { mutableStateOf<String?>(null) }
    androidx.activity.compose.BackHandler { onBack() }

    fun save() {
        if (!isNew) { SeedlessCore.cheatSetCustomEnabled(editIdx, enabled); onDone(); return }
        try {
            val codes = parseCheatCode(code)
            if (SeedlessCore.cheatFindCustom(codes, codes.size) >= 0) { error = uiContext.getString(R.string.cheats_that_cheat_already_exists); return }
            when (SeedlessCore.cheatAddCustom(name, codes, codes.size, enabled)) {
                0 -> onDone()
                1 -> error = uiContext.getString(R.string.cheats_couldn_t_add_the_cheat)
                2 -> error = uiContext.getString(R.string.cheats_couldn_t_save_the_cheat)
            }
        } catch (e: Exception) { error = uiContext.getString(R.string.cheats_invalid_code_format_8_hex_digit_pairs) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (isNew) uiContext.getString(R.string.cheats_add_cheat) else uiContext.getString(R.string.cheats_edit_cheat))
                        Text(if (isNew) uiContext.getString(R.string.cheats_new_custom_code) else name.ifEmpty { uiContext.getString(R.string.cheats_custom_cheat) },
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, uiContext.getString(R.string.common_back)) }
                }
            )
        }
    ) { pad ->
        val scroll = rememberScrollState()
        Column(Modifier.fillMaxSize().padding(pad)
            .verticalScrollbar(scroll, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            .verticalScroll(scroll)
            .padding(18.dp)) {
            Group(uiContext.getString(R.string.cheats_cheat_details))
            OutlinedTextField(
                value = name, onValueChange = { name = it }, label = { Text(uiContext.getString(R.string.common_name)) },
                singleLine = true, enabled = isNew, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = code, onValueChange = { code = it },
                label = { Text(uiContext.getString(R.string.cheats_code_xxxxxxxx_yyyyyyyy)) }, enabled = isNew,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )

            Group(uiContext.getString(R.string.common_status))
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .clickable { enabled = !enabled }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBox(Icons.Rounded.Bolt)
                Column(Modifier.weight(1f).padding(start = 14.dp, end = 12.dp)) {
                    Text(uiContext.getString(R.string.cheats_enable_cheat), fontSize = 15.5.sp)
                    Text(uiContext.getString(R.string.cheats_apply_this_code_to_the_game), fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, top = 12.dp))
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically) {
                if (!isNew) TextButton(onClick = { SeedlessCore.cheatRemoveCustom(editIdx); onDone() }) {
                    Text(uiContext.getString(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
                Box(Modifier.weight(1f))
                TextButton(onClick = { save() }) { Text(uiContext.getString(R.string.common_save)) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
