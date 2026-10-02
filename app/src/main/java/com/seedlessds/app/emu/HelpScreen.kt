package com.seedlessds.app.emu

import com.seedlessds.app.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    androidx.activity.compose.BackHandler { onBack() }
    SeedlessScreenScaffold(
        title = uiContext.getString(R.string.common_help),
        subtitle = uiContext.getString(R.string.help_user_guide_and_about),
        onBack = onBack,
        modifier = Modifier.fillMaxSize(),
    ) { pad ->
        RowCursorHost(onCancel = onBack) {}
        val scroll = rememberScrollState()
        Column(
            Modifier.fillMaxSize().padding(pad)
                .verticalScrollbar(scroll, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                .verticalScroll(scroll)
        ) {
            HelpCard(Icons.Rounded.Gamepad, uiContext.getString(R.string.help_controls),
                uiContext.getString(R.string.help_controls_body))
            HelpCard(Icons.Rounded.Save, uiContext.getString(R.string.help_save_states),
                uiContext.getString(R.string.help_save_states_body))
            HelpCard(Icons.Rounded.Bolt, uiContext.getString(R.string.common_cheats),
                uiContext.getString(R.string.help_cheats_body))
            HelpCard(Icons.Rounded.Tune, uiContext.getString(R.string.common_settings),
                uiContext.getString(R.string.help_settings_body))
            HelpCard(Icons.Rounded.HelpOutline, uiContext.getString(R.string.help_faq),
                uiContext.getString(R.string.help_faq_body))
            HelpCard(Icons.Rounded.NewReleases, uiContext.getString(R.string.help_what_s_new),
                uiContext.getString(R.string.help_features_body))
            HelpCard(Icons.Rounded.Info, uiContext.getString(R.string.help_about),
                uiContext.getString(R.string.help_about_body))
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HelpCard(icon: ImageVector, title: String, body: String) {
    Card(
        Modifier.fillMaxWidth().padding(18.dp, 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)) }
                Text(
                    title.uppercase(), fontWeight = FontWeight.Bold, fontSize = 11.5.sp,
                    letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
            Text(
                body, fontSize = 14.sp, lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}
