package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.i18n.Message
import com.joebywan.daybook.core.i18n.message

import com.joebywan.daybook.core.PlacementStep
import com.joebywan.daybook.core.bit
import com.joebywan.daybook.core.cap
import com.joebywan.daybook.core.boxPositionMessage
import com.joebywan.daybook.core.substringBefore
import com.joebywan.daybook.core.joinedMessages
import com.joebywan.daybook.core.digitsOf
import com.joebywan.daybook.core.has

/**
 * Sudoku, reasoned the way a person reasons it, one named step at a time.
 *
 * As in [KingsTeacher], the split is the point. [deduce] is handed the digits the player can see and
 * nothing else, so a step it explains cannot lean on the stored answer — a guarantee of the
 * signature. The answer is read only in [teach]: to decide which of the player's digits are
 * mistakes, and for [FALLBACK], which says openly that it is pointing at the answer.
 *
 * Candidates are *derived* from the visible digits on every call (a digit is a candidate for an
 * empty cell when no cell in its row, column or box holds it). The player's pencil marks
 * ([SudokuState.notes]) are deliberately never read, so notes cannot change what is suggested or
 * what is called a mistake, and a step never writes one: it can never end on "cross 7 off here",
 * every step ends in one digit placed. The
 * eliminating techniques therefore carry their consequence with them — "7 is locked to row 2 in this
 * box, and that leaves the glowing cell as the only place for 7 in row 2" — and the move is the
 * placement.
 *
 * Techniques, tried simplest first in the order puzzle raters (Sudoku Explainer's scale) put them,
 * because the hint should be the step a person would have found next:
 * 1. [FULL_HOUSE] — a row, column or box with one empty cell left.
 * 2. [HIDDEN_SINGLE_BOX] — a digit with only one place left in a box.
 * 3. [HIDDEN_SINGLE_LINE] — a digit with only one place left in a row or column.
 * 4. [NAKED_SINGLE] — a cell whose row, column and box leave it one digit.
 * 5. [LOCKED] — pointing (a digit confined to one line within a box) or claiming (confined to one
 *    box within a line), then the single that the elimination makes.
 * 6. [NAKED_PAIR] / [HIDDEN_PAIR] — two cells that between them must hold two digits, then the
 *    single that follows.
 * 7. [WHAT_IF] — a cell with two digits left, or a digit with two places left in a unit: suppose
 *    one, follow at most [MAX_CHAIN] forced singles, reach a cell or unit with nothing left, so it
 *    is the other. As Kings' what-if, bounded so one sentence can carry it.
 *
 * Tried and dropped, measured on 300 Expert boards: two eliminations chained before a single (0
 * boards once its explanation had to fit the panel), X-wing and XY-wing (together 16 boards of
 * the 114 reaching the fallback). Even every elimination above applied to a fixpoint before looking
 * for a single left 95 Expert boards needing the fallback somewhere: the 24-clue Expert boards are
 * dug for uniqueness, not rated, and many need chains no four-line hint can carry. A step cannot
 * leave eliminations behind for the next one, so every step has to end in a placement, and that
 * is the ceiling here. Since 2026-10 the generator digs only while this ladder finishes the board
 * ([depth], `Sudoku.generateVerified`), so no generated board reaches the fallback.
 *
 * Orders never come from a hash: every loop walks cells, units or digits in index order, so the same
 * board gets the same hint on every platform (CLAUDE.md, "Hash iteration order").
 */
internal object SudokuTeacher {

    const val FULL_HOUSE = "full-house"
    const val HIDDEN_SINGLE_BOX = "hidden-single-box"
    const val HIDDEN_SINGLE_LINE = "hidden-single-line"
    const val NAKED_SINGLE = "naked-single"
    const val LOCKED = "locked-candidates"
    const val NAKED_PAIR = "naked-pair"
    const val HIDDEN_PAIR = "hidden-pair"
    const val WHAT_IF = "what-if"
    const val FALLBACK = "fallback"
    const val MISTAKE = "mistake"

    /**
     * The hint panel shows four lines. A step whose explanation would not fit is passed over for the
     * next one that does, as on Tower, rather than shown cut off.
     */
    const val MAX_EXPLANATION = 200

    /** How many forced digits a [WHAT_IF] may walk through before it stops being followable. */
    const val MAX_CHAIN = 2

    /** Highlight index of digit [d]'s key under the board; 0..80 are the cells. */
    fun pad(d: Int): Int = 81 + d

    /** The pencil key beside the digit keys. A highlight index, though no step asks for it. */
    const val NOTES_KEY = 91

    /** Every technique in the order [deduce] tries them, for reports. */
    val TECHNIQUES = listOf(
        FULL_HOUSE, HIDDEN_SINGLE_BOX, HIDDEN_SINGLE_LINE, NAKED_SINGLE,
        LOCKED, NAKED_PAIR, HIDDEN_PAIR, WHAT_IF, FALLBACK,
    )

    // ---- units ------------------------------------------------------------------------------------

    private enum class Kind { BOX, ROW, COLUMN }

    private class Unit(val kind: Kind, val id: Int, val cells: List<Int>) {
        val name: Message
            get() = when (kind) {
                Kind.BOX -> message("teacher.sudoku.name.1", "boxNames_id_" to (boxNames[id]))
                Kind.ROW -> message("teacher.sudoku.name.2", "id_1" to (id + 1))
                Kind.COLUMN -> message("teacher.sudoku.name.3", "id_1" to (id + 1))
            }
    }

    private val boxNames = listOf(
        boxPositionMessage(0), boxPositionMessage(1), boxPositionMessage(2),
        boxPositionMessage(3), boxPositionMessage(4), boxPositionMessage(5),
        boxPositionMessage(6), boxPositionMessage(7), boxPositionMessage(8),
    )

    /** Boxes first: a box is what a player scans first on a Sudoku, and hidden singles in one are easiest. */
    private val units: List<Unit> = buildList {
        for (b in 0 until 9) add(Unit(Kind.BOX, b, List(9) { (b / 3 * 3 + it / 3) * 9 + b % 3 * 3 + it % 3 }))
        for (r in 0 until 9) add(Unit(Kind.ROW, r, List(9) { r * 9 + it }))
        for (c in 0 until 9) add(Unit(Kind.COLUMN, c, List(9) { it * 9 + c }))
    }

    private fun boxOf(cell: Int) = cell / 9 / 3 * 3 + cell % 9 / 3
    private fun unitsOf(cell: Int): List<Unit> =
        listOf(units[boxOf(cell)], units[9 + cell / 9], units[18 + cell % 9])

    // ---- what the player can see -------------------------------------------------------------------

    /**
     * The digits and the candidates they leave, with any eliminations a step has argued for struck
     * off. Candidates are a 1..9 bitmask per empty cell; a filled cell's mask is 0.
     */
    private class Sight(val cells: List<Int>, val cand: IntArray) {
        fun copyWithout(eliminations: List<Pair<Int, Int>>): Sight {
            val next = cand.copyOf()
            for ((cell, d) in eliminations) next[cell] = next[cell] and bit(d).inv()
            return Sight(cells, next)
        }

        fun places(u: Unit, d: Int): List<Int> = u.cells.filter { cells[it] == 0 && has(cand[it], d) }

        fun holds(u: Unit, d: Int): Boolean = u.cells.any { cells[it] == d }
    }

    private fun sightOf(cells: List<Int>): Sight = Sight(cells, IntArray(81) { i ->
        if (cells[i] != 0) 0
        else {
            var mask = 0b11_1111_1110
            for (p in Sudoku.peers(i)) if (cells[p] != 0) mask = mask and bit(cells[p]).inv()
            mask
        }
    })

    // ---- the whole hint: mistakes, then reasoning, then the honest fallback -----------------------

    /**
     * The next thing to show this player, or null on a solved board. Mistakes come first: every step
     * reasoned from a board with a wrong digit on it could be reasoning from a falsehood.
     */
    fun teach(s: SudokuState): PlacementStep? {
        if (s.solved) return null
        mistake(s)?.let { return it }
        return deduce(s.cells) ?: fallback(s)
    }

    private fun mistake(s: SudokuState): PlacementStep? {
        val wrong = s.cells.indices.firstOrNull { s.cells[it] != 0 && s.cells[it] != s.solution[it] }
            ?: return null
        val d = s.cells[wrong]
        val clear = message("teacher.sudoku.mistake.1", "d" to (d))
        fun step(explanation: CharSequence, cited: Set<Int>) = PlacementStep(
            MISTAKE, wrong, 0, setOf(wrong), cited, message("teacher.sudoku.step.1", "d" to (d)), explanation,
        )

        // A clash the red digits already show.
        for (u in unitsOf(wrong)) {
            val twin = u.cells.firstOrNull { it != wrong && s.cells[it] == d } ?: continue
            return step(message("teacher.sudoku.mistake.2", "d" to (d), "u_name" to (u.name), "clear" to (clear)), setOf(twin))
        }

        // Otherwise, reason from the correct digits plus this one: does it leave somewhere empty?
        val seen = s.cells.mapIndexed { i, v -> if (v != 0 && v != s.solution[i] && i != wrong) 0 else v }
        val sight = sightOf(seen)
        for (p in Sudoku.peers(wrong)) {
            if (seen[p] == 0 && sight.cand[p] == 0) {
                val shared = unitsOf(p).first { wrong in it.cells }
                return step(
                    message("teacher.sudoku.mistake.3", "d" to (d), "shared_name" to (shared.name), "clear" to (clear)),
                    setOf(p),
                )
            }
        }
        for (u in units) {
            if (u.cells.none { it in Sudoku.peers(wrong) }) continue
            for (e in 1..9) {
                if (sight.holds(u, e) || sight.places(u, e).isNotEmpty()) continue
                return step(
                    message("teacher.sudoku.mistake.4", "d" to (d), "u_name" to (u.name), "e" to (e), "clear" to (clear)),
                    u.cells.toSet(),
                )
            }
        }
        return step(message("teacher.sudoku.mistake.5", "d" to (d), "clear" to (clear)), emptySet())
    }

    /**
     * Nothing short enough to explain applies, so point at a digit from the answer — in the empty
     * cell with the fewest candidates, which is where the player was most likely stuck. Says plainly
     * that it is doing this.
     */
    private fun fallback(s: SudokuState): PlacementStep? = longerChain(s.cells, sightOf(s.cells).cand, s.solution)

    /** [fallback]'s step (Cages' too): [solution]'s digit in the empty square of [cells] with the fewest of [cand]. */
    internal fun longerChain(cells: List<Int>, cand: IntArray, solution: List<Int>): PlacementStep? {
        val cell = cells.indices.filter { cells[it] == 0 }.minByOrNull { cand[it].countOneBits() } ?: return null
        val d = solution[cell]
        return PlacementStep(
            FALLBACK, cell, d,
            focus = setOf(cell),
            cited = emptySet(),
            nudge = message("teacher.sudoku.longer_chain.1"),
            explanation = message("teacher.sudoku.longer_chain.2", "d" to (d)),
        )
    }

    // ---- reasoning from what the player can see ----------------------------------------------------

    /**
     * The simplest step visible on this board, or null when none of the techniques applies.
     *
     * Takes no answer on purpose — see the class comment. [cells] is 81 digits, 0 for empty; the
     * caller has already dealt with any that are mistakes.
     */
    fun deduce(cells: List<Int>, ceiling: String = WHAT_IF): PlacementStep? {
        val sight = sightOf(cells)
        val top = TECHNIQUES.indexOf(ceiling)
        return fullHouse(sight)
            ?: hiddenSingle(sight, Kind.BOX)
            ?: hiddenSingle(sight, null)
            ?: (if (top >= TECHNIQUES.indexOf(NAKED_SINGLE)) nakedSingle(sight) else null)
            ?: (if (top >= TECHNIQUES.indexOf(LOCKED)) withElimination(sight) else null)
            ?: (if (top >= TECHNIQUES.indexOf(WHAT_IF)) whatIf(sight) else null)
    }

    /**
     * How deep a walk by [deduce] goes to finish [cells] from the start, applying every step: the index in [TECHNIQUES]
     * of the deepest technique it used (-1 for a full grid), or null when it stops short, so the board needs more than
     * [ceiling] (the generator's gate, `Sudoku.generateVerified`). [ceiling] is [HIDDEN_SINGLE_LINE] (singles in a unit),
     * [NAKED_SINGLE] or [WHAT_IF] (everything); the eliminations come and go together, so no ceiling falls between.
     */
    fun depth(cells: List<Int>, ceiling: String = WHAT_IF): Int? {
        val grid = cells.toMutableList()
        var deepest = -1
        while (0 in grid) {
            val step = deduce(grid, ceiling) ?: return null
            deepest = maxOf(deepest, TECHNIQUES.indexOf(step.technique))
            grid[step.cell] = step.digit
        }
        return deepest
    }

    private fun fullHouse(s: Sight): PlacementStep? {
        for (u in units) {
            val empty = u.cells.filter { s.cells[it] == 0 }
            if (empty.size != 1) continue
            val d = (1..9).first { !s.holds(u, it) }
            return PlacementStep(
                FULL_HOUSE, empty[0], d,
                focus = u.cells.toSet(),
                cited = u.cells.toSet(),
                nudge = message("teacher.sudoku.full_house.1", "u_name" to (u.name)),
                explanation = message("teacher.sudoku.full_house.2", "u_name_cap_" to (u.name.cap()), "d" to (d)),
            )
        }
        return null
    }

    private class Hidden(val unit: Unit, val cell: Int, val digit: Int)

    /** The first digit with one place in a unit: with [kind] BOX, boxes only; with null, rows and columns. */
    private fun findHidden(s: Sight, kind: Kind?): Hidden? {
        for (u in units) {
            if ((kind == Kind.BOX) != (u.kind == Kind.BOX)) continue
            for (d in 1..9) {
                if (s.holds(u, d)) continue
                val places = s.places(u, d)
                if (places.size == 1) return Hidden(u, places[0], d)
            }
        }
        return null
    }

    /** The first empty cell, in reading order, with one candidate left. */
    private fun findNaked(s: Sight): Int? = (0 until 81).firstOrNull { s.cells[it] == 0 && s.cand[it].countOneBits() == 1 }

    private fun hiddenSingle(s: Sight, kind: Kind?): PlacementStep? = findHidden(s, kind)?.let { h ->
        val u = h.unit
        val d = h.digit
        PlacementStep(
            if (u.kind == Kind.BOX) HIDDEN_SINGLE_BOX else HIDDEN_SINGLE_LINE, h.cell, d,
            focus = u.cells.toSet(),
            cited = u.cells.toSet() + blockers(s, u, d, h.cell),
            nudge = message("teacher.sudoku.hidden_single.1", "u_name" to (u.name)),
            explanation = message("teacher.sudoku.hidden_single.2", "u_name" to (u.name), "d" to (d)),
        )
    }

    /** The visible [d]s that rule [d] out of the other empty cells of [u], one per cell, lowest first. */
    private fun blockers(s: Sight, u: Unit, d: Int, except: Int): Set<Int> =
        u.cells.filter { it != except && s.cells[it] == 0 }
            .mapNotNull { c -> Sudoku.peers(c).firstOrNull { s.cells[it] == d } }
            .sorted()
            .toSet()

    private fun nakedSingle(s: Sight): PlacementStep? = findNaked(s)?.let { cell ->
        val d = digitsOf(s.cand[cell])[0]
        PlacementStep(
            NAKED_SINGLE, cell, d,
            focus = setOf(cell),
            cited = Sudoku.peers(cell).filter { s.cells[it] != 0 }.toSet(),
            nudge = message("teacher.sudoku.naked_single.1"),
            explanation = message("teacher.sudoku.naked_single.2", "d" to (d)),
        )
    }

    // ---- eliminations, each carried through to the single it makes ---------------------------------

    /** Some candidates struck off, and the sentence that justifies it. */
    private class Elimination(
        val technique: String,
        val removed: List<Pair<Int, Int>>,
        val pattern: Set<Int>,
        val where: Set<Int>,
        val nudge: CharSequence,
        val reason: Message,
    )

    private fun eliminations(s: Sight): Sequence<Elimination> = sequence {
        yieldAll(locked(s))
        yieldAll(nakedPairs(s))
        yieldAll(hiddenPairs(s))
    }

    /** Pointing, then claiming. */
    private fun locked(s: Sight): Sequence<Elimination> = sequence {
        for (box in units.filter { it.kind == Kind.BOX }) {
            for (d in 1..9) {
                val places = s.places(box, d)
                if (places.size < 2) continue
                for (lineKind in listOf(Kind.ROW, Kind.COLUMN)) {
                    val lines = places.map { lineOf(lineKind, it) }.distinct()
                    if (lines.size != 1) continue
                    val line = units[(if (lineKind == Kind.ROW) 9 else 18) + lines[0]]
                    val removed = s.places(line, d).filter { it !in box.cells }.map { it to d }
                    if (removed.isEmpty()) continue
                    yield(
                        Elimination(
                            LOCKED, removed, places.toSet(), box.cells.toSet() + line.cells,
                            nudge = message("teacher.sudoku.locked.1", "box_name" to (box.name), "line_name" to (line.name)),
                            reason = message("teacher.sudoku.locked.2", "box_name" to (box.name), "d" to (d), "line_name" to (line.name), "value4" to (line.name.substringBefore(' '))),
                        )
                    )
                }
            }
        }
        for (line in units.filter { it.kind != Kind.BOX }) {
            for (d in 1..9) {
                val places = s.places(line, d)
                if (places.size < 2) continue
                val boxes = places.map(::boxOf).distinct()
                if (boxes.size != 1) continue
                val box = units[boxes[0]]
                val removed = s.places(box, d).filter { it !in line.cells }.map { it to d }
                if (removed.isEmpty()) continue
                yield(
                    Elimination(
                        LOCKED, removed, places.toSet(), line.cells.toSet() + box.cells,
                        nudge = message("teacher.sudoku.locked.3", "line_name" to (line.name), "box_name" to (box.name)),
                        reason = message("teacher.sudoku.locked.4", "line_name" to (line.name), "d" to (d), "box_name" to (box.name)),
                    )
                )
            }
        }
    }

    private fun nakedPairs(s: Sight): Sequence<Elimination> = sequence {
        for (u in units) {
            val empty = u.cells.filter { s.cells[it] == 0 }
            for (i in empty.indices) for (j in i + 1 until empty.size) {
                val a = empty[i]
                val b = empty[j]
                val mask = s.cand[a]
                if (mask.countOneBits() != 2 || s.cand[b] != mask) continue
                val (x, y) = digitsOf(mask)
                val removed = empty.filter { it != a && it != b }
                    .flatMap { c -> listOf(x, y).filter { has(s.cand[c], it) }.map { c to it } }
                if (removed.isEmpty()) continue
                yield(
                    Elimination(
                        NAKED_PAIR, removed, setOf(a, b), u.cells.toSet(),
                        nudge = message("teacher.sudoku.naked_pairs.1", "u_name" to (u.name)),
                        reason = message("teacher.sudoku.naked_pairs.2", "u_name" to (u.name), "x" to (x), "y" to (y)),
                    )
                )
            }
        }
    }

    private fun hiddenPairs(s: Sight): Sequence<Elimination> = sequence {
        for (u in units) {
            val placesOf = (1..9).associateWith { d -> if (s.holds(u, d)) emptyList() else s.places(u, d) }
            for (x in 1..9) for (y in x + 1..9) {
                val px = placesOf.getValue(x)
                if (px.size != 2 || placesOf.getValue(y) != px) continue
                val removed = px.flatMap { c ->
                    digitsOf(s.cand[c]).filter { it != x && it != y }.map { c to it }
                }
                if (removed.isEmpty()) continue
                yield(
                    Elimination(
                        HIDDEN_PAIR, removed, px.toSet(), u.cells.toSet(),
                        nudge = message("teacher.sudoku.hidden_pairs.1", "u_name" to (u.name)),
                        reason = message("teacher.sudoku.hidden_pairs.2", "u_name" to (u.name), "x" to (x), "y" to (y)),
                    )
                )
            }
        }
    }

    /**
     * A single that exists only because of [removed]: a cell those eliminations leave with one digit,
     * or a digit they leave with one place in a unit. Returned as the step's closing sentence.
     */
    private class Single(val cell: Int, val digit: Int, val unit: Unit?) {
        fun sentence(): Message =
            if (unit == null) message("teacher.sudoku.sentence.1", "digit" to (digit))
            else message("teacher.sudoku.sentence.2", "digit" to (digit), "unit_name" to (unit.name))
    }

    private fun singleAfter(after: Sight, removed: List<Pair<Int, Int>>): Single? {
        // Hidden singles first: the digit just struck off is the one the player is following.
        for ((cell, d) in removed) {
            for (u in unitsOf(cell)) {
                if (after.holds(u, d)) continue
                val places = after.places(u, d)
                if (places.size == 1) return Single(places[0], d, u)
            }
        }
        for (cell in removed.map { it.first }.distinct().sorted()) {
            if (after.cand[cell].countOneBits() == 1) return Single(cell, digitsOf(after.cand[cell])[0], null)
        }
        return null
    }

    private fun withElimination(s: Sight): PlacementStep? {
        for (e in eliminations(s)) {
            val after = s.copyWithout(e.removed)
            val single = singleAfter(after, e.removed) ?: continue
            val text = message("teacher.sudoku.with_elimination.extra.1", "e_reason" to (e.reason), "single_sentence_" to (single.sentence()))
            if (text.length > MAX_EXPLANATION) continue
            return PlacementStep(
                e.technique, single.cell, single.digit,
                focus = e.where,
                cited = e.pattern + (single.unit?.cells.orEmpty()),
                nudge = e.nudge,
                explanation = text,
            )
        }
        return null
    }

    // ---- what if: a short chain of forced digits that ends in a contradiction ----------------------

    /** How a supposed digit breaks the board: a cell with no digit left, or a unit with no place for one. */
    private class Contradiction(val cell: Int?, val unit: Unit?, val digit: Int) {
        fun phrase(afterForced: Boolean): Message =
            if (cell != null) message("teacher.sudoku.phrase.1", "value1" to (if (afterForced) message("teacher.sudoku.phrase.extra.1") else message("teacher.sudoku.phrase.extra.2")))
            else message("teacher.sudoku.phrase.2", "unit_name" to (unit!!.name), "digit" to (digit))

        fun cells(): Set<Int> = if (cell != null) setOf(cell) else unit!!.cells.toSet()
    }

    /** The digits a supposition forces, in order, and where it breaks. */
    private class Chain(val forced: List<Pair<Int, Int>>, val broken: Contradiction)

    /*
     * The chain is the hot loop of a hint and of the generator's dig (every what-if tried on every board it digs), so it
     * keeps the digits and candidates in arrays, strikes a placed digit from its peers in place, and allocates nothing
     * per unit. The candidates it keeps are exactly [sightOf] of the digits so far: [whatIf] is handed a fresh sight
     * with nothing struck off. A filled cell's mask is 0, so a held digit is never counted as a place.
     */

    /** Where [cells] breaks: the first empty cell with no digit left, else the first unit with no place for a digit. */
    private fun contradiction(cells: IntArray, cand: IntArray): Contradiction? {
        for (c in 0 until 81) if (cells[c] == 0 && cand[c] == 0) return Contradiction(c, null, 0)
        for (u in units) {
            var held = 0
            var free = 0
            for (c in u.cells) if (cells[c] != 0) held = held or bit(cells[c]) else free = free or cand[c]
            for (d in 1..9) if (!has(held or free, d)) return Contradiction(null, u, d)
        }
        return null
    }

    /** A single on [cells]: hidden in a box, hidden in a line, then naked. The same order [deduce] uses. */
    private fun anySingle(cells: IntArray, cand: IntArray): Pair<Int, Int>? {
        for (boxFirst in listOf(true, false)) for (u in units) {
            if ((u.kind == Kind.BOX) != boxFirst) continue
            var once = 0
            var twice = 0
            for (c in u.cells) {
                twice = twice or (once and cand[c])
                once = once or cand[c]
            }
            val single = once and twice.inv()
            if (single == 0) continue
            val d = single.countTrailingZeroBits()
            return u.cells.first { has(cand[it], d) } to d
        }
        for (c in 0 until 81) if (cells[c] == 0 && cand[c].countOneBits() == 1) return c to cand[c].countTrailingZeroBits()
        return null
    }

    /** With [cell] supposed to be [digit], the forced singles up to [MAX_CHAIN] and where they break. */
    private fun chainFrom(s: Sight, cell: Int, digit: Int): Chain? {
        val cells = s.cells.toIntArray()
        val cand = s.cand.copyOf()
        fun place(c: Int, d: Int) {
            cells[c] = d
            cand[c] = 0
            for (p in Sudoku.peers(c)) cand[p] = cand[p] and bit(d).inv()
        }
        place(cell, digit)
        val forced = mutableListOf<Pair<Int, Int>>()
        while (true) {
            contradiction(cells, cand)?.let { return Chain(forced.toList(), it) }
            if (forced.size == MAX_CHAIN) return null
            val (c, d) = anySingle(cells, cand) ?: return null
            forced += c to d
            place(c, d)
        }
    }

    private fun whatIf(s: Sight): PlacementStep? {
        var best: PlacementStep? = null
        var bestLength = Int.MAX_VALUE
        fun consider(target: Int, digit: Int, supposed: Int, supposedDigit: Int, lead: Message, close: Message) {
            if (bestLength == 0) return // nothing can be shorter
            val chain = chainFrom(s, supposed, supposedDigit) ?: return
            if (chain.forced.size >= bestLength) return
            val digits = chain.forced.mapIndexed { i, (_, d) ->
                if (chain.forced.take(i).any { it.second == d }) message("teacher.sudoku.consider.1", "d" to (d)) else message("teacher.sudoku.consider.2", "d" to (d))
            }
            val middle = when (digits.size) {
                0 -> com.joebywan.daybook.core.i18n.literalMessage("")
                1 -> message("teacher.sudoku.consider.3", "digits_0_" to (digits[0]))
                else -> message("teacher.sudoku.consider.4", "value1" to (digits.joinedMessages(message("teacher.sudoku.consider.extra.1"))))
            }
            val text = message("teacher.sudoku.consider.extra.2", "lead" to (lead), "middle" to (middle), "value3" to (chain.broken.phrase(digits.isNotEmpty())), "close" to (close))
            if (text.length > MAX_EXPLANATION) return
            bestLength = chain.forced.size
            best = PlacementStep(
                WHAT_IF, target, digit,
                focus = setOf(target, supposed),
                cited = chain.forced.map { it.first }.toSet() + chain.broken.cells() + supposed,
                nudge = if (supposed == target) message("teacher.sudoku.consider.5", "supposedDigit" to (supposedDigit))
                else message("teacher.sudoku.consider.6", "digit" to (digit)),
                explanation = text,
            )
        }
        // A cell with two digits left: suppose one, and it breaks, so it's the other.
        for (c in 0 until 81) {
            if (s.cells[c] != 0 || s.cand[c].countOneBits() != 2) continue
            val (a, b) = digitsOf(s.cand[c])
            consider(c, b, c, a, message("teacher.sudoku.what_if.1", "a" to (a)), message("teacher.sudoku.what_if.2", "a" to (a), "b" to (b)))
            consider(c, a, c, b, message("teacher.sudoku.what_if.3", "b" to (b)), message("teacher.sudoku.what_if.4", "b" to (b), "a" to (a)))
        }
        // A digit with two places left in a unit: suppose one, and it breaks, so it's the other.
        for (u in units) for (d in 1..9) {
            if (s.holds(u, d)) continue
            val places = s.places(u, d)
            if (places.size != 2) continue
            val (p, q) = places
            consider(p, d, q, d, message("teacher.sudoku.what_if.5", "u_name_cap_" to (u.name.cap()), "d" to (d)), message("teacher.sudoku.what_if.6", "d" to (d)))
            consider(q, d, p, d, message("teacher.sudoku.what_if.7", "u_name_cap_" to (u.name.cap()), "d" to (d)), message("teacher.sudoku.what_if.8", "d" to (d)))
        }
        return best
    }

    // ---- small helpers -----------------------------------------------------------------------------

    private fun lineOf(kind: Kind, cell: Int) = if (kind == Kind.ROW) cell / 9 else cell % 9
}
