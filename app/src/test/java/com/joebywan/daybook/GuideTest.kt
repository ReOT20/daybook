package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PlacementStep
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.core.TeachStep
import com.joebywan.daybook.core.praisedTechniquesOf
import com.joebywan.daybook.core.techniqueNamesOf
import com.joebywan.daybook.core.tierNotes
import com.joebywan.daybook.puzzles.CodewordLogic
import com.joebywan.daybook.puzzles.CodewordTeacher
import com.joebywan.daybook.puzzles.DwindleTeacher
import com.joebywan.daybook.puzzles.HexTake
import com.joebywan.daybook.puzzles.HexwordsState
import com.joebywan.daybook.puzzles.HexwordsTeacher
import com.joebywan.daybook.puzzles.LexiconMark
import com.joebywan.daybook.puzzles.LexiconTeacher
import com.joebywan.daybook.puzzles.MiniLogic
import com.joebywan.daybook.puzzles.MiniTeacher
import com.joebywan.daybook.puzzles.PuzzleState
import com.joebywan.daybook.puzzles.Sudoku
import com.joebywan.daybook.puzzles.SudokuTeacher
import com.joebywan.daybook.puzzles.WordWheelTeacher
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

        /** `r3c4=17` as square to "17". */
        fun marks(key: String): Map<Int, String> = fields[key].orEmpty().split(' ').filter { it.isNotEmpty() }.associate { part ->
            val n = Regex("\\d+").findAll(part.substringBefore('=')).map { it.value.toInt() - 1 }.toList()
            n[0] * width + n[1] to part.substringAfter('=')
        }
    }

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
        "words" to ::lexiconStep,
        "wordwheel" to ::wordWheelStep,
        "mini" to ::miniStep,
        "codeword" to ::codewordStep,
        "dwindle" to ::dwindleStep,
        "hexwords" to ::hexwordsStep,
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

    /** A teacher's step with its highlights mapped to the board's squares. */
    private fun step(from: TeachStep, focus: Set<Int>, targets: Set<Int>): TeachStep = object : TeachStep by from {
        override val focus = focus
        override val targets = targets
    }

    /** The guesses are the rows with marks, and their marks draw the board's `tiles`; the last row is the one being typed. */
    private fun lexiconStep(b: Board): TeachStep? {
        val tiles = b.marks("tiles")
        val n = b.width
        val guessRows = b.rows.indices.filter { r -> (0 until n).all { r * n + it in tiles } }
        val guesses = guessRows.map { b.rows[it].joinToString("").lowercase() }
        val marks = guessRows.map { r ->
            List(n) { c ->
                when (tiles.getValue(r * n + c)) {
                    "right" -> LexiconMark.CORRECT
                    "near" -> LexiconMark.PRESENT
                    else -> LexiconMark.ABSENT
                }
            }
        }
        assertTrue("${b.technique}: some word draws these marks", LexiconTeacher.candidates(n, guesses, marks).isNotEmpty())
        val taught = LexiconTeacher.teach(n, guesses, marks, "") ?: return null
        val move = taught.move as LexiconTeacher.Move.Pin
        assertEquals("${b.technique}: the letter", move.letter.uppercase(), b.marks("answer")[guesses.size * n + move.slot])
        return step(taught, taught.focus, taught.targets.filter { it < LexiconTeacher.KEY }.toSet())
    }

    /** Maximal lines of two or more squares among [rows], across by row and then down by column. */
    private fun wheelShapes(rows: List<List<String>>): List<WordWheelTeacher.Shape> {
        val h = rows.size
        val w = rows[0].size
        val out = mutableListOf<WordWheelTeacher.Shape>()
        fun runs(lines: Int, length: Int, open: (Int, Int) -> Boolean, add: (Int, Int, Int) -> Unit) {
            for (l in 0 until lines) {
                var i = 0
                while (i < length) {
                    var e = i
                    while (e < length && open(l, e)) e++
                    if (e - i >= 2) add(l, i, e - i)
                    i = maxOf(e, i + 1)
                }
            }
        }
        runs(h, w, { r, c -> rows[r][c] != "_" }) { r, c, n -> out += WordWheelTeacher.Shape(r, c, true, n) }
        runs(w, h, { c, r -> rows[r][c] != "_" }) { c, r, n -> out += WordWheelTeacher.Shape(r, c, false, n) }
        return out
    }

    /** The last row is the wheel, under a row of nothing; a slot with no gap is a word already found. */
    private fun wordWheelStep(b: Board): TeachStep? {
        val grid = b.rows.dropLast(2)
        assertTrue("${b.technique}: a gap above the wheel", b.rows[grid.size].all { it == "_" })
        val wheel = b.rows.last().filter { it != "_" }.joinToString("").lowercase()
        val shown = grid.flatten().map { if (it == "." || it == "_") null else it.lowercase()[0] }
        val shapes = wheelShapes(grid)
        val letters = shapes.map { s -> s.cells(b.width).map { shown[it] } }
        val empty = shapes.indices.filter { null in letters[it] }
        val tried = shapes.indices.filter { it !in empty }.map { letters[it].joinToString("") }
        val taught = WordWheelTeacher.deduce(wheel, b.width, shapes, empty, shown, tried) ?: return null
        val spelled = shapes[taught.slot].cells(b.width).joinToString("") { b.marks("answer").getValue(it) }
        assertEquals("${b.technique}: the word", taught.word.uppercase(), spelled)
        return taught
    }

    private fun miniStep(b: Board): TeachStep? {
        val size = b.width
        val letters = b.rows.flatten().joinToString("") {
            when (it) {
                "_" -> MiniLogic.BLACK.toString()
                "." -> MiniLogic.OPEN.toString()
                else -> it.lowercase()
            }
        }
        val slots = MiniLogic.slots(size, letters)
        val taught = MiniTeacher.deduce(size, letters, List(slots.size) { "" }) ?: return null
        val squares = slots[taught.slot].squares
        assertEquals("${b.technique}: the word", taught.word!!.uppercase(), squares.joinToString("") { b.marks("answer").getValue(it) })
        return object : TeachStep {
            override val technique = taught.technique
            override val nudge = taught.nudge
            override val explanation = taught.explanation
            override val focus = taught.focus
            override val cited = taught.cited
            override val targets = squares.toSet()
        }
    }

    /** Open squares show their number and placed ones their letter. A placed number's own value does not matter, so each letter takes a number left over. */
    private fun codewordStep(b: Board): TeachStep? {
        val tokens = b.rows.flatten()
        val open = tokens.mapNotNull { it.toIntOrNull() }.distinct()
        val placed = tokens.filter { it != "_" && it.toIntOrNull() == null }.distinct()
        val k = open.size + placed.size
        val spare = ((1..k) - open.toSet()).toList()
        val numberOf = placed.withIndex().associate { (i, letter) -> letter to spare[i] }
        val numbers = tokens.map { if (it == "_") 0 else it.toIntOrNull() ?: numberOf.getValue(it) }
        val letters = MutableList(k) { CodewordLogic.EMPTY }
        for ((letter, n) in numberOf) letters[n - 1] = letter[0] - 'A'
        val taught = CodewordTeacher.deduce(b.width, numbers, letters) ?: return null
        val squares = numbers.indices.filter { numbers[it] == taught.number + 1 }.toSet()
        for (v in b.marks("answer").values) assertEquals("${b.technique}: the letter", (taught.letter + 'A'.code).toChar().toString(), v)
        return object : TeachStep {
            override val technique = taught.technique
            override val nudge = taught.nudge
            override val explanation = taught.explanation
            override val focus = taught.focus
            override val cited = taught.cited
            override val targets = squares
        }
    }

    /**
     * What Dwindle's hint reads beyond the tiles: the open dashes' lengths, the words already found, and (as the one way
     * of a word is counted on the full grid) the letters of the tiles gone. All from the position the board was copied from.
     */
    private class DwindleFixture(val lengths: List<Int>, val tried: List<String> = emptyList(), val letters: String? = null)

    private val dwindleFixtures = mapOf(
        "through" to DwindleFixture(listOf(5, 4, 4, 4, 4, 3, 3)),
        "length" to DwindleFixture(
            listOf(7, 5, 5, 4, 4, 4, 4, 4, 3, 3, 3, 3),
            listOf("swift", "sweat", "dry", "pod", "cutlet"),
            "tapalbfsazsuiwwetpaetfyotlifrducubev",
        ),
        "all-tiles" to DwindleFixture(
            listOf(4, 4, 4, 4, 4, 3, 3, 3, 3),
            listOf("swift", "sweat", "dry", "pod", "cutlet", "rebuilt", "blaze", "cliff"),
            "tapalbfsazsuiwwetpaetfyotlifrducubev",
        ),
    )

    /** A tile's token is its letter and its uses left, `S2`; `_` a tile gone. */
    private fun dwindleStep(b: Board): TeachStep? {
        val tokens = b.rows.flatten()
        val fixture = dwindleFixtures.getValue(b.technique)
        val letters = fixture.letters ?: tokens.joinToString("") { it.take(1).lowercase() }
        tokens.forEachIndexed { i, t -> if (t != "_") assertEquals("${b.technique}: tile $i", letters[i], t[0].lowercaseChar()) }
        val counts = IntArray(tokens.size) { tokens[it].drop(1).toIntOrNull() ?: 0 }
        return DwindleTeacher.deduce(b.width, b.rows.size, letters, counts, fixture.lengths, fixture.tried, diagonal = false)
    }

    /** The points still to go, shown in the app's header line and said in each caption. */
    private val hexPointsToGo = mapOf("biggest" to 16, "hold-back" to 13, "rare" to 5)

    /**
     * Hexes sit in a staggered grid: `_` is a gap and `.` a hex already spent, so a hex's number is its place among the
     * other tokens. A spent hex's letter cannot matter, and the points still needed are the whole target of a state whose
     * spent hexes are one word worth nothing.
     */
    private fun hexwordsStep(b: Board): TeachStep? {
        val tokens = b.rows.flatten()
        val cells = tokens.indices.filter { tokens[it] != "_" }
        val letters = cells.joinToString("") { if (tokens[it] == ".") "q" else tokens[it].lowercase() }
        val spent = cells.indices.filter { tokens[cells[it]] == "." }
        val state = HexwordsState(
            rows = b.rows.map { row -> row.count { it != "_" } },
            letters = letters,
            target = hexPointsToGo.getValue(b.technique),
            taken = listOf(HexTake("", spent)),
        )
        val taught = HexwordsTeacher.teach(state) ?: return null
        assertEquals("${b.technique}: the first letter", taught.word[0].uppercase(), b.marks("answer")[cells[taught.path[0]]])
        return step(taught, taught.focus.map { cells[it] }.toSet(), taught.targets.map { cells[it] }.toSet())
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
