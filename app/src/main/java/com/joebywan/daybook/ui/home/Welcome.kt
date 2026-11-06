package com.joebywan.daybook.ui.home

import com.joebywan.daybook.core.i18n.text

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.InsertChart
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.platform.platformLanguage
import com.joebywan.daybook.ui.stats.MedalIcon

/**
 * Shown once, in place of the home grid, to a new player (`LaunchPreferences.needsWelcome`): one line on what Daybook is
 * and a few pointers to what the home screen holds, then Start. No question, no tour of pages, nothing over the grid.
 */
@Composable
internal fun Welcome(onStart: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val icon = Modifier.size(22.dp)
    val small = Modifier.size(18.dp)
    Box(
        Modifier.fillMaxSize().background(scheme.background).windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxSize(), verticalArrangement = Arrangement.Center) {
            // Centred together on a tall screen; on a short one only the words scroll, so Start is never hidden.
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DaybookMark(Modifier.size(44.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(text("ui.welcome.welcome_to_daybook"), style = MaterialTheme.typography.headlineSmall, color = scheme.onBackground)
                }
                Text(
                    text("welcome.intro", "count" to (PuzzleRegistry.forLanguage(platformLanguage()).size)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onBackground,
                )
                Pointer(
                    { Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFFE8A317), modifier = icon) },
                    text("ui.welcome.today_s_three_over_the_grid"),
                )
                Pointer(
                    { Icon(Icons.Default.CalendarToday, null, tint = scheme.secondary, modifier = icon) },
                    text("ui.welcome.at_the_top_daily_for_today"),
                )
                Pointer(
                    {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(Icons.Default.History, null, tint = scheme.onSurfaceVariant, modifier = small)
                            MedalIcon(small, scheme.onSurfaceVariant, "")
                            Icon(Icons.Default.InsertChart, null, tint = scheme.onSurfaceVariant, modifier = small)
                        }
                    },
                    text("ui.welcome.the_buttons_by_the_title_open"),
                )
                Pointer(
                    { Icon(Icons.Default.OpenWith, null, tint = scheme.secondary, modifier = icon) },
                    text("ui.welcome.press_and_hold_a_puzzle_to"),
                )
            }
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).height(52.dp)) {
                Text(text("ui.welcome.start"), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun Pointer(icon: @Composable () -> Unit, text: String) {
    Row {
        Box(Modifier.width(36.dp).padding(top = 1.dp), contentAlignment = Alignment.TopStart) { icon() }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
