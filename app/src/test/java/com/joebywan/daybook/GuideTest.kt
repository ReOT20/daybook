package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PlacementStep
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.core.TeachStep
import com.joebywan.daybook.core.praisedTechniquesOf
import com.joebywan.daybook.core.techniqueNamesOf
import com.joebywan.daybook.core.tierNotes
import com.joebywan.daybook.puzzles.KingsTeacher
import com.joebywan.daybook.puzzles.LampsLogic
import com.joebywan.daybook.puzzles.LampsTeacher
import com.joebywan.daybook.puzzles.Link
import com.joebywan.daybook.puzzles.Lits
import com.joebywan.daybook.puzzles.LitsTeacher
import com.joebywan.daybook.puzzles.MamboTeacher
import com.joebywan.daybook.puzzles.Mark
import com.joebywan.daybook.puzzles.MinesTeacher
import com.joebywan.daybook.puzzles.PuzzleState
import com.joebywan.daybook.puzzles.Sudoku
import com.joebywan.daybook.puzzles.SudokuTeacher
import com.joebywan.daybook.puzzles.Sym
import com.joebywan.daybook.puzzles.TentsLogic
import com.joebywan.daybook.puzzles.TentsTeacher
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
        "kings" to ::kingsStep,
        "mambo" to ::mamboStep,
        "tents" to ::tentsStep,
        "lamps" to ::lampsStep,
        "mines" to ::minesStep,
        "lits" to ::litsStep,
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

    /** `regions:` as the game's numbering: `a` is region 0. */
    private fun regionsOf(b: Board) = b.fields.getValue("regions").replace(" ", "").map { it - 'a' }

    private fun kingsStep(b: Board): TeachStep? {
        val marks = b.rows.flatten().map { when (it) { "K" -> Mark.KING; "x" -> Mark.BLOCKED; else -> Mark.EMPTY } }
        val step = KingsTeacher.deduce(b.width, regionsOf(b), marks) ?: return null
        assertEquals("${b.technique}: the move", step.kings.associateWith { "K" } + step.crosses.associateWith { "x" }, b.marks("answer"))
        return step
    }

    private fun mamboStep(b: Board): TeachStep? {
        val cells = b.rows.flatten().map { when (it) { "S" -> Sym.SUN; "M" -> Sym.MOON; else -> Sym.NONE } }
        val links = b.fields.getValue("between").split(' ').map { part ->
            val n = Regex("\\d+").findAll(part).map { it.value.toInt() - 1 }.toList()
            Link(n[0] * b.width + n[1], n[2] * b.width + n[3], part.endsWith("=="))
        }
        val step = MamboTeacher.deduce(b.width, cells, links) ?: return null
        assertEquals("${b.technique}: the move", step.places.mapValues { (_, s) -> if (s == Sym.SUN) "S" else "M" }, b.marks("answer"))
        return step
    }

    /** The clues are a top row and a left column, so the teacher's squares and clues move into the board's numbering. */
    private fun tentsStep(b: Board): TeachStep? {
        val n = b.width - 1
        val grid = b.rows.drop(1).map { it.drop(1) }.flatten()
        val rowCounts = b.rows.drop(1).map { it[0].toInt() }
        val colCounts = b.rows[0].drop(1).map { it.toInt() }
        val marks = grid.map { when (it) { "▲" -> TentsLogic.TENT; "x" -> TentsLogic.GRASS; else -> 0 } }
        val step = TentsTeacher.deduce(n, grid.map { it == "♣" }, rowCounts, colCounts, marks) ?: return null
        fun drawn(i: Int) = when {
            i < n * n -> (i / n + 1) * b.width + i % n + 1
            i < n * n + n -> (i - n * n + 1) * b.width
            else -> i - n * n - n + 1
        }
        val mark = if (step.value == TentsLogic.TENT) "▲" else "x"
        assertEquals("${b.technique}: the move", step.targets.map(::drawn).associateWith { mark }, b.marks("answer"))
        return object : TeachStep by step {
            override val focus = step.focus.map(::drawn).toSet()
            override val cited = step.cited.map(::drawn).toSet()
            override val targets = step.targets.map(::drawn).toSet()
        }
    }

    private fun lampsStep(b: Board): TeachStep? {
        val tokens = b.rows.flatten()
        val solid = b.squares("solid")
        val isWall = tokens.indices.map { it in solid }
        val walls = tokens.indices.joinToString("") { i -> if (!isWall[i]) "." else if (tokens[i] == ".") "#" else tokens[i] }
        val marks = tokens.indices.joinToString("") { i -> if (isWall[i]) "." else when (tokens[i]) { "●" -> "L"; "x" -> "x"; else -> "." } }
        val lit = LampsLogic.lit(b.width, walls, BooleanArray(tokens.size) { marks[it] == 'L' })
        for (i in tokens.indices) if (!isWall[i] && tokens[i] in listOf(".", "·")) {
            assertEquals("${b.technique}: the dot on square $i", lit[i], tokens[i] == "·")
        }
        val step = LampsTeacher.deduce(b.width, walls, marks) ?: return null
        assertEquals("${b.technique}: the move", step.targets.associateWith { if (step.value == LampsLogic.LAMP) "●" else "x" }, b.marks("answer"))
        return step
    }

    private fun minesStep(b: Board): TeachStep? {
        val tokens = b.rows.flatten()
        val hidden = b.squares("solid")
        val num = IntArray(tokens.size) { if (it in hidden) -1 else tokens[it].toIntOrNull() ?: 0 }
        val total = mapOf(64 to 10, 80 to 16, 90 to 21).getValue(tokens.size)
        val step = MinesTeacher.deduce(
            b.width, b.rows.size, num, BooleanArray(tokens.size), BooleanArray(tokens.size) { tokens[it] == "F" }, total,
        ) ?: return null
        assertEquals("${b.technique}: the move", step.safe.associateWith { "✓" } + step.mines.associateWith { "F" }, b.marks("answer"))
        return step
    }

    private fun litsStep(b: Board): TeachStep? {
        val tokens = b.rows.flatten()
        val region = regionsOf(b)
        val shaded = tokens.map { it != "." && it != "x" }
        val letters = Lits.letters(b.width, b.rows.size, region, shaded)
        for (i in tokens.indices) if (shaded[i]) assertEquals("${b.technique}: square $i", letters[i]?.name ?: "■", tokens[i])
        assertEquals("${b.technique}: the crosses", Lits.impossible(b.width, b.rows.size, region, shaded), tokens.indices.filter { tokens[it] == "x" }.toSet())
        val step = LitsTeacher.deduce(b.width, b.rows.size, region, shaded) ?: return null
        assertEquals("${b.technique}: the move", step.shades.associateWith { "■" }, b.marks("answer"))
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
