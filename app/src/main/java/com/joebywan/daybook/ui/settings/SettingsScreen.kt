package com.joebywan.daybook.ui.settings

import com.joebywan.daybook.core.i18n.text

import com.joebywan.daybook.core.i18n.SUPPORTED_LANGUAGES
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import com.joebywan.daybook.ui.card
import com.joebywan.daybook.ui.CardText
import com.joebywan.daybook.ui.TitledScreen
import com.joebywan.daybook.ui.MotionChoice
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.joebywan.daybook.platform.OFFERS_REMINDER
import com.joebywan.daybook.platform.SOLVE_SOUND_NOTE
import com.joebywan.daybook.platform.rememberAskToNotify
import com.joebywan.daybook.platform.rememberReminderSchedule
import com.joebywan.daybook.ui.home.DEFAULT_REMINDER_HOUR
import com.joebywan.daybook.ui.home.REMINDER_HOURS
import com.joebywan.daybook.ui.home.hourLabel
import com.joebywan.daybook.ui.home.rememberLaunchPreferences
import kotlinx.coroutines.launch
import com.joebywan.daybook.platform.VIBRATION_NOTE
import com.joebywan.daybook.platform.rememberRateLink
import com.joebywan.daybook.platform.rememberAppLanguage
import com.joebywan.daybook.ui.home.SegmentedControl
import com.joebywan.daybook.ui.theme.Appearance

/**
 * The player's switches. Reached from the gear on Home; the system back button (or, on the web,
 * the arrow) is the way out, as on Stats. Each setting is a row in [LazyColumn], so the next one
 * is one more `item`.
 */
@Composable
fun SettingsScreen(
    showTimer: Boolean,
    onShowTimer: (Boolean) -> Unit,
    playSound: Boolean,
    onPlaySound: (Boolean) -> Unit,
    vibrate: Boolean,
    onVibrate: (Boolean) -> Unit,
    motionChoice: MotionChoice,
    onMotionChoice: (MotionChoice) -> Unit,
    customOrder: Boolean,
    onResetOrder: () -> Unit,
    appearance: Appearance,
    onAppearance: (Appearance) -> Unit,
    onBack: () -> Unit,
    /** Opens Training (`docs/TRAINING_SPEC.md`): its list of puzzles. */
    onTraining: () -> Unit = {},
) {
    val rateLink = rememberRateLink()

    val languageNote = text("ui.settings_screen.message")
    TitledScreen(text("ui.home_screen.settings"), null, onBack) {
        LazyColumn(
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { LanguageChoice() }
            item {
                SettingSwitch(
                    title = text("ui.settings_screen.show_the_timer_while_playing"),
                    detail = text("ui.settings_screen.a_running_clock_above_the_board"),
                    checked = showTimer,
                    onChecked = onShowTimer,
                )
            }
            item {
                SettingSwitch(
                    title = text("ui.settings_screen.sound"),
                    detail = text("ui.settings_screen.soft_sounds_as_you_play_and", "SOLVE_SOUND_NOTE" to (SOLVE_SOUND_NOTE)),
                    checked = playSound,
                    onChecked = onPlaySound,
                )
            }
            if (OFFERS_REMINDER) item { ReminderSetting() }
            item { AppearanceChoice(appearance, onAppearance) }
            item { AccessibilityHeading() }
            item { MotionSetting(motionChoice, onMotionChoice) }
            item {
                SettingSwitch(
                    title = text("ui.settings_screen.vibration"),
                    detail = text("ui.settings_screen.a_light_tap_when_a_move", "VIBRATION_NOTE" to (VIBRATION_NOTE)),
                    checked = vibrate,
                    onChecked = onVibrate,
                )
            }
            item { PuzzleOrderRow(customOrder, onResetOrder) }
            item { TrainingRow(onTraining) }
            item { RateRow(rateLink) }
            if (languageNote.isNotEmpty()) item {
                Text(languageNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { Credits() }
        }
    }
}

@Composable
private fun LanguageChoice() {
    val (selected, select) = rememberAppLanguage()
    var open by remember { mutableStateOf(false) }
    val system = text("ui.settings_screen.system_default")
    val title = text("ui.settings_screen.language")
    val label = SUPPORTED_LANGUAGES.firstOrNull { it.tag == selected }?.nativeName ?: system
    Column(Modifier.fillMaxWidth().card(MaterialTheme.colorScheme.surface).clickable(role = Role.Button) { open = true }.padding(14.dp)) {
        CardText(title, label)
    }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(title) },
        confirmButton = { TextButton(onClick = { open = false }) { Text(text("ui.share_preview.close")) } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                (listOf(null to system) + SUPPORTED_LANGUAGES.map { it.tag to it.nativeName }).forEach { (tag, name) ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = tag == selected, role = Role.RadioButton) {
                            open = false
                            select(tag)
                        }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = tag == selected, onClick = null)
                        Text(name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
    )
}

/**
 * The daily reminder (`ui/home/Reminder.kt`): a switch, and the hour once it is on. It reads and writes the launch
 * preferences itself. Turning it on asks for notification permission; refused, it stays off and says why.
 */
@Composable
private fun ReminderSetting() {
    val prefs = rememberLaunchPreferences()
    val on by prefs.reminder.collectAsState(false)
    val hour by prefs.reminderHour.collectAsState(DEFAULT_REMINDER_HOUR)
    var refused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ask = rememberAskToNotify()
    val schedule = rememberReminderSchedule()
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().card(scheme.surface)) {
        SettingSwitch(
            title = text("ui.android_reminder.daily_reminder"),
            detail = if (refused) text("ui.settings_screen.daybook_isn_t_allowed_to_send")
            else text("ui.settings_screen.one_quiet_note_a_day_at"),
            checked = on,
            onChecked = { want ->
                fun apply(now: Boolean) = scope.launch {
                    prefs.setReminder(now)
                    schedule(now, hour)
                }
                if (!want) apply(false) else ask { granted -> refused = !granted; if (granted) apply(true) }
            },
        )
        if (on) {
            SegmentedControl(
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                options = REMINDER_HOURS,
                selected = hour,
                label = ::hourLabel,
                verticalPadding = 7.dp,
                onSelect = { h ->
                    scope.launch {
                        prefs.setReminderHour(h)
                        schedule(true, h)
                    }
                },
                track = scheme.background,
            )
        }
    }
}

/**
 * The Accessibility section's heading, and what is always on with no switch (each is in the code: Kings', Tents',
 * Lamps' and Dominoes' long press; `boardSp`; the non-colour signals of `docs/COLOUR.md`). Reduced motion and Vibration
 * follow it. Screen readers are not a goal (CLAUDE.md, settled), so nothing here claims them.
 */
@Composable
private fun AccessibilityHeading() {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 12.dp)) {
        Text(text("ui.settings_screen.accessibility"), style = MaterialTheme.typography.titleLarge, color = scheme.onBackground)
        Text(
            text("ui.settings_screen.always_on_press_and_hold_does"),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
    }
}

/** Reduced motion: the device's setting (the default), or on or off whatever the device says. */
@Composable
private fun MotionSetting(choice: MotionChoice, onChoice: (MotionChoice) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .card(scheme.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column {
            CardText(
                text("ui.settings_screen.reduced_motion"),
                text("ui.settings_screen.stills_the_confetti_sparkles_shakes_and"),
            )
        }
        SegmentedControl(
            modifier = Modifier,
            options = MotionChoice.entries,
            selected = choice,
            label = { it.label },
            verticalPadding = 7.dp,
            onSelect = onChoice,
            track = scheme.background,
        )
    }
}

/** Light, dark or the device's, as the home grid's segmented rows are drawn. */
@Composable
private fun AppearanceChoice(appearance: Appearance, onAppearance: (Appearance) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .card(scheme.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column { CardText(text("ui.settings_screen.appearance"), text("ui.settings_screen.light_or_dark_or_the_same")) }
        SegmentedControl(
            modifier = Modifier,
            options = Appearance.entries,
            selected = appearance,
            label = { it.label },
            verticalPadding = 7.dp,
            onSelect = onAppearance,
            // The card is already surface-coloured, so the track takes the page's colour to show.
            track = scheme.background,
        )
    }
}

/**
 * The word lists' credit, which SCOWL's licence asks to travel with them, and the typeface's. The full
 * notices are `docs/word-lists/LICENSE-SCOWL.txt` and `docs/fonts/LICENSE-FREDOKA-OFL.txt`; this is the
 * copyright line and the permission each rests on.
 */
@Composable
private fun Credits() {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .card(scheme.surface)
            .padding(14.dp),
    ) {
        CardText(
            text("ui.settings_screen.word_lists"),
            text("ui.settings_screen.lexicon_s_words_come_from_scowl"),
        )
        Spacer(Modifier.height(12.dp))
        CardText(text("ui.settings_screen.typeface"), text("ui.settings_screen.fredoka_copyright_2016_the_fredoka_project"))
    }
}

/** The home grid's order: says which it is and how to change it (on the grid itself), and resets a player's own. */
@Composable
private fun PuzzleOrderRow(customOrder: Boolean, onReset: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .card(scheme.surface)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            CardText(
                text("ui.settings_screen.puzzle_order"),
                if (customOrder) text("ui.settings_screen.your_own_order_puzzles_added_later")
                else text("ui.settings_screen.most_played_first_over_the_last"),
            )
        }
        if (customOrder) {
            Text(
                text("ui.settings_screen.reset_to_most_played"),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = scheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onReset)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    detail: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .card(scheme.surface)
            // The whole row toggles, and the switch inside is only its picture, so a screen
            // reader meets one control with a name rather than an unlabelled switch.
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChecked)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            CardText(title, detail)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Training's way in beside the rules sheet's and the Techniques page's: the whole row opens its list of puzzles. */
@Composable
private fun TrainingRow(onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .card(scheme.surface)
            .clickable(onClick = onOpen)
            .padding(14.dp),
    ) {
        CardText(text("ui.launch_options.training"), text("ui.settings_screen.practise_the_hints_techniques_one_at"))
    }
}
