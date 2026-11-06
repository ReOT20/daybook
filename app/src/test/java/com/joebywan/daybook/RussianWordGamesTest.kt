package com.joebywan.daybook

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.core.SeedHash
import com.joebywan.daybook.core.words.RussianWords
import com.joebywan.daybook.puzzles.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import java.io.File

class RussianWordGamesTest {
    /** Opt-in annual measurement: keep Russian fallback rates separate from the English corpus. */
    @Test fun measureRussianHintWalksWhenAsked() {
        val days = System.getenv("DAYBOOK_RUSSIAN_TEACHING_DAYS")?.toIntOrNull()
        assumeTrue("set DAYBOOK_RUSSIAN_TEACHING_DAYS to measure hint walks", days != null)
        require(days!! > 0)
        for (puzzle in PuzzleRegistry.all.filter { it.id.endsWith("-ru") }) for (tier in Difficulty.entries) {
            var steps = 0
            var fallbacks = 0
            var fallbackBoards = 0
            var lost = 0
            val techniques = sortedMapOf<String, Int>()
            for (day in 0 until days) {
                val seed = SeedHash.daily(SeedHash.RUSSIAN_LEXICON_CYCLE_FROM + day, puzzle.id, tier)
                var state = puzzle.generate(seed, tier)
                var usedFallback = false
                var boardSteps = 0
                while (!state.solved && !state.failed) {
                    check(boardSteps++ < 500) { "${puzzle.id} $tier day $day did not finish" }
                    if (state is LexiconState && state.current.length == state.length) {
                        assertNull(LexiconRules.problem(state))
                        state = state.submit()
                        continue
                    }
                    val step = requireNotNull(puzzle.teach(state)) { "${puzzle.id} $tier day $day has no hint" }
                    assertFalse("${puzzle.id} $tier day $day: clean hint walk called a mistake", step.mistake)
                    val next = step.apply(state)
                    assertNotEquals("${puzzle.id} $tier day $day: hint made no progress", state, next)
                    steps++
                    techniques[step.technique] = (techniques[step.technique] ?: 0) + 1
                    if (step.fallback) { fallbacks++; usedFallback = true }
                    state = next
                }
                if (usedFallback) fallbackBoards++
                if (!state.solved) {
                    assertTrue("${puzzle.id} $tier day $day lost", state is LexiconState && state.failed)
                    lost++
                }
            }
            println("RU_HINTS ${puzzle.id} $tier boards=$days steps=$steps fallbackSteps=$fallbacks " +
                "fallbackBoards=$fallbackBoards losses=$lost techniques=$techniques")
        }
    }

    @Test fun languageIsSavedExplicitlyAndOldSavesDefaultToEnglish() {
        val states = listOf(RussianLexicon.tutorialBoard(), RussianWordWheel.tutorialBoard(), RussianDwindle.tutorialBoard(), RussianHexwords.tutorialBoard())
        for (state in states) {
            val encoded = Json.encodeToString(PuzzleState.serializer(), state)
            assertTrue(encoded, encoded.contains("\"contentLanguage\":\"ru\""))
            assertEquals(state, Json.decodeFromString(PuzzleState.serializer(), encoded))
        }
        val old = Json.decodeFromString(LexiconState.serializer(), """{"length":4,"maxGuesses":7,"answer":"coat"}""")
        assertEquals("en", old.contentLanguage)
        assertFalse(Json.encodeToString(LexiconState.serializer(), old).contains("contentLanguage"))
        assertNull(lexiconKeyAction(Key.A, "ru"))
        assertNull(wordWheelKeyAction(Key.A, "ru"))
        assertNull(hexKeyAction(Key.A, "ru"))
        assertEquals(LexiconKeyAction.Enter, lexiconKeyAction(Key.Enter, "ru"))
        assertEquals("елке", RussianLexicon.tutorialBoard().copy(current = "елк").withLetter('Ё').current)
    }

    @Test fun RussianMarksRespectDuplicateLettersAndLatinPathIsUnchanged() {
        assertEquals(listOf(1, 2, 1, 0, 0), LexiconRules.mark("лампа", "масло"))
        assertEquals(listOf(1, 0, 0, 0, 2), LexiconRules.mark("eerie", "these"))
        val sameDay = SeedHash.RUSSIAN_LEXICON_CYCLE_FROM
        val standard = RussianLexicon.generate(SeedHash.daily(sameDay, RussianLexicon.id, Difficulty.STANDARD), Difficulty.STANDARD)
        val hard = RussianLexicon.generate(SeedHash.daily(sameDay, RussianLexicon.id, Difficulty.HARD), Difficulty.HARD)
        assertNotEquals(standard.answer, hard.answer)
        for (tier in Difficulty.entries) {
            val start = SeedHash.RUSSIAN_LEXICON_CYCLE_FROM
            val answers = (0 until 365).map { day ->
                val seed = SeedHash.daily(start + day, RussianLexicon.id, tier)
                val state = RussianLexicon.generate(seed, tier)
                assertEquals("ru", state.contentLanguage)
                assertTrue(RussianWords.isWord(state.answer))
                assertTrue(LexiconTeacher.openers(state.length, "ru")[state.opener].all { it in RussianWords.ALPHABET })
                state.answer
            }
            assertEquals(365, answers.toSet().size)
        }
    }

    @Test fun RussianTutorialsArePlayableAndIndependentlyProved() {
        val wheel = RussianWordWheel.tutorialBoard()
        assertEquals(emptyList<String>(), WordWheelOracle.problems(wheel))
        assertEquals(WheelEntry.Bonus, WordWheelLogic.judge(wheel, "сало"))
        assertTrue(wheel.slots.fold(wheel) { state, slot -> state.enter(slot.word) }.solved)
        assertTrue(WordWheelTeacher.walk(wheel).maxChoice <= WordWheelLogic.MAX_CHOICE)
        val dwindle = RussianDwindle.tutorialBoard()
        assertEquals(emptyList<String>(), DwindleOracle.problems(dwindle))
        assertEquals(DwindleEntry.Bonus, DwindleLogic.judge(dwindle, "пила"))
        assertTrue(dwindle.goals.fold(dwindle) { state, goal -> state.enter(goal.word) }.solved)
        val hex = RussianHexwords.tutorialBoard()
        val oracle = HexwordsOracle(hex.rows, hex.letters, false, "ru")
        val planned = listOf("кот" to listOf(0, 1, 2), "лиса" to listOf(3, 4, 5, 6), "дуб" to listOf(7, 8, 9))
        assertEquals(11, HexwordsOracle.score(oracle, hex.letters, planned))
        assertTrue(planned.fold(hex) { state, pair -> state.take(pair.first, pair.second) }.solved)
        assertTrue(oracle.best(hex.free) >= hex.target)
    }

    @Test fun generatorsProveRussianBoardsOnEveryTier() {
        val start = SeedHash.RUSSIAN_LEXICON_CYCLE_FROM
        for (tier in Difficulty.entries) {
            for (day in 0 until 12) {
                val wheel = RussianWordWheel.generateVerified(SeedHash.daily(start + day, RussianWordWheel.id, tier), tier)
                    ?: error("Russian wheel failed $tier day $day")
                assertEquals("ru", wheel.contentLanguage)
                assertEquals(emptyList<String>(), WordWheelOracle.problems(wheel))
                assertTrue(WordWheelTeacher.walk(wheel).maxChoice <= WordWheelLogic.MAX_CHOICE)
                val dwindle = RussianDwindle.generateVerified(SeedHash.daily(start + day, RussianDwindle.id, tier), tier)
                    ?: error("Russian Dwindle failed $tier day $day")
                assertEquals("ru", dwindle.contentLanguage)
                assertTrue(DwindleLogic.proved(dwindle))
                if (day == 0) assertEquals(emptyList<String>(), DwindleOracle.problems(dwindle))
                val hex = RussianHexwords.generateVerified(SeedHash.daily(start + day, RussianHexwords.id, tier), tier)
                    ?: error("Russian Hexwords failed $tier day $day")
                assertEquals("ru", hex.contentLanguage)
                val roads = HexLogic.roads(hex.letters, HexLogic.neighbours(hex.rows), hex.free, HexLogic.gridWords("ru"))
                val proof = HexSearch(roads, hex.letters.length, HexLogic.BUDGET).reach(hex.free, hex.target)
                assertTrue(proof is HexReach.Yes)
                if (day == 0) {
                    val oracle = HexwordsOracle(hex.rows, hex.letters, false, "ru")
                    val score = HexwordsOracle.score(oracle, hex.letters, (proof as HexReach.Yes).road.map { it.word to it.path.toList() })
                    assertTrue(score >= hex.target)
                }
            }
        }
    }
    /** Real teacher positions for native guide diagrams; opt in, never part of the normal suite. */
    @Test fun findRussianGuidePositions() {
        assumeTrue(System.getenv("DAYBOOK_RU_GUIDE_FIND") == "1")
        for (puzzle in listOf(RussianLexicon, RussianWordWheel, RussianDwindle, RussianHexwords)) {
            val counts = mutableMapOf<String, Int>()
            val out = StringBuilder()
            for (seed in 1L..40L) for (tier in Difficulty.entries) {
                var state = puzzle.generate(seed, tier)
                for (turn in 0 until 200) {
                    if (state is LexiconState && state.current.length == state.length && LexiconRules.problem(state) == null) {
                        state = state.submit()
                        continue
                    }
                    val deduction = puzzle.teach(state) ?: break
                    if (deduction.mistake) break
                    val count = (counts[deduction.technique] ?: 0) + 1
                    counts[deduction.technique] = count
                    if (count <= 8) {
                        out.append("== ${deduction.technique} seed $seed $tier\n")
                        out.append("focus ${deduction.focus.sorted()}\ncited ${deduction.cited.sorted()}\ntargets ${deduction.targets.sorted()}\n")
                        out.append("${deduction.nudge}\n${deduction.explanation}\n")
                        out.append(Json.encodeToString(PuzzleState.serializer(), state)).append('\n')
                    }
                    state = deduction.apply(state)
                }
            }
            File("build/guide-positions-${puzzle.id}.txt").writeText(out.toString())
        }
    }

}
