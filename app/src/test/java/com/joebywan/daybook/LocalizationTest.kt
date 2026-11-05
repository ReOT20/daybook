package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.core.i18n.calendarDate
import com.joebywan.daybook.core.i18n.isRussianLanguage
import com.joebywan.daybook.core.i18n.localized
import com.joebywan.daybook.core.i18n.russianPlural
import com.joebywan.daybook.platform.formatDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.datetime.LocalDate
import java.util.Locale

class LocalizationTest {
    @Test fun `Russian language tags select Russian and other languages keep English`() {
        for (tag in listOf("ru", "ru-RU", "ru_BY", "RU-ru")) {
            assertTrue(tag, isRussianLanguage(tag))
            assertEquals("Настройки", localized("Settings", "Настройки", tag))
        }
        for (tag in listOf("en", "fr-FR", "", "rue")) {
            assertFalse(tag, isRussianLanguage(tag))
            assertEquals("Settings", localized("Settings", "Настройки", tag))
        }
    }

    @Test fun `Russian counts distinguish teens and repeated tens`() {
        val expected = mapOf(
            0 to "дней", 1 to "день", 2 to "дня", 4 to "дня", 5 to "дней",
            11 to "дней", 12 to "дней", 14 to "дней", 20 to "дней",
            21 to "день", 22 to "дня", 25 to "дней", 101 to "день", 111 to "дней",
        )
        for ((count, form) in expected) assertEquals("$count", form, russianPlural(count, "день", "дня", "дней"))
    }

    @Test fun `web dates distinguish dates from standalone month headings`() {
        val date = LocalDate(2026, 11, 5)
        assertEquals("Thursday 5 November", calendarDate(date, "EEEE d MMMM", "en-US"))
        assertEquals("четверг 5 ноября", calendarDate(date, "EEEE d MMMM", "ru-RU"))
        assertEquals("ноябрь 2026", calendarDate(date, "LLLL yyyy", "ru"))
        assertEquals("November 2026", calendarDate(date, "LLLL yyyy", "fr-FR"))
        assertEquals("5 мая", calendarDate(LocalDate(2026, 5, 5), "d MMM", "ru"))
        assertEquals("29 февраля", calendarDate(LocalDate(2024, 2, 29), "d MMMM", "ru"))
    }

    @Test fun `Russian UI keeps stored difficulty names and every generated puzzle unchanged`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ENGLISH)
            val english = PuzzleRegistry.all.associate { it.id to it.generate(97531L, Difficulty.STANDARD) }
            Locale.setDefault(Locale.forLanguageTag("ru-RU"))
            assertEquals("Эксперт", Difficulty.EXPERT.label)
            assertEquals("Expert", Difficulty.EXPERT.englishLabel)
            assertEquals(listOf("STANDARD", "HARD", "EXPERT"), Difficulty.entries.map { it.name })
            for (puzzle in PuzzleRegistry.all) {
                assertEquals(puzzle.id, english.getValue(puzzle.id), puzzle.generate(97531L, Difficulty.STANDARD))
            }
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test fun `Android date cache follows locale changes and retains other languages`() {
        val original = Locale.getDefault()
        val date = LocalDate(2026, 11, 5)
        try {
            Locale.setDefault(Locale.ENGLISH)
            assertEquals("5 November", formatDate(date, "d MMMM"))
            Locale.setDefault(Locale.forLanguageTag("ru-RU"))
            assertEquals("5 ноября", formatDate(date, "d MMMM"))
            Locale.setDefault(Locale.FRANCE)
            assertEquals("5 novembre", formatDate(date, "d MMMM"))
            Locale.setDefault(Locale.ENGLISH)
            assertEquals("5 November", formatDate(date, "d MMMM"))
        } finally {
            Locale.setDefault(original)
        }
    }
}
