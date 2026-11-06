package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** Exercise real nested phrases, not just isolated catalog entries with invented arguments. */
class LogicTeacherLocalizationTest {
    @Test fun `logic hint walks translate every displayed step without changing reasoning or moves`() {
        val original = Locale.getDefault()
        try {
            for (type in PuzzleRegistry.all.filter { it.id in LOGIC_IDS }) for (difficulty in Difficulty.entries) {
                Locale.setDefault(Locale.ENGLISH)
                var state = type.generate(49217L, difficulty)
                var steps = 0
                while (!state.solved) {
                    val where = "${type.id}/$difficulty, step $steps"
                    assertTrue("$where: hint walk did not finish", steps++ < 300)
                    Locale.setDefault(Locale.ENGLISH)
                    val english = type.teach(state)
                    assertNotNull("$where: no hint", english)
                    english!!
                    val nudge = english.nudgeMessage.resolve("en")
                    val explanation = english.explanationMessage.resolve("en")
                    assertEquals(where, english.nudge, nudge)
                    assertEquals(where, english.explanation, explanation)
                    for (phrase in listOf(english.nudgeMessage, english.explanationMessage)) {
                        val russian = phrase.resolve("ru-RU")
                        assertFalse("$where: blank translation", russian.isBlank())
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
                    val next = english.apply(state)
                    assertEquals(where, next, russian.apply(state))
                    assertFalse("$where: hint did not advance the board", next == state)
                    state = next
                }
            }
        } finally {
            Locale.setDefault(original)
        }
    }

    private companion object {
        val LOGIC_IDS = setOf(
            "atoms", "pipes", "pearls", "lamps", "tents", "lits", "mambo", "mosaic",
            "nonogram", "shikaku", "sand", "sets", "dominoes", "mines", "kings", "tally",
        )
    }
}
