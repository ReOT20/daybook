package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.i18n.SavedText
import com.joebywan.daybook.data.Completion
import com.joebywan.daybook.data.LevelSolve
import com.joebywan.daybook.platform.formatDate
import com.joebywan.daybook.puzzles.Chess
import com.joebywan.daybook.puzzles.ChessState
import com.joebywan.daybook.puzzles.Lexicon
import com.joebywan.daybook.puzzles.LexiconState
import com.joebywan.daybook.puzzles.Pointers
import com.joebywan.daybook.puzzles.PuzzleState
import com.joebywan.daybook.puzzles.RussianCipher
import com.joebywan.daybook.puzzles.RussianCodeword
import com.joebywan.daybook.puzzles.RussianDwindle
import com.joebywan.daybook.puzzles.RussianHexwords
import com.joebywan.daybook.puzzles.RussianLexicon
import com.joebywan.daybook.puzzles.RussianMini
import com.joebywan.daybook.puzzles.RussianWordWheel
import com.joebywan.daybook.puzzles.Sets
import com.joebywan.daybook.puzzles.SetsState
import com.joebywan.daybook.puzzles.Sudoku
import com.joebywan.daybook.puzzles.Target
import com.joebywan.daybook.puzzles.TargetMove
import com.joebywan.daybook.puzzles.TargetOp
import com.joebywan.daybook.puzzles.TargetState
import com.joebywan.daybook.puzzles.Tower
import com.joebywan.daybook.puzzles.TowerState
import com.joebywan.daybook.puzzles.WordWheel
import com.joebywan.daybook.puzzles.WordWheelState
import com.joebywan.daybook.ui.play.Mark
import com.joebywan.daybook.ui.play.Praise
import com.joebywan.daybook.ui.play.ShareCard
import com.joebywan.daybook.ui.play.ShareCardText
import com.joebywan.daybook.ui.play.ShareLine
import com.joebywan.daybook.ui.play.ShareStats
import com.joebywan.daybook.ui.play.dayShareCard
import com.joebywan.daybook.ui.play.levelShareCard
import com.joebywan.daybook.ui.play.shareCard
import com.joebywan.daybook.ui.play.threeShareCard
import java.util.Locale
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetainedShareLanguageTest {
    private val today = LocalDate(2026, 11, 6)
    private inline fun languages(check: () -> Unit) {
        val original = Locale.getDefault()
        try { check() } finally { Locale.setDefault(original) }
    }

    private fun checkCard(make: () -> ShareCard) {
        Locale.setDefault(Locale.ENGLISH)
        val english = make()
        val englishText = english.text
        val saved = Json.encodeToString(ShareCard.serializer(), english)
        Locale.setDefault(Locale.forLanguageTag("ru-RU"))
        val restored = Json.decodeFromString(ShareCard.serializer(), saved)
        val russian = restored.localized()
        assertEquals(make(), russian)
        assertTrue(englishText != russian.text)
        assertEquals(english.lines.map { it.marks to it.hinted }, russian.lines.map { it.marks to it.hinted })
        val frame = Praise(share = restored, setShare = restored).localized()
        assertEquals(russian, frame.share)
        assertEquals(russian, frame.setShare)
        Locale.setDefault(Locale.ENGLISH)
        assertEquals(english, restored.localized())
        // A stored share card must never acquire the underlying puzzle's secret as render metadata.
        assertFalse(saved, "crane" in saved)
    }

    @Test fun `every single-solve format retains marks while all saved wording changes`() = languages {
        val op = TargetMove(0, 1, 2, 3, TargetOp.Add, 5)
        val fixtures: List<Pair<PuzzleType, PuzzleState>> = listOf(
            Sudoku to Sudoku.generate(1L, Difficulty.STANDARD),
            Lexicon to LexiconState(5, 6, "crane", listOf("slate", "crane")),
            Tower to TowerState(4, 6, 8, listOf(0, 1, 2, 3), listOf(listOf(0, 1, 2, 3)), List(4) { -1 }),
            Sets to SetsState(emptyList(), 0, emptyList(), emptyList()),
            Chess to ChessState(start = "8/8/8/8/8/8/8/8 w - - 0 1", mateIn = 2),
            Target to TargetState(listOf(2, 3, 4, 5, 25, 50), target = 999, par = 3, log = listOf(op, op, op)),
            Pointers to Pointers.tutorialBoard(),
            WordWheel to (WordWheel.generate(1L, Difficulty.STANDARD) as WordWheelState).copy(bonus = listOf("one", "two")),
            RussianLexicon to RussianLexicon.tutorialBoard(),
            RussianWordWheel to RussianWordWheel.tutorialBoard(),
            RussianMini to RussianMini.tutorialBoard(),
            RussianCodeword to RussianCodeword.tutorialBoard(),
            RussianDwindle to RussianDwindle.tutorialBoard(),
            RussianHexwords to RussianHexwords.tutorialBoard(),
            RussianCipher to RussianCipher.tutorialBoard(),
        )
        for ((puzzle, state) in fixtures) for (hints in listOf(0, 2)) {
            checkCard {
                shareCard(puzzle, Difficulty.HARD, formatDate(today, "d MMM"), "12:04", 22, state,
                    "mwwmum", hints, dateValue = today)
            }
        }
    }

    @Test fun `day three and level cards retain names counts dates and partial progress`() = languages {
        val ids = listOf("sudoku", "tally", "tower", "snap")
        for (n in listOf(2, 4)) {
            val completions = ids.take(n).map { Completion(it, Difficulty.HARD, today, 60, 1) }
            checkCard { requireNotNull(dayShareCard(ids, completions, today, formatDate(today, "d MMM"), 12, dateValue = today)) }
        }
        for (n in listOf(2, 3)) {
            val completions = ids.take(n).map { Completion(it, Difficulty.STANDARD, today, 60, 1) }
            checkCard { requireNotNull(threeShareCard(ids.take(3), completions, today, Difficulty.STANDARD,
                formatDate(today, "d MMM"), 2, { "3:00" }, dateValue = today)) }
        }
        for (hints in listOf(0, 2)) {
            val solves = ids.map { LevelSolve(14, it, 60, hints) }
            checkCard { levelShareCard(14, ids, solves) { "4:00" } }
        }
    }

    @Test fun `unreadable restored recipes fall back to their complete snapshot`() = languages {
        val line = ShareLine(listOf(Mark.GREEN), tail = " old tail")
        val card = ShareCard("Old header", listOf(line), "Old stats", listOf(Mark.GREEN to "Old key"), "Old nudge")
        val source = ShareCardText(SavedText("future.share_header"), ShareStats(clock = "1:00"),
            listOf(Mark.GREEN to SavedText("ui.share_card.solved")), SavedText("ui.share_card.every_board_your_own_work"))
        val unknownHeader = card.copy(source = source)
        val unknownTail = card.copy(lines = listOf(line.copy(tailSource = SavedText("future.share_tail"))),
            source = source.copy(header = SavedText("ui.share_card.first_guess_nothing_to_improve_on")))
        for (language in listOf(Locale.ENGLISH, Locale.forLanguageTag("ru"))) {
            Locale.setDefault(language)
            for (snapshot in listOf(unknownHeader, unknownTail)) {
                val saved = Json.encodeToString(ShareCard.serializer(), snapshot)
                val restored = Json.decodeFromString(ShareCard.serializer(), saved)
                assertEquals(snapshot, restored.localized())
                assertEquals(card.text, restored.text)
            }
            val frame = Praise(title = "Old title", lines = listOf("Old line"), streak = 22,
                titleSource = SavedText("future.finish_title"))
            assertEquals(frame, frame.localized())
        }
    }

    @Test fun `legacy card payload is an unchanged readable snapshot across languages`() = languages {
        val old = """{"header":"Daybook · old header","lines":[{"marks":["GREEN"],"tail":" old tail"}],"stats":"old stats","key":[{"first":"GREEN","second":"old key"}],"nudge":"old nudge"}"""
        Locale.setDefault(Locale.ENGLISH)
        val english = Json.decodeFromString(ShareCard.serializer(), old)
        val text = english.text
        Locale.setDefault(Locale.forLanguageTag("ru"))
        assertEquals(english, english.localized())
        assertEquals(text, english.text)
    }
}
