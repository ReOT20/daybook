package com.joebywan.daybook

import com.joebywan.daybook.core.words.RussianWords
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class RussianWordsTest {
    @Test fun expandedGridDoesNotChangeFrozenLexiconAnswers() {
        val hashes = mapOf(
            4 to "b147f542bc8464d2e360325f96666120eac8427a72543ca4d7b7b2dda1b3bfb1",
            5 to "ec5afb1206ce1da005548aab13cffc9d7c759b9d24999f97475e847813227f00",
        )
        for ((length, expected) in hashes) {
            val bytes = RussianWords.answers(length).joinToString("\n", postfix = "\n").toByteArray()
            val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            assertEquals("frozen Russian Lexicon length $length", expected, actual)
            assertTrue(RussianWords.grid(length).size > RussianWords.answers(length).size)
        }
    }

    @Test fun canonicalSpellingDoesNotConfuseLatinAndCyrillic() {
        assertEquals(32, RussianWords.ALPHABET.length)
        assertEquals(32, RussianWords.ALPHABET.toSet().size)
        assertEquals("елка", RussianWords.normalize(" ЁЛКА\u0301 "))
        assertEquals("ёлка", RussianWords.spelling("ЕЛКА"))
        assertTrue(RussianWords.isWord("ЁЛКА"))
        assertFalse(RussianWords.isWord("eлка")) // Latin e.
        assertFalse(RussianWords.isWord("кот!"))
        assertFalse(RussianWords.isWord(""))
    }

    @Test fun repeatedTilesAndForeignLettersAreChecked() {
        assertTrue(RussianWords.spellable("сторона", "СТОРОНА"))
        assertTrue(RussianWords.spellable("нос", "сторона"))
        assertFalse(RussianWords.spellable("сосна", "сторона"))
        assertFalse(RussianWords.spellable("нос", "стoрона"))
        assertFalse(RussianWords.spellable("", "сторона"))
        assertEquals(emptyList<String>(), RussianWords.gridWordsFrom("я"))
    }

    @Test fun printedVocabularyHasDiversityCluesAndNoScreenedWords() {
        val minimum = mapOf(3 to 150, 4 to 400, 5 to 600, 6 to 550, 7 to 500)
        val clues = mutableSetOf<String>()
        for (length in 3..7) {
            val words = RussianWords.grid(length)
            assertTrue("length $length", words.size >= minimum.getValue(length))
            assertEquals(words.sorted().distinct(), words)
            assertTrue(words.containsAll(RussianWords.answers(length)))
            for (word in words) {
                assertEquals(length, word.length)
                assertTrue(word.all { it in RussianWords.ALPHABET })
                assertTrue(RussianWords.isWord(word))
                assertEquals(word, RussianWords.normalize(RussianWords.spelling(word)))
                val clue = RussianWords.clue(word)
                assertTrue("$word: $clue", clue.length in 10..170)
                assertFalse("$word: $clue", Regex("(?<![а-я])$word(?![а-я])").containsMatchIn(RussianWords.normalize(clue)))
                assertTrue("duplicate clue: $clue", clues.add(clue.lowercase()))
            }
        }
        // A representative independent safety sample; the exhaustive build checks exclude.txt.
        val printed = (3..7).flatMap(RussianWords::grid).toSet()
        for (word in listOf("бомба", "пуля", "война", "морг", "труп", "водка", "пиво", "пьяница", "задница", "дурак")) {
            assertFalse(word, word in printed)
        }
        // The screen must never turn a legitimate typed guess into a mistake.
        assertTrue(RussianWords.isWord("война"))
        assertTrue(RussianWords.isWord("котами"))
    }

    @Test fun rootsOfferEnoughScreenedWordsAndExpertHasAnotherLongWord() {
        for ((length, minimum) in listOf(5 to 4, 6 to 6, 7 to 8)) {
            val roots = RussianWords.roots(length)
            assertTrue("root length $length", roots.size >= 30)
            assertEquals(roots.sorted().distinct(), roots)
            for (root in roots) {
                val possible = RussianWords.gridWordsFrom(root)
                assertTrue(root, root in possible)
                assertTrue("$root: $possible", possible.size >= minimum)
                if (length == 7) assertTrue(root, possible.any { it.length == 6 })
            }
        }
    }

    @Test fun dailyRoundsDoNotRepeat() {
        for (length in 4..5) {
            val words = RussianWords.answers(length)
            for (round in 0..1) {
                val cycle = words.indices.map { RussianWords.dailyAnswer(round.toLong() * words.size + it, length) }
                assertEquals(words.size, cycle.toSet().size)
                assertEquals(words.toSet(), cycle.toSet())
            }
            assertNotEquals(RussianWords.dailyAnswer(0, length), RussianWords.dailyAnswer(1, length))
        }
    }
}
