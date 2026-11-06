package com.joebywan.daybook

import com.joebywan.daybook.core.Verdict
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.words.RussianWords
import com.joebywan.daybook.core.words.WordContent
import com.joebywan.daybook.puzzles.CodewordLogic
import com.joebywan.daybook.puzzles.CodewordState
import com.joebywan.daybook.puzzles.CodewordTeacher
import com.joebywan.daybook.puzzles.CipherLogic
import com.joebywan.daybook.puzzles.CipherTeacher
import com.joebywan.daybook.puzzles.MiniLogic
import com.joebywan.daybook.puzzles.MiniState
import com.joebywan.daybook.puzzles.MiniTeacher
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RussianCrosswordRulesTest {
    @Test fun `frozen Russian tables contain a year of distinct sorted native boards per tier`() {
        val sumsSeen = mutableSetOf<String>()
        for (tier in Difficulty.entries) {
            for ((family, entries) in listOf("mini" to MiniLogic.entries(tier, "ru"),
                "codeword" to CodewordLogic.entries(tier, "ru"), "cipher" to CipherLogic.sums(tier, "ru"))) {
                assertTrue("$family $tier: ${entries.size}", entries.size >= 365)
                assertEquals("$family $tier sorted", entries.sorted(), entries)
                assertEquals("$family $tier distinct", entries.size, entries.distinct().size)
                for (entry in entries) {
                    if (family == "cipher") {
                        assertTrue("sum appears in only one tier", sumsSeen.add(entry))
                        val sum = CipherLogic.parse(entry, "ru")
                        assertTrue(entry, sum.wellFormed)
                        assertTrue(entry, sum.addends.all { it in RussianWords.answers(4) })
                        assertTrue(entry, sum.total in RussianWords.answers(5))
                    } else {
                        val grid = entry.substringBefore(':')
                        val size = if (family == "mini") MiniLogic.specFor(tier).size else CodewordLogic.specFor(tier).size
                        assertEquals(entry, size * size, grid.length)
                        val runs = MiniOracle.runs(size, grid)
                        assertEquals("$entry repeats a word", runs.size, runs.distinct().size)
                        assertTrue(entry, runs.all { it.length in 3..7 && it in RussianWords.grid(it.length) })
                        for (i in grid.indices) assertEquals("$entry blocks are symmetric", grid[i] == '#', grid[grid.lastIndex - i] == '#')
                        val seen = mutableSetOf(grid.indexOfFirst { it != '#' })
                        val todo = ArrayDeque(seen.toList())
                        while (todo.isNotEmpty()) {
                            val square = todo.removeLast()
                            val neighbours = listOf(square - size, square + size,
                                if (square % size > 0) square - 1 else -1,
                                if (square % size < size - 1) square + 1 else -1)
                            for (next in neighbours) if (next in grid.indices && grid[next] != '#' && seen.add(next)) todo.add(next)
                        }
                        assertEquals("$entry connected", grid.count { it != '#' }, seen.size)
                    }
                }
            }
        }
    }

    @Test fun `Russian crossword proofs agree with independent letter by letter solvers`() {
        val rng = Rng(20261106)
        for (tier in Difficulty.entries) repeat(6) {
            val entry = rng.pick(MiniLogic.entries(tier, "ru"))
            val board = requireNotNull(MiniLogic.board(entry, tier, "ru"))
            val read = CharArray(entry.length) { if (entry[it] == '#') '#' else ' ' }
            val walk = requireNotNull(MiniTeacher.walk(board.size, entry, contentLanguage = "ru"))
            for (hit in walk) if (hit.technique == MiniTeacher.CLUE) read[hit.square] = hit.letter
            assertEquals(entry, 1, MiniOracle.count(board.size, read.concatToString(), contentLanguage = "ru"))
            assertTrue(MiniLogic.specFor(tier).accepts(read.count { it in RussianWords.ALPHABET }, entry.count { it != '#' }))
            assertTrue(board.copy(letters = entry, answer = "?".repeat(entry.length)).solved)

            val coded = rng.pick(CodewordLogic.entries(tier, "ru"))
            val codeword = requireNotNull(CodewordLogic.board(CodewordLogic.parse(coded, "ru"), rng))
            assertEquals(coded, 1, CodewordOracle.count(codeword.size, codeword.numbers, codeword.letters, contentLanguage = "ru"))
            assertTrue(codeword.copy(letters = codeword.solution, solution = emptyList()).solved)
        }
    }

    @Test fun `every frozen Russian sum has one proved answer and its measured tier`() {
        for (tier in Difficulty.entries) for (entry in CipherLogic.sums(tier, "ru")) {
            val sum = CipherLogic.parse(entry, "ru")
            assertTrue(entry, CipherLogic.prove(sum) is Verdict.ExactlyOne)
            assertTrue(entry, CipherTeacher.solvesByLogic(sum))
            assertEquals(entry, tier, CipherSumsBuildTest.tierOf(sum))
        }
        val rng = Rng(20261106)
        for (tier in Difficulty.entries) repeat(6) {
            val entry = rng.pick(CipherLogic.sums(tier, "ru"))
            val sum = CipherLogic.parse(entry, "ru")
            assertEquals(entry, listOf((CipherLogic.prove(sum) as Verdict.ExactlyOne).value), CipherOracle.answers(sum.words))
        }
    }

    @Test fun `the last Russian alphabet bit survives codeword candidates and proof`() {
        val alphabet = WordContent.alphabet("ru")
        val numbers = listOf(1, 2, 3, 0, 0, 0, 0, 0, 0)
        val solution = "яма".map { alphabet.indexOf(it) }
        val known = listOf(CodewordLogic.EMPTY, solution[1], solution[2])
        assertEquals(31, solution[0])
        val step = CodewordTeacher.deduce(3, numbers, known, "ru")!!
        assertEquals(solution[0], step.letter)
        val proof = CodewordLogic.solve(3, numbers, known, "ru") as Verdict.ExactlyOne
        assertEquals(solution, proof.value)
        val board = CodewordState(3, numbers, solution, listOf(2, 3), known, contentLanguage = "ru")
        assertFalse(board.solved)
        assertTrue(board.withLetter(1, step.letter).solved)
        assertEquals(board, Json.decodeFromString<CodewordState>(Json.encodeToString(board)))
    }

    @Test fun `Russian crossword clues letters and saves use explicit content language`() {
        val clue = RussianWords.clue("яма")
        val answer = "яма######"
        val letters = " ма######"
        val step = MiniTeacher.deduce(3, letters, listOf(clue), "ru")!!
        assertEquals("яма", step.word)
        val board = MiniState(3, answer, listOf(clue), letters, contentLanguage = "ru")
        assertFalse(board.solved)
        assertTrue(board.withLetter(0, 'я').solved)
        assertFalse(MiniLogic.isSolved(3, answer, listOf(clue)))
        assertEquals(board, Json.decodeFromString<MiniState>(Json.encodeToString(board)))
    }
}
