package com.joebywan.daybook.ui.settings

import com.joebywan.daybook.core.i18n.tr
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import com.joebywan.daybook.ui.card
import com.joebywan.daybook.ui.CardText
import com.joebywan.daybook.ui.TitledScreen
import com.joebywan.daybook.ui.MotionChoice
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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

    val languageNote = tr("", "Переведён интерфейс. Правила, подсказки, обучающие задания и словесные головоломки пока на английском.")
    TitledScreen(tr("Settings", "Настройки"), null, onBack) {
        LazyColumn(
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SettingSwitch(
                    title = tr("Show the timer while playing", "Показывать таймер во время игры"),
                    detail = tr("A running clock above the board. Off or on, your time is counted, " +
                        "shown when you solve, and kept for your statistics.", "Таймер над полем. Независимо от этой настройки время учитывается, показывается после решения и сохраняется в статистике."),
                    checked = showTimer,
                    onChecked = onShowTimer,
                )
            }
            item {
                SettingSwitch(
                    title = tr("Sound", "Звук"),
                    detail = tr("Soft sounds as you play, and a short tune when you solve a puzzle. $SOLVE_SOUND_NOTE", "Тихие звуки во время игры и короткая мелодия после решения. $SOLVE_SOUND_NOTE"),
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
                    title = tr("Vibration", "Вибрация"),
                    detail = tr("A light tap when a move lands, a firmer one when a bump costs a life, and a short buzz " +
                        "when you solve. $VIBRATION_NOTE", "Лёгкая вибрация при ходе, более сильная при потере жизни и короткий сигнал после решения. $VIBRATION_NOTE"),
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
            title = tr("Daily reminder", "Ежедневное напоминание"),
            detail = if (refused) tr("Daybook isn't allowed to send notifications. Allow them in your phone's settings, then try again.", "Daybook не разрешено отправлять уведомления. Разрешите их в настройках телефона и попробуйте снова.")
            else tr("One quiet note a day at a time you choose, and none on a day you've already solved a daily.", "Одно тихое уведомление в выбранное время. Если вы уже решили задание дня, уведомления не будет."),
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
        Text(tr("Accessibility", "Специальные возможности"), style = MaterialTheme.typography.titleLarge, color = scheme.onBackground)
        Text(
            tr("Always on: press and hold does whatever a double tap does, the numbers and letters on a board stay " +
                "inside their squares at large font sizes, and what is right or wrong is never shown by colour alone.", "Всегда включено: долгое нажатие заменяет двойное, цифры и буквы остаются внутри клеток при крупном шрифте, а верные и неверные ходы различаются не только цветом."),
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
                tr("Reduced motion", "Меньше анимации"),
                tr("Stills the confetti, sparkles, shakes and slides, and holds the hint glow steady. System follows your device's setting.", "Отключает конфетти, искры, тряску и движение, делает подсветку подсказок постоянной. «Система» использует настройку устройства."),
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
        Column { CardText(tr("Appearance", "Тема"), tr("Light or dark, or the same as your device.", "Светлая, тёмная или как на устройстве.")) }
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
            tr("Word lists", "Словари"),
            tr("Lexicon's words come from SCOWL (Spell Checker Oriented Word Lists), Copyright 2000-2016 " +
                "by Kevin Atkinson, used under its permissive licence. The lists are provided \"as is\" " +
                "without warranty, and Kevin Atkinson makes no representations about their suitability " +
                "for any purpose.", "Слова Lexicon взяты из SCOWL (Spell Checker Oriented Word Lists), Copyright 2000-2016 Kevin Atkinson, и используются по свободной лицензии. Списки предоставляются «как есть», без гарантий; Kevin Atkinson не заявляет об их пригодности для каких-либо целей."),
        )
        Spacer(Modifier.height(12.dp))
        CardText(tr("Typeface", "Шрифт"), tr("Fredoka, Copyright 2016 The Fredoka Project Authors, used under the SIL Open Font License 1.1.", "Fredoka, Copyright 2016 The Fredoka Project Authors. Используется по лицензии SIL Open Font License 1.1."))
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
                tr("Puzzle order", "Порядок головоломок"),
                if (customOrder) tr("Your own order. Puzzles added later join at the end.", "Ваш порядок. Новые головоломки добавляются в конец.")
                else tr("Most played first, over the last two months. Press and hold a puzzle on the home screen to move it.", "Сначала те, в которые вы чаще играли за последние два месяца. Чтобы переместить головоломку на главном экране, нажмите и удерживайте её."),
            )
        }
        if (customOrder) {
            Text(
                tr("Reset to most played", "Вернуть по частоте игры"),
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
        CardText(tr("Training", "Обучение"), tr("Practise the hints' techniques one at a time, on real boards.", "Отрабатывайте приёмы из подсказок по одному на настоящих заданиях."))
    }
}
