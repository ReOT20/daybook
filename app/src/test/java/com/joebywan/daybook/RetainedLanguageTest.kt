package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.core.SudokuTraining
import com.joebywan.daybook.core.Training
import com.joebywan.daybook.core.TrainingRecord
import com.joebywan.daybook.data.Completion
import com.joebywan.daybook.puzzles.RussianLexicon
import com.joebywan.daybook.ui.tutorial.tutorialCarriedText
import com.joebywan.daybook.ui.play.Praise
import com.joebywan.daybook.ui.play.finishPraise
import com.joebywan.daybook.ui.training.Cheer
import com.joebywan.daybook.ui.training.graduateCheer
import com.joebywan.daybook.ui.training.passCheer
import java.util.Locale
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetainedLanguageTest {
    private inline fun languages(check: () -> Unit) {
        val original = Locale.getDefault()
        try { check() } finally { Locale.setDefault(original) }
    }

    @Test fun `saved win renders new language without changing the earned result`() = languages {
        val today = LocalDate(2026, 11, 6)
        val history = (1..2).map {
            Completion("tally", Difficulty.HARD, LocalDate.fromEpochDays(today.toEpochDays() - it), 100, 0)
        }
        val solve = Completion("tally", Difficulty.HARD, today, 110, 1)
        fun praise() = finishPraise(history, solve, today, PuzzleRegistry.byId("tally")!!.displayName, listOf("what-if"))
        Locale.setDefault(Locale.ENGLISH)
        val english = praise()
        val saved = Json.encodeToString(Praise.serializer(), english)
        Locale.setDefault(Locale.forLanguageTag("ru-RU"))
        val restored = Json.decodeFromString(Praise.serializer(), saved)
        val russian = restored.localized()
        assertEquals(praise().localized(), russian)
        assertTrue(russian.title.orEmpty().any { it in 'А'..'я' })
        assertTrue(russian.lines != english.lines)
        assertTrue(russian.toGo != english.toGo)
        assertEquals(english.achievements.map { it.id }, russian.achievements.map { it.id })
        assertEquals(english.streak, russian.streak)
        assertEquals(english.winKind(), russian.winKind())
        Locale.setDefault(Locale.ENGLISH)
        assertEquals(english, restored.localized())
    }

    @Test fun `training JSON keeps the same pass and graduation while all prose changes`() = languages {
        val course = SudokuTraining
        val technique = course.techniques.first()
        val others = course.techniques.drop(1).map { TrainingRecord.entry(course.id, it) }.toSet()
        val passed = others + TrainingRecord.entry(course.id, technique)
        for (record in listOf(others, passed)) {
            Locale.setDefault(Locale.ENGLISH)
            val english = requireNotNull(passCheer(course, record, technique, Training.RUN))
            val saved = Json.encodeToString(Cheer.serializer(), english)
            Locale.setDefault(Locale.forLanguageTag("ru"))
            val restored = Json.decodeFromString(Cheer.serializer(), saved)
            val expected = requireNotNull(passCheer(course, record, technique, Training.RUN))
            assertEquals(expected.praise.localized(), restored.praise.localized())
            assertTrue(english.praise.lines != restored.praise.localized().lines)
            assertEquals(english.copy(praise = restored.praise), restored)
            assertEquals(english.praise.achievements.map { it.id }, restored.praise.localized().achievements.map { it.id })
        }
        Locale.setDefault(Locale.ENGLISH)
        val english = graduateCheer(course, passed)
        val saved = Json.encodeToString(Cheer.serializer(), english)
        Locale.setDefault(Locale.forLanguageTag("ru"))
        val restored = Json.decodeFromString(Cheer.serializer(), saved)
        assertEquals(graduateCheer(course, passed).praise.localized(), restored.praise.localized())
        assertEquals(english.badge, restored.badge)
        assertEquals(english.badgeEarned, restored.badgeEarned)
    }

    @Test fun `completed walkthrough feedback follows language without moving the frame`() = languages {
        val frames = RussianLexicon.tutorial
        val savedFrame = "2"
        Locale.setDefault(Locale.ENGLISH)
        val english = tutorialCarriedText(frames, savedFrame)
        Locale.setDefault(Locale.forLanguageTag("ru"))
        assertEquals(frames[2].done, tutorialCarriedText(frames, savedFrame))
        assertTrue(english != tutorialCarriedText(frames, savedFrame))
        assertEquals("Earlier feedback", tutorialCarriedText(frames, "Earlier feedback"))
    }

    @Test fun `an unknown training technique keeps the existing title fallback`() = languages {
        Locale.setDefault(Locale.ENGLISH)
        val english = requireNotNull(passCheer(SudokuTraining, emptySet(), "unknown", Training.RUN))
        val saved = Json.encodeToString(Cheer.serializer(), english)
        Locale.setDefault(Locale.forLanguageTag("ru"))
        val restored = Json.decodeFromString(Cheer.serializer(), saved)
        assertEquals(SudokuTraining.titleOf("unknown"), restored.praise.localized().lines.first())
        assertEquals(english.passed, restored.passed)
    }

    @Test fun `old string-only celebration payloads retain defaults and remain readable`() = languages {
        Locale.setDefault(Locale.forLanguageTag("ru"))
        val oldPraise = """{"title":"Passed!","lines":["3 in a row"],"toGo":"An older progress line"}"""
        val praise = Json.decodeFromString(Praise.serializer(), oldPraise)
        assertEquals(praise, praise.localized())
        val cheer = Json.decodeFromString(Cheer.serializer(),
            """{"praise":$oldPraise,"badge":"sudokuTrained","badgeEarned":false,"passed":1,"of":7,"fresh":true}""")
        assertEquals(praise, cheer.praise.localized())
        assertTrue(cheer.fresh)
        assertEquals(1, cheer.passed)
    }
}
