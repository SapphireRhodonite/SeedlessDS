package com.seedlessds.app.emu

import com.seedlessds.app.R
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PresetsScreen(
    settings: DsSettings,
    onChange: (DsSettings) -> Unit,
    onBack: () -> Unit,
) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    var page by remember { mutableStateOf(Page.MAIN) }
    val scroll = rememberScrollState()

    SeedlessScreenScaffold(
        title = when (page) {
            Page.MAIN -> uiContext.getString(R.string.common_dual_screen_presets)
            Page.FILL -> uiContext.getString(R.string.presets_fill_area)
            Page.ALIGN -> uiContext.getString(R.string.common_vertical_alignment)
        },
        subtitle = when (page) {
            Page.MAIN -> if (settings.dualScreenPreset == DualScreenPresets.OFF) uiContext.getString(R.string.presets_disabled) else uiContext.getString(R.string.common_active_3)
            else -> uiContext.getString(R.string.common_dual_screen_presets_2)
        },
        onBack = { if (page == Page.MAIN) onBack() else page = Page.MAIN },
        hints = listOf(
            GamepadHint(null, uiContext.getString(R.string.common_navigate)),
            GamepadHint(uiContext.getString(R.string.button_a), uiContext.getString(R.string.common_change)),
            GamepadHint(uiContext.getString(R.string.button_b), uiContext.getString(R.string.common_back)),
        ),
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            RowCursorHost(
                onCancel = { if (page == Page.MAIN) onBack() else page = Page.MAIN },
                resetKey = page,
                scroll = scroll,
            ) {
                Column(
                    Modifier
                        .widthIn(max = 760.dp)
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .centerScrollViewport(LocalCenterScroll.current)
                        .verticalScroll(scroll)
                        .padding(horizontal = 16.dp),
                ) {
                    val presetOn = settings.dualScreenPreset != DualScreenPresets.OFF
                    val panelNote = DeviceProfiles.current(androidx.compose.ui.platform.LocalContext.current)?.let { uiContext.getString(R.string.presets_panel_sizes_from_the_profile, it.name) } ?: ""
                    val fillEnabled = presetOn && (settings.dsKeepRatio || settings.integerScale)
                    val alignEnabled = fillEnabled || (presetOn && settings.dsSameSize)
                    when (page) {
                        Page.MAIN -> {
                            if (!presetOn) {
                                SeedlessSectionLabel(uiContext.getString(R.string.presets_presets_disabled_the_layout_settings_apply_as))
                            }
                            SeedlessSectionLabel(uiContext.getString(R.string.common_preset))
                            DualScreenPresets.labels(uiContext).forEachIndexed { i, label ->
                                SeedlessRow(
                                    modifier = Modifier.padding(vertical = 3.dp),
                                    external = info(label, presetDescriptions(uiContext)[i]),
                                    onClick = { onChange(DualScreenPresets.apply(settings, i)) },
                                ) {
                                    RowLabel(label, Modifier.weight(1f))
                                    if (i == settings.dualScreenPreset) {
                                        Icon(
                                            Icons.Rounded.Check, null,
                                            tint = seedless.green,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            SeedlessSectionLabel(uiContext.getString(R.string.common_scaling))
                            SeedlessToggleRow(
                                label = uiContext.getString(R.string.common_keep_ds_ratio),
                                checked = settings.dsKeepRatio,
                                enabled = presetOn,
                                external = info(uiContext.getString(R.string.common_keep_ds_ratio), uiContext.getString(R.string.common_each_screen_holds_256_192_inside_its_panel)),
                                onToggle = { onChange(settings.copy(dsKeepRatio = it)) },
                            )
                            SeedlessToggleRow(
                                label = uiContext.getString(R.string.presets_same_screen_size),
                                checked = settings.dsSameSize,
                                enabled = presetOn,
                                external = info(uiContext.getString(R.string.presets_same_screen_size), uiContext.getString(R.string.presets_both_screens_take_the_size_of_the_smaller_panel_so_they_line_up_exactly_fil) + panelNote),
                                onToggle = { onChange(settings.copy(dsSameSize = it)) },
                            )
                            SeedlessToggleRow(
                                label = uiContext.getString(R.string.common_integer_scale),
                                checked = settings.integerScale,
                                enabled = presetOn,
                                external = info(uiContext.getString(R.string.common_integer_scale), uiContext.getString(R.string.common_scales_by_a_whole_number_so_a_ds_pixel_is)),
                                onToggle = { onChange(settings.copy(integerScale = it)) },
                            )
                            Spacer(Modifier.height(10.dp))
                            SeedlessRow(
                                modifier = Modifier.padding(vertical = 3.dp),
                                enabled = fillEnabled,
                                external = info(uiContext.getString(R.string.presets_fill_area), uiContext.getString(R.string.presets_let_each_screen_span_its_panel_even_where_the)),
                                onClick = { page = Page.FILL },
                            ) {
                                RowLabel(uiContext.getString(R.string.presets_fill_area), Modifier.weight(1f))
                                Chevron()
                            }
                            SeedlessRow(
                                modifier = Modifier.padding(vertical = 3.dp),
                                enabled = alignEnabled,
                                external = info(uiContext.getString(R.string.common_vertical_alignment), uiContext.getString(R.string.presets_where_the_space_left_over_goes_on_each_panel)),
                                onClick = { page = Page.ALIGN },
                            ) {
                                RowLabel(uiContext.getString(R.string.common_vertical_alignment), Modifier.weight(1f))
                                Chevron()
                            }
                        }
                        Page.FILL -> {
                            if (!fillEnabled) {
                                SeedlessSectionLabel(uiContext.getString(R.string.presets_filling_needs_keep_ratio_or_integer_scale_on))
                            }
                            SeedlessSectionLabel(uiContext.getString(R.string.common_this_device))
                            SeedlessToggleRow(
                                label = uiContext.getString(R.string.common_fill_width),
                                checked = settings.dsIntFillW,
                                enabled = fillEnabled,
                                onToggle = { onChange(settings.copy(dsIntFillW = it)) },
                            )
                            SeedlessToggleRow(
                                label = uiContext.getString(R.string.common_fill_height),
                                checked = settings.dsIntFillH,
                                enabled = fillEnabled,
                                onToggle = { onChange(settings.copy(dsIntFillH = it)) },
                            )
                            Spacer(Modifier.height(10.dp))
                            SeedlessSectionLabel(uiContext.getString(R.string.common_external_display))
                            SeedlessToggleRow(
                                label = uiContext.getString(R.string.common_fill_width),
                                checked = settings.dsExtFillW,
                                enabled = fillEnabled,
                                onToggle = { onChange(settings.copy(dsExtFillW = it)) },
                            )
                            SeedlessToggleRow(
                                label = uiContext.getString(R.string.common_fill_height),
                                checked = settings.dsExtFillH,
                                enabled = fillEnabled,
                                onToggle = { onChange(settings.copy(dsExtFillH = it)) },
                            )
                        }
                        Page.ALIGN -> {
                            SeedlessSectionLabel(uiContext.getString(R.string.common_this_device))
                            AlignRows(settings.dsIntAlign, alignEnabled) {
                                onChange(settings.copy(dsIntAlign = it))
                            }
                            Spacer(Modifier.height(10.dp))
                            SeedlessSectionLabel(uiContext.getString(R.string.common_external_display))
                            AlignRows(settings.dsExtAlign, alignEnabled) {
                                onChange(settings.copy(dsExtAlign = it))
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

private enum class Page { MAIN, FILL, ALIGN }

private fun presetDescriptions(uiContext: android.content.Context) = listOf(
    uiContext.getString(R.string.presets_the_screen_settings_apply_exactly_as),
    uiContext.getString(R.string.presets_top_screen_on_the_device_bottom_screen_on_the),
    uiContext.getString(R.string.presets_bottom_screen_on_the_device_top_screen_on_the),
)

@Composable
private fun info(title: String, description: String): @Composable () -> Unit =
    run {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    { ExternalSettingInfo(title = title, description = description, crumb = uiContext.getString(R.string.common_dual_screen_presets)) }
}

@Composable
private fun RowLabel(text: String, modifier: Modifier = Modifier) {
    androidx.compose.material3.Text(
        text = text,
        color = seedless.text,
        fontFamily = Manrope,
        fontSize = 13.5.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
        modifier = modifier.padding(end = 12.dp),
    )
}

@Composable
private fun AlignRows(selected: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    DualScreenPresets.alignLabels(uiContext).forEachIndexed { i, label ->
        SeedlessRow(
            modifier = Modifier.padding(vertical = 3.dp),
            enabled = enabled,
            onClick = { onSelect(i) },
        ) {
            RowLabel(label, Modifier.weight(1f))
            if (i == selected) {
                Icon(Icons.Rounded.Check, null, tint = seedless.green, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun Chevron() {
    Icon(
        Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
        tint = Color.White.copy(alpha = 0.45f),
        modifier = Modifier.size(20.dp),
    )
}
