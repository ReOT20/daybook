package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.puzzles.LexiconState
import com.joebywan.daybook.puzzles.WhosWhoLogic
import com.joebywan.daybook.puzzles.WhosWhoState
import com.joebywan.daybook.puzzles.WhosWhoText
import com.joebywan.daybook.puzzles.WhosWhoWords
import com.joebywan.daybook.puzzles.WhoClue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** Real nested arguments and length gates, complementary to the logic-family hint walks. */
class ExpandedTeacherLocalizationTest {
    @Test fun `digit and word hint walks translate without changing canonical reasoning or moves`() {
        val original = Locale.getDefault()
        try {
            for (type in PuzzleRegistry.all.filter { it.id in IDS }) for (tier in Difficulty.entries) {
                Locale.setDefault(Locale.ENGLISH)
                var state = type.generate(49217L, tier)
                var steps = 0
                while (!state.solved) {
                    val where = "${type.id}, $tier, step $steps"
                    assertTrue("$where: hint walk did not finish", steps++ < 300)
                    Locale.setDefault(Locale.ENGLISH)
                    val english = type.teach(state)
                    assertNotNull("$where: no hint", english)
                    english!!
                    for (phrase in listOf(english.nudgeMessage, english.explanationMessage)) {
                        val russian = phrase.resolve("ru-RU")
                        assertTrue("$where: no Russian text: $russian", russian.any { it in 'А'..'я' || it == 'ё' || it == 'Ё' })
                        assertFalse("$where: unresolved placeholder: $russian", '{' in russian || '}' in russian)
                    }
                    Locale.setDefault(Locale.forLanguageTag("ru-RU"))
                    val russian = type.teach(state)
                    assertNotNull("$where: locale removed a hint", russian)
                    russian!!
                    assertEquals(where, english.technique, russian.technique)
                    assertEquals(where, english.focus, russian.focus)
                    assertEquals(where, english.cited, russian.cited)
                    assertEquals(where, english.targets, russian.targets)
                    assertEquals(where, english.mistake, russian.mistake)
                    assertEquals(where, english.fallback, russian.fallback)
                    assertEquals(where, english.nudge, russian.nudge)
                    assertEquals(where, english.explanation, russian.explanation)
                    val applied = english.apply(state)
                    assertEquals(where, applied, russian.apply(state))
                    // The opening hint types a word; Enter is a separate player action before another hint.
                    val next = if (applied is LexiconState && applied.current.length == applied.length) applied.submit() else applied
                    assertFalse("$where: hint did not advance the board", next == state)
                    state = next
                }
            }
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test fun `Whos Who display grammar preserves every canonical item phrase`() {
        val s = vocabularyBoard()
        for (ref in 0 until s.n * (s.columns + 1)) {
            val where = "item $ref"
            assertEquals(where, WhosWhoLogic.word(s, ref), WhosWhoText.word(s, ref).english)
            assertEquals(where, WhosWhoLogic.who(s, ref), WhosWhoText.who(s, ref).english)
            assertEquals(where, WhosWhoLogic.has(s, ref), WhosWhoText.has(s, ref).english)
            assertEquals(where, WhosWhoLogic.hasNot(s, ref), WhosWhoText.hasNot(s, ref).english)
            assertEquals(where, WhosWhoLogic.base(s, ref), WhosWhoText.base(s, ref).english)
            assertEquals(where, WhosWhoLogic.thing(s, ref), WhosWhoText.thing(s, ref).english)
        }
        assertEquals("Анна", WhosWhoText.name(s, 0).resolve("ru"))
        assertEquals("держит кошку", WhosWhoText.has(s, 3 * s.n).resolve("ru"))
        assertEquals("работает пекарем", WhosWhoText.has(s, 4 * s.n).resolve("ru"))
    }

    @Test fun `Whos Who clue ordering and English wording stay exact for all relation shapes`() {
        val base = vocabularyBoard()
        val refs = 0 until base.n * (base.columns + 1)
        val clues = buildList {
            for (a in refs) for (b in refs) {
                for (kind in listOf(WhosWhoLogic.SAME, WhosWhoLogic.NOT, WhosWhoLogic.NEXT, WhosWhoLogic.LEFT)) {
                    add(WhoClue(kind, a, b))
                }
                val c = b / base.n * base.n + (b + 1) % base.n
                add(WhoClue(WhosWhoLogic.EITHER, a, b, c))
            }
        }
        val s = base.copy(clues = clues)
        for (k in clues.indices) {
            val text = WhosWhoText.clueText(s, k)
            assertEquals("clue $k: ${clues[k]}", WhosWhoLogic.clueText(s, k), text.english)
            assertFalse("clue $k", text.resolve("ru").isBlank())
            assertFalse("clue $k", '{' in text.resolve("ru"))
        }
    }

    private fun vocabularyBoard(): WhosWhoState = WhosWhoState(
        names = WhosWhoWords.NAMES.take(5),
        groups = (WhosWhoWords.HOUSE..WhosWhoWords.MUSIC).toList(),
        items = List(6) { (0 until 5).toList() },
        clues = emptyList(),
        answer = List(6) { (0 until 5).toList() },
    )

    private companion object {
        val IDS = setOf(
            "sudoku", "cages", "inequality", "regions", "chess", "cipher", "codeword", "dwindle",
            "hexwords", "words", "mini", "target", "tower", "whoswho", "wordwheel",
        )
    }
}
