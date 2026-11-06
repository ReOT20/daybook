package com.joebywan.daybook

import com.joebywan.daybook.core.Levels
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.core.SeedHash
import com.joebywan.daybook.core.puzzleFamilyId
import com.joebywan.daybook.data.savedGameKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageLevelsTest {
    @Test fun `every level in both lanes deals five unique visible families including after level one hundred`() {
        for (tag in listOf("en-GB", "ru-RU")) {
            val visible = PuzzleRegistry.forLanguage(tag).mapTo(HashSet()) { it.id }
            for (level in Levels.LEVELS) {
                val previous = Levels.plan(level, Levels.LANGUAGE_POOL_SIZE)
                val boards = Levels.planForLanguage(level, languageTag = tag)
                assertEquals("$tag $level", 5, boards.size)
                assertEquals("$tag $level", 5, boards.map { puzzleFamilyId(it.puzzleId) }.distinct().size)
                assertTrue("$tag $level", boards.all { it.puzzleId in visible })
                assertEquals("$tag $level", previous.map { it.puzzleId }, boards.map { puzzleFamilyId(it.puzzleId) })
                assertEquals("$tag $level", previous.map { it.difficulty }, boards.map { it.difficulty })
            }
        }
    }

    @Test fun `neutral saved boards are isolated between both lanes and previous levels`() {
        for (level in 1..365) {
            val english = Levels.planForLanguage(level, languageTag = "en")
            val russian = Levels.planForLanguage(level, languageTag = "ru")
            for ((en, ru) in english.zip(russian).filter { (en, ru) -> en.puzzleId == ru.puzzleId }) {
                val previous = savedGameKey(en.puzzleId, en.difficulty, SeedHash.level(level, en.puzzleId, en.difficulty))
                val englishKey = savedGameKey(en.puzzleId, en.difficulty, en.seed)
                val russianKey = savedGameKey(ru.puzzleId, ru.difficulty, ru.seed)
                assertNotEquals("$level ${en.puzzleId}", previous, englishKey)
                assertNotEquals("$level ${en.puzzleId}", previous, russianKey)
                assertNotEquals("$level ${en.puzzleId}", englishKey, russianKey)
            }
        }
    }

    @Test fun `regional and unsupported settings use the same stable lane seeds`() {
        for (level in listOf(1, 100, 101, 365, 10_000)) {
            assertEquals(Levels.planForLanguage(level, languageTag = "en"), Levels.planForLanguage(level, languageTag = "en-GB"))
            assertEquals(Levels.planForLanguage(level, languageTag = "en"), Levels.planForLanguage(level, languageTag = "unsupported"))
            assertEquals(Levels.planForLanguage(level, languageTag = "ru"), Levels.planForLanguage(level, languageTag = "RU_by"))
        }
    }
}
