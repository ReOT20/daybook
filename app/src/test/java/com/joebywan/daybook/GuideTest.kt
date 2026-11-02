package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PlacementStep
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.core.TeachStep
import com.joebywan.daybook.core.praisedTechniquesOf
import com.joebywan.daybook.core.techniqueNamesOf
import com.joebywan.daybook.core.tierNotes
import com.joebywan.daybook.puzzles.ChessPosition
import com.joebywan.daybook.puzzles.ChessRules
import com.joebywan.daybook.puzzles.ChessTeacher
import com.joebywan.daybook.puzzles.PuzzleState
import com.joebywan.daybook.puzzles.Sudoku
import com.joebywan.daybook.puzzles.SudokuTeacher
import com.joebywan.daybook.puzzles.WhosWhoLogic
import com.joebywan.daybook.puzzles.WhosWhoState
import com.joebywan.daybook.puzzles.WhosWhoTeacher
import com.joebywan.daybook.puzzles.WhosWhoWords
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
        "chess" to ::chessStep,
        "whoswho" to ::whosWhoStep,
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

    /** A step with its squares moved to the board's `row * width + col`. */
    private fun mapped(step: TeachStep, focus: Set<Int>, cited: Set<Int>, targets: Set<Int>): TeachStep = object : TeachStep by step {
        override val focus = focus
        override val cited = cited
        override val targets = targets
    }

    /** The moves White has left on each chess board: the rows don't show them. White to move, no castling, no en passant. */
    private val chessMoves = mapOf("mate-in-one" to 1, "forcing-key" to 2, "quiet-key" to 2)

    private fun chessStep(b: Board): TeachStep? {
        assertEquals("a b c d e f g h", b.fields["top"])
        assertEquals("8 7 6 5 4 3 2 1", b.fields["left"])
        val letters = mapOf(
            '♔' to 'K', '♕' to 'Q', '♖' to 'R', '♗' to 'B', '♘' to 'N', '♙' to 'P',
            '♚' to 'k', '♛' to 'q', '♜' to 'r', '♝' to 'b', '♞' to 'n', '♟' to 'p',
        )
        val placement = b.rows.joinToString("/") { row -> row.map { letters[it[0]] ?: '1' }.joinToString("") }
        val step = ChessTeacher.deduce(ChessPosition.fromFen("$placement w - -"), chessMoves.getValue(b.technique)) ?: return null
        val at = { sq: Int -> (7 - sq / 8) * 8 + sq % 8 } // a1 = 0 to row * width + col, White at the bottom
        assertEquals("${b.technique}: rings", step.cited.map(at).toSet(), b.squares("cite"))
        // The filled squares are drawn before the move: the piece that moves, and what stands where it lands.
        val rows = b.rows.flatten()
        for (sq in listOf(ChessRules.from(step.move), ChessRules.to(step.move))) {
            assertEquals("${b.technique}: the move's square $sq", rows[at(sq)], b.marks("answer")[at(sq)])
        }
        return mapped(step, step.focus.map(at).toSet(), step.cited.map(at).toSet(), step.targets.map(at).toSet())
    }

    /** The two games on the Who's Who page, their words and clues: what its rows don't show. */
    private fun whosWhoGame(json: String) = Json.decodeFromString(PuzzleState.serializer(), json) as WhosWhoState
    private val whosWhoHard = whosWhoGame(
        """{"type":"com.joebywan.daybook.puzzles.WhosWhoState","names":["Eve","Kai","Leo","Raj"],"groups":[0,5,1],"items":[[0,1,2,3],[0,1,3,4],[0,2,3,4]],"clues":[{"kind":3,"a":0,"b":9},{"kind":2,"a":0,"b":13},{"kind":0,"a":1,"b":10},{"kind":3,"a":2,"b":3},{"kind":2,"a":0,"b":12},{"kind":0,"a":0,"b":8},{"kind":0,"a":1,"b":15},{"kind":0,"a":4,"b":13}],"answer":[[1,3,0,2],[0,2,3,1],[2,3,1,0]]}"""
    )
    private val whosWhoExpert = whosWhoGame(
        """{"type":"com.joebywan.daybook.puzzles.WhosWhoState","names":["Ann","Ben","Finn","Ivy","Kai"],"groups":[0,5,4],"items":[[0,1,2,3,4],[0,1,2,3,4],[0,1,2,3,4]],"clues":[{"kind":3,"a":1,"b":10},{"kind":2,"a":0,"b":14},{"kind":1,"a":6,"b":14},{"kind":0,"a":5,"b":18},{"kind":0,"a":13,"b":16},{"kind":0,"a":2,"b":6},{"kind":3,"a":10,"b":16},{"kind":4,"a":4,"b":7,"c":8},{"kind":2,"a":3,"b":11},{"kind":0,"a":3,"b":17},{"kind":2,"a":11,"b":19}],"answer":[[4,0,1,3,2],[3,2,0,4,1],[1,3,4,2,0]]}"""
    )
    private val whosWhoBoards = mapOf(
        "clue" to whosWhoHard.copy(marks = "..................o............................."),
        "only" to whosWhoHard.copy(marks = "x..xo....x..x.....o....o...x........x..........."),
        "link" to whosWhoHard.copy(marks = "x..xo....x..x.....o....oo..x........x..........."),
        "house" to whosWhoHard.copy(marks = "x..xo....x..x.....o....o........................"),
        "what-if" to whosWhoExpert.copy(marks = "x........x.....o...xxxoxx...o..o...ox.xx.x.x.x.....x.xo..o..x...x........x."),
    )

    private fun whosWhoStep(b: Board): TeachStep? {
        val s = whosWhoBoards.getValue(b.technique)
        assertEquals(s.groups.joinToString(" ") { WhosWhoWords.HEADERS[it] }, b.fields["top"])
        assertEquals(s.names.joinToString(" "), b.fields["left"])
        val text = pages.first { it.id == "whoswho" }.text
        for (k in s.clues.indices) {
            val line = "- ${k + 1}. ${WhosWhoLogic.clueText(s, k)}"
            assertTrue("${b.technique}: the page should list \"$line\"", line in text)
        }
        // Every square but the move's shows what the marks leave it: its ticked word, or the items still possible.
        val grid = WhosWhoLogic.gridOf(s.n, s.columns, s.marks)
        val step = WhosWhoTeacher.deduce(s, s.marks) ?: return null
        for (c in 0 until s.n * s.columns) {
            if (c in step.targets) continue
            val column = c % s.columns
            val placed = grid.placed[c]
            assertEquals("${b.technique}: square $c", if (placed >= 0) s.word(column, placed) else ".", b.rows[c / s.columns][column])
            val open = (0 until s.n).filter { placed < 0 && grid.cand[c] shr it and 1 == 1 }.map { s.word(column, it) }
            assertEquals("${b.technique}: what square $c can hold", open.joinToString(","), b.marks("notes")[c].orEmpty())
        }
        // The move: a tick says the item, a cross says "not" it.
        for (t in step.ticks) assertEquals(s.word(t / s.n % s.columns, t % s.n), b.marks("answer")[t / s.n])
        for (t in step.crosses) assertEquals("not_" + s.word(t / s.n % s.columns, t % s.n), b.marks("answer")[t / s.n])
        // A clue's index isn't a square: a step that reads one must name it in the caption.
        for (k in step.focus.filter { it >= WhosWhoTeacher.CLUE_BASE }) {
            assertTrue("${b.technique}: the caption names its clue", "lue ${k - WhosWhoTeacher.CLUE_BASE + 1}" in b.fields["caption"].orEmpty())
        }
        val cells = { set: Set<Int> -> set.filter { it < WhosWhoTeacher.CLUE_BASE }.toSet() }
        assertEquals("${b.technique}: rings", cells(step.cited), b.squares("cite"))
        return mapped(step, cells(step.focus), cells(step.cited), step.targets)
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
