package com.seedlessds.app.emu

import com.seedlessds.app.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SeedlessRailScaffold(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    acceptLabel: String? = null,
    header: @Composable (RowScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val colors = seedless
    Box(modifier.fillMaxSize().background(colors.bg)) {
        Row(Modifier.fillMaxSize().systemBarsPadding()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(58.dp).fillMaxHeight().padding(top = 8.dp, bottom = 12.dp),
            ) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack, uiContext.getString(R.string.common_back),
                        tint = colors.text, modifier = Modifier.size(19.dp),
                    )
                }
                Box(Modifier.weight(1f))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RailHint(label = uiContext.getString(R.string.ui_nav)) {
                        Icon(
                            Icons.Filled.SportsEsports, null,
                            tint = colors.text3, modifier = Modifier.size(15.dp),
                        )
                    }
                    if (acceptLabel != null) RailHint(button = uiContext.getString(R.string.button_a), label = acceptLabel.uppercase())
                    RailHint(button = uiContext.getString(R.string.button_b), label = uiContext.getString(R.string.common_close_2))
                }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(colors.line))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (header != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 20.dp, top = 10.dp, bottom = 8.dp),
                        content = header,
                    )
                    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
                }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter, content = content)
            }
        }
    }
}

@Composable
private fun RailHint(
    label: String,
    button: String? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    val colors = seedless
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (icon != null) {
            icon()
        } else if (button != null) {
            Box(
                modifier = Modifier.size(16.dp).clip(CircleShape).background(colors.surface2),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = button,
                    color = colors.text3,
                    fontSize = 8.sp,
                    lineHeight = 8.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Text(
            text = label,
            color = colors.text3,
            fontFamily = SeedlessMono,
            fontSize = 7.5.sp,
            lineHeight = 9.sp,
            textAlign = TextAlign.Center,
        )
    }
}
