package com.joebywan.daybook

import com.joebywan.daybook.core.ACHIEVEMENTS
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Training
import com.joebywan.daybook.core.earnedAchievements
import com.joebywan.daybook.core.i18n.message
import com.joebywan.daybook.core.remaining
import com.joebywan.daybook.data.Completion
import com.joebywan.daybook.ui.play.finishPraise
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AchievementLocalizationTest {
    private val today = LocalDate(2026, 11, 5)

    @Test fun `already initialized achievements follow language changes without changing earning rules`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ENGLISH)
            val first = ACHIEVEMENTS.first { it.id == "solves1" }
            val top = ACHIEVEMENTS.first { it.id == "firstTop" }
            val course = Training.COURSES.first()
            val trained = ACHIEVEMENTS.first { it.id == "${course.id}Trained" }
            val englishTraining = trained.title
            val history = listOf(Completion("sudoku", Difficulty.EXPERT, today, 120, 0))
            val earned = earnedAchievements(history, today).map { it.id }
            assertEquals("First solve", first.title)
            assertEquals("First Expert", top.title)

            Locale.setDefault(Locale.forLanguageTag("ru-RU"))
            assertEquals("Первое решение", first.title)
            assertEquals("Решите первую головоломку.", first.description)
            assertEquals("Первый «Эксперт»", top.title)
            assertTrue(trained.title, trained.title.contains(course.puzzle.displayName))
            assertFalse(englishTraining == trained.title)
            assertEquals(earned, earnedAchievements(history, today).map { it.id })

            Locale.setDefault(Locale.ENGLISH)
            assertEquals("First solve", first.title)
            assertEquals(englishTraining, trained.title)
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test fun `remaining achievement prose selects whole CLDR phrases`() {
        for ((n, noun) in listOf(1 to "день", 2 to "дня", 5 to "дней", 11 to "дней", 21 to "день")) {
            assertEquals("Ещё $n $noun до 7-дневной серии.",
                message("achievement.remaining.streak", "count" to n, "target" to 7).resolve("ru"))
        }
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ru-RU"))
            val history = listOf(Difficulty.STANDARD, Difficulty.EXPERT).map { Completion("sudoku", it, today, 120, 0) }
            val near = remaining("fullSet", history, today, listOf("sudoku"))!!
            assertEquals(1, near.left)
            assertTrue(near.text, near.text.contains("Сложный"))
            assertFalse(near.text.contains("Hard"))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test fun `finish praise keeps the same earned ids while translating its streak and achievements`() {
        val original = Locale.getDefault()
        try {
            val history = (1..6).map { back ->
                Completion("sudoku", Difficulty.HARD, LocalDate.fromEpochDays(today.toEpochDays() - back), 120, 0)
            }
            val solve = Completion("sudoku", Difficulty.HARD, today, 120, 0)
            Locale.setDefault(Locale.ENGLISH)
            val english = finishPraise(history, solve, today, "Sudoku")
            assertEquals("7 day streak", english.title)
            Locale.setDefault(Locale.forLanguageTag("ru-RU"))
            val russian = finishPraise(history, solve, today, "Судоку")
            assertEquals("серия: 7 дней", russian.title)
            assertEquals(english.achievements.map { it.id }, russian.achievements.map { it.id })
            assertTrue(russian.achievements.any { it.title == "Целая неделя" })
        } finally {
            Locale.setDefault(original)
        }
    }
}
