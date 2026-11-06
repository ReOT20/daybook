package com.joebywan.daybook

import com.joebywan.daybook.ui.stats.calendarWeekday
import com.joebywan.daybook.core.i18n.SUPPORTED_LANGUAGES
import com.joebywan.daybook.core.i18n.calendarDate
import com.joebywan.daybook.core.i18n.catalogCalendars
import kotlinx.datetime.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlinx.datetime.LocalDate
import org.junit.Test

class CalendarLocalizationTest {
    @Test fun `every selectable language has complete generated calendar metadata`() {
        assertEquals(SUPPORTED_LANGUAGES.map { it.tag }.toSet(), catalogCalendars.keys)
        for (names in catalogCalendars.values) {
            for (days in listOf(names.weekdays, names.shortWeekdays, names.weekdayCaptions)) {
                assertEquals(7, days.size)
                assertTrue(days.all { it.isNotBlank() })
            }
            for (months in listOf(names.months, names.shortMonths, names.standaloneMonths)) {
                assertEquals(12, months.size)
                assertTrue(months.all { it.isNotBlank() })
            }
        }
    }

    @Test fun `Russian month headings and dates keep distinct grammatical forms`() {
        val headings = listOf("январь", "февраль", "март", "апрель", "май", "июнь", "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь")
        val dates = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")
        for (month in 1..12) {
            val date = LocalDate(2026, month, 5)
            assertEquals("${headings[month - 1]} 2026", calendarDate(date, "LLLL yyyy", "ru"))
            assertEquals("5 ${dates[month - 1]}", calendarDate(date, "d MMMM", "ru"))
        }
    }

    @Test fun `calendar captions stay Monday first in English and Russian`() {
        assertEquals(
            listOf("M", "T", "W", "T", "F", "S", "S"),
            DayOfWeek.entries.map { calendarWeekday(it, "en-US") },
        )
        for (tag in listOf("ru", "ru-RU", "ru_BY")) {
            assertEquals(
                listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"),
                DayOfWeek.entries.map { calendarWeekday(it, tag) },
            )
        }
    }

    @Test fun `unsupported calendar languages keep English captions`() {
        assertEquals(
            DayOfWeek.entries.map { calendarWeekday(it, "en") },
            DayOfWeek.entries.map { calendarWeekday(it, "fr-FR") },
        )
    }
}
