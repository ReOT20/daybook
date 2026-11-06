package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.i18n.text
import com.joebywan.daybook.core.i18n.message

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.TutorialFrame
import com.joebywan.daybook.core.TutorialPath
import com.joebywan.daybook.core.placementDeduction
import com.joebywan.daybook.ui.theme.ArcadeBoard
import kotlinx.serialization.Serializable
import com.joebywan.daybook.ui.BoardGlyph
import com.joebywan.daybook.ui.style

/**
 * A Sudoku grid ([grid], which carries the digits, the answer, the selection, the pencil marks and the move count)
 * with cages over it: [cageOf] gives each square its cage and [sums] each cage's total. See [CageLogic].
 */
@Serializable
data class CageState(
    val grid: SudokuState,
    val cageOf: List<Int>,
    val sums: List<Int>,
) : PuzzleState {
    override val moves: Int get() = grid.moves

    /** The rules, not [SudokuState.solution]: a finished Sudoku grid whose cages add up and repeat nothing. */
    override val solved: Boolean get() = CageLogic.isSolved(grid.cells, cageOf, sums)

    val cells: List<Int> get() = grid.cells

    /** Squares the board already shows to be wrong: Sudoku's clashes and the cages' own. */
    fun conflicts(): Set<Int> = grid.conflicts() + CageLogic.cageConflicts(grid.cells, cageOf, sums)

    fun select(index: Int): CageState = copy(grid = grid.select(index))

    fun toggleNote(index: Int, digit: Int): CageState {
        val next = grid.toggleNote(index, digit)
        return if (next === grid) this else copy(grid = next)
    }

    /**
     * As [SudokuState.withCell], and the digit is struck from the pencil marks of the rest of its cage too,
     * in the same state, so one undo puts back the digit and every mark together. `this` if nothing changes.
     */
    fun withCell(index: Int, value: Int): CageState {
        val next = grid.withCell(index, value)
        if (next === grid) return this
        if (value == 0) return copy(grid = next)
        val bit = (1 shl (value - 1)).inv()
        val notes = next.notes.toMutableList()
        for (i in cageOf.indices) if (i != index && cageOf[i] == cageOf[index]) notes[i] = notes[i] and bit
        return copy(grid = if (notes == next.notes) next else next.copy(notes = notes))
    }

    /** What a key or a digit pad key does to [cell]: [SudokuState.enter]'s rule, with the cage's marks struck as well. */
    fun enter(cell: Int, digit: Int, notesMode: Boolean): CageState = when {
        notesMode -> toggleNote(cell, digit)
        else -> withCell(cell, if (grid.cells[cell] == digit) 0 else digit)
    }
}

/** The share of a square's height that a cage's total takes from the top of its first square. */
private const val LABEL_BAND = 0.34f

/**
 * Cage Sudoku — a Sudoku whose dashed cages must add up. The grid, the pad, the notes and the keys are
 * Sudoku's own ([SudokuGridBoard]); this adds the cages over it, their totals, and the logic that makes
 * them a puzzle ([CageLogic], [CageTeacher]). `id = "cages"` is hashed into every seed: never rename it.
 */
object Cages : PuzzleType {

    override val id = "cages"
    override val displayName get() = text("puzzle.cages.name")
    override val tagline get() = text("puzzle.cages.tagline")
    override val accent = 0xFF528DC9
    override val rules get() = listOf(
        text("puzzle.cages.rule.1"),
        text("puzzle.cages.rule.2"),
        text("puzzle.cages.rule.3"),
        text("puzzle.cages.rule.4"),
        text("puzzle.cages.rule.5"),
        text("puzzle.cages.rule.6"),
        text("puzzle.cages.rule.7"),
    )

    override val keyboardHelp get() = listOf(
        text("puzzle.cages.keyboard.1"),
    )

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState =
        CageLogic.generateVerified(seed, difficulty) ?: CageLogic.lastResort()

    internal fun generateVerified(seed: Long, difficulty: Difficulty): CageState? =
        CageLogic.generateVerified(seed, difficulty)

    // ---- teaching --------------------------------------------------------------------------

    override fun teach(state: PuzzleState): Deduction? {
        val s = state as CageState
        val step = CageTeacher.teach(s) ?: return null
        return placementDeduction(
            s, step,
            mistake = step.technique == CageTeacher.MISTAKE,
            fallback = step.technique == CageTeacher.FALLBACK,
            valueAt = { t, cell -> t.cells[cell] },
            place = { t, cell, digit -> t.withCell(cell, digit).select(cell) },
        )
    }

    // ---- walkthrough ---------------------------------------------------------------------------

    /** Sudoku's own walkthrough grid and gaps, with a domino cage over every pair of neighbours. */
    internal val TUTORIAL_SOLUTION = Sudoku.TUTORIAL_SOLUTION
    internal val TUTORIAL_OPEN = Sudoku.TUTORIAL_OPEN

    /** The first gap: a cage of 11 beside a 7. */
    internal const val TUTORIAL_FIRST = 63

    /** Where the wrong 5 goes: the cage of 11 beside a 2 needs a 9, and 5 + 2 is only 7 (and a 5 already stands in its column). */
    internal const val TUTORIAL_WRONG = 42
    internal const val TUTORIAL_NOTED = 9

    internal fun tutorialBoard(
        entered: Map<Int, Int> = emptyMap(),
        selected: Int? = null,
        noted: Map<Int, Int> = emptyMap(),
    ): CageState {
        val cageOf = CageLogic.dominoes()
        val cells = TUTORIAL_SOLUTION.mapIndexed { i, v -> if (i in TUTORIAL_OPEN) entered[i] ?: 0 else v }
        return CageState(
            grid = SudokuState(
                givens = TUTORIAL_SOLUTION.indices.map { it !in TUTORIAL_OPEN },
                cells = cells,
                solution = TUTORIAL_SOLUTION,
                selected = selected,
                notes = List(81) { i -> noted[i]?.let { 1 shl (it - 1) } ?: 0 },
            ),
            cageOf = cageOf,
            sums = CageLogic.sumsOf(cageOf, TUTORIAL_SOLUTION),
        )
    }

    override val tutorial: List<TutorialFrame> by lazy {
        val start = tutorialBoard()
        val cage = CageLogic.cageCells(start.cageOf)
        val firstCage = cage[start.cageOf[TUTORIAL_FIRST]].toSet()
        val wrongCage = cage[start.cageOf[TUTORIAL_WRONG]].toSet()
        val placed = mapOf(TUTORIAL_FIRST to 4)
        val done = tutorialBoard(placed)
        val place = TutorialPath(
            { it is CageState && it.cells == start.cells && it.grid.selected == TUTORIAL_FIRST },
            { it is CageState && it.cells == done.cells },
        )
        val clash = TutorialPath(
            { it is CageState && it.cells == done.cells && it.grid.selected == TUTORIAL_WRONG },
            { it is CageState && it.cells == tutorialBoard(placed + (TUTORIAL_WRONG to 5)).cells },
            // Back where it started, but by way of the 5: only a move counted since says so.
            { it is CageState && it.cells == done.cells && it.moves > 0 },
        )
        listOf(
            TutorialFrame(
                state = start,
                caption = message("puzzle.cages.tutorial.1.caption"),
                highlight = BoardHighlight(strong = firstCage),
            ),
            TutorialFrame(
                state = start,
                caption = message("puzzle.cages.tutorial.2.caption"),
                glow = { s ->
                    val strong = if (place.stage(s) < 0) TUTORIAL_FIRST else SudokuTeacher.pad(4)
                    BoardHighlight(strong = setOf(strong), soft = firstCage)
                },
                steps = place.steps,
                retry = message("puzzle.cages.tutorial.2.retry"),
                done = message("puzzle.cages.tutorial.2.done"),
            ),
            TutorialFrame(
                state = tutorialBoard(placed, selected = TUTORIAL_FIRST),
                caption = message("puzzle.cages.tutorial.3.caption"),
                glow = { s ->
                    when (clash.stage(s)) {
                        -1 -> BoardHighlight(strong = setOf(TUTORIAL_WRONG), soft = wrongCage)
                        0 -> BoardHighlight(strong = setOf(SudokuTeacher.pad(5)), soft = wrongCage)
                        else -> BoardHighlight(strong = setOf(TUTORIAL_WRONG, SudokuTeacher.pad(5)), soft = wrongCage, warning = true)
                    }
                },
                steps = clash.steps,
                retry = message("puzzle.cages.tutorial.3.retry"),
                done = message("puzzle.cages.tutorial.3.done"),
            ),
            TutorialFrame(
                state = tutorialBoard(placed, selected = TUTORIAL_WRONG),
                caption = message("puzzle.cages.tutorial.4.caption"),
                highlight = BoardHighlight(
                    strong = setOf(TUTORIAL_WRONG, SudokuTeacher.NOTES_KEY, SudokuTeacher.pad(TUTORIAL_NOTED)),
                    soft = wrongCage,
                ),
                accepts = { next ->
                    next is CageState && next.cells == done.cells &&
                        next.grid.notes == tutorialBoard(placed, noted = mapOf(TUTORIAL_WRONG to TUTORIAL_NOTED)).grid.notes
                },
                retry = message("puzzle.cages.tutorial.4.retry"),
                done = message("puzzle.cages.tutorial.4.done"),
            ),
            TutorialFrame(
                state = tutorialBoard(placed, selected = TUTORIAL_WRONG, noted = mapOf(TUTORIAL_WRONG to TUTORIAL_NOTED)),
                caption = message("puzzle.cages.tutorial.5.caption"),
                freePlay = true,
                done = message("puzzle.cages.tutorial.5.done"),
            ),
        )
    }

    // ---- drawing ------------------------------------------------------------------------------

    /** One straight piece of a cage's outline, in squares (a square is 1 by 1), from the grid's top-left corner. */
    internal class Seg(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val cage: Int)

    /**
     * The outline of every cage of an [n] x [n] grid, drawn [inset] squares inside the cage's edge. Each square makes the
     * pieces for its sides that face outside its cage; at a corner a piece stops short (a bend of the cage), carries on
     * past the edge (a notch in it) or runs straight into the next square's, so the outline of an L or a bar closes up
     * without a gap. Pure, so the closing can be tested.
     */
    internal fun cageOutline(cageOf: List<Int>, n: Int, inset: Float = 0.09f): List<Seg> {
        val out = ArrayList<Seg>()
        fun inCage(r: Int, c: Int, k: Int) = r in 0 until n && c in 0 until n && cageOf[r * n + c] == k
        // How far a side's end moves past the square's edge: in if the cage bends, out if it notches, 0 if it runs on.
        fun ext(neighbour: Boolean, diagonal: Boolean) = if (!neighbour) -inset else if (diagonal) inset else 0f
        for (r in 0 until n) for (c in 0 until n) {
            val k = cageOf[r * n + c]
            val left = c.toFloat()
            val top = r.toFloat()
            val right = left + 1f
            val bottom = top + 1f
            if (!inCage(r - 1, c, k)) {
                val a = left - ext(inCage(r, c - 1, k), inCage(r - 1, c - 1, k))
                val b = right + ext(inCage(r, c + 1, k), inCage(r - 1, c + 1, k))
                out += Seg(a, top + inset, b, top + inset, k)
            }
            if (!inCage(r + 1, c, k)) {
                val a = left - ext(inCage(r, c - 1, k), inCage(r + 1, c - 1, k))
                val b = right + ext(inCage(r, c + 1, k), inCage(r + 1, c + 1, k))
                out += Seg(a, bottom - inset, b, bottom - inset, k)
            }
            if (!inCage(r, c - 1, k)) {
                val a = top - ext(inCage(r - 1, c, k), inCage(r - 1, c - 1, k))
                val b = bottom + ext(inCage(r + 1, c, k), inCage(r + 1, c - 1, k))
                out += Seg(left + inset, a, left + inset, b, k)
            }
            if (!inCage(r, c + 1, k)) {
                val a = top - ext(inCage(r - 1, c, k), inCage(r - 1, c + 1, k))
                val b = bottom + ext(inCage(r + 1, c, k), inCage(r + 1, c + 1, k))
                out += Seg(right - inset, a, right - inset, b, k)
            }
        }
        return out
    }

    /** Dashed outlines for the cages of an [n] x [n] grid of [cell]-pixel squares (see [cageOutline]). */
    internal fun DrawScope.drawCages(cageOf: List<Int>, n: Int, cell: Float, ink: Color, halo: Color, stroke: Float) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(cell * 0.13f, cell * 0.09f))
        // The squares under a line run from white to violet to amber: a dash on its halo parts from every one of them.
        for (g in cageOutline(cageOf, n)) {
            drawLine(halo, Offset(g.x0 * cell, g.y0 * cell), Offset(g.x1 * cell, g.y1 * cell), strokeWidth = stroke * 2.4f, pathEffect = dash)
            drawLine(ink, Offset(g.x0 * cell, g.y0 * cell), Offset(g.x1 * cell, g.y1 * cell), strokeWidth = stroke, pathEffect = dash)
        }
    }

    /** Each cage's total in the top-left corner of its first square, on a chip of [chip] where [filled]: an empty square's pale fill, and its notes, need none. */
    private fun DrawScope.drawTotals(
        cageOf: List<Int>, sums: List<Int>, n: Int, cell: Float, measurer: TextMeasurer, style: TextStyle, chip: Color, filled: (Int) -> Boolean,
    ) {
        val seen = BooleanArray(sums.size)
        for (i in cageOf.indices) {
            val k = cageOf[i]
            if (seen[k]) continue
            seen[k] = true
            val laid = measurer.measure(sums[k].toString(), style, maxLines = 1)
            val at = Offset(i % n * cell + cell * 0.09f, i / n * cell + cell * 0.04f)
            if (filled(i)) drawRoundRect(chip, at, Size(laid.size.width.toFloat(), laid.lastBaseline + cell * 0.02f), CornerRadius(cell * 0.06f))
            drawText(laid, topLeft = at)
        }
    }

    // ---- home-grid motif ----------------------------------------------------------------------

    /**
     * One hand-picked 3x3 box, as Sudoku's tile is, with its squares in dashed cages: 5+3, 4+2, 6+7, 1+9 and
     * a lone 8. A legal crop of a board (the box holds 1-9 once). Fixed, so the tile never changes.
     */
    private val PREVIEW_CAGES = listOf(0, 0, 1, 2, 2, 1, 3, 3, 4)
    private val PREVIEW_SUMS = listOf(8, 6, 13, 10, 8)
    private val PREVIEW_DIGITS = listOf(0, 0, 0, 0, 7, 0, 0, 0, 8)

    @Composable
    override fun Preview(modifier: Modifier) {
        val a = ArcadeBoard.current
        val measurer = rememberTextMeasurer()
        BoxWithConstraints(modifier) {
            val cell = minOf(maxWidth, maxHeight) / 3
            for (r in 0 until 3) for (c in 0 until 3) {
                val i = r * 3 + c
                Box(
                    Modifier
                        .padding(start = cell * c, top = cell * r)
                        .size(cell)
                        .padding(1.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(a.square),
                    contentAlignment = Alignment.Center,
                ) {
                    if (PREVIEW_DIGITS[i] != 0) {
                        Text(
                            text = PREVIEW_DIGITS[i].toString(),
                            style = BoardGlyph.ENTERED.style(cell),
                            color = a.squareInk,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            val label = BoardGlyph.CORNER.style(cell).copy(color = a.squareInk)
            Canvas(Modifier.fillMaxSize()) {
                val px = size.width.coerceAtMost(size.height) / 3f
                drawCages(PREVIEW_CAGES, 3, px, a.givenInk, a.square, stroke = px * 0.045f)
                drawTotals(PREVIEW_CAGES, PREVIEW_SUMS, 3, px, measurer, label, a.square) { false }
            }
        }
    }

    override fun withoutSelection(state: PuzzleState): PuzzleState =
        (state as CageState).let { if (it.grid.selected == null) it else it.copy(grid = it.grid.copy(selected = null)) }

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as CageState
        val measurer = rememberTextMeasurer()
        // The square a cage's total sits in: the first of each, in reading order.
        val labelled = remember(s.cageOf) {
            val seen = BooleanArray(s.sums.size)
            s.cageOf.indices.filter { i -> !seen[s.cageOf[i]].also { seen[s.cageOf[i]] = true } }.toSet()
        }
        val units = remember(s.cageOf) { SUDOKU_UNITS + CageLogic.cageCells(s.cageOf) }
        SudokuGridBoard(
            s = s.grid,
            conflicts = s.conflicts(),
            interactive = interactive,
            onSelect = { onState(s.select(it)) },
            onDeselect = { onState(withoutSelection(s)) },
            onEnter = { cell, digit, notesMode ->
                val next = s.enter(cell, digit, notesMode)
                if (next !== s) onState(next)
            },
            onKey = { action, notesMode -> s.applyKey(action, notesMode)?.let(onState) },
            topBand = { if (it in labelled) LABEL_BAND else 0f },
            // The rows, columns and boxes, then the cages: a cage is finished when it is full, repeats nothing and makes its total.
            units = units,
            complete = { u ->
                if (u < SUDOKU_UNITS.size) {
                    s.cells.holdsOneToNine(units[u])
                } else {
                    val cage = units[u]
                    cage.all { s.cells[it] != 0 } && cage.map { s.cells[it] }.toSet().size == cage.size &&
                        cage.sumOf { s.cells[it] } == s.sums[u - SUDOKU_UNITS.size]
                }
            },
            overlay = { cell -> CageOverlay(s, cell, ArcadeBoard.current, measurer) },
        )
    }

    @Composable
    private fun BoxScope.CageOverlay(s: CageState, cell: Dp, a: ArcadeBoard, measurer: TextMeasurer) {
        val label = BoardGlyph.CORNER.style(cell).copy(color = a.squareInk)
        // Not a pointer target: taps fall through to the squares underneath.
        Canvas(Modifier.fillMaxSize()) {
            val px = size.width / 9f
            drawCages(s.cageOf, 9, px, a.squareInk, a.square, stroke = 1.5.dp.toPx())
            drawTotals(s.cageOf, s.sums, 9, px, measurer, label, a.square) { s.grid.cells[it] != 0 || s.grid.selected == it }
        }
    }
}
