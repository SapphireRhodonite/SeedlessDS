package com.seedlessds.app.emu

import com.seedlessds.app.R
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.seedlessds.app.ra.RaAchievement
import com.seedlessds.app.ra.RaBucket
import com.seedlessds.app.ra.RaChallenge
import com.seedlessds.app.ra.RaMastery
import com.seedlessds.app.ra.RetroAchievements

@Composable
fun AchievementsScreen(onBack: () -> Unit) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val colors = seedless
    val buckets by RetroAchievements.achievements
    val loading by RetroAchievements.achLoading
    val loggedIn by RetroAchievements.loggedIn
    val title by RetroAchievements.gameTitle
    val total by RetroAchievements.achTotal
    val unlocked by RetroAchievements.achUnlocked
    val score by RetroAchievements.score
    val listState = rememberLazyListState()

    var detail by remember { androidx.compose.runtime.mutableStateOf<RaAchievement?>(null) }

    BackHandler { if (detail != null) detail = null else onBack() }
    LaunchedEffect(Unit) { RetroAchievements.refreshAchievements() }

    detail?.let { AchievementDetailDialog(it) { detail = null } }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { ExternalInfoBus.clear() } }

    val flat = remember(buckets) { buckets.flatMap { it.achievements } }
    var cursorScrollKey by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(cursorScrollKey) {
        var pos = 0
        var seen = 0
        for (b in buckets) {
            pos++
            if (cursorScrollKey < seen + b.achievements.size) { pos += cursorScrollKey - seen; break }
            seen += b.achievements.size
            pos += b.achievements.size
        }
        runCatching { listState.animateScrollToItem(pos) }
    }
    val cursor = rememberMenuCursor(
        count = flat.size,
        onAccept = { i -> flat.getOrNull(i)?.let { detail = it } },
        onCancel = { if (detail != null) detail = null else onBack() },
    )
    androidx.compose.runtime.LaunchedEffect(cursor.index) { cursorScrollKey = cursor.index }

    SeedlessRailScaffold(
        onBack = onBack,
        acceptLabel = uiContext.getString(R.string.common_view),
        header = {
            Column(Modifier.weight(1f)) {
                Text(
                    text = uiContext.getString(R.string.common_achievements),
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
            if (title != null) SummaryChip(unlocked, total, score)
            SeedlessHeaderAction(
                icon = Icons.Rounded.Refresh,
                contentDescription = uiContext.getString(R.string.common_refresh),
                onClick = { RetroAchievements.refreshAchievements() },
            )
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            when {
                !loggedIn -> EmptyMsg(uiContext.getString(R.string.achievements_sign_in_at_settings_retroachievements_to_see))
                loading && buckets.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = colors.green)
                }
                buckets.isEmpty() -> EmptyMsg(
                    if (title == null) uiContext.getString(R.string.achievements_this_game_isn_t_identified_in_retroachievements)
                    else uiContext.getString(R.string.achievements_this_game_has_no_achievements)
                )
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .widthIn(max = 760.dp)
                        .fillMaxSize()
                        .align(Alignment.TopCenter)
                        .verticalScrollbar(listState, colors.green.copy(alpha = 0.4f)),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    buckets.forEach { bucket ->
                        item(key = "h-${bucket.type}") { BucketHeader(bucket) }
                        items(bucket.achievements, key = { "a-${it.id}" }) { a ->
                            val flatIndex = flat.indexOf(a)
                            AchievementRow(
                                a = a,
                                selected = flatIndex == cursor.index,
                                onFocused = { pushAchievementToExternal(uiContext, a) },
                            ) {
                                cursor.select(flatIndex)
                                detail = a
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryChip(unlocked: Int, total: Int, score: Int) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val colors = seedless
    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier.padding(end = 6.dp),
    ) {
        Text(
            text = uiContext.getString(R.string.achievement_count_ratio, unlocked, total),
            color = colors.text,
            fontFamily = SeedlessMono,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = uiContext.getString(R.string.common_pts, score),
            color = SeedlessColors.gold,
            fontFamily = SeedlessMono,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun BucketHeader(bucket: RaBucket) =
    run {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    SeedlessSectionLabel(uiContext.getString(R.string.label_with_count, bucket.label, bucket.achievements.size), Modifier.fillMaxWidth())
}

@Composable
private fun AchievementRow(
    a: RaAchievement,
    selected: Boolean,
    onFocused: (RaAchievement) -> Unit,
    onClick: () -> Unit,
) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val colors = seedless
    val shape = RoundedCornerShape(13.dp)
    val focused = selected
    LaunchedEffect(focused) { if (focused) onFocused(a) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (a.unlocked) colors.greenDim else colors.surface)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    focused -> colors.red
                    a.unlocked -> colors.green.copy(alpha = 0.35f)
                    else -> colors.line
                },
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(11.dp),
    ) {
        Badge(a.badgeUrl, 42.dp, dim = !a.unlocked)
        Column(Modifier.weight(1f).padding(start = 11.dp)) {
            Text(
                text = a.title.ifBlank { uiContext.getString(R.string.unknown_symbol) },
                color = colors.text,
                fontFamily = Manrope,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = a.description,
                color = colors.text3,
                fontFamily = Manrope,
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (!a.unlocked && a.percent in 1..99) {
                Spacer(Modifier.size(6.dp))
                LinearProgressIndicator(
                    progress = { a.percent / 100f },
                    color = colors.green,
                    trackColor = colors.switchOff,
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                )
                Text(
                    text = a.progress ?: uiContext.getString(R.string.progress_percentage, a.percent),
                    color = colors.text3,
                    fontFamily = SeedlessMono,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(start = 8.dp),
        ) {
            Icon(
                imageVector = if (a.unlocked) Icons.Rounded.CheckCircle else Icons.Rounded.Lock,
                contentDescription = null,
                tint = if (a.unlocked) colors.green else colors.text3,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = uiContext.getString(R.string.number_value, a.points),
                color = SeedlessColors.gold,
                fontFamily = SeedlessMono,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

private fun pushAchievementToExternal(uiContext: android.content.Context, a: RaAchievement) {
    ExternalInfoBus.show {
        ExternalAchievementInfo(
            title = a.title,
            description = a.description,
            badgeUrl = a.badgeUrl,
            unlocked = a.unlocked,
            points = a.points,
            progress = if (!a.unlocked && a.percent in 1..99) (a.progress ?: uiContext.getString(R.string.progress_percentage, a.percent)) else null,
        )
    }
}

@Composable
private fun AchievementDetailDialog(a: RaAchievement, onDismiss: () -> Unit) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val colors = seedless
    SeedlessDialog(onDismiss = onDismiss, width = 380.dp) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 22.dp),
        ) {
            Badge(a.badgeUrl, 76.dp, dim = !a.unlocked)
            Spacer(Modifier.size(14.dp))
            Text(
                text = a.title.ifBlank { uiContext.getString(R.string.unknown_symbol) },
                color = colors.text,
                fontFamily = SpaceGrotesk,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(
                text = a.description,
                color = colors.text2,
                fontFamily = Manrope,
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.size(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (a.unlocked) Icons.Rounded.CheckCircle else Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = if (a.unlocked) colors.green else colors.text3,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = (if (a.unlocked) uiContext.getString(R.string.achievements_unlocked) else uiContext.getString(R.string.achievements_locked)) + uiContext.getString(R.string.common_pts_2, a.points),
                    color = if (a.unlocked) colors.green else colors.text3,
                    fontFamily = SeedlessMono,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (!a.unlocked && a.percent in 1..99) {
                Spacer(Modifier.size(12.dp))
                LinearProgressIndicator(
                    progress = { a.percent / 100f },
                    color = colors.green,
                    trackColor = colors.switchOff,
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                )
                Text(
                    text = a.progress ?: uiContext.getString(R.string.progress_percentage, a.percent),
                    color = colors.text3,
                    fontFamily = SeedlessMono,
                    fontSize = 9.5.sp,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
            Spacer(Modifier.size(18.dp))
            SeedlessButton(uiContext.getString(R.string.common_close), colors.surface2, onDismiss, fillWidth = true)
        }
    }
}

@Composable
private fun Badge(url: String?, size: androidx.compose.ui.unit.Dp, dim: Boolean) {
    val colors = seedless
    val shape = RoundedCornerShape(9.dp)
    if (url.isNullOrBlank()) {
        Box(
            Modifier.size(size).clip(shape).background(colors.surface2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.EmojiEvents, null,
                tint = colors.text3, modifier = Modifier.size(size * 0.5f),
            )
        }
    } else {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier.size(size).clip(shape).alpha(if (dim) 0.45f else 1f),
        )
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
            lineHeight = 19.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
    }
}

@Composable
private fun RaCard(
    iconUrl: String?,
    iconShape: Shape,
    eyebrowIcon: ImageVector,
    eyebrow: String,
    title: String,
    subtitle: String,
    subtitleLines: Int = 1,
    fallbackIcon: ImageVector = Icons.Rounded.EmojiEvents,
) {
    val gold = SeedlessColors.gold
    val shape = RoundedCornerShape(15.dp)
    Box(
        Modifier.padding(12.dp).widthIn(max = 400.dp)
            .shadow(10.dp, shape)
            .background(Color(0xF21E1D24), shape)
            .border(1.dp, gold.copy(alpha = 0.35f), shape)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (iconUrl.isNullOrBlank()) {
                Box(Modifier.size(48.dp).clip(iconShape).background(gold.copy(alpha = 0.18f)), Alignment.Center) {
                    Icon(fallbackIcon, null, tint = gold, modifier = Modifier.size(26.dp))
                }
            } else {
                AsyncImage(model = iconUrl, contentDescription = null,
                    modifier = Modifier.size(48.dp).clip(iconShape))
            }
            Column(Modifier.padding(start = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(eyebrowIcon, null, tint = gold, modifier = Modifier.size(12.dp))
                    Text(
                        text = eyebrow,
                        color = gold,
                        fontFamily = SeedlessMono,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(start = 5.dp),
                    )
                }
                Text(
                    text = title,
                    color = Color.White,
                    fontFamily = SpaceGrotesk,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (subtitle.isNotBlank()) Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.62f),
                    fontFamily = Manrope,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    maxLines = subtitleLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun RaUnlockOverlay(modifier: Modifier = Modifier) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val unlock by RetroAchievements.lastUnlock
    LaunchedEffect(unlock?.id) {
        if (unlock != null) { kotlinx.coroutines.delay(4500); RetroAchievements.consumeUnlock() }
    }
    AnimatedVisibility(
        visible = unlock != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier
    ) {
        val u = unlock ?: return@AnimatedVisibility
        RaCard(
            iconUrl = u.badgeUrl, iconShape = RoundedCornerShape(10.dp),
            eyebrowIcon = Icons.Rounded.EmojiEvents,
            eyebrow = if (u.points > 0) uiContext.getString(R.string.achievements_achievement_unlocked, u.points) else uiContext.getString(R.string.achievements_achievement_unlocked_2),
            title = u.title, subtitle = u.description, subtitleLines = 2,
        )
    }
}

@Composable
fun RaRichPresenceBar(modifier: Modifier = Modifier) {
    val rp by RetroAchievements.richPresence
    val text = rp
    if (!text.isNullOrBlank()) Box(modifier.background(Color(0x8C000000), RoundedCornerShape(8.dp))) {
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.85f),
            fontFamily = SeedlessMono,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).widthIn(max = 280.dp),
        )
    }
}

@Composable
fun RaChallengeIndicators(modifier: Modifier = Modifier) {
    val challenges = RetroAchievements.challenges
    if (challenges.isEmpty()) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        challenges.entries.sortedBy { it.key }.take(8).forEach { (id, ch) ->
            key(id) { ChallengeBadge(id, ch) }
        }
    }
}

@Composable
private fun ChallengeBadge(id: Int, ch: RaChallenge) {
    LaunchedEffect(ch.phase) {
        if (ch.phase != 0) { kotlinx.coroutines.delay(1600); RetroAchievements.clearChallenge(id) }
    }
    val colors = seedless
    val ring by animateColorAsState(
        when (ch.phase) {
            1 -> colors.green
            2 -> colors.red
            else -> Color(0x59FFFFFF)
        }, tween(250), label = "ra-challenge-ring"
    )
    Box(
        Modifier
            .size(40.dp)
            .alpha(if (ch.phase == 0) 0.72f else 1f)
            .clip(RoundedCornerShape(11.dp))
            .background(Color(0x66000000))
            .border(2.dp, ring, RoundedCornerShape(11.dp))
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Badge(ch.badgeUrl, 34.dp, dim = ch.phase == 2)
    }
}

@Composable
fun RaProgressPopup(modifier: Modifier = Modifier) {
    val prog by RetroAchievements.progressPopup
    AnimatedVisibility(
        visible = prog != null,
        enter = fadeIn(tween(150)), exit = fadeOut(tween(150)), modifier = modifier
    ) {
        val p = prog ?: return@AnimatedVisibility
        Box(Modifier.padding(8.dp).background(Color(0xCC0B0A0D), RoundedCornerShape(20.dp))) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Badge(p.badgeUrl, 24.dp, dim = false)
                Column(Modifier.padding(start = 8.dp)) {
                    Text(
                        text = p.title,
                        color = Color.White,
                        fontFamily = Manrope,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 170.dp),
                    )
                    Text(
                        text = p.progress,
                        color = SeedlessColors.gold,
                        fontFamily = SeedlessMono,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
fun RaWelcomeOverlay(modifier: Modifier = Modifier) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val nonce by RetroAchievements.welcomeNonce
    val username by RetroAchievements.username
    val avatar by RetroAchievements.userAvatar
    val score by RetroAchievements.score
    val title by RetroAchievements.gameTitle
    val gameIcon by RetroAchievements.gameIconUrl
    val total by RetroAchievements.achTotal
    val unlocked by RetroAchievements.achUnlocked

    val phaseState = remember { androidx.compose.runtime.mutableIntStateOf(-1) }
    LaunchedEffect(nonce) {
        if (nonce > 0 && username != null && title != null) {
            phaseState.intValue = 0
            kotlinx.coroutines.delay(2600)
            phaseState.intValue = 1
            kotlinx.coroutines.delay(4800)
            phaseState.intValue = -1
        }
    }
    AnimatedVisibility(
        visible = phaseState.intValue >= 0,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier
    ) {
        Crossfade(targetState = phaseState.intValue.coerceAtLeast(0), animationSpec = tween(400), label = "ra-welcome") { p ->
            if (p == 0) RaCard(
                iconUrl = avatar, iconShape = CircleShape,
                eyebrowIcon = Icons.Rounded.EmojiEvents, eyebrow = uiContext.getString(R.string.common_retroachievements),
                title = uiContext.getString(R.string.achievements_welcome, username ?: uiContext.getString(R.string.achievements_player)),
                subtitle = if (score > 0) uiContext.resources.getQuantityString(R.plurals.achievements_points_casual, score, score) else uiContext.getString(R.string.achievements_casual_mode),
            )
            else RaCard(
                iconUrl = gameIcon, iconShape = RoundedCornerShape(10.dp),
                eyebrowIcon = Icons.Rounded.SportsEsports, eyebrow = uiContext.getString(R.string.achievements_now_playing),
                title = title ?: "",
                subtitle = uiContext.resources.getQuantityString(R.plurals.achievements_achievements_casual, total, unlocked, total),
            )
        }
    }
}

@Composable
fun RaMasteryOverlay(modifier: Modifier = Modifier) {
    val mastery by RetroAchievements.masteryPopup
    LaunchedEffect(mastery?.nonce) {
        val m = mastery
        if (m != null) { kotlinx.coroutines.delay(6500); RetroAchievements.clearMastery(m.nonce) }
    }
    AnimatedVisibility(
        visible = mastery != null,
        enter = fadeIn(tween(400)) + scaleIn(initialScale = 0.85f, animationSpec = tween(400)),
        exit = fadeOut(tween(400)) + scaleOut(targetScale = 0.9f),
        modifier = modifier
    ) {
        MasteryCard(mastery ?: return@AnimatedVisibility)
    }
}

@Composable
private fun MasteryCard(m: RaMastery) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val gold = SeedlessColors.gold
    val shape = RoundedCornerShape(17.dp)
    Box(
        Modifier.padding(16.dp).widthIn(max = 420.dp)
            .shadow(16.dp, shape).background(Color(0xF2141317), shape)
            .border(BorderStroke(1.5.dp, Brush.linearGradient(listOf(gold, Color(0xFFB98A2E)))), shape)
    ) {
        Column(
            Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.WorkspacePremium, null, tint = gold, modifier = Modifier.size(15.dp))
                Text(
                    text = if (m.isSubset) uiContext.getString(R.string.achievements_subset_completed) else uiContext.getString(R.string.achievements_game_completed),
                    color = gold,
                    fontFamily = SeedlessMono,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Spacer(Modifier.size(14.dp))
            if (m.iconUrl.isNullOrBlank())
                Box(Modifier.size(76.dp).clip(RoundedCornerShape(14.dp)).background(gold.copy(alpha = 0.2f)),
                    Alignment.Center) { Icon(Icons.Rounded.WorkspacePremium, null, tint = gold, modifier = Modifier.size(42.dp)) }
            else
                AsyncImage(model = m.iconUrl, contentDescription = null,
                    modifier = Modifier.size(76.dp).clip(RoundedCornerShape(14.dp)))
            Spacer(Modifier.size(12.dp))
            Text(
                text = m.title,
                color = Color.White,
                fontFamily = SpaceGrotesk,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                text = if (m.isSubset) uiContext.getString(R.string.achievements_all_achievements_earned_casual)
                    else uiContext.resources.getQuantityString(R.plurals.achievements_all_achievements_earned_casual_2, m.count, m.count),
                color = Color.White.copy(alpha = 0.62f),
                fontFamily = Manrope,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
fun RaLeaderboardTrackers(modifier: Modifier = Modifier) {
    val trackers = RetroAchievements.trackers
    if (trackers.isEmpty()) return
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        trackers.entries.sortedBy { it.key }.take(4).forEach { (id, display) ->
            key(id) {
                Box(Modifier.background(Color(0xCC0B0A0D), RoundedCornerShape(8.dp))) {
                    Row(
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Timer, null, tint = SeedlessColors.gold, modifier = Modifier.size(12.dp))
                        Text(
                            text = display,
                            color = Color.White,
                            fontFamily = SeedlessMono,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 5.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RaScoreboardPopup(modifier: Modifier = Modifier) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val sb by RetroAchievements.scoreboard
    LaunchedEffect(sb?.lbId, sb?.submitted) {
        if (sb != null) { kotlinx.coroutines.delay(6000); RetroAchievements.clearScoreboard() }
    }
    AnimatedVisibility(
        visible = sb != null,
        enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
    ) {
        val s = sb ?: return@AnimatedVisibility
        RaCard(
            iconUrl = null, iconShape = RoundedCornerShape(10.dp),
            eyebrowIcon = Icons.Rounded.Leaderboard, eyebrow = uiContext.getString(R.string.achievements_leaderboard),
            title = s.title.ifBlank { uiContext.getString(R.string.achievements_result_submitted) },
            subtitle = uiContext.getString(R.string.achievements_rank_of, s.rank, s.total, s.submitted),
            fallbackIcon = Icons.Rounded.Leaderboard,
        )
    }
}

@Composable
fun RaLbToast(modifier: Modifier = Modifier) {
    val msg by RetroAchievements.lbToast
    LaunchedEffect(msg) { if (msg != null) { kotlinx.coroutines.delay(2500); RetroAchievements.clearLbToast() } }
    AnimatedVisibility(visible = msg != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        val m = msg ?: return@AnimatedVisibility
        val colors = seedless
        Box(Modifier.padding(8.dp).background(colors.red.copy(alpha = 0.92f), RoundedCornerShape(10.dp))) {
            Text(
                text = m,
                color = Color.White,
                fontFamily = Manrope,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
