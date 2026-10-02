package com.seedlessds.app.emu

import com.seedlessds.app.R
import android.os.Build
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ControllerMapping(@androidx.annotation.StringRes val title: Int, val rows: List<Pair<Int, Int>>) {
    DEFAULT(R.string.mappinginfoscreen_generic_controller, listOf(
        R.string.mappinginfoscreen_d_pad to R.string.mappinginfoscreen_d_pad_navigate_menus, R.string.mappinginfoscreen_left_stick to R.string.mappinginfoscreen_d_pad_navigate_menus,
        R.string.button_y to R.string.button_x, R.string.button_x to R.string.button_y, R.string.button_b to R.string.mappinginfoscreen_a_cancel_in_menus, R.string.button_a to R.string.mappinginfoscreen_b_confirm_in_menus,
        R.string.common_play to R.string.common_start_2, R.string.common_back to R.string.mappinginfoscreen_open_menu, R.string.mappinginfoscreen_l1 to R.string.mappinginfoscreen_l_trigger, R.string.mappinginfoscreen_r1 to R.string.mappinginfoscreen_r_trigger,
        R.string.mappinginfoscreen_l3 to R.string.common_swap_screens, R.string.mappinginfoscreen_r3 to R.string.common_swap_layouts_1_2)),
    MOGA_POCKET(R.string.mappinginfoscreen_moga_pocket, listOf(
        R.string.mappinginfoscreen_left_stick to R.string.mappinginfoscreen_d_pad_navigate_menus, R.string.button_y to R.string.button_x, R.string.button_x to R.string.button_y,
        R.string.button_b to R.string.mappinginfoscreen_a_cancel, R.string.button_a to R.string.mappinginfoscreen_b_confirm, R.string.button_l to R.string.button_l, R.string.button_r to R.string.button_r,
        R.string.common_start_2 to R.string.common_start_2, R.string.common_select_2 to R.string.mappinginfoscreen_open_menu, R.string.mappinginfoscreen_select_l to R.string.common_quick_load_2,
        R.string.mappinginfoscreen_select_r to R.string.common_quick_save_2, R.string.mappinginfoscreen_select_x to R.string.common_fast_forward_2, R.string.mappinginfoscreen_select_y to R.string.common_swap_screens)),
    MOGA_PRO(R.string.mappinginfoscreen_moga_pro, listOf(
        R.string.mappinginfoscreen_d_pad to R.string.mappinginfoscreen_d_pad_navigate_menus, R.string.mappinginfoscreen_left_stick to R.string.mappinginfoscreen_d_pad_navigate_menus,
        R.string.button_y to R.string.button_x, R.string.button_x to R.string.button_y, R.string.button_b to R.string.mappinginfoscreen_a_cancel, R.string.button_a to R.string.mappinginfoscreen_b_confirm,
        R.string.mappinginfoscreen_l2 to R.string.button_l, R.string.mappinginfoscreen_r2 to R.string.button_r, R.string.common_start_2 to R.string.common_start_2, R.string.common_select_2 to R.string.common_select_2,
        R.string.mappinginfoscreen_l3 to R.string.mappinginfoscreen_open_menu, R.string.mappinginfoscreen_r3_l1 to R.string.common_quick_load_2, R.string.mappinginfoscreen_r3_r1 to R.string.common_quick_save_2,
        R.string.mappinginfoscreen_l1 to R.string.common_swap_screens, R.string.mappinginfoscreen_r1 to R.string.common_fast_forward_2)),
    SHIELD(R.string.mappinginfoscreen_nvidia_shield, listOf(
        R.string.mappinginfoscreen_d_pad to R.string.mappinginfoscreen_d_pad_navigate_menus, R.string.mappinginfoscreen_left_stick to R.string.mappinginfoscreen_d_pad_navigate_menus,
        R.string.button_y to R.string.button_x, R.string.button_x to R.string.button_y, R.string.button_b to R.string.mappinginfoscreen_a_cancel, R.string.button_a to R.string.mappinginfoscreen_b_confirm,
        R.string.common_play to R.string.common_start_2, R.string.common_back to R.string.mappinginfoscreen_open_menu, R.string.mappinginfoscreen_l1 to R.string.mappinginfoscreen_touch_pointer,
        R.string.mappinginfoscreen_r1 to R.string.common_fast_forward_2, R.string.mappinginfoscreen_l2 to R.string.button_l, R.string.mappinginfoscreen_r2 to R.string.button_r,
        R.string.mappinginfoscreen_l3 to R.string.common_swap_screens, R.string.mappinginfoscreen_r3 to R.string.common_swap_layouts_1_2)),
    XPERIA_PLAY(R.string.mappinginfoscreen_xperia_play, listOf(
        R.string.mappinginfoscreen_d_pad to R.string.mappinginfoscreen_d_pad_navigate_menus, R.string.common_select_2 to R.string.common_select_2, R.string.common_start_2 to R.string.common_start_2,
        R.string.mappinginfoscreen_triangle to R.string.button_x, R.string.mappinginfoscreen_square to R.string.button_y, R.string.mappinginfoscreen_circle to R.string.mappinginfoscreen_a_cancel,
        R.string.button_x to R.string.mappinginfoscreen_b_confirm, R.string.button_l to R.string.button_l, R.string.button_r to R.string.button_r, R.string.mappinginfoscreen_menu to R.string.mappinginfoscreen_open_menu));

    companion object {
        fun detected(): ControllerMapping {
            val model = Build.MODEL ?: ""; val man = Build.MANUFACTURER ?: ""
            return when {
                model.contains("SHIELD") && man.equals("NVIDIA", true) -> SHIELD
                model.startsWith("R800") && man.contains("Sony", true) -> XPERIA_PLAY
                else -> DEFAULT
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MappingInfoScreen(onBack: () -> Unit) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    androidx.activity.compose.BackHandler { onBack() }
    val mappings = ControllerMapping.entries
    var tab by remember { mutableIntStateOf(mappings.indexOf(ControllerMapping.detected()).coerceAtLeast(0)) }
    val m = mappings[tab]

    SeedlessScreenScaffold(
        title = uiContext.getString(R.string.mappinginfoscreen_controller_mapping),
        subtitle = uiContext.getString(R.string.mappinginfoscreen_read_only_known_controllers),
        onBack = onBack,
        modifier = Modifier.fillMaxSize(),
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            ScrollableTabRow(
                selectedTabIndex = tab,
                edgePadding = 12.dp,
                containerColor = MaterialTheme.colorScheme.background
            ) {
                mappings.forEachIndexed { i, mp ->
                    Tab(tab == i, onClick = { tab = i }, text = {
                        Text(uiContext.getString(mp.title), fontSize = 12.sp, maxLines = 1,
                            fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal)
                    })
                }
            }

            val st = rememberLazyListState()
            LazyColumn(
                state = st,
                modifier = Modifier.fillMaxSize()
                    .verticalScrollbar(st, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            ) {
                item {
                    Card(
                        Modifier.fillMaxWidth().padding(18.dp, 14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.SportsEsports, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp))
                            }
                            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                Text(uiContext.getString(m.title), fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold)
                                Text(uiContext.getString(R.string.mappinginfoscreen_default_mapping_for_this_controller), fontSize = 12.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                item {
                    Text(
                        uiContext.getString(R.string.mappinginfoscreen_button_assignment).uppercase(),
                        fontWeight = FontWeight.Bold, fontSize = 11.5.sp, letterSpacing = 0.8.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                            .padding(start = 18.dp, end = 16.dp, top = 6.dp, bottom = 2.dp)
                    )
                }

                item {
                    Row(Modifier.fillMaxWidth().padding(18.dp, 10.dp)) {
                        Text(uiContext.getString(R.string.mappinginfoscreen_physical_button), Modifier.weight(1f), fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary, fontSize = 12.5.sp)
                        Text(uiContext.getString(R.string.mappinginfoscreen_ds_function), Modifier.weight(1.3f), fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary, fontSize = 12.5.sp)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }

                items(m.rows) { (btn, fn) ->
                    Row(Modifier.fillMaxWidth().padding(18.dp, 11.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(uiContext.getString(btn), Modifier.weight(1f), fontSize = 15.5.sp,
                            fontWeight = FontWeight.Medium)
                        Text(uiContext.getString(fn), Modifier.weight(1.3f), fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
