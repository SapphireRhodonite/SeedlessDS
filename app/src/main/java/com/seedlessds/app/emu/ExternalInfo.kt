package com.seedlessds.app.emu

import com.seedlessds.app.R
import android.app.Presentation
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.WindowManager
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import coil.compose.AsyncImage
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

class ExternalInfoPresentation(
    private val activity: ComponentActivity,
    display: Display,
) : Presentation(activity, display) {

    private val content = mutableStateOf<(@Composable () -> Unit)?>(null)

    fun setInfoContent(block: (@Composable () -> Unit)?) {
        content.value = block
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        )
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setContent {
                SeedlessTheme(isDarkTheme = true) {
                    Box(Modifier.fillMaxSize().background(SeedlessColors.tvBg)) {
                        content.value?.invoke() ?: ExternalIdleInfo()
                    }
                }
            }
        }
        setContentView(composeView)
    }
}

class ExternalInfoController(private val activity: ComponentActivity) {

    private var presentation: ExternalInfoPresentation? = null
    private var currentContent: (@Composable () -> Unit)? = null

    private val displayManager: DisplayManager
        get() = activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = refresh()
        override fun onDisplayRemoved(displayId: Int) = refresh()
        override fun onDisplayChanged(displayId: Int) = refresh()
    }

    fun attach() {
        displayManager.registerDisplayListener(displayListener, null)
        refresh()
    }

    fun detach() {
        runCatching { displayManager.unregisterDisplayListener(displayListener) }
        presentation?.dismiss()
        presentation = null
    }

    fun setContent(block: (@Composable () -> Unit)?) {
        currentContent = block
        presentation?.setInfoContent(block)
    }

    private fun isRealDisplay(d: Display): Boolean =
        d.state == Display.STATE_ON && (d.flags and Display.FLAG_PRIVATE) == 0

    private fun refresh() {
        val display = displayManager
            .getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .firstOrNull { isRealDisplay(it) }
        if (display == null) {
            presentation?.dismiss()
            presentation = null
            return
        }
        if (presentation?.display?.displayId == display.displayId) return
        presentation?.dismiss()
        presentation = ExternalInfoPresentation(activity, display).also {
            it.setInfoContent(currentContent)
            runCatching { it.show() }
        }
    }
}

@Composable
fun ExternalIdleInfo() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        SeedlessLogo(slabWidth = 34.dp, slabHeight = 22.dp)
        Spacer(Modifier.height(16.dp))
        SeedlessWordmark(fontSize = 34.sp)
    }
}

@Composable
fun ExternalLibraryGameInfo(
    title: String,
    icon: ImageBitmap?,
    coverUrl: String? = null,
    raIconUrl: String? = null,
    platformLabel: String,
    profile: String,
    favorite: Boolean,
    hasAchievements: Boolean,
    playTimeLabel: String?,
    continueSlot: Int?,
) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(gameGradient(title)).scanlines(0.03f))
        val backdropUrl = coverUrl ?: raIconUrl
        if (backdropUrl != null) {
            AsyncImage(
                model = backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.45f,
                modifier = Modifier.fillMaxSize().blur(20.dp),
            )
        } else if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.45f,
                modifier = Modifier.fillMaxSize().blur(20.dp),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(Color.Black.copy(alpha = 0.74f), Color.Black.copy(alpha = 0.34f)),
                )
            )
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, top = 30.dp, bottom = 44.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(210.dp)
                    .aspectRatio(DsBoxArtAspectRatio)
                    .shadow(14.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
            ) { GameArt(title, icon, Modifier.fillMaxSize(), coverUrl = coverUrl, raIconUrl = raIconUrl) }
            Spacer(Modifier.width(28.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = platformLabel.uppercase(),
                        color = Color.White,
                        fontFamily = SeedlessMono,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.6.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color.White.copy(alpha = 0.14f))
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                    )
                    if (favorite) {
                        Icon(
                            Icons.Filled.Star, null,
                            tint = FavoriteStar,
                            modifier = Modifier.padding(start = 9.dp).size(16.dp),
                        )
                    }
                    if (hasAchievements) {
                        Icon(
                            Icons.Rounded.EmojiEvents, null,
                            tint = SeedlessColors.gold,
                            modifier = Modifier.padding(start = 8.dp).size(15.dp),
                        )
                    }
                }
                Text(
                    text = title,
                    color = Color.White,
                    fontFamily = SpaceGrotesk,
                    fontSize = 27.sp,
                    lineHeight = 31.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    text = buildString {
                        append(uiContext.getString(R.string.keymap_profile))
                        append(profile)
                        if (continueSlot != null) append(uiContext.getString(R.string.external_continue_from_slot, continueSlot + 1))
                        if (playTimeLabel != null) append(uiContext.getString(R.string.external_detail_suffix, playTimeLabel))
                    },
                    color = Color.White.copy(alpha = 0.6f),
                    fontFamily = SeedlessMono,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(top = 20.dp),
                ) {
                    ExternalButtonHint(uiContext.getString(R.string.button_a), uiContext.getString(R.string.common_open))
                    ExternalButtonHint(uiContext.getString(R.string.button_y), uiContext.getString(R.string.common_favorite))
                }
            }
        }
    }
}

@Composable
fun ExternalCrumbInfo(
    crumb: String,
    title: String,
    description: String?,
    icon: ImageVector? = null,
    accent: Color? = null,
) {
    val colors = seedless
    val tint = accent ?: colors.green
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, bottom = 34.dp),
    ) {
        Text(
            text = crumb.uppercase(),
            color = Color.White.copy(alpha = 0.4f),
            fontFamily = SeedlessMono,
            fontSize = 10.sp,
            letterSpacing = 0.9.sp,
        )
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(13.dp)).background(tint.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, tint = tint, modifier = Modifier.size(27.dp)) }
                Spacer(Modifier.width(18.dp))
            }
            Text(
                text = title,
                color = Color.White,
                fontFamily = SpaceGrotesk,
                fontSize = 26.sp,
                lineHeight = 31.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!description.isNullOrBlank()) {
            Text(
                text = description,
                color = Color.White.copy(alpha = 0.62f),
                fontFamily = Manrope,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 18.dp),
            )
        }
    }
}

@Composable
private fun ExternalButtonHint(button: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(21.dp).border(1.5.dp, Color.White.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = button,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 11.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.55f),
            fontFamily = SeedlessMono,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
fun ExternalAchievementInfo(
    title: String,
    description: String,
    badgeUrl: String?,
    unlocked: Boolean,
    points: Int,
    progress: String?,
) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val colors = seedless
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(SeedlessColors.tvBg)
            .padding(horizontal = 56.dp, vertical = 44.dp),
    ) {
        if (badgeUrl.isNullOrBlank()) {
            Box(
                Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.EmojiEvents, null, tint = SeedlessColors.gold, modifier = Modifier.size(46.dp)) }
        } else {
            AsyncImage(
                model = badgeUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .alpha(if (unlocked) 1f else 0.5f),
            )
        }
        Text(
            text = title.ifBlank { uiContext.getString(R.string.unknown_symbol) },
            color = Color.White,
            fontFamily = SpaceGrotesk,
            fontSize = 25.sp,
            lineHeight = 29.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 18.dp),
        )
        Text(
            text = (if (unlocked) uiContext.getString(R.string.external_unlocked) else uiContext.getString(R.string.external_locked)) + uiContext.getString(R.string.common_pts_2, points),
            color = if (unlocked) colors.green else Color.White.copy(alpha = 0.5f),
            fontFamily = SeedlessMono,
            fontSize = 10.sp,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (description.isNotBlank()) {
            Text(
                text = description,
                color = Color.White.copy(alpha = 0.7f),
                fontFamily = Manrope,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 12.dp).width(440.dp),
            )
        }
        if (!progress.isNullOrBlank()) {
            Text(
                text = progress,
                color = SeedlessColors.gold,
                fontFamily = SeedlessMono,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
fun ExternalSettingInfo(
    title: String,
    description: String?,
    crumb: String,
    icon: ImageVector? = null,
) {
    val colors = seedless
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(SeedlessColors.tvBg)
            .padding(horizontal = 60.dp, vertical = 40.dp),
    ) {
        Icon(
            imageVector = icon ?: Icons.Rounded.Tune,
            contentDescription = null,
            tint = colors.green,
            modifier = Modifier.size(46.dp),
        )
        Text(
            text = title,
            color = Color.White,
            fontFamily = SpaceGrotesk,
            fontSize = 23.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 14.dp),
        )
        if (!description.isNullOrBlank()) {
            Text(
                text = description,
                color = Color.White.copy(alpha = 0.6f),
                fontFamily = Manrope,
                fontSize = 13.5.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 9.dp).width(420.dp),
            )
        }
        Text(
            text = crumb.uppercase(),
            color = Color.White.copy(alpha = 0.35f),
            fontFamily = SeedlessMono,
            fontSize = 9.5.sp,
            letterSpacing = 0.6.sp,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
fun ExternalStateSlotInfo(
    label: String,
    quick: Boolean,
    detail: String?,
    thumbnail: ImageBitmap?,
    saving: Boolean,
) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val colors = seedless
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(SeedlessColors.tvBg)
            .padding(horizontal = 56.dp, vertical = 40.dp),
    ) {
        Box(
            Modifier
                .width(320.dp)
                .aspectRatio(256f / 192f)
                .clip(RoundedCornerShape(12.dp))
                .background(if (thumbnail != null) SeedlessColors.emulationBg else colors.surface2)
                .border(
                    width = if (quick) 2.dp else 1.dp,
                    color = if (quick) colors.red else Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = uiContext.getString(R.string.common_empty),
                    color = colors.text3,
                    fontFamily = SeedlessMono,
                    fontSize = 13.sp,
                    letterSpacing = 1.sp,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 18.dp)) {
            Text(
                text = label,
                color = Color.White,
                fontFamily = SpaceGrotesk,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
            )
            if (quick) {
                Text(
                    text = uiContext.getString(R.string.common_quick),
                    color = colors.red,
                    fontFamily = SeedlessMono,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(
            text = detail ?: if (saving) uiContext.getString(R.string.external_empty_press_to_save) else uiContext.getString(R.string.common_empty),
            color = Color.White.copy(alpha = 0.55f),
            fontFamily = SeedlessMono,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = (if (saving) uiContext.getString(R.string.common_save_state_2) else uiContext.getString(R.string.common_load_state_2)),
            color = Color.White.copy(alpha = 0.35f),
            fontFamily = SeedlessMono,
            fontSize = 9.5.sp,
            letterSpacing = 0.6.sp,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
fun ExternalLoadingInfo(title: String) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    DsBootOverlay(
        half = DsBootScreenHalf.BOTH,
        romReady = false,
        romTitle = title,
        statusText = uiContext.getString(R.string.common_loading),
        onFinished = {},
    )
}

@Composable
fun ExternalDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
}
