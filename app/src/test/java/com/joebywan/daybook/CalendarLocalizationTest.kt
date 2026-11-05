package com.joebywan.daybook

import com.joebywan.daybook.ui.stats.calendarWeekday
import kotlinx.datetime.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarLocalizationTest {
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
