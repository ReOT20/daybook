package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.TeachStep
import com.joebywan.daybook.core.asMessage
import com.joebywan.daybook.core.cap
import com.joebywan.daybook.core.forEachBit
import com.joebywan.daybook.core.i18n.Message
import com.joebywan.daybook.core.i18n.message

/**
 * Mosaic, taught one fill at a time.
 *
 * Mosaic is a planning puzzle, not a deduction puzzle: nothing on the board is hidden, there is no
 * stored answer, and every fill is legal. What can go wrong is running out, and because the limit
 * is the proven optimum with nothing to spare, one fill off an optimal line loses. So the teacher's
 * one hard rule is **never teach a fill the search has not proved still finishes inside the
 * limit**. Its reasons are then chosen from what the player can see and say out loud, simplest
 * first:
 *
 * 1. [FINISH] — one fill turns the whole board one colour.
 * 2. [COUNT] — as many fills left as colours to wipe out. A fill can only wipe a colour out by
 *    covering its last patch, so every fill must do exactly that. At zero slack this is very often
 *    what the end of a board comes down to.
 * 3. [BIGGEST] — the fill that swallows the most neighbouring areas at once, when it is on an
 *    optimal line. (It is not always: the obvious fill loses a good share of Expert boards, and the
 *    hint says so when it does.)
 * 4. [CENTRE] — grow the most central area: the one with every other area fewest steps away.
 * 5. [KEEPS] — no plain reason singles a fill out. The hint says exactly that, and offers a fill
 *    the search proved keeps the board finishable. This is the honest fallback, counted as such.
 *
 * [deduce] takes the board as the player sees it — sizes, cells, colours and fills left — and
 * nothing else; there is no answer to take. [teach] adds the one thing it needs on top: the
 * player's own fills ([MosaicState.trail]), to say which fill lost the board and how far back
 * Undo has to go.
 *
 * Free of `java.*` and never ordered by a hash: every loop walks areas, colours or fills in index
 * order, so a board gets the same hint on every platform.
 */
internal object MosaicTeacher {

    const val FINISH = "finish"
    const val COUNT = "count"
    const val BIGGEST = "biggest-swallow"
    const val CENTRE = "centre"
    const val KEEPS = "keeps-in-limit"
    const val MISTAKE = "mistake"

    /** Every technique in the order [deduce] tries them, for reports. [KEEPS] is the fallback. */
    val TECHNIQUES = listOf(FINISH, COUNT, BIGGEST, CENTRE, KEEPS)

    /**
     * Search nodes one hint may spend, across all its questions. Generous next to
     * [Mosaic.SOLVE_BUDGET] because a hint asks several questions of one board — is it still
     * winnable, and does each candidate keep it so — though they share one table, so the later
     * questions are mostly answered by the earlier ones. A board that runs this out gets no hint
     * rather than a guess, the same rule [Mosaic.solve] keeps.
     */
    const val BUDGET = 1_500_000

    /** Names for [Mosaic.palette], index for index. */
    val colourNames = listOf(message("teacher.mosaic.colour_names.1"), message("teacher.mosaic.colour_names.2"), message("teacher.mosaic.colour_names.3"), message("teacher.mosaic.colour_names.4"), message("teacher.mosaic.colour_names.5"))

    /**
     * One step: a fill to make ([cell] and [colour]), or — for a [MISTAKE] — a board to go back to.
     *
     * [rewind] is the board as it stood at the last point it could still be finished, when the
     * player's fills are on record; [rewindTo] is how many fills had been used there.
     */
    class Step(
        override val technique: String,
        val cell: Int = -1,
        val colour: Int = -1,
        override val focus: Set<Int>,
        override val cited: Set<Int>,
        override val targets: Set<Int>,
        nudge: CharSequence,
        explanation: CharSequence,
        val rewind: MosaicState? = null,
        val rewindTo: Int = -1,
    ) : TeachStep {

        override val nudgeMessage: Message = nudge.asMessage()
        override val explanationMessage: Message = explanation.asMessage()
        override val nudge: String get() = nudgeMessage.english
        override val explanation: String get() = explanationMessage.english
    }

    // ---- the board as areas --------------------------------------------------------------------

    /**
     * The board's single-colour areas, labelled in cell order, with who touches whom.
     *
     * Capped at [MAX_AREAS] so a group's membership and its colour pack into one Long for the
     * search's table; generated boards run to two dozen areas and a fill never adds one.
     */
    class Areas private constructor(
        val width: Int,
        val cells: List<Int>,
        val label: IntArray,
        /** The board as a search position; [Mosaic.Position.play] copies, so this one is never changed. */
        val root: Mosaic.Position,
    ) {
        val count get() = root.count
        val nbr get() = root.nbr
        val hue get() = root.hue

        fun cellsOf(area: Int): Set<Int> = cells.indices.filter { label[it] == area }.toSet()

        fun cellsOf(areas: Long): Set<Int> = cells.indices.filter { (areas shr label[it]) and 1L != 0L }.toSet()

        /** The first cell of [area] in reading order, which is what a fill is recorded against. */
        fun firstCell(area: Int): Int = cells.indices.first { label[it] == area }

        companion object {
            const val MAX_AREAS = 61

            fun of(width: Int, height: Int, cells: List<Int>): Areas? {
                val (label, root) = Mosaic.areas(width, height, cells, MAX_AREAS) ?: return null
                return Areas(width, cells, label, root)
            }
        }
    }

    /** An exact table key: every live group's membership with its colour packed on top, sorted. */
    private class Key(val v: LongArray) {
        override fun equals(other: Any?) = other is Key && v.contentEquals(other.v)
        override fun hashCode() = v.contentHashCode()
    }

    // ---- the search ----------------------------------------------------------------------------

    /**
     * "Can this position still be finished in r fills?", answered exactly or not at all.
     *
     * Depth-first under the same admissible bound [Mosaic]'s generator uses — colours left minus
     * one, and half the spread of the area graph — with a table of refuted and proven positions
     * shared across every question one hint asks. Once [exhausted] is set, no answer it gave is to
     * be trusted, and the caller says nothing rather than guess.
     */
    class Search(val colours: Int, private val budget: Int = BUDGET) {
        var exhausted = false
            private set
        var spent = 0
            private set
        private val failedAt = HashMap<Key, Int>()
        private val wonAt = HashMap<Key, Int>()

        private val bounds = Mosaic.Bound(64)

        fun gain(p: Mosaic.Position, group: Int, colour: Int): Int = p.eaten(group, colour).countOneBits()

        /** How many steps the farthest live group is from [from]. */
        fun eccentricity(p: Mosaic.Position, from: Int): Int = bounds.sweep(p, from).toInt()

        private fun key(p: Mosaic.Position): Key {
            val v = LongArray(p.count)
            var k = 0
            p.alive.forEachBit { j -> v[k++] = p.member[j] or (p.hue[j].toLong() shl 61) }
            v.sort()
            return Key(v)
        }

        /** Every fill in [p], encoded `group * 8 + colour`, most swallowing first, then index order. */
        fun moves(p: Mosaic.Position): List<Int> {
            val out = ArrayList<Int>()
            p.alive.forEachBit { g -> for (c in 0 until colours) if (c != p.hue[g]) out += g * 8 + c }
            return out.sortedWith(compareByDescending<Int> { gain(p, it / 8, it % 8) }.thenBy { it })
        }

        /** Whether [p] can be finished in at most [r] fills. Meaningless once [exhausted]. */
        fun canFinish(p: Mosaic.Position, r: Int): Boolean {
            if (p.count <= 1) return true
            if (r <= 0 || exhausted) return false
            if (bounds.of(p) > r) return false
            val key = key(p)
            if ((failedAt[key] ?: -1) >= r) return false
            if ((wonAt[key] ?: Int.MAX_VALUE) <= r) return true
            if (++spent > budget) {
                exhausted = true
                return false
            }
            for (m in moves(p)) {
                if (canFinish(p.play(m / 8, m % 8), r - 1)) {
                    wonAt[key] = r
                    return true
                }
                if (exhausted) return false
            }
            failedAt[key] = r
            return false
        }

        /** Whether filling [group] with [colour] leaves [p] finishable in the [r] - 1 fills after it. */
        fun keeps(p: Mosaic.Position, group: Int, colour: Int, r: Int): Boolean = canFinish(p.play(group, colour), r - 1)

        /** The fewest fills that finish [p], or null past [cap] or the budget. */
        fun optimum(p: Mosaic.Position, cap: Int): Int? {
            for (r in bounds.of(p)..cap) {
                if (canFinish(p, r)) return r
                if (exhausted) return null
            }
            return null
        }
    }

    // ---- the whole hint: a lost board first, then a reason, then the honest fallback -------------

    /**
     * The next thing to show on [s], or null on a finished board or one the search could not
     * decide inside [BUDGET].
     *
     * A board that can no longer be finished comes first. Teaching a good fill on a lost board would
     * be teaching a move that cannot matter; what the player needs is where it slipped.
     */
    fun teach(s: MosaicState): Step? {
        if (s.solved) return null
        val areas = Areas.of(s.width, s.height, s.cells) ?: return null
        val search = Search(s.colours)
        val left = s.limit - s.moves
        val alive = left > 0 && search.canFinish(areas.root, left)
        if (search.exhausted) return null
        if (!alive) return lost(s, areas, search)
        return deduce(areas, s.colours, left, search)
    }

    /**
     * The simplest fill on this board that is still on an optimal line, with the reason for it.
     *
     * Everything it takes is on the player's screen: the board, the palette size and the count of
     * fills left. Null when the board is finished, already lost, or too big to decide.
     */
    fun deduce(width: Int, height: Int, colours: Int, cells: List<Int>, fillsLeft: Int): Step? {
        val areas = Areas.of(width, height, cells) ?: return null
        if (areas.count <= 1 || fillsLeft <= 0) return null
        val search = Search(colours)
        if (!search.canFinish(areas.root, fillsLeft)) return null
        return deduce(areas, colours, fillsLeft, search)
    }

    /**
     * [technique]'s explanation of one particular fill, [colour] into the area holding [cell], or null when that fill is
     * not an instance of it here. Training's verdict on a right fill that is not the teacher's own pick.
     */
    fun explainFill(width: Int, height: Int, colours: Int, cells: List<Int>, fillsLeft: Int, technique: String, cell: Int, colour: Int): Step? {
        val a = Areas.of(width, height, cells) ?: return null
        val search = Search(colours)
        val only = a.label[cell] * 8 + colour
        val step = when (technique) {
            COUNT -> count(a, a.root, colours, fillsLeft, search, only)
            BIGGEST -> biggest(a, a.root, colours, fillsLeft, search, only)
            CENTRE -> centre(a, a.root, colours, fillsLeft, search, only)
            else -> null
        }
        return if (search.exhausted) null else step
    }

    private fun deduce(a: Areas, colours: Int, r: Int, search: Search): Step? {
        val root = a.root
        val step = finish(a, root)
            ?: count(a, root, colours, r, search)
            ?: biggest(a, root, colours, r, search)
            ?: centre(a, root, colours, r, search)
            ?: keeps(a, root, r, search)
        return if (search.exhausted) null else step
    }

    private fun name(colour: Int) = colourNames.getOrElse(colour) { message("teacher.mosaic.name.1", "colour_1" to (colour + 1)) }

    private fun fill(
        a: Areas,
        technique: String,
        group: Int,
        colour: Int,
        focus: Set<Int>,
        cited: Set<Int>,
        nudge: Message,
        explanation: Message,
    ) = Step(
        technique = technique,
        cell = a.firstCell(group),
        colour = colour,
        focus = focus,
        cited = cited,
        targets = a.cellsOf(group),
        nudge = nudge,
        explanation = explanation,
    )

    /** The neighbours of [group] in [colour], as cells: what the fill would swallow. */
    private fun swallowed(a: Areas, group: Int, colour: Int): Set<Int> {
        return a.cellsOf(a.root.eaten(group, colour))
    }

    /** Why this colour for this area, in a few words, and never claiming more than is true. */
    private fun colourReason(search: Search, root: Mosaic.Position, colours: Int, group: Int, colour: Int): Message {
        val g = search.gain(root, group, colour)
        val best = (0 until colours).filter { it != root.hue[group] }.maxOf { search.gain(root, group, it) }
        return when {
            g > 0 && g == best && g == 1 -> message("teacher.mosaic.colour_reason.1", "name_colour_" to (name(colour)))
            g > 0 && g == best -> message("teacher.mosaic.colour_reason.2")
            else -> message("teacher.mosaic.colour_reason.3")
        }
    }

    private fun finish(a: Areas, root: Mosaic.Position): Step? {
        for (g in 0 until a.count) {
            if (root.nbr[g] != root.alive and (1L shl g).inv()) continue
            val others = (0 until a.count).filter { it != g }.map { a.hue[it] }.distinct()
            if (others.size != 1) continue
            val c = others[0]
            return fill(
                a, FINISH, g, c,
                focus = a.cellsOf(g),
                cited = swallowed(a, g, c),
                nudge = message("teacher.mosaic.finish.1"),
                explanation = message("teacher.mosaic.finish.2", "name_c_" to (name(c))),
            )
        }
        return null
    }

    /** [only] (`group * 8 + colour`), when set, restricts a technique to that one fill ([explainFill]). */
    private fun count(a: Areas, root: Mosaic.Position, colours: Int, r: Int, search: Search, only: Int = -1): Step? {
        val k = root.colours()
        if (k < 3 || r != k - 1) return null
        val lone = (0 until a.count).filter { g -> (0 until a.count).count { a.hue[it] == a.hue[g] } == 1 }
        val present = (0 until colours).filter { c -> a.hue.any { it == c } }
        val candidates = lone.flatMap { g -> present.filter { it != a.hue[g] }.map { g * 8 + it } }
            .filter { only < 0 || it == only }
            .sortedWith(compareByDescending<Int> { search.gain(root, it / 8, it % 8) }.thenBy { it })
        val move = candidates.firstOrNull { search.keeps(root, it / 8, it % 8, r) } ?: return null
        val g = move / 8
        val c = move % 8
        val which = if (lone.size == 1) {
            message("teacher.mosaic.count.1", "name_a_hue_g_" to (name(a.hue[g])), "name_c_" to (name(c)))
        } else {
            message("teacher.mosaic.count.2", "name_c_" to (name(c)), "name_a_hue_g_" to (name(a.hue[g])), "value3" to (colourReason(search, root, colours, g, c)))
        }
        return fill(
            a, COUNT, g, c,
            focus = lone.flatMap { a.cellsOf(it) }.toSet(),
            cited = swallowed(a, g, c),
            nudge = message("teacher.mosaic.count.3", "fills_r_" to (fills(r)), "k" to (k)),
            explanation = message("teacher.mosaic.count.4", "fills_r_cap_" to (fills(r).cap()), "k_1" to (k - 1), "which" to (which)),
        )
    }

    private fun maxGain(search: Search, root: Mosaic.Position, colours: Int): Int =
        (0 until root.count).maxOf { g ->
            (0 until colours).filter { it != root.hue[g] }.maxOfOrNull { search.gain(root, g, it) } ?: 0
        }

    private fun biggest(a: Areas, root: Mosaic.Position, colours: Int, r: Int, search: Search, only: Int = -1): Step? {
        val mx = maxGain(search, root, colours)
        if (mx < 2) return null
        for (g in 0 until a.count) {
            for (c in 0 until colours) {
                if (c == a.hue[g] || (only >= 0 && g * 8 + c != only) || search.gain(root, g, c) != mx) continue
                if (!search.keeps(root, g, c, r)) continue
                var near = a.cellsOf(g)
                a.nbr[g].forEachBit { j -> near = near + a.cellsOf(j) }
                return fill(
                    a, BIGGEST, g, c,
                    focus = near,
                    cited = swallowed(a, g, c),
                    nudge = message("teacher.mosaic.biggest.1"),
                    explanation = message("teacher.mosaic.biggest.2", "name_c_" to (name(c)), "mx" to (mx)),
                )
            }
        }
        return null
    }

    /** Whether some fill swallows several areas and none of the fills that swallow the most is safe. */
    private fun biggestLoses(a: Areas, root: Mosaic.Position, colours: Int, r: Int, search: Search): Boolean {
        val mx = maxGain(search, root, colours)
        if (mx < 2) return false
        for (g in 0 until a.count) for (c in 0 until colours) {
            if (c != a.hue[g] && search.gain(root, g, c) == mx && search.keeps(root, g, c, r)) return false
        }
        return true
    }

    private fun centre(a: Areas, root: Mosaic.Position, colours: Int, r: Int, search: Search, only: Int = -1): Step? {
        if (a.count < 3) return null
        val ecc = IntArray(a.count) { g -> search.eccentricity(root, g) }
        val least = ecc.min()
        if (least == ecc.max()) return null
        for (g in 0 until a.count) {
            if (ecc[g] != least) continue
            val order = (0 until colours).filter { it != a.hue[g] && (only < 0 || g * 8 + it == only) }
                .sortedWith(compareByDescending<Int> { search.gain(root, g, it) }.thenBy { it })
            val c = order.firstOrNull { search.keeps(root, g, it, r) } ?: continue
            val warn = if (biggestLoses(a, root, colours, r, search)) message("teacher.mosaic.centre.1") else ""
            return fill(
                a, CENTRE, g, c,
                focus = a.cellsOf(g),
                cited = swallowed(a, g, c),
                nudge = message("teacher.mosaic.centre.2"),
                explanation = message("teacher.mosaic.centre.3", "least" to (least), "name_c_" to (name(c)), "value3" to (colourReason(search, root, colours, g, c)), "warn" to (warn)),
            )
        }
        return null
    }

    private fun keeps(a: Areas, root: Mosaic.Position, r: Int, search: Search): Step? {
        // Up to two keeping fills: the first to teach, the second only to know whether it is alone.
        // "The only fill that works" is a fact the player can use — everything else loses — even
        // where no rule of thumb explains why.
        val safe = search.moves(root).asSequence().filter { search.keeps(root, it / 8, it % 8, r) }.take(2).toList()
        val m = safe.firstOrNull() ?: return null
        val g = m / 8
        val c = m % 8
        val warn = if (biggestLoses(a, root, search.colours, r, search)) message("teacher.mosaic.keeps.1") else ""
        val claim = if (safe.size == 1) {
            message("teacher.mosaic.keeps.2", "name_c_" to (name(c)))
        } else {
            message("teacher.mosaic.keeps.3", "name_c_" to (name(c)))
        }
        return fill(
            a, KEEPS, g, c,
            focus = a.cellsOf(g),
            cited = swallowed(a, g, c),
            nudge = message("teacher.mosaic.keeps.4"),
            explanation = message("teacher.mosaic.keeps.5", "claim" to (claim), "warn" to (warn)),
        )
    }

    // ---- a board that can no longer be finished ----------------------------------------------------

    /** The board after the first [k] of [s]'s recorded fills, or null when they are not on record. */
    fun replay(s: MosaicState, k: Int): MosaicState? {
        if (s.start.size != s.cells.size || s.trail.size != s.moves) return null
        var b = MosaicState(s.width, s.height, s.colours, s.limit, s.start)
        for (f in s.trail.take(k)) b = b.flood(f / 8, f % 8)
        return b
    }

    private fun lost(s: MosaicState, a: Areas, search: Search): Step {
        val left = s.limit - s.moves
        val k = a.root.colours()
        val why = when {
            left <= 0 -> message("teacher.mosaic.lost.1")
            k - 1 > left -> message("teacher.mosaic.lost.2", "k" to (k), "fills_k_1_" to (fills(k - 1)), "left" to (left))
            else -> message("teacher.mosaic.lost.3", "fills_left_" to (fills(left)))
        }
        val nudge = if (left <= 0) message("teacher.mosaic.lost.4") else message("teacher.mosaic.lost.5")

        // The record is only trusted if it replays to the board on screen.
        val whole = replay(s, s.moves)
        val known = whole != null && whole.cells == s.cells
        var good = -1
        if (known) {
            for (j in s.moves - 1 downTo 0) {
                val b = replay(s, j)!!
                val bAreas = Areas.of(b.width, b.height, b.cells) ?: break
                val probe = Search(s.colours)
                val ok = probe.canFinish(bAreas.root, s.limit - j)
                if (probe.exhausted) break
                if (ok) {
                    good = j
                    break
                }
            }
        }
        if (good < 0) {
            return Step(
                technique = MISTAKE,
                focus = emptySet(),
                cited = emptySet(),
                targets = emptySet(),
                nudge = nudge,
                explanation = message("teacher.mosaic.lost.6", "why" to (why)),
            )
        }
        val at = replay(s, good)!!
        val slip = s.trail[good]
        val slipCells = at.area(slip / 8).toSet()
        val undo = s.moves - good
        val back = if (good == 0) {
            message("teacher.mosaic.lost.7", "name_slip_8_" to (name(slip % 8)))
        } else {
            message("teacher.mosaic.lost.8", "fills_undo_" to (fills(undo)), "good" to (good), "good_1" to (good + 1), "name_slip_8_" to (name(slip % 8)))
        }
        return Step(
            technique = MISTAKE,
            focus = slipCells,
            cited = emptySet(),
            targets = slipCells,
            nudge = nudge,
            explanation = message("teacher.mosaic.lost.extra.1", "why" to (why), "back" to (back)),
            rewind = at,
            rewindTo = good,
        )
    }

    // ---- words ----------------------------------------------------------------------------------

    private fun fills(n: Int) = message("teacher.mosaic.count.fill", "count" to (n))
}
