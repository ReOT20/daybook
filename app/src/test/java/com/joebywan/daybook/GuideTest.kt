package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PlacementStep
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.core.TeachStep
import com.joebywan.daybook.core.Verdict
import com.joebywan.daybook.core.praisedTechniquesOf
import com.joebywan.daybook.core.techniqueNamesOf
import com.joebywan.daybook.core.tierNotes
import com.joebywan.daybook.puzzles.Block
import com.joebywan.daybook.puzzles.CageLogic
import com.joebywan.daybook.puzzles.CageTeacher
import com.joebywan.daybook.puzzles.InequalityLogic
import com.joebywan.daybook.puzzles.InequalityTeacher
import com.joebywan.daybook.puzzles.NonogramLogic
import com.joebywan.daybook.puzzles.NonogramTeacher
import com.joebywan.daybook.puzzles.PuzzleState
import com.joebywan.daybook.puzzles.RegionsLayout
import com.joebywan.daybook.puzzles.RegionsLogic
import com.joebywan.daybook.puzzles.RegionsTeacher
import com.joebywan.daybook.puzzles.ShikakuTeacher
import com.joebywan.daybook.puzzles.Sign
import com.joebywan.daybook.puzzles.Sudoku
import com.joebywan.daybook.puzzles.SudokuTeacher
import com.joebywan.daybook.puzzles.TallyLogic
import com.joebywan.daybook.puzzles.TallyTeacher
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The player guide (`tools/guide/pages/<puzzle id>.txt`, see `tools/guide/AUTHORING.md`) against the app: only pages
 * that exist are checked. Gradle knows the pages as an input (`app/build.gradle.kts`), so a page-only change re-runs this.
 */
class GuideTest {

    private class Board(val technique: String, val rows: List<List<String>>, val fields: Map<String, String>) {
        val width = rows[0].size

        /** `r1c1` and rectangles `r1c1-r3c9` as `row * width + col`, the index most boards use for a highlight. */
        fun squares(key: String): Set<Int> = fields[key].orEmpty().split(' ').filter { it.isNotEmpty() }.flatMap { part ->
            val n = Regex("\\d+").findAll(part).map { it.value.toInt() - 1 }.toList()
            val (r2, c2) = if (n.size == 4) n[2] to n[3] else n[0] to n[1]
            (n[0]..r2).flatMap { r -> (n[1]..c2).map { c -> r * width + c } }
        }.toSet()

        /** `aab acc` as a number per square, numbered by first square as the puzzles do; `.` is -1. */
        fun letters(key: String): List<Int> {
            val seen = HashMap<Char, Int>()
            return fields[key].orEmpty().filter { it != ' ' }.map { if (it == '.') -1 else seen.getOrPut(it) { seen.size } }
        }

        /** `r3c4=17` as square to "17". */
        fun marks(key: String): Map<Int, String> = fields[key].orEmpty().split(' ').filter { it.isNotEmpty() }.associate { part ->
            val n = Regex("\\d+").findAll(part.substringBefore('=')).map { it.value.toInt() - 1 }.toList()
            n[0] * width + n[1] to part.substringAfter('=')
        }
    }

    /** A teacher's step with its highlights mapped to the board's squares: clues in a margin, signs it draws differently. */
    private class Mapped(step: TeachStep, override val focus: Set<Int>, override val targets: Set<Int> = step.targets) : TeachStep by step

    private class Page(val id: String, val text: String) {
        val lines = text.lines()
        val headings = lines.filter { it.startsWith("## ") }.map { it.drop(3).trim() }
        val techniques = lines.filter { it.startsWith("### ") }.map { it.drop(4).split('|').map(String::trim) }

        fun section(name: String): List<String> =
            lines.dropWhile { it.trim() != "## $name" }.drop(1).takeWhile { !it.startsWith("## ") }

        val boards: List<Board> = buildList {
            var technique = ""
            var block: MutableList<String>? = null
            for (line in lines) {
                if (line.startsWith("### ")) technique = line.drop(4).substringBefore('|').trim()
                when {
                    line.trim() == "~~~board" -> block = mutableListOf()
                    line.trim() == "~~~" -> {
                        val (fields, rows) = block!!.partition { ':' in it }
                        add(
                            Board(
                                technique,
                                rows.filter { it.isNotBlank() }.map { it.trim().split(Regex("\\s+")) },
                                fields.associate { it.substringBefore(':').trim() to it.substringAfter(':').trim() },
                            )
                        )
                        block = null
                    }
                    else -> block?.add(line)
                }
            }
        }
    }

    private val pages = File("../tools/guide/pages").listFiles { f -> f.extension == "txt" }.orEmpty()
        .map { Page(it.nameWithoutExtension, it.readText()) }

    /**
     * What a puzzle's teacher says on the position a board draws. Every page's puzzle needs a line here: it is what
     * proves the diagrams are the technique they sit under. Add puzzle-specific checks in the function, as Sudoku's.
     */
    private val teachers: Map<String, (Board) -> TeachStep?> = mapOf(
        "sudoku" to ::sudokuStep,
        "inequality" to ::inequalityStep,
        "regions" to ::regionsStep,
        "cages" to ::cagesStep,
        "tally" to ::tallyStep,
        "nonogram" to ::nonogramStep,
        "shikaku" to ::shikakuStep,
    )

    private fun sudokuStep(b: Board): PlacementStep? {
        val cells = b.rows.flatten().map { it.toIntOrNull() ?: 0 }
        assertEquals("${b.technique}: a position with one answer", 1, Sudoku.countSolutions(cells))
        val step = SudokuTeacher.deduce(cells) ?: return null
        assertEquals("${b.technique}: the digit placed", step.digit.toString(), b.marks("answer")[step.cell])
        // Pencil marks on a board are digits that square can really hold, worked out here from the rules alone.
        for ((cell, digits) in b.marks("notes") + b.marks("struck")) for (d in digits) {
            val seen = (0 until 81).filter { it != cell && (it / 9 == cell / 9 || it % 9 == cell % 9 || (it / 27 == cell / 27 && it % 9 / 3 == cell % 9 / 3)) }
            assertTrue("${b.technique}: $d is no pencil mark for square $cell", seen.none { cells[it] == d.digitToInt() })
        }
        return step
    }

    private fun inequalityStep(b: Board): TeachStep? {
        val n = b.width
        val cells = b.rows.flatten().map { it.toIntOrNull() ?: 0 }
        val signs = b.fields["edges"].orEmpty().split(' ').filter { it.isNotEmpty() }.map { part ->
            val (first, second) = Regex("r(\\d+)c(\\d+)").findAll(part).map { (it.groupValues[1].toInt() - 1) * n + it.groupValues[2].toInt() - 1 }.toList()
            if (part.endsWith("<")) Sign(first, second) else Sign(second, first)
        }
        assertEquals("${b.technique}: a position with one answer", 1, InequalityLogic.countSolutions(n, cells, signs))
        val step = InequalityTeacher.deduce(n, cells, signs) ?: return null
        assertEquals("${b.technique}: the digit placed", step.digit.toString(), b.marks("answer")[step.cell])
        return Mapped(step, step.focus.filter { it < n * n }.toSet())
    }

    private fun regionsStep(b: Board): TeachStep? {
        val layout = RegionsLayout(b.width, b.letters("regions"))
        val cells = b.rows.flatten().map { it.toIntOrNull() ?: 0 }
        assertEquals("${b.technique}: a position with one answer", 1, RegionsLogic.countSolutions(layout, cells))
        val step = RegionsTeacher.deduce(layout, cells) ?: return null
        assertEquals("${b.technique}: the digit placed", step.digit.toString(), b.marks("answer")[step.cell])
        return step
    }

    private fun cagesStep(b: Board): TeachStep? {
        val cageOf = b.letters("cages")
        val corners = b.marks("corners")
        val sums = List(cageOf.max() + 1) { k -> corners.getValue(cageOf.indexOf(k)).toInt() }
        val board = CageLogic.Board(cageOf, sums)
        val cells = b.rows.flatten().map { it.toIntOrNull() ?: 0 }
        assertTrue("${b.technique}: a position with one answer", CageLogic.solutions(board, cells) is Verdict.ExactlyOne)
        val step = CageTeacher.deduce(board, cells) ?: return null
        assertEquals("${b.technique}: the digit placed", step.digit.toString(), b.marks("answer")[step.cell])
        return step
    }

    /** A margin of targets right and below. A ring is a circled number, `struck` a removed one; both must agree with the one answer. */
    private fun tallyStep(b: Board): TeachStep? {
        val n = b.width - 1
        fun board(i: Int) = i / n * b.width + i % n
        val digits = IntArray(n * n) { b.rows[it / n][it % n].toInt() }
        val targets = List(n) { b.rows[it][n].toInt() } + List(n) { b.rows[n][it].toInt() }
        val kept = b.squares("cite")
        val gone = b.marks("struck").keys
        val marks = String(CharArray(n * n) {
            when (board(it)) {
                in kept -> TallyLogic.KEPT
                in gone -> TallyLogic.REMOVED
                else -> TallyLogic.UNMARKED
            }
        })
        val answer = TallyLogic.countSolutions(n, digits, targets)
        assertTrue("${b.technique}: a position with one answer", answer is Verdict.ExactlyOne)
        val keep = (answer as Verdict.ExactlyOne).value
        for (i in 0 until n * n) {
            if (board(i) in kept) assertEquals("${b.technique}: a ring the answer removes", '1', keep[i])
            if (board(i) in gone) assertEquals("${b.technique}: a strike the answer keeps", '0', keep[i])
        }
        val step = TallyTeacher.deduce(n, digits, targets, marks) ?: return null
        for (i in step.targets) assertEquals("${b.technique}: the number marked", digits[i].toString(), b.marks("answer")[board(i)])
        fun mapped(i: Int) = when {
            i < n * n -> board(i)
            i < n * n + n -> (i - n * n) * b.width + n
            else -> n * b.width + i - n * n - n
        }
        return Mapped(step, step.focus.map(::mapped).toSet(), step.targets.map(::mapped).toSet())
    }

    /** Clues in margins above and to the left: `.` or `_` where a line has fewer. An answer of `.` is a fill, `x` a cross. */
    private fun nonogramStep(b: Board): TeachStep? {
        val height = b.rows.size
        val n = listOf(5, 10, 15).last { it <= minOf(b.width, height) }
        val left = b.width - n
        val top = height - n
        fun clue(tokens: List<String>) = tokens.mapNotNull { it.toIntOrNull() }
        val rows = List(n) { clue(b.rows[top + it].take(left)) }
        val cols = List(n) { c -> clue(List(top) { b.rows[it][left + c] }) }
        fun board(i: Int) = (i / n + top) * b.width + i % n + left
        val filled = b.squares("filled")
        val start = String(CharArray(n * n) {
            when {
                board(it) in filled -> NonogramLogic.FILLED
                b.rows[it / n + top][it % n + left] == "x" -> NonogramLogic.CROSSED
                else -> NonogramLogic.UNMARKED
            }
        })
        val step = NonogramTeacher.deduce(n, n, rows, cols, start) ?: return null
        val answer = b.marks("answer")
        for (i in step.fills) assertEquals("${b.technique}: a fill", ".", answer[board(i)])
        for (i in step.crosses) assertEquals("${b.technique}: a cross", "x", answer[board(i)])
        // Lines alone finish it from here, and the picture has the clues it shows: so it is the board's one answer.
        val cells = start.toCharArray()
        var next: NonogramTeacher.Step? = step
        while (next != null) {
            next.fills.forEach { cells[it] = NonogramLogic.FILLED }
            next.crosses.forEach { cells[it] = NonogramLogic.CROSSED }
            next = NonogramTeacher.deduce(n, n, rows, cols, String(cells))
        }
        fun runs(line: List<Char>) = line.joinToString("").split(Regex("[^#]+")).filter { it.isNotEmpty() }.map { it.length }
        for (i in 0 until n) {
            assertEquals("${b.technique}: row ${i + 1} finished", rows[i], runs(List(n) { cells[i * n + it] }))
            assertEquals("${b.technique}: column ${i + 1} finished", cols[i], runs(List(n) { cells[it * n + i] }))
        }
        fun mapped(i: Int) = when {
            i < n * n -> setOf(board(i))
            i < n * n + n -> List(left) { ((i - n * n) + top) * b.width + it }.toSet()
            else -> List(top) { it * b.width + left + i - n * n - n }.toSet()
        }
        return Mapped(step, step.focus.flatMap(::mapped).toSet(), step.targets.map(::board).toSet())
    }

    /** `regions` are the rectangles drawn, `.` the squares still open. The answer is proved here by trying every rectangle. */
    private fun shikakuStep(b: Board): TeachStep? {
        val w = b.width
        val h = b.rows.size
        val clues = b.rows.flatten().map { it.toIntOrNull() }
        val region = b.letters("regions").ifEmpty { List(w * h) { -1 } }
        val blocks = (0..(region.maxOrNull() ?: -1)).map { k ->
            val at = region.indices.filter { region[it] == k }
            val block = Block(at.minOf { it / w }, at.minOf { it % w }, at.maxOf { it / w }, at.maxOf { it % w })
            assertEquals("${b.technique}: a drawn region is a rectangle", block.area, at.size)
            assertEquals("${b.technique}: a drawn rectangle's number", block.area, at.firstNotNullOf { clues[it] })
            block
        }
        val taken = BooleanArray(w * h) { region[it] >= 0 }
        fun tilings(): Int {
            val first = taken.indexOfFirst { !it }
            if (first < 0) return 1
            var found = 0
            val r0 = first / w
            val c0 = first % w
            // The first open square in reading order is the top-left of whatever covers it.
            for (r1 in r0 until h) for (c1 in c0 until w) {
                val inside = (r0..r1).flatMap { r -> (c0..c1).map { r * w + it } }
                if (inside.any { taken[it] }) continue
                val number = inside.mapNotNull { clues[it] }
                if (number.size != 1 || number[0] != inside.size) continue
                inside.forEach { taken[it] = true }
                found += tilings()
                inside.forEach { taken[it] = false }
            }
            return found
        }
        assertEquals("${b.technique}: a position with one answer", 1, tilings())
        return ShikakuTeacher.deduce(w, h, clues, blocks)
    }

    @Test
    fun `there is a page to check, and every page is a puzzle`() {
        assertTrue(pages.isNotEmpty())
        val ids = PuzzleRegistry.all.map { it.id }
        for (p in pages) assertTrue("${p.id} is not a puzzle id", p.id in ids)
    }

    @Test
    fun `a page has the sections in order and the puzzle's own name and tagline`() {
        for (p in pages) {
            val puzzle = PuzzleRegistry.all.first { it.id == p.id }
            val techniques = if (praisedTechniquesOf(p.id).isEmpty() && p.techniques.isEmpty()) emptyList() else listOf("Techniques")
            assertEquals(p.id, listOf("How to play", "Rules", "What changes per tier") + techniques, p.headings)
            assertEquals(p.id, "# ${puzzle.displayName}", p.lines.first { it.startsWith("# ") }.trim())
            assertEquals(p.id, "> ${puzzle.tagline}", p.lines.first { it.startsWith("> ") }.trim())
        }
    }

    @Test
    fun `a page says what TierNotes says`() {
        for (p in pages) {
            val items = p.section("What changes per tier").filter { it.startsWith("- ") }
            assertEquals("${p.id}: one line a tier", 3, items.size)
            listOf("Standard", "Hard", "Expert").zip(tierNotes(p.id)).forEachIndexed { i, (tier, note) ->
                assertTrue("${p.id}: \"${items[i]}\" should start \"- $tier: $note\"", items[i].startsWith("- $tier: $note"))
            }
        }
    }

    @Test
    fun `a page has the teacher's techniques, no others, in order, under the app's names`() {
        for (p in pages) {
            val names = techniqueNamesOf(p.id)
            val ids = p.techniques.map { it[0] }
            // A step that only points at a word or a guess (NOT_PRAISED) is not reasoning: a page may leave it out.
            assertEquals("${p.id}: technique ids", names.keys.filter { it in ids || it in praisedTechniquesOf(p.id) }, ids)
            for ((id, shown) in p.techniques) {
                assertEquals("${p.id}#$id", names.getValue(id).replaceFirstChar { it.uppercase() }, shown)
            }
        }
    }

    @Test
    fun `every technique has a board, and the teacher gives that technique on it`() {
        for (p in pages) {
            val drawn = p.boards.map { it.technique }.toSet()
            assertTrue("${p.id}: a technique without a board", drawn.containsAll(praisedTechniquesOf(p.id)))
            assertTrue("${p.id}: a board outside a technique", p.techniques.map { it[0] }.containsAll(drawn))
            val teacher = teachers[p.id] ?: error("${p.id}: add it to GuideTest.teachers")
            for (b in p.boards) {
                val step = teacher(b) ?: error("${p.id}#${b.technique}: the teacher has no step here")
                assertEquals("${p.id}: the teacher's step on this board", b.technique, step.technique)
                assertEquals("${p.id}#${b.technique}: focus", step.focus, b.squares("focus"))
                assertEquals("${p.id}#${b.technique}: the move", step.targets, b.marks("answer").keys)
            }
        }
    }

    @Test
    fun `no page names a banned genre or trademark`() {
        // Capitalised only: a domino's pips, a lamp that lights up and a target's countdown are plain words.
        val exact = listOf("Pips", "Light Up", "Countdown", "Kami", "Mastermind")
        val anyCase = listOf(
            "minesweeper", "masyu", "killer sudoku", "rullo", "akari", "wordle", "kakuro", "number sums", "hashiwokakero",
            "hashi", "takuzu", "binairo", "einstein", "zebra puzzle", "fill-a-pix", "nikoli", "new york times", "nyt",
            "wordscapes", "water sort",
        )
        for (p in pages) {
            for (word in exact) assertTrue("${p.id} says $word", !Regex("\\b$word\\b").containsMatchIn(p.text))
            for (word in anyCase) {
                assertTrue("${p.id} says $word", !Regex("\\b$word\\b", RegexOption.IGNORE_CASE).containsMatchIn(p.text))
            }
        }
    }

    /**
     * For authors: `DAYBOOK_GUIDE_FIND=<puzzle id>` walks generated boards by hints and writes the first positions at
     * each technique, with the step's squares and words, to `app/build/guide-positions-<id>.txt`. Not a check.
     */
    @Test
    fun `find positions for a page's boards`() {
        val id = System.getenv("DAYBOOK_GUIDE_FIND")
        assumeTrue("set DAYBOOK_GUIDE_FIND=<puzzle id>", id != null)
        val puzzle = PuzzleRegistry.all.first { it.id == id }
        val found = HashMap<String, Int>()
        val out = StringBuilder()
        for (seed in 1L..400L) for (tier in Difficulty.entries) {
            var state = puzzle.generate(seed, tier)
            while (true) {
                val step = puzzle.teach(state) ?: break
                if (step.mistake || step.fallback) break
                if (found.merge(step.technique, 1, Int::plus)!! <= 40) {
                    out.append("== ${step.technique}  seed $seed $tier\n")
                    out.append("focus ${step.focus.sorted()}\ncited ${step.cited.sorted()}\ntargets ${step.targets.sorted()}\n")
                    out.append("${step.nudge}\n${step.explanation}\n")
                    out.append(Json.encodeToString(PuzzleState.serializer(), state)).append('\n')
                }
                state = step.apply(state)
            }
        }
        File("build/guide-positions-$id.txt").writeText(out.toString())
    }
}
