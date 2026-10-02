package com.seedlessds.app.emu

import com.seedlessds.app.R
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.seedlessds.app.ra.RaLbEntry
import com.seedlessds.app.ra.RaLeaderboard
import com.seedlessds.app.ra.RetroAchievements

@Composable
fun LeaderboardsScreen(onBack: () -> Unit) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val buckets by RetroAchievements.leaderboards
    val loading by RetroAchievements.lbLoading
    val loggedIn by RetroAchievements.loggedIn
    val title by RetroAchievements.gameTitle
    var openLb by remember { mutableStateOf<RaLeaderboard?>(null) }
    val listState = rememberLazyListState()

    BackHandler { if (openLb != null) openLb = null else onBack() }
    LaunchedEffect(Unit) { RetroAchievements.refreshLeaderboards() }

    val current = openLb
    if (current != null) { LeaderboardEntries(current) { openLb = null }; return }

    val colors = seedless
    SeedlessRailScaffold(
        onBack = onBack,
        acceptLabel = uiContext.getString(R.string.common_view),
        header = {
            Column(Modifier.weight(1f)) {
                Text(
                    text = uiContext.getString(R.string.common_leaderboards),
                    color = colors.text,
                    fontFamily = SpaceGrotesk,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = title ?: uiContext.getString(R.string.common_no_game_identified),
                    color = colors.text3,
                    fontFamily = SeedlessMono,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SeedlessHeaderAction(
                icon = Icons.Rounded.Refresh,
                contentDescription = uiContext.getString(R.string.common_refresh),
                onClick = { RetroAchievements.refreshLeaderboards() },
            )
        },
    ) {
        val flat = remember(buckets) { buckets.flatMap { it.leaderboards } }
        val positions = remember(buckets) {
            val out = ArrayList<Int>()
            var pos = 0
            buckets.forEach { bucket ->
                pos++
                bucket.leaderboards.forEach { out.add(pos); pos++ }
            }
            out
        }
        val cursor = rememberMenuCursor(
            count = flat.size,
            onAccept = { i -> flat.getOrNull(i)?.let { openLb = it } },
            onCancel = onBack,
        )
        LaunchedEffect(cursor.index, positions) {
            positions.getOrNull(cursor.index)?.let { runCatching { listState.animateScrollToItem(it) } }
        }
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.widthIn(max = 760.dp).fillMaxSize().align(Alignment.TopCenter)) {
                if (loggedIn && buckets.isNotEmpty()) Row(
                    Modifier.fillMaxWidth().padding(16.dp, 8.dp, 16.dp, 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Info, null, tint = colors.text3, modifier = Modifier.size(13.dp))
                    Text(
                        text = uiContext.getString(R.string.leaderboards_live_tracking_and_submission_require_hardcore),
                        color = colors.text3,
                        fontFamily = Manrope,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                when {
                    !loggedIn -> LbEmpty(uiContext.getString(R.string.leaderboards_sign_in_at_settings_retroachievements_to_see))
                    loading && buckets.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator(color = colors.green)
                    }
                    buckets.isEmpty() -> LbEmpty(
                        if (title == null) uiContext.getString(R.string.achievements_this_game_isn_t_identified_in_retroachievements)
                        else uiContext.getString(R.string.leaderboards_this_game_has_no_leaderboards)
                    )
                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                            .verticalScrollbar(listState, colors.green.copy(alpha = 0.4f)),
                        contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        buckets.forEach { bucket ->
                            item(key = "lh-${bucket.type}") {
                                SeedlessSectionLabel(
                                    uiContext.getString(R.string.label_with_count, bucket.label, bucket.leaderboards.size),
                                    Modifier.fillMaxWidth(),
                                )
                            }
                            items(bucket.leaderboards, key = { "l-${it.id}" }) { lb ->
                                val i = flat.indexOf(lb)
                                LeaderboardRow(lb, i == cursor.index) {
                                    cursor.select(i)
                                    openLb = lb
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaderboardRow(lb: RaLeaderboard, selected: Boolean, onClick: () -> Unit) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val colors = seedless
    SeedlessRow(
        minHeight = 54.dp,
        selected = selected,
        external = {
            ExternalSettingInfo(
                title = lb.title,
                description = lb.description.ifBlank { uiContext.getString(R.string.leaderboards_leaderboard) },
                crumb = uiContext.getString(R.string.common_leaderboards),
            )
        },
        onClick = onClick,
    ) {
        Icon(Icons.Rounded.Leaderboard, null, tint = colors.text2, modifier = Modifier.size(19.dp))
        Column(Modifier.weight(1f).padding(start = 11.dp)) {
            Text(
                text = lb.title.ifBlank { uiContext.getString(R.string.unknown_symbol) },
                color = colors.text,
                fontFamily = Manrope,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (lb.description.isNotBlank()) Text(
                text = lb.description,
                color = colors.text3,
                fontFamily = Manrope,
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (lb.trackerValue.isNotBlank() && lb.state in 1..2) {
            Text(
                text = lb.trackerValue,
                color = colors.green,
                fontFamily = SeedlessMono,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.greenDim)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun LeaderboardEntries(lb: RaLeaderboard, onBack: () -> Unit) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val entries by RetroAchievements.lbEntries
    val loading by RetroAchievements.lbEntriesLoading
    val error by RetroAchievements.lbEntriesError
    var aroundMe by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(lb.id, aroundMe) { RetroAchievements.fetchLbEntries(lb.id, lb.title, aroundUser = aroundMe) }

    val colors = seedless
    SeedlessRailScaffold(
        onBack = onBack,
        header = {
            Column(Modifier.weight(1f)) {
                Text(
                    text = lb.title.ifBlank { uiContext.getString(R.string.leaderboards_leaderboard) },
                    color = colors.text,
                    fontFamily = SpaceGrotesk,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (lb.description.isNotBlank()) Text(
                    text = lb.description,
                    color = colors.text3,
                    fontFamily = SeedlessMono,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
    ) {
        val cursor = rememberMenuCursor(
            count = entries.size,
            onAccept = { },
            onCancel = onBack,
            onTab = { aroundMe = !aroundMe },
        )
        LaunchedEffect(cursor.index, entries) {
            runCatching { listState.animateScrollToItem(cursor.index) }
        }
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxSize().align(Alignment.TopCenter)) {
                SeedlessSegmented(
                    options = listOf(uiContext.getString(R.string.common_top), uiContext.getString(R.string.leaderboards_around_me)),
                    selected = if (aroundMe) 1 else 0,
                    onSelect = { aroundMe = it == 1 },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
                when {
                    loading && entries.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator(color = colors.green)
                    }
                    error != null -> LbEmpty(error ?: uiContext.getString(R.string.common_couldn_t_load_entries))
                    entries.isEmpty() -> LbEmpty(uiContext.getString(R.string.leaderboards_no_entries_yet))
                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                            .verticalScrollbar(listState, colors.green.copy(alpha = 0.4f)),
                        contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        itemsIndexed(
                            entries,
                            key = { _, e -> "e-${e.rank}-${e.user}" },
                        ) { i, e -> EntryRow(e, i == cursor.index) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(e: RaLbEntry, selected: Boolean) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val colors = seedless
    val shape = RoundedCornerShape(12.dp)
    if (selected) {
        LaunchedEffect(e.rank, e.user) {
            ExternalInfoBus.show {
                ExternalSettingInfo(
                    title = uiContext.getString(R.string.leaderboard_rank_user, e.rank, e.user),
                    description = e.display,
                    crumb = uiContext.getString(R.string.leaderboards_leaderboard),
                )
            }
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (e.isYou) colors.greenDim else colors.surface)
            .let {
                when {
                    selected -> it.border(2.dp, colors.red, shape)
                    e.isYou -> it.border(1.dp, colors.green.copy(alpha = 0.4f), shape)
                    else -> it
                }
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = uiContext.getString(R.string.leaderboard_rank, e.rank),
            color = if (e.isYou) colors.green else colors.text3,
            fontFamily = SeedlessMono,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(48.dp),
        )
        Avatar(e.avatarUrl, 28.dp)
        Text(
            text = e.user,
            color = colors.text,
            fontFamily = Manrope,
            fontSize = 13.sp,
            fontWeight = if (e.isYou) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 10.dp),
        )
        Text(
            text = e.display,
            color = colors.text,
            fontFamily = SeedlessMono,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun Avatar(url: String?, size: androidx.compose.ui.unit.Dp) {
    if (url.isNullOrBlank()) {
        val colors = seedless
        Box(
            Modifier.size(size).clip(CircleShape).background(colors.surface2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Person, null, tint = colors.text3, modifier = Modifier.size(size * 0.6f))
        }
    } else {
        AsyncImage(model = url, contentDescription = null, modifier = Modifier.size(size).clip(CircleShape))
    }
}

@Composable
private fun LbEmpty(msg: String) {
    val colors = seedless
    Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
        Text(
            text = msg,
            color = colors.text3,
            fontFamily = Manrope,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
