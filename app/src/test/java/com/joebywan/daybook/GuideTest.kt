package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PlacementStep
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.core.TeachStep
import com.joebywan.daybook.core.Verdict
import com.joebywan.daybook.core.praisedTechniquesOf
import com.joebywan.daybook.core.techniqueNamesOf
import com.joebywan.daybook.core.tierNotes
import com.joebywan.daybook.puzzles.Atom
import com.joebywan.daybook.puzzles.AtomsTeacher
import com.joebywan.daybook.puzzles.Domino
import com.joebywan.daybook.puzzles.DominoLogic
import com.joebywan.daybook.puzzles.DominoTeacher
import com.joebywan.daybook.puzzles.MosaicTeacher
import com.joebywan.daybook.puzzles.Pair2
import com.joebywan.daybook.puzzles.PearlsTeacher
import com.joebywan.daybook.puzzles.PipesTeacher
import com.joebywan.daybook.puzzles.Placement
import com.joebywan.daybook.puzzles.PuzzleState
import com.joebywan.daybook.puzzles.RegionRule
import com.joebywan.daybook.puzzles.RuleKind
import com.joebywan.daybook.puzzles.Sudoku
import com.joebywan.daybook.puzzles.SudokuTeacher
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
        "pipes" to ::pipesStep,
        "atoms" to ::atomsStep,
        "pearls" to ::pearlsStep,
        "mosaic" to ::mosaicStep,
        "dominoes" to ::dominoesStep,
        // No techniques and so no boards (hints are off): these two lines are never asked.
        "snap" to { _ -> null },
        "pointers" to { _ -> null },
    )

    /** A step's squares as a board draws them, for a teacher whose highlights are not all squares (or whose move is a line). */
    private class Drawn(override val technique: String, override val focus: Set<Int>, override val targets: Set<Int> = emptySet()) : TeachStep {
        override val nudge = ""
        override val explanation = ""
        override val cited = emptySet<Int>()
    }

    /** `lines` or `moves` as (lower square, the mark between, higher square). */
    private fun Board.drawn(key: String): List<Triple<Int, Char, Int>> = fields[key].orEmpty().split(' ').filter { it.isNotEmpty() }.map { part ->
        val n = Regex("\\d+").findAll(part).map { it.value.toInt() - 1 }.toList()
        val a = n[0] * width + n[1]
        val b = n[2] * width + n[3]
        Triple(minOf(a, b), Regex("[-=x]").find(part)!!.value[0], maxOf(a, b))
    }

    private fun pipesStep(b: Board): TeachStep? {
        val dirs = "urdl"
        fun mask(token: String) = token.sumOf { 1 shl dirs.indexOf(it) as Int }
        val cells = b.rows.flatten().map(::mask)
        assertTrue("${b.technique}: a board the reasoning settles, so one answer", PipesTeacher.settles(b.width, b.rows.size, cells))
        val step = PipesTeacher.deduce(b.width, b.rows.size, cells) ?: return null
        assertEquals("${b.technique}: the turn", mask(b.marks("answer").getValue(step.cell)), step.mask)
        return step
    }

    private fun atomsStep(b: Board): TeachStep? {
        val atoms = b.rows.flatMapIndexed { r, row ->
            row.mapIndexedNotNull { c, t -> t.toIntOrNull()?.let { Atom(r, c, it) } }
        }
        val at = { a: Atom -> a.row * b.width + a.col }
        // Who sees whom along a clear line, worked out here from the rows.
        val pairs = buildList {
            for (i in atoms.indices) for (j in i + 1 until atoms.size) {
                val (x, y) = atoms[i] to atoms[j]
                val across = x.row == y.row && atoms.none { it.row == x.row && it.col in x.col + 1 until y.col }
                val down = x.col == y.col && atoms.none { it.col == x.col && it.row in x.row + 1 until y.row }
                if (across || down) add(Pair2(i, j, across))
            }
        }
        fun pairOf(d: Triple<Int, Char, Int>) = pairs.indexOfFirst { at(atoms[it.a]) == d.first && at(atoms[it.b]) == d.third }
        val counts = MutableList(pairs.size) { 0 }
        for (d in b.drawn("lines")) counts[pairOf(d)] = if (d.second == '=') 2 else 1
        val step = AtomsTeacher.deduce(atoms, pairs, counts) ?: return null
        val moved = b.drawn("moves").associate { pairOf(it) to if (it.second == '=') 2 else 1 }
        assertEquals("${b.technique}: the bonds", moved, step.raise)
        return Drawn(step.technique, step.focus.filter { it < atoms.size }.map { at(atoms[it]) }.toSet())
    }

    private fun pearlsStep(b: Board): TeachStep? {
        val n = b.width
        val pearls = b.rows.flatten().joinToString("")
        fun edge(d: Triple<Int, Char, Int>) =
            if (d.third == d.first + 1) d.first / n * (n - 1) + d.first % n else n * (n - 1) + d.first
        val edges = CharArray(2 * n * (n - 1)) { '.' }
        for (d in b.drawn("lines")) edges[edge(d)] = if (d.second == 'x') 'x' else '-'
        val marks = edges.concatToString()
        // A pearl with two ways in is never the hint itself: a pearl's own rule always speaks first (black-pearl on this
        // board), so that heading alone asks the teacher for the steps of its technique rather than for its next step.
        val step = if (b.technique == "pearl-ways") {
            PearlsTeacher.steps(n, pearls, marks, b.technique).first { s -> s.focus.filter { it < n * n }.toSet() == b.squares("focus") }
        } else {
            PearlsTeacher.deduce(n, pearls, marks) ?: return null
        }
        val moved = b.drawn("moves")
        assertEquals("${b.technique}: the lines", moved.filter { it.second != 'x' }.map(::edge).toSet(), step.lines.toSet())
        assertEquals("${b.technique}: the crosses", moved.filter { it.second == 'x' }.map(::edge).toSet(), step.crosses.toSet())
        return Drawn(step.technique, step.focus.filter { it < n * n }.toSet())
    }

    /** The two things a Mosaic board's squares can't show (fills left, the colour poured), and how many colours it deals. */
    private val mosaicFixtures = mapOf("count" to Triple(3, 2, 2), "biggest-swallow" to Triple(3, 4, 0), "centre" to Triple(3, 3, 1))

    private fun mosaicStep(b: Board): TeachStep? {
        val cells = b.fields.getValue("regions").filter { it != ' ' }.map { it - 'a' }
        val (colours, fillsLeft, colour) = mosaicFixtures.getValue(b.technique)
        val step = MosaicTeacher.deduce(b.width, b.rows.size, colours, cells, fillsLeft) ?: return null
        assertEquals("${b.technique}: the colour poured", colour, step.colour)
        // The board tints the area filled: the hint's own focus can be the area and all its neighbours.
        return Drawn(step.technique, step.targets)
    }

    private fun dominoesStep(b: Board): TeachStep? {
        val w = b.width
        val grid = b.rows.take(w).flatten()
        val regions = b.fields.getValue("regions").split(' ').take(w).joinToString("")
        val badges = b.marks("badges")
        val regionOf = regions.map { if (it == '.') -1 else it - 'a' }
        val rules = (0..regionOf.max()).map { r ->
            val rule = badges.entries.first { regionOf[it.key] == r }.value
            when {
                rule == "=" -> RegionRule(RuleKind.EQUAL)
                rule == "≠" -> RegionRule(RuleKind.DIFFERENT)
                rule[0] == '<' -> RegionRule(RuleKind.LESS, rule.drop(1).toInt())
                rule[0] == '>' -> RegionRule(RuleKind.MORE, rule.drop(1).toInt())
                else -> RegionRule(RuleKind.SUM, rule.toInt())
            }
        }
        // The tray is under the grid and an empty row, a slot per domino in the tray's sorted order; a laid one's slot is empty.
        val slots = b.rows.drop(w + 1).flatten()
        val laid = b.drawn("lines").map { (x, _, y) -> x to y }
        fun domino(p: Int, q: Int) = Domino(minOf(p, q), maxOf(p, q))
        val fromSlots = slots.filter { it != "_" }.map { t -> t.split('-').let { domino(it[0].toInt(), it[1].toInt()) } }
        val tray = (fromSlots + laid.map { (x, y) -> domino(grid[x].toInt(), grid[y].toInt()) }).sortedWith(compareBy({ it.a }, { it.b }))
        val placed = tray.indices.map<Int, Placement?> { k ->
            if (slots[k] != "_") return@map null
            val (x, y) = laid.first { (x, y) -> domino(grid[x].toInt(), grid[y].toInt()) == tray[k] }
            if (grid[x].toInt() == tray[k].a) Placement(x, y) else Placement(y, x)
        }
        val layout = DominoLogic.Layout(w, w, grid.map { it != "_" }, regionOf, rules, tray)
        assertTrue("${b.technique}: a position with one answer", DominoLogic.solutions(layout) is Verdict.ExactlyOne)
        val step = DominoTeacher.deduce(layout, placed) ?: return null
        val slotSquare = { k: Int -> (w + 1) * w + k }
        val move = step.placement!!
        val domino = tray[step.domino]
        assertEquals("${b.technique}: the digits", mapOf(move.first to "${domino.a}", move.second to "${domino.b}", slotSquare(step.domino) to "${domino.b}-${domino.a}"), b.marks("answer"))
        val mapped = { s: Set<Int> -> s.map { if (it >= DominoTeacher.TRAY) slotSquare(it - DominoTeacher.TRAY) else it }.toSet() }
        return Drawn(step.technique, mapped(step.focus), mapped(step.targets))
    }

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
