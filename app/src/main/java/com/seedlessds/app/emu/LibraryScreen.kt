@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.seedlessds.app.emu

import com.seedlessds.app.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderDelete
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VideogameAsset
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun LibraryScreen(
    refreshKey: Int,
    onPlayNew: (RomLibrary.RomEntry) -> Unit,
    onGameSettings: (RomLibrary.RomEntry) -> Unit,
    onGlobalSettings: () -> Unit,
    onAddFolder: () -> Unit,
    onOpenSingle: () -> Unit,
) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val colors = seedless
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var detail by remember { mutableStateOf<RomLibrary.RomEntry?>(null) }
    var profileTick by remember { mutableIntStateOf(0) }
    var profileDialog by remember { mutableStateOf(false) }
    var browseTick by remember { mutableIntStateOf(0) }

    var filter by remember { mutableStateOf(RomLibrary.filter(ctx)) }
    var sorting by remember { mutableStateOf(RomLibrary.sorting(ctx)) }
    var descending by remember { mutableStateOf(RomLibrary.sortingDescending(ctx)) }
    var viewMode by remember { mutableStateOf(RomLibrary.viewMode(ctx)) }
    var openFolder by remember { mutableStateOf<RomLibrary.VirtualFolder?>(null) }

    var scanned by remember { mutableStateOf(false) }
    var localRefresh by remember { mutableIntStateOf(0) }
    var removeFolder by remember { mutableStateOf<RomLibrary.VirtualFolder?>(null) }
    var foldersDialog by remember { mutableStateOf(false) }
    var allGames by remember { mutableStateOf<List<RomLibrary.RomEntry>>(emptyList()) }
    var folders by remember { mutableStateOf<List<RomLibrary.VirtualFolder>>(emptyList()) }
    LaunchedEffect(refreshKey, localRefresh) {
        val (g, f) = withContext(Dispatchers.IO) {
            val g = RomLibrary.scan(ctx)
            g to RomLibrary.virtualFolders(ctx, g)
        }
        allGames = g; folders = f; scanned = true
    }
    val recentAll = remember(refreshKey, browseTick, localRefresh) { RomLibrary.recent(ctx) }
    val recent = remember(recentAll, allGames, scanned) {
        if (!scanned) emptyList() else recentAll.filter { r -> allGames.any { it.uri == r.uri } }
    }
    val profile = remember(profileTick) { Profiles.current(ctx) }.let {
        if (it == Profiles.DEFAULT) uiContext.getString(R.string.profile_default) else it
    }

    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    var games by remember { mutableStateOf<List<RomLibrary.RomEntry>>(emptyList()) }
    LaunchedEffect(allGames, browseTick, filter, sorting, descending, openFolder, query) {
        games = withContext(Dispatchers.IO) {
            val browsed = RomLibrary.browse(ctx, allGames, filter, openFolder?.uri, sorting, descending)
            val needle = normalizeForSearch(query)
            if (needle.isEmpty()) browsed else browsed.filter { normalizeForSearch(it.name).contains(needle) }
        }
    }
    val favorites = remember(browseTick) { RomLibrary.favorites(ctx) }
    LaunchedEffect(Unit) {
        BoxArt.ensureIndex(ctx)
        BoxArt.refreshIndexIfStale(ctx)
    }
    var highlighted by remember { mutableStateOf<RomLibrary.RomEntry?>(null) }
    if (foldersDialog) {
        SeedlessDialog(onDismiss = { foldersDialog = false }, width = 390.dp) {
          Column(Modifier.padding(18.dp)) {
            Text(uiContext.getString(R.string.library_rom_folders), color = colors.text, fontFamily = SpaceGrotesk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            if (folders.isEmpty()) {
                Text(uiContext.getString(R.string.library_no_folders_added_yet), color = colors.text2, fontFamily = Manrope, fontSize = 13.sp)
            } else folders.forEach { f ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Icon(Icons.Rounded.Folder, null, tint = colors.green, modifier = Modifier.size(18.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(f.name, color = colors.text, fontFamily = Manrope, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(uiContext.resources.getQuantityString(R.plurals.common_games_2, f.gameCount, f.gameCount) + folderPath(f.uri), color = colors.text3, fontFamily = Manrope, fontSize = 11.sp, maxLines = 1)
                    }
                    SeedlessButton(uiContext.getString(R.string.library_remove), colors.red, onClick = { removeFolder = f })
                }
            }
            Spacer(Modifier.height(10.dp))
            SeedlessButton(uiContext.getString(R.string.common_close), colors.surface3, onClick = { foldersDialog = false }, fillWidth = true)
          }
        }
    }
    removeFolder?.let { f ->
        SeedlessConfirmDialog(
            title = uiContext.getString(R.string.library_remove_folder),
            message = uiContext.getString(R.string.library_leaves_the_library_no_file_is_deleted_you_can_add_the_folder_again_later, f.name),
            confirmLabel = uiContext.getString(R.string.library_remove),
            onConfirm = {
                RomLibrary.removeFolder(ctx, f.uri)
                runCatching {
                    ctx.contentResolver.releasePersistableUriPermission(f.uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
                if (openFolder?.uri == f.uri) openFolder = null
                removeFolder = null
                localRefresh++
            },
            onDismiss = { removeFolder = null },
        )
    }

    LaunchedEffect(highlighted, profile, browseTick) {
        val entry = highlighted
        if (entry == null) {
            ExternalInfoBus.clear()
            return@LaunchedEffect
        }
        val icon = withContext(Dispatchers.IO) { RomLibrary.info(ctx, entry).icon }
        val coverUrl = BoxArt.resolveUrl(ctx, entry)
        val raIconUrl = BoxArt.raIconUrl(ctx, entry)
        val platform = withContext(Dispatchers.IO) { RomLibrary.platform(ctx, entry) }
        val stats = RomLibrary.stats(ctx, entry.uri)
        val fav = RomLibrary.favorites(ctx).contains(entry.uri.toString())
        val ra = RomLibrary.hasAchievements(ctx, entry)
        ExternalInfoBus.show {
            ExternalLibraryGameInfo(
                title = entry.title,
                icon = icon?.asImageBitmap(),
                coverUrl = coverUrl,
                raIconUrl = raIconUrl,
                platformLabel = RomLibrary.platformLabel(uiContext, platform),
                profile = profile,
                favorite = fav,
                hasAchievements = ra,
                playTimeLabel = if (stats.playTimeMs >= 60_000L) RomLibrary.formatPlayTime(uiContext, stats.playTimeMs) else null,
                continueSlot = null,
            )
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { ExternalInfoBus.clear() }
    }

    val atRoot = filter == RomLibrary.Filter.ALL && openFolder == null && query.isEmpty()

    detail?.let { entry ->
        GameDetailDialog(
            entry = entry,
            profile = profile,
            favorite = favorites.contains(entry.uri.toString()),
            onDismiss = { detail = null },
            onPlayNew = { detail = null; onPlayNew(entry) },
            onSettings = { detail = null; onGameSettings(entry) },
            onToggleFavorite = { RomLibrary.toggleFavorite(ctx, entry); browseTick++ },
        )
    }

    if (profileDialog) {
        ProfilesDialog(
            onDismiss = { profileDialog = false },
            onChanged = { profileTick++ },
        )
    }

    androidx.activity.compose.BackHandler(enabled = openFolder != null) { openFolder = null }

    androidx.compose.runtime.DisposableEffect(Unit) {
        LibraryKeyRouter.cycleFilter = { forward ->
            val all = RomLibrary.Filter.entries
            val next = all[(all.indexOf(filter) + if (forward) 1 else all.size - 1) % all.size]
            filter = next
            openFolder = null
            RomLibrary.setFilter(ctx, next)
            true
        }
        LibraryKeyRouter.openOptions = {
            val entry = highlighted
            if (entry != null) { detail = entry; true } else false
        }
        LibraryKeyRouter.toggleFavorite = {
            val entry = highlighted
            if (entry != null) { RomLibrary.toggleFavorite(ctx, entry); browseTick++; true } else false
        }
        onDispose {
            LibraryKeyRouter.cycleFilter = null
            LibraryKeyRouter.openOptions = null
            LibraryKeyRouter.toggleFavorite = null
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
        containerColor = colors.bg,
        topBar = {
            Column(Modifier.background(colors.bg).statusBarsPadding()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                ) {
                    val folder = openFolder
                    if (searchOpen) {
                        val searchFocus = remember { FocusRequester() }
                        Box(
                            Modifier.size(38.dp).clip(CircleShape).clickable {
                                searchOpen = false
                                query = ""
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack, uiContext.getString(R.string.library_close_search),
                                tint = colors.text, modifier = Modifier.size(19.dp),
                            )
                        }
                        val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
                        androidx.compose.foundation.text.BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                imeAction = androidx.compose.ui.text.input.ImeAction.Search,
                            ),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                onSearch = { focusManager.clearFocus() },
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = colors.text,
                                fontFamily = Manrope,
                                fontSize = 16.sp,
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.red),
                            decorationBox = { field ->
                                Box {
                                    if (query.isEmpty()) {
                                        Text(
                                            text = uiContext.getString(R.string.library_search_games),
                                            color = colors.text3,
                                            fontFamily = Manrope,
                                            fontSize = 16.sp,
                                        )
                                    }
                                    field()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                                .focusRequester(searchFocus),
                        )
                        androidx.compose.runtime.LaunchedEffect(Unit) {
                            runCatching { searchFocus.requestFocus() }
                        }
                        if (query.isNotEmpty()) {
                            Box(
                                Modifier.size(38.dp).clip(CircleShape).clickable { query = "" },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Rounded.Close, uiContext.getString(R.string.common_clear),
                                    tint = colors.text2, modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    } else if (folder != null) {
                        Box(
                            Modifier.size(38.dp).clip(CircleShape).clickable { openFolder = null },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack, uiContext.getString(R.string.common_back),
                                tint = colors.text, modifier = Modifier.size(19.dp),
                            )
                        }
                        Column(Modifier.weight(1f).padding(start = 6.dp)) {
                            Text(
                                text = folder.name,
                                color = colors.text,
                                fontFamily = SpaceGrotesk,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = uiContext.resources.getQuantityString(R.plurals.library_games_profile, games.size, games.size, profile),
                                color = colors.text3,
                                fontFamily = SeedlessMono,
                                fontSize = 9.sp,
                            )
                        }
                    } else {
                        SeedlessBrandHeader(
                            subtitle = uiContext.resources.getQuantityString(R.plurals.library_games_profile, games.size, games.size, profile),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (!searchOpen) {
                        SeedlessHeaderAction(Icons.Rounded.Search, uiContext.getString(R.string.library_search), { searchOpen = true })
                        ProfileChip(profile) { profileDialog = true }
                    Spacer(Modifier.width(4.dp))
                    ViewModeToggle(viewMode) {
                        viewMode = it
                        RomLibrary.setViewMode(ctx, it)
                    }
                    Spacer(Modifier.width(4.dp))
                    SeedlessHeaderAction(Icons.Rounded.VideogameAsset, uiContext.getString(R.string.library_open_rom), onOpenSingle)
                    SeedlessHeaderAction(Icons.Rounded.CreateNewFolder, uiContext.getString(R.string.library_add_folder), onAddFolder)
                    SeedlessHeaderAction(Icons.Rounded.FolderDelete, uiContext.getString(R.string.library_remove_folder), { foldersDialog = true })
                    SeedlessHeaderAction(Icons.Rounded.Settings, uiContext.getString(R.string.common_settings), onGlobalSettings)
                    }
                }
                LibraryFilterRow(
                    selected = filter,
                    onSelect = {
                        filter = it
                        openFolder = null
                        RomLibrary.setFilter(ctx, it)
                    },
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
            }
        },
        bottomBar = {
            GamepadHintsFooter(
                modifier = Modifier.background(colors.bg).navigationBarsPadding(),
                hints = listOf(
                    GamepadHint(null, uiContext.getString(R.string.common_navigate)),
                    GamepadHint(uiContext.getString(R.string.button_a), uiContext.getString(R.string.common_play)),
                    GamepadHint(uiContext.getString(R.string.button_x), uiContext.getString(R.string.library_options)),
                    GamepadHint(uiContext.getString(R.string.button_y), uiContext.getString(R.string.common_favorite)),
                    GamepadHint(uiContext.getString(R.string.library_lb), uiContext.getString(R.string.library_filter)),
                ),
            )
        },
    ) { pad ->
        if (scanned && folders.isEmpty() && allGames.isEmpty()) {
            EmptyLibrary(onAddFolder, onOpenSingle, Modifier.fillMaxSize().padding(pad))
            return@Scaffold
        }

        val gridState = rememberLazyGridState()
        val showAlphabet = sorting == RomLibrary.Sorting.ALPHABETICALLY && games.size > 12
        val alphabetIndex = remember(games) {
            linkedMapOf<Char, Int>().apply {
                games.forEachIndexed { i, g -> putIfAbsent(indexLetter(g.title), i) }
            }
        }
        val showFolders = atRoot && folders.size > 1
        val showRecent = atRoot && recent.isNotEmpty()
        val headerItems = (if (showFolders) 2 else 0) + (if (showRecent) 2 else 0) + 1

        Row(Modifier.fillMaxSize().padding(pad)) {
            LazyVerticalGrid(
                columns = if (viewMode == RomLibrary.ViewMode.GRID) {
                    GridCells.Adaptive(minSize = 104.dp)
                } else {
                    GridCells.Fixed(1)
                },
                state = gridState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .verticalScrollbar(gridState, colors.green.copy(alpha = 0.4f)),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                verticalArrangement = Arrangement.spacedBy(if (viewMode == RomLibrary.ViewMode.GRID) 11.dp else 6.dp),
            ) {
                if (showFolders) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SeedlessSectionLabel(uiContext.getString(R.string.library_folders), Modifier.padding(top = 0.dp))
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                            contentPadding = PaddingValues(bottom = 4.dp),
                        ) {
                            items(folders, key = { "f_" + it.uri }) { folder ->
                                VirtualFolderChip(folder, active = false, onLongClick = { removeFolder = folder }) { openFolder = folder }
                            }
                        }
                    }
                }
                if (showRecent) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = uiContext.getString(R.string.library_continue_playing),
                            color = colors.text,
                            fontFamily = SpaceGrotesk,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 4.dp),
                        ) {
                            items(recent, key = { "r_" + it.uri }) { entry ->
                                RecentCard(
                                    entry = entry,
                                    onPlay = { onPlayNew(entry) },
                                    onMenu = { detail = entry },
                                    onHighlight = { highlighted = entry },
                                )
                            }
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp),
                    ) {
                        Text(
                            text = sectionTitle(uiContext, filter, openFolder),
                            color = colors.text,
                            fontFamily = SpaceGrotesk,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.width(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.weight(1f)) {
                            RomLibrary.Sorting.entries.forEach { mode ->
                                SortChip(sortLabel(uiContext, mode), sorting == mode, descending) {
                                    descending = if (sorting == mode) !descending else false
                                    sorting = mode
                                    RomLibrary.setSorting(ctx, sorting, descending)
                                }
                            }
                        }
                        Text(
                            text = uiContext.resources.getQuantityString(R.plurals.common_games, games.size, games.size),
                            color = colors.text3,
                            fontFamily = SeedlessMono,
                            fontSize = 10.5.sp,
                        )
                    }
                }
                items(games, key = { it.uri.toString() }) { entry ->
                    val fav = favorites.contains(entry.uri.toString())
                    if (viewMode == RomLibrary.ViewMode.GRID) {
                        GameCard(
                            entry = entry,
                            favorite = fav,
                            onPlay = { onPlayNew(entry) },
                            onMenu = { detail = entry },
                            onHighlight = { highlighted = entry },
                        )
                    } else {
                        GameListRow(
                            entry = entry,
                            favorite = fav,
                            onPlay = { onPlayNew(entry) },
                            onMenu = { detail = entry },
                            onFavorite = { RomLibrary.toggleFavorite(ctx, entry); browseTick++ },
                            onHighlight = { highlighted = entry },
                        )
                    }
                }
                if (games.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                            Text(
                                text = emptyMessage(uiContext, filter),
                                color = colors.text3,
                                fontFamily = Manrope,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
            if (showAlphabet) {
                val activeLetter by androidx.compose.runtime.remember {
                    androidx.compose.runtime.derivedStateOf {
                        val first = gridState.firstVisibleItemIndex - headerItems
                        games.getOrNull(first.coerceAtLeast(0))?.let { indexLetter(it.title) }
                    }
                }
                AlphabetIndexRail(
                    letters = alphabetIndex.keys.toList(),
                    activeLetter = activeLetter,
                    onTop = { scope.launch { gridState.scrollToItem(0) } },
                    onLetter = { letter ->
                        alphabetIndex[letter]?.let { scope.launch { gridState.scrollToItem(headerItems + it) } }
                    },
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
        }
    }
}

private fun sortLabel(uiContext: android.content.Context, mode: RomLibrary.Sorting) = when (mode) {
    RomLibrary.Sorting.ALPHABETICALLY -> uiContext.getString(R.string.library_a_z)
    RomLibrary.Sorting.RECENTLY_PLAYED -> uiContext.getString(R.string.library_recent)
    RomLibrary.Sorting.MOST_PLAYED -> uiContext.getString(R.string.library_most_played)
}

private val NonAscii = Regex("[^\\p{ASCII}]")

private fun normalizeForSearch(input: String): String =
    java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD)
        .replace(NonAscii, "")
        .lowercase()

private fun folderPath(uri: android.net.Uri): String =
    runCatching { android.provider.DocumentsContract.getTreeDocumentId(uri).substringAfter(':') }.getOrDefault("")

private fun sectionTitle(uiContext: android.content.Context, filter: RomLibrary.Filter, folder: RomLibrary.VirtualFolder?): String = when {
    folder != null -> uiContext.getString(R.string.library_in_this_folder)
    filter == RomLibrary.Filter.FAVORITES -> uiContext.getString(R.string.common_favorites)
    filter == RomLibrary.Filter.DS -> uiContext.getString(R.string.library_ds_games)
    filter == RomLibrary.Filter.DSIWARE -> uiContext.getString(R.string.common_dsiware)
    filter == RomLibrary.Filter.RETRO_ACHIEVEMENTS -> uiContext.getString(R.string.library_with_achievements)
    else -> uiContext.getString(R.string.library_all_games)
}

private fun emptyMessage(uiContext: android.content.Context, filter: RomLibrary.Filter): String = when (filter) {
    RomLibrary.Filter.FAVORITES -> uiContext.getString(R.string.library_no_favorites_yet_hold_a_game_or_press_y_to)
    RomLibrary.Filter.DS -> uiContext.getString(R.string.library_no_ds_cartridge_dumps_in_the_added_folders)
    RomLibrary.Filter.DSIWARE -> uiContext.getString(R.string.library_no_dsiware_titles_in_the_added_folders)
    RomLibrary.Filter.RETRO_ACHIEVEMENTS ->
        uiContext.getString(R.string.library_no_games_with_achievements_yet_a_game_joins_this_list_once_you_launch_it_an)
    RomLibrary.Filter.ALL -> uiContext.getString(R.string.library_no_roms_in_the_added_folders)
}

@Composable
private fun ProfileChip(profile: String, onClick: () -> Unit) {
    val colors = seedless
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surface2)
            .let { if (focused) it.border(2.dp, colors.red, RoundedCornerShape(20.dp)) else it }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
    ) {
        Icon(Icons.Rounded.Person, null, tint = colors.green, modifier = Modifier.size(15.dp))
        Text(
            text = profile,
            color = colors.text2,
            fontFamily = SeedlessMono,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@androidx.compose.runtime.Immutable
private data class GameArtState(
    val icon: ImageBitmap? = null,
    val coverUrl: String? = null,
    val raIconUrl: String? = null,
    val resolving: Boolean = true,
    val platform: RomLibrary.Platform? = null,
)

@Composable
private fun rememberGameArt(entry: RomLibrary.RomEntry): GameArtState {
    val ctx = LocalContext.current
    var state by remember(entry.uri) {
        mutableStateOf(GameArtState(icon = RomLibrary.cachedIcon(entry.uri)?.asImageBitmap(), platform = RomLibrary.cachedPlatform(ctx, entry)))
    }
    LaunchedEffect(entry.uri) {
        if (state.icon == null) {
            val romIcon = withContext(Dispatchers.IO) { RomLibrary.info(ctx, entry).icon }
            state = state.copy(icon = romIcon?.asImageBitmap())
        }
        if (state.platform == null) {
            val p = withContext(Dispatchers.IO) { RomLibrary.platform(ctx, entry) }
            state = state.copy(platform = p)
        }
        state = state.copy(raIconUrl = BoxArt.raIconUrl(ctx, entry))
        state = state.copy(coverUrl = BoxArt.resolveUrl(ctx, entry), resolving = false)
    }
    return state
}

@Composable
private fun RecentCard(
    entry: RomLibrary.RomEntry,
    onPlay: () -> Unit,
    onMenu: () -> Unit,
    onHighlight: () -> Unit,
) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val colors = seedless
    val ctx = LocalContext.current
    val art = rememberGameArt(entry)
    val platform = art.platform ?: RomLibrary.Platform.DS
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(8.dp)
    LaunchedEffect(focused) { if (focused) onHighlight() }
    val pressScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness = 4000f,
        ),
        label = "press",
    )

    Column(
        modifier = Modifier
            .width(116.dp)
            .scale(pressScale)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onPlay,
                onLongClick = onMenu,
            ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(DsBoxArtAspectRatio)
                .shadow(6.dp, shape)
                .clip(shape)
                .let { if (focused) it.border(3.dp, colors.red, shape) else it },
        ) {
            GameArt(entry.title, art.icon, Modifier.fillMaxSize(),
                coverUrl = art.coverUrl, raIconUrl = art.raIconUrl, loading = art.resolving)
            PlatformBadge(
                text = RomLibrary.platformLabel(uiContext, platform),
                modifier = Modifier.align(Alignment.TopStart).padding(start = 6.dp, top = 6.dp),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)))
                    )
                    .padding(start = 8.dp, end = 32.dp, top = 9.dp, bottom = 7.dp),
            ) {
                Text(
                    text = entry.title,
                    color = Color.White,
                    fontFamily = Manrope,
                    fontSize = 10.5.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 6.dp, bottom = 7.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(12.dp))
            }
        }
    }
}

@Composable
private fun PlatformBadge(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 8.sp,
) {
    Text(
        text = text,
        color = Color.White,
        fontFamily = SeedlessMono,
        fontSize = fontSize,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.4f))
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

@Composable
private fun GameCard(
    entry: RomLibrary.RomEntry,
    favorite: Boolean,
    onPlay: () -> Unit,
    onMenu: () -> Unit,
    onHighlight: () -> Unit,
) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val colors = seedless
    val ctx = LocalContext.current
    val art = rememberGameArt(entry)
    val platform = art.platform ?: RomLibrary.Platform.DS
    val hasRa = remember(entry.uri) { RomLibrary.hasAchievements(ctx, entry) }
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(7.dp)
    LaunchedEffect(focused) { if (focused) onHighlight() }
    val pressScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness = 4000f,
        ),
        label = "press",
    )

    Box(
        modifier = Modifier
            .scale(pressScale)
            .fillMaxWidth()
            .aspectRatio(DsBoxArtAspectRatio)
            .shadow(5.dp, shape, ambientColor = colors.shadow, spotColor = colors.shadow)
            .clip(shape)
            .let { if (focused) it.border(3.dp, colors.red, shape) else it }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onPlay,
                onLongClick = onMenu,
            ),
    ) {
        GameArt(entry.title, art.icon, Modifier.fillMaxSize(), showInitial = art.icon == null,
            coverUrl = art.coverUrl, raIconUrl = art.raIconUrl, loading = art.resolving)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.TopStart).padding(7.dp),
        ) {
            PlatformBadge(RomLibrary.platformLabel(uiContext, platform))
            if (hasRa) {
                Spacer(Modifier.width(4.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    Icon(
                        Icons.Rounded.EmojiEvents, null,
                        tint = SeedlessColors.gold, modifier = Modifier.size(10.dp),
                    )
                }
            }
        }
        if (favorite) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = FavoriteStar,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 7.dp).size(13.dp),
            )
        }
        if (art.coverUrl == null) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))
                        )
                    )
                    .padding(start = 8.dp, end = 8.dp, top = 22.dp, bottom = 7.dp),
            ) {
                Text(
                    text = entry.title,
                    color = Color.White,
                    fontFamily = Manrope,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (art.coverUrl != null) RomIconBadge(art.icon)
    }
}

@Composable
private fun GameListRow(
    entry: RomLibrary.RomEntry,
    favorite: Boolean,
    onPlay: () -> Unit,
    onMenu: () -> Unit,
    onFavorite: () -> Unit,
    onHighlight: () -> Unit,
) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val colors = seedless
    val ctx = LocalContext.current
    val art = rememberGameArt(entry)
    val platform = art.platform ?: RomLibrary.Platform.DS
    val stats = remember(entry.uri) { RomLibrary.stats(ctx, entry.uri) }
    val hasRa = remember(entry.uri) { RomLibrary.hasAchievements(ctx, entry) }
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)
    LaunchedEffect(focused) { if (focused) onHighlight() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) colors.red else colors.line,
                shape = shape,
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onPlay,
                onLongClick = onMenu,
            )
            .padding(10.dp),
    ) {
        Box(Modifier.width(48.dp).aspectRatio(DsBoxArtAspectRatio).clip(RoundedCornerShape(7.dp))) {
            GameArt(entry.title, art.icon, Modifier.fillMaxSize(),
                coverUrl = art.coverUrl, raIconUrl = art.raIconUrl, loading = art.resolving)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = entry.title,
                color = colors.text,
                fontFamily = Manrope,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = RomLibrary.platformLabel(uiContext, platform),
                    color = colors.text3,
                    fontFamily = SeedlessMono,
                    fontSize = 9.sp,
                )
                if (hasRa) {
                    Icon(
                        Icons.Rounded.EmojiEvents, null,
                        tint = SeedlessColors.gold,
                        modifier = Modifier.padding(start = 7.dp).size(11.dp),
                    )
                }
                if (stats.playTimeMs >= 60_000L) {
                    Text(
                        text = uiContext.getString(R.string.detail_suffix, RomLibrary.formatPlayTime(uiContext, stats.playTimeMs)),
                        color = colors.text3,
                        fontFamily = SeedlessMono,
                        fontSize = 9.sp,
                    )
                }
            }
        }
        Box(
            Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onFavorite),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = uiContext.getString(R.string.common_favorite),
                tint = if (favorite) FavoriteStar else colors.text3,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

@Composable
private fun GameDetailDialog(
    entry: RomLibrary.RomEntry,
    profile: String,
    favorite: Boolean,
    onDismiss: () -> Unit,
    onPlayNew: () -> Unit,
    onSettings: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val colors = seedless
    val ctx = LocalContext.current
    val art = rememberGameArt(entry)
    val platform = art.platform ?: RomLibrary.Platform.DS
    val stats = remember(entry.uri) { RomLibrary.stats(ctx, entry.uri) }
    var isFavorite by remember(entry.uri, favorite) { mutableStateOf(favorite) }

    SeedlessDialog(onDismiss = onDismiss, width = 340.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 20.dp, bottom = 14.dp),
        ) {
            Box(
                Modifier.width(44.dp).aspectRatio(DsBoxArtAspectRatio).clip(RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center,
            ) {
                GameArt(entry.title, art.icon, Modifier.fillMaxSize(),
                    coverUrl = art.coverUrl, raIconUrl = art.raIconUrl, loading = art.resolving)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = entry.title,
                    color = colors.text,
                    fontFamily = SpaceGrotesk,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(uiContext.getString(R.string.library_platform_profile, RomLibrary.platformLabel(uiContext, platform), profile).uppercase())
                        if (stats.playTimeMs >= 60_000L) append(uiContext.getString(R.string.detail_suffix, RomLibrary.formatPlayTime(uiContext, stats.playTimeMs)))
                    },
                    color = colors.text3,
                    fontFamily = SeedlessMono,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Box(
                Modifier.size(38.dp).clip(CircleShape).clickable {
                    onToggleFavorite(); isFavorite = !isFavorite
                },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = uiContext.getString(R.string.common_favorite),
                    tint = if (isFavorite) FavoriteStar else colors.text3,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
        SeedlessRowSeparator()
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            DetailAction(
                icon = Icons.Rounded.PlayArrow,
                tint = colors.green,
                title = uiContext.getString(R.string.library_start_game),
                desc = uiContext.getString(R.string.library_start_a_new_game_from_the_beginning),
                onClick = onPlayNew,
            )
            DetailAction(
                icon = Icons.Rounded.Tune,
                tint = colors.text2,
                title = uiContext.getString(R.string.common_game_settings),
                desc = uiContext.getString(R.string.library_config_for_this_game_only),
                onClick = onSettings,
            )
            DetailAction(
                icon = Icons.Rounded.AddToHomeScreen,
                tint = colors.text2,
                title = uiContext.getString(R.string.library_create_shortcut),
                desc = uiContext.getString(R.string.library_add_this_game_to_the_home_screen),
                onClick = { RomLibrary.createShortcut(ctx, entry); onDismiss() },
            )
        }
    }
}

@Composable
private fun DetailAction(
    icon: ImageVector,
    tint: Color,
    title: String,
    desc: String,
    onClick: () -> Unit,
) {
    val colors = seedless
    SeedlessRow(minHeight = 54.dp, onClick = onClick, modifier = Modifier.padding(vertical = 3.dp)) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = title,
                color = colors.text,
                fontFamily = Manrope,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = desc,
                color = colors.text3,
                fontFamily = Manrope,
                fontSize = 11.5.sp,
                lineHeight = 14.sp,
            )
        }
    }
}

@Composable
private fun ProfilesDialog(onDismiss: () -> Unit, onChanged: () -> Unit) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val colors = seedless
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val profiles = remember(tick) { Profiles.list(ctx) }
    val current = remember(tick) { Profiles.current(ctx) }
    var newName by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    val trimmed = newName.trim()
    val nameError = when {
        trimmed.isEmpty() -> null
        trimmed.length > 20 -> uiContext.getString(R.string.library_max_20_characters)
        (trimmed.equals(Profiles.DEFAULT, ignoreCase = true) || trimmed.equals(uiContext.getString(R.string.profile_default), ignoreCase = true)) -> uiContext.getString(R.string.library_reserved_name)
        profiles.any { it.equals(trimmed, ignoreCase = true) } -> uiContext.getString(R.string.library_already_exists)
        else -> null
    }
    val canCreate = trimmed.isNotEmpty() && nameError == null

    SeedlessDialog(onDismiss = onDismiss, width = 340.dp) {
        Text(
            text = uiContext.getString(R.string.library_user_profile),
            color = colors.text,
            fontFamily = SpaceGrotesk,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
        )
        Column(Modifier.padding(horizontal = 12.dp)) {
            profiles.forEach { name ->
                SeedlessRow(
                    modifier = Modifier.padding(vertical = 2.dp),
                    selected = name == current,
                    onClick = { Profiles.switch(ctx, name); tick++; onChanged() },
                ) {
                    Box(
                        Modifier.size(16.dp).clip(CircleShape)
                            .border(2.dp, if (name == current) colors.green else colors.text3, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (name == current) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(colors.green))
                        }
                    }
                    Text(
                        text = if (name == Profiles.DEFAULT) uiContext.getString(R.string.profile_default) else name,
                        color = if (name == current) colors.text else colors.text2,
                        fontFamily = Manrope,
                        fontSize = 13.5.sp,
                        fontWeight = if (name == current) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 10.dp),
                    )
                    if (name != Profiles.DEFAULT) {
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).clickable { confirmDelete = name },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Delete, uiContext.getString(R.string.common_delete), tint = colors.red, modifier = Modifier.size(17.dp)) }
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            ) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    modifier = Modifier.weight(1f),
                    label = { Text(uiContext.getString(R.string.library_new_profile), fontFamily = Manrope, fontSize = 12.sp) },
                    singleLine = true,
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it, fontFamily = Manrope, fontSize = 11.sp) } },
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colors.surface2,
                        unfocusedContainerColor = colors.surface2,
                        errorContainerColor = colors.surface2,
                        focusedIndicatorColor = colors.green,
                        unfocusedIndicatorColor = colors.line,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                SeedlessButton(
                    text = uiContext.getString(R.string.library_create),
                    accent = colors.green,
                    enabled = canCreate,
                    modifier = Modifier.padding(top = 6.dp),
                    onClick = { Profiles.create(ctx, trimmed); newName = ""; tick++; onChanged() },
                )
            }
            Text(
                text = uiContext.getString(R.string.library_each_profile_keeps_its_own_settings_and_key),
                color = colors.text3,
                fontFamily = Manrope,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 16.dp),
            )
        }
    }

    confirmDelete?.let { name ->
        SeedlessConfirmDialog(
            title = uiContext.getString(R.string.library_delete_the_profile, name),
            message = uiContext.getString(R.string.library_this_removes_its_settings_and_key_mappings_it),
            confirmLabel = uiContext.getString(R.string.common_delete),
            onConfirm = { Profiles.delete(ctx, name); tick++; onChanged(); confirmDelete = null },
            onDismiss = { confirmDelete = null },
        )
    }
}

@Composable
private fun EmptyLibrary(onAddFolder: () -> Unit, onOpenSingle: () -> Unit, modifier: Modifier) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val colors = seedless
    Column(modifier.padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Box(
            Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)).background(colors.greenDim),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.VideogameAsset, null, tint = colors.green, modifier = Modifier.size(34.dp)) }
        Spacer(Modifier.height(18.dp))
        Text(
            text = uiContext.getString(R.string.library_your_library_is_empty),
            color = colors.text,
            fontFamily = SpaceGrotesk,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = uiContext.getString(R.string.library_add_a_folder_with_nds_roms_or_open_one_directly),
            color = colors.text3,
            fontFamily = Manrope,
            fontSize = 12.5.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 22.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SeedlessButton(uiContext.getString(R.string.library_add_folder), colors.green, icon = Icons.Rounded.CreateNewFolder, onClick = onAddFolder)
            SeedlessButton(uiContext.getString(R.string.library_open_rom), colors.surface2, icon = Icons.Rounded.Add, onClick = onOpenSingle)
        }
    }
}
