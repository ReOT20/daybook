package com.joebywan.daybook.ui.home

import com.joebywan.daybook.core.i18n.text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.data.Completion
import com.joebywan.daybook.data.KeyValueStore
import com.joebywan.daybook.platform.OFFERS_REMINDER
import com.joebywan.daybook.platform.rememberAskToNotify
import com.joebywan.daybook.platform.rememberKeyValueStore
import com.joebywan.daybook.platform.rememberReminderSchedule
import com.joebywan.daybook.ui.CardText
import com.joebywan.daybook.ui.card
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

// The daily reminder (Android only; the web cannot notify reliably, so OFFERS_REMINDER hides it there).
// Off by default, offered once on Home, kept in Settings. At most one notification a day, at the player's
// hour, and none on a day they have already solved a daily. No follow-ups, no streak talk.

/** The hours Settings offers; 7 pm unless the player picks another. */
// ponytail: five fixed hours, not a clock picker; add a TimePicker if players ask for 6:30.
val REMINDER_HOURS = listOf(8, 12, 17, 19, 21)
const val DEFAULT_REMINDER_HOUR = 19

/** 19 as "7 pm". */
fun hourLabel(hour: Int): String = text("reminder.hour.$hour")

/**
 * The notification's text when it goes off at [hour]:[minute] (read at that moment, since an inexact alarm can
 * be late): a greeting for the time of day, how much of the day is left, then the invitation.
 */
fun reminderText(hour: Int, minute: Int = 0): String {
    val greeting = when (hour) {
        in 5..11 -> text("ui.reminder.good_morning")
        in 12..16 -> text("ui.reminder.good_afternoon")
        in 17..23 -> text("ui.reminder.good_evening")
        else -> text("ui.reminder.hello")
    }
    val left = 24 * 60 - (hour * 60 + minute)
    val hours = (left + 30) / 60
    val time = when {
        left < 45 -> text("ui.reminder.not_long_left_today")
        hours >= 12 -> text("ui.reminder.the_whole_day_is_ahead_of")
        hours == 1 -> text("ui.reminder.about_an_hour_left_today")
        else -> text("reminder.hours_left", "count" to (hours), "number" to (text("number.small.$hours")))
    }
    return text("ui.reminder.your_puzzles_are_ready_when_you", "greeting" to (greeting), "time" to (time))
}

/**
 * Whether Home may offer the reminder, from the solves alone: the player has solved a daily today and on some
 * other day, so the offer comes after their first solve on a second day. The days are the boards' dates, so an
 * archive board can stand in for a day played; that only brings the one offer a little early.
 */
fun reminderOfferDue(completions: List<Completion>, today: LocalDate): Boolean {
    val days = completions.mapNotNull { it.day }.toSet()
    return today in days && days.size >= 2
}

/** The launch preferences file, read by the reminder's own controls so nothing else has to pass them down. */
@Composable
internal fun rememberLaunchPreferences(): LaunchPreferences {
    val file = rememberKeyValueStore(KeyValueStore.LAUNCH)
    return remember(file) { LaunchPreferences(file) }
}

/**
 * The one-time offer on Home: a card under the grid (so it moves nothing above it), shown until the player
 * answers either way and never again. Turning the reminder on asks for notification permission first; a refusal
 * counts as an answer.
 */
@Composable
fun ReminderOffer(completions: List<Completion>, today: LocalDate) {
    if (!OFFERS_REMINDER) return
    val prefs = rememberLaunchPreferences()
    // Until the file is read, assume it was answered, so the card never flashes up for a player who said no.
    val offered by prefs.reminderOffered.collectAsState(true)
    val on by prefs.reminder.collectAsState(true)
    val hour by prefs.reminderHour.collectAsState(DEFAULT_REMINDER_HOUR)
    if (offered || on || !reminderOfferDue(completions, today)) return
    val scope = rememberCoroutineScope()
    val ask = rememberAskToNotify()
    val schedule = rememberReminderSchedule()
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(top = 14.dp).card(scheme.surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CardText(
            text("ui.reminder.a_reminder_each_day"),
            text("ui.reminder.one_quiet_note_at_and_none", "hourLabel_hour_" to (hourLabel(hour))),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            TextButton(onClick = { scope.launch { prefs.setReminderOffered() } }) { Text(text("ui.reminder.no_thanks")) }
            Button(onClick = {
                ask { granted ->
                    scope.launch {
                        if (granted) {
                            prefs.setReminder(true)
                            schedule(true, hour)
                        } else {
                            prefs.setReminderOffered()
                        }
                    }
                }
            }) { Text(text("ui.reminder.remind_me")) }
        }
    }
}
