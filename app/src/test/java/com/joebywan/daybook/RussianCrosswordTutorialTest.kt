package com.joebywan.daybook

import com.joebywan.daybook.core.TutorialStep
import com.joebywan.daybook.core.Verdict
import com.joebywan.daybook.core.words.RussianWords
import com.joebywan.daybook.core.words.WordContent
import com.joebywan.daybook.puzzles.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class RussianCrosswordTutorialTest {
    @Test fun `native variants keep distinct identities and explicit content languages`() {
        assertEquals(listOf("mini-ru", "codeword-ru", "cipher-ru"), listOf(RussianMini.id, RussianCodeword.id, RussianCipher.id))
        assertEquals("ru", RussianMini.tutorialBoard().contentLanguage)
        assertEquals("ru", RussianCodeword.tutorialBoard().contentLanguage)
        assertEquals("ru", RussianCipher.tutorialBoard().contentLanguage)
        val original = Locale.getDefault()
        try {
            for (locale in listOf(Locale.ENGLISH, Locale.forLanguageTag("ru-RU"))) {
                Locale.setDefault(locale)
                val rules = RussianCodeword.rules.joinToString(" ").lowercase()
                assertFalse("native rules must not inherit the English word-list claim", "english" in rules || "английск" in rules)
                assertTrue("native rules identify their Russian words", "russian" in rules || "русск" in rules)
            }
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test fun `Russian Mini clues independently identify its native fixture`() {
        val s = RussianMini.tutorialBoard()
        assertFalse(s.solved)
        val slots = MiniLogic.slots(s.size, s.answer)
        assertEquals(listOf("нос", "оса", "сад", "нос", "оса", "сад"), slots.map { MiniLogic.spelled(s.answer, it) })
        for ((index, slot) in slots.withIndex()) {
            val matches = RussianWords.grid(slot.length).filter { RussianWords.clue(it) == s.clues[index] }
            assertEquals(listOf(MiniLogic.spelled(s.answer, slot)), matches)
        }
        val solved = s.copy(letters = s.answer)
        assertTrue(solved.solved)
        assertTrue("solved checks clues, not the stored answer", solved.copy(answer = "я".repeat(9)).solved)
    }

    @Test fun `Russian Mini accepts every typed prefix and rejects a stray letter`() {
        val frames = RussianMini.tutorial
        var s = RussianMini.tutorialBoard().select(0)
        assertEquals(TutorialStep.PROGRESS, frames[1].judge!!(s))
        for (letter in "нос") {
            s = s.type(letter)
            assertEquals(if (letter == 'с') TutorialStep.DONE else TutorialStep.PROGRESS, frames[1].judge!!(s))
        }
        assertEquals(TutorialStep.REJECT, frames[1].judge!!(s.withLetter(8, 'д')))
        s = s.select(0)
        assertEquals(TutorialStep.PROGRESS, frames[2].judge!!(s))
        s = s.select(0)
        assertEquals(TutorialStep.DONE, frames[2].judge!!(s))
        s = s.selectSlot(1)
        for (letter in "оса") {
            s = s.type(letter)
            assertEquals(if (letter == 'а') TutorialStep.DONE else TutorialStep.PROGRESS, frames[3].judge!!(s))
        }
        assertTrue(frames.last().freePlay)
        assertEquals(s.letters, (frames.last().state as MiniState).letters)
        assertTrue(s.withWord(2, "сад").solved)
    }

    @Test fun `Russian Codeword fixture has one answer by a separate alphabet oracle`() {
        val s = RussianCodeword.tutorialBoard()
        assertEquals(1, CodewordOracle.count(s.size, s.numbers, s.letters, contentLanguage = "ru"))
        val proof = CodewordLogic.solve(s.size, s.numbers, s.letters, "ru") as Verdict.ExactlyOne
        assertEquals(s.solution, proof.value)
        // The first guided input unlocks the short teacher ladder; the fixture itself is independently unique.
        val afterFirst = s.withLetter(4, WordContent.index('о', "ru"))
        assertNotNull(CodewordTeacher.walk(s.size, s.numbers, afterFirst.letters, contentLanguage = "ru"))
        val solved = s.copy(letters = s.solution)
        assertTrue(solved.solved)
        assertTrue("solved checks words, not the stored answer", solved.copy(solution = List(5) { 0 }).solved)
    }

    @Test fun `Russian Codeword walkthrough propagates shared letters then permits free play`() {
        val frames = RussianCodeword.tutorial
        val o = WordContent.index('о', "ru")
        val c = WordContent.index('с', "ru")
        var s = RussianCodeword.tutorialBoard().select(4)
        assertEquals(TutorialStep.PROGRESS, frames[1].judge!!(s))
        assertEquals(TutorialStep.REJECT, frames[1].judge!!(s.withLetter(4, c)))
        s = s.withLetter(4, o)
        assertEquals(TutorialStep.DONE, frames[1].judge!!(s))
        assertEquals(listOf(1, 3), s.numbers.indices.filter { s.numbers[it] == 4 })
        s = s.select(5)
        assertEquals(TutorialStep.PROGRESS, frames[2].judge!!(s))
        s = s.withLetter(5, c)
        assertEquals(TutorialStep.DONE, frames[2].judge!!(s))
        assertEquals(s.letters, (frames.last().state as CodewordState).letters)
        assertTrue(frames.last().freePlay)
        assertFalse(s.solved)
    }

    @Test fun `Russian Cipher fixture has one independently enumerated answer and logical hints`() {
        val s = RussianCipher.tutorialBoard()
        assertEquals(listOf(s.solution), CipherOracle.answers(s.words))
        assertTrue(CipherTeacher.solvesByLogic(s.sum))
        val solved = s.copy(digits = s.solution)
        assertTrue(solved.solved)
        assertTrue("solved checks arithmetic, not the stored answer", solved.copy(solution = List(4) { 0 }).solved)
        assertEquals(891L + 198L, CipherLogic.value(s.words.last(), s.sum, s.solution))
    }

    @Test fun `Russian Cipher teaches leading carry and zero before free play`() {
        val frames = RussianCipher.tutorial
        var s = RussianCipher.tutorialBoard()
        val c = s.sum.letters.indexOf('с')
        val e = s.sum.letters.indexOf('е')
        s = s.select(c)
        assertEquals(TutorialStep.PROGRESS, frames[1].judge!!(s))
        assertEquals(TutorialStep.REJECT, frames[1].judge!!(s.withDigit(c, 2)))
        s = s.withDigit(c, 1)
        assertEquals(TutorialStep.DONE, frames[1].judge!!(s))
        s = s.select(e)
        assertEquals(TutorialStep.PROGRESS, frames[2].judge!!(s))
        s = s.withDigit(e, 0)
        assertEquals(TutorialStep.DONE, frames[2].judge!!(s))
        assertEquals(s.digits, (frames.last().state as CipherState).digits)
        assertTrue(frames.last().freePlay)
        while (!s.solved) s = RussianCipher.teach(s)!!.apply(s) as CipherState
        assertEquals(RussianCipher.TUTORIAL_SOLUTION, s.digits)
    }
}
