package com.joebywan.daybook.ui.stats

import com.joebywan.daybook.core.i18n.text

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.core.Training
import com.joebywan.daybook.data.PuzzleTechniques
import com.joebywan.daybook.platform.BackButton
import com.joebywan.daybook.platform.rememberOpenUrl
import com.joebywan.daybook.ui.play.guideUrl

/**
 * Per puzzle that teaches, its techniques in the teacher's order; the ones a hint has explained carry a tick. The rest
 * are plain, never greyed or locked: this is a record of what hints showed, not a list to work through (no drills).
 * Each has a "Guide" link to its heading in the player guide; one Training has also a "Practise" link ([onPractise]:
 * puzzle id, technique), which opens its practice.
 */
@Composable
fun TechniquesScreen(
    techniques: List<PuzzleTechniques>,
    onBack: () -> Unit,
    onPractise: (String, String) -> Unit = { _, _ -> },
) {
    val scheme = MaterialTheme.colorScheme
    val openUrl = rememberOpenUrl()
    Column(
        Modifier.fillMaxSize().background(scheme.background).windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack)
            Column(Modifier.weight(1f).padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 8.dp)) {
                Text(text("ui.stats_screen.techniques"), style = MaterialTheme.typography.titleLarge, color = scheme.onBackground)
                Text(
                    text("ui.techniques_screen.techniques_you_ve_been_shown_in"),
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        LazyColumn(
            Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(techniques, key = { it.puzzleId }) { p ->
                val trained = Training.of(p.puzzleId)?.techniques.orEmpty()
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.surface).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        PuzzleRegistry.byId(p.puzzleId)?.displayName ?: p.puzzleId,
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurface,
                    )
                    p.techniques.forEach { t ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // The tick's room is kept either way, so the names line up.
                            if (t.met) Icon(Icons.Default.Check, text("ui.techniques_screen.shown"), tint = scheme.primary, modifier = Modifier.size(18.dp))
                            else Spacer(Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                t.name.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (t.met) scheme.primary else scheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // Under the name, not beside it: two links beside a long name squeeze it to a word a line.
                        // 16dp + a link's own 10dp padding starts them under the name.
                        Row(Modifier.padding(start = 16.dp)) {
                            TechniqueLink(text("ui.techniques_screen.guide")) { openUrl(guideUrl(p.puzzleId, t.id)) }
                            if (t.id in trained) TechniqueLink(text("ui.techniques_screen.practise")) { onPractise(p.puzzleId, t.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TechniqueLink(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
    )
}
