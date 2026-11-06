package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.asMessage
import com.joebywan.daybook.core.i18n.Message
import com.joebywan.daybook.core.i18n.message
import com.joebywan.daybook.core.joinedMessages
import com.joebywan.daybook.core.squareNameMessage

/**
 * Pearls, reasoned the way a person reasons it: a pearl at a time, a square at a time, then the rule
 * that the loop is one, and on Expert one supposition followed a few rounds.
 *
 * Every board Daybook deals is finished by exactly this ladder ([PearlsLogic.accepts]), so a step
 * always exists on a board whose marks are right, and there is no fallback to measure.
 *
 * [deduce] is handed the pearls and the player's edges and has no parameter through which the answer
 * could arrive. The answer is read in one place, [mistake], to decide which of the player's marks
 * cannot be right; sound because every shipped board has exactly one loop.
 *
 * A square with two lines takes no more: the teacher reads its other edges as crossed whether or not
 * the player has crossed them ([view]), so it never asks for that tidying.
 *
 * Techniques, simplest first:
 * 1. [BLACK_PEARL] — a black pearl turns and runs straight on through the next square each way: an
 *    arm it cannot draw sends it the other way, and a line out of it carries on.
 * 2. [WHITE_PEARL] — a white pearl goes straight through: a way it cannot go sends it the other.
 * 3. [WHITE_TURN] — a white pearl's loop turns on at least one side: straight on one side, a turn on
 *    the other.
 * 4. [ONE_WAY] — a square with one line and one way on: the line continues.
 * 5. [PEARL_WAYS] — a pearl with only two ways in left takes both.
 * 6. [DEAD_END] — an empty square with one way in can never be passed through: cross it.
 * 7. [SMALL_LOOP] — joining two ends of one line would close a loop that leaves something out.
 * 8. [WHAT_IF] — suppose a line (or a cross), follow the rules a few rounds, and something breaks.
 *
 * Within a technique the first square in reading order wins (edges in edge order for 7 and 8), so a
 * board gets the same hint on every platform.
 *
 * ## Highlight indices
 * Squares are `row * size + column`; edge `e` is `size * size + e` ([edgeIndex]).
 */
internal object PearlsTeacher {

    const val BLACK_PEARL = "black-pearl"
    const val WHITE_PEARL = "white-pearl"
    const val WHITE_TURN = "white-turn"
    const val ONE_WAY = "one-way"
    const val PEARL_WAYS = "pearl-ways"
    const val DEAD_END = "dead-end"
    const val SMALL_LOOP = "small-loop"
    const val WHAT_IF = "what-if"
    const val MISTAKE = "mistake"

    /** Every technique in the order [deduce] prefers them, for reports. */
    val TECHNIQUES = listOf(BLACK_PEARL, WHITE_PEARL, WHITE_TURN, ONE_WAY, PEARL_WAYS, DEAD_END, SMALL_LOOP, WHAT_IF)

    /** What the hint panel has room for (the test pins it). */
    const val MAX_NUDGE = 70
    const val MAX_EXPLANATION = 200

    /** One step: edges to draw, cross or clear, where to look, and why. [clears] is only ever a mistake. */
    class Step(
        val technique: String,
        val lines: List<Int> = emptyList(),
        val crosses: List<Int> = emptyList(),
        val clears: List<Int> = emptyList(),
        val focus: Set<Int>,
        val cited: Set<Int>,
        nudge: CharSequence,
        explanation: CharSequence,
    ) {
        val nudgeMessage: Message = nudge.asMessage()
        val explanationMessage: Message = explanation.asMessage()
        val nudge: String get() = nudgeMessage.english
        val explanation: String get() = explanationMessage.english


        /** Highlight indices of the edges the move changes. */
        fun targets(n: Int): Set<Int> = (lines + crosses + clears).map { edgeIndex(n, it) }.toSet()
    }

    fun edgeIndex(n: Int, e: Int) = n * n + e

    fun teach(s: PearlsState): Step? {
        if (s.solved) return null
        mistake(s)?.let { return it }
        return deduce(s.size, s.pearls, s.edges)
    }

    /**
     * The edges as the teacher reads them: the player's lines and crosses, and every open edge of a
     * square that already has two lines read as crossed.
     */
    fun view(n: Int, edges: String): IntArray {
        val g = PearlsGrid.of(n)
        val st = PearlsLogic.stateOf(edges)
        val lines = IntArray(g.squares)
        for (e in 0 until g.edgeCount) if (st[e] == PearlsLogic.LINE) {
            lines[g.endA[e]]++
            lines[g.endB[e]]++
        }
        for (e in 0 until g.edgeCount) {
            if (st[e] == PearlsLogic.OPEN && (lines[g.endA[e]] >= 2 || lines[g.endB[e]] >= 2)) st[e] = PearlsLogic.CROSS
        }
        return st
    }

    // ---- mistakes ---------------------------------------------------------------------------

    /** The first edge, in edge order, that the board's one loop disagrees with. */
    private fun mistake(s: PearlsState): Step? {
        val n = s.size
        val g = PearlsGrid.of(n)
        for (e in 0 until g.edgeCount) {
            val wrongLine = s.edges[e] == PearlsLogic.LINE_MARK && s.loop[e] == '0'
            val wrongCross = s.edges[e] == PearlsLogic.CROSS_MARK && s.loop[e] == '1'
            if (!wrongLine && !wrongCross) continue
            return Step(
                technique = MISTAKE,
                clears = listOf(e),
                focus = setOf(edgeIndex(n, e)),
                cited = setOf(g.endA[e], g.endB[e]),
                nudge = message("teacher.pearls.mistake.1"),
                explanation = if (wrongLine) {
                    message("teacher.pearls.mistake.2")
                } else {
                    message("teacher.pearls.mistake.3")
                },
            )
        }
        return null
    }

    // ---- reasoning --------------------------------------------------------------------------

    /**
     * The next step reasoned from the [pearls] and the player's [edges] alone, or null when nothing
     * can be settled (a finished board, or one with a wrong mark).
     */
    fun deduce(n: Int, pearls: String, edges: String): Step? {
        val g = PearlsGrid.of(n)
        val st = view(n, edges)
        val found = localFounds(g, pearls, st)
        if (found.any { it.contradiction }) return null
        // The simplest kind, the first square in reading order among them.
        found.minByOrNull { it.kind }?.let { return localStep(g, pearls, st, it) }
        PearlsLogic.smallLoop(g, pearls, st, firstOnly = true)?.let { f ->
            return if (f.contradiction) null else loopStep(g, st, f)
        }
        // A short what-if, as the generator's ladder uses. Its round cap is not monotone (a player's own order of moves
        // can leave a board whose short what-ifs are gone while longer ones remain), so when none is short enough the
        // shortest of any length is taken: still sound, still named, and it keeps the hints from running out.
        val w = PearlsLogic.bestWhatIf(g, pearls, st) ?: PearlsLogic.bestWhatIf(g, pearls, st, cap = Int.MAX_VALUE) ?: return null
        return whatIfStep(g, pearls, w)
    }

    /** Every local rule that settles something, square by square in reading order, pearl's rule first. */
    private fun localFounds(g: PearlsGrid, pearls: String, st: IntArray): List<PearlsLogic.Found> = buildList {
        for (s in 0 until g.squares) {
            when (pearls[s]) {
                PearlsLogic.BLACK -> PearlsLogic.blackRule(g, pearls, st, s)
                PearlsLogic.WHITE -> PearlsLogic.whiteRule(g, st, s)
                else -> null
            }?.let(::add)
            PearlsLogic.squareRule(g, pearls, st, s, full = false)?.let(::add)
        }
    }

    /**
     * Every step of [technique] reasoned from the [pearls] and [edges] alone, as [deduce] (Training accepts any of them):
     * each square's local rule of that kind, or each edge that would close a small loop. A what-if is only ever the one
     * [deduce] picks. Empty on a board with a contradiction.
     */
    fun steps(n: Int, pearls: String, edges: String, technique: String): List<Step> {
        val g = PearlsGrid.of(n)
        val st = view(n, edges)
        val found = localFounds(g, pearls, st)
        if (found.any { it.contradiction }) return emptyList()
        if (technique == SMALL_LOOP) {
            val f = PearlsLogic.smallLoop(g, pearls, st) ?: return emptyList()
            if (f.contradiction) return emptyList()
            return f.crosses.map { e -> loopStep(g, st, PearlsLogic.Found(f.kind, f.square, IntArray(0), intArrayOf(e))) }
        }
        return found.map { localStep(g, pearls, st, it) }.filter { it.technique == technique }
    }

    private fun localStep(g: PearlsGrid, pearls: String, st: IntArray, f: PearlsLogic.Found): Step {
        val n = g.n
        val s = f.square
        val edgesOf = (0 until 4).mapNotNull { d -> g.edge(s, d).takeIf { it >= 0 } }
        fun step(technique: String, nudge: Message, why: Message, focus: Set<Int>, cited: Set<Int> = focus) = Step(
            technique, f.lines.toList(), f.crosses.toList(), focus = focus, cited = cited, nudge = nudge, explanation = why,
        )
        val touched = (f.lines + f.crosses).map { edgeIndex(n, it) }.toSet()
        return when (f.kind) {
            PearlsLogic.K_BLACK -> {
                val why = (0 until 2).mapNotNull { axis -> blackAxis(g, pearls, st, s, axis) }.joinedMessages(" ")
                step(
                    BLACK_PEARL, message("teacher.pearls.local_step.1"),
                    message("teacher.pearls.local_step.2", "why" to (why)),
                    setOf(s) + touched,
                )
            }
            PearlsLogic.K_WHITE -> {
                val axis = if (f.lines.any { !g.isHorizontal(it) } || f.crosses.any { g.isHorizontal(it) }) 0 else 1
                val run = if (axis == 0) message("teacher.pearls.local_step.3") else message("teacher.pearls.local_step.4")
                val other = if (axis == 0) message("teacher.pearls.local_step.5") else message("teacher.pearls.local_step.6")
                val has = (0 until 2).any { d -> st[g.edge(s, axis + 2 * d)] == PearlsLogic.LINE }
                val reason = if (has) message("teacher.pearls.local_step.7", "run" to (run)) else message("teacher.pearls.local_step.8", "other" to (other), "value2" to (whiteReason(g, st, s, 1 - axis)))
                step(
                    WHITE_PEARL, message("teacher.pearls.local_step.9"),
                    message("teacher.pearls.local_step.10", "reason" to (reason), "run" to (run)),
                    setOf(s) + touched,
                )
            }
            PearlsLogic.K_WHITE_TURN -> {
                val far = f.crosses[0]
                val near = if (g.endA[far] == s || g.endB[far] == s) s else neighbourOn(g, s, far)
                val straight = (0 until 4).first { d ->
                    val q = g.next(s, d)
                    q >= 0 && q != near && st[g.edge(s, d)] == PearlsLogic.LINE && g.edge(q, d) >= 0 && st[g.edge(q, d)] == PearlsLogic.LINE
                }
                val q = g.next(s, straight)
                step(
                    WHITE_TURN, message("teacher.pearls.local_step.11"),
                    message("teacher.pearls.local_step.12"),
                    setOf(s, near) + touched, setOf(s, q, near) + touched,
                )
            }
            PearlsLogic.K_ONE_WAY -> step(
                ONE_WAY, message("teacher.pearls.local_step.13"),
                message("teacher.pearls.local_step.14"),
                setOf(s) + touched, setOf(s) + edgesOf.map { edgeIndex(n, it) },
            )
            PearlsLogic.K_PEARL_WAYS -> step(
                PEARL_WAYS, message("teacher.pearls.local_step.15"),
                message("teacher.pearls.local_step.16"),
                setOf(s) + touched, setOf(s) + edgesOf.map { edgeIndex(n, it) },
            )
            else -> step(
                DEAD_END, message("teacher.pearls.local_step.17"),
                message("teacher.pearls.local_step.18"),
                setOf(s) + touched, setOf(s) + edgesOf.map { edgeIndex(n, it) },
            )
        }
    }

    private fun loopStep(g: PearlsGrid, st: IntArray, f: PearlsLogic.Found): Step {
        val n = g.n
        val e = f.crosses[0]
        val a = g.endA[e]
        val b = g.endB[e]
        // The stretch the edge would close, for the highlight.
        val s = PearlsLogic.stretches(g, st)
        val stretch = (0 until g.edgeCount).filter { st[it] == PearlsLogic.LINE && s.root[g.endA[it]] == s.root[a] }
        return Step(
            SMALL_LOOP,
            crosses = listOf(e),
            focus = setOf(a, b, edgeIndex(n, e)),
            cited = stretch.map { edgeIndex(n, it) }.toSet() + setOf(a, b),
            nudge = message("teacher.pearls.loop_step.1"),
            explanation = message("teacher.pearls.loop_step.2"),
        )
    }

    private fun whatIfStep(g: PearlsGrid, pearls: String, w: PearlsLogic.WhatIf): Step {
        val n = g.n
        val e = w.edge
        val line = w.assume == PearlsLogic.LINE
        val a = g.endA[e]
        val b = g.endB[e]
        val c = w.contradiction.square
        val broke = when (w.contradiction.kind) {
            PearlsLogic.C_THREE -> message("teacher.pearls.what_if_step.1", "squareName_n_c_" to (squareNameMessage(n, c)))
            PearlsLogic.C_DEAD_LINE -> message("teacher.pearls.what_if_step.2", "squareName_n_c_" to (squareNameMessage(n, c)))
            PearlsLogic.C_PEARL_SHUT -> message("teacher.pearls.what_if_step.3", "squareName_n_c_" to (squareNameMessage(n, c)))
            PearlsLogic.C_BLACK -> message("teacher.pearls.what_if_step.4", "squareName_n_c_" to (squareNameMessage(n, c)))
            PearlsLogic.C_WHITE -> message("teacher.pearls.what_if_step.5", "squareName_n_c_" to (squareNameMessage(n, c)))
            else -> message("teacher.pearls.what_if_step.6")
        }
        val rounds = if (w.rounds <= 1) "" else message("teacher.pearls.what_if_step.7", "w_rounds" to (w.rounds))
        val suppose = if (line) message("teacher.pearls.what_if_step.8") else message("teacher.pearls.what_if_step.9")
        return Step(
            WHAT_IF,
            lines = if (line) emptyList() else listOf(e),
            crosses = if (line) listOf(e) else emptyList(),
            focus = setOf(a, b, edgeIndex(n, e)),
            cited = setOf(a, b, c),
            nudge = message("teacher.pearls.what_if_step.10", "value1" to (if (line) message("teacher.pearls.what_if_step.extra.1") else message("teacher.pearls.what_if_step.extra.2"))),
            explanation = message("teacher.pearls.what_if_step.11", "suppose" to (suppose), "rounds" to (rounds), "broke" to (broke), "value4" to (if (line) message("teacher.pearls.what_if_step.extra.3") else message("teacher.pearls.what_if_step.extra.4"))),
        )
    }

    // ---- why a pearl can't go a way ----------------------------------------------------------

    /** The sentence for one axis of a black pearl's step, or null when that axis settles nothing. */
    private fun blackAxis(g: PearlsGrid, pearls: String, st: IntArray, p: Int, axis: Int): Message? {
        val a = axis
        val b = axis + 2
        fun has(d: Int) = g.edge(p, d) >= 0 && st[g.edge(p, d)] == PearlsLogic.LINE
        val arm = when {
            has(a) -> a
            has(b) -> b
            !PearlsLogic.blackArmOk(g, pearls, st, p, b) -> a
            !PearlsLogic.blackArmOk(g, pearls, st, p, a) -> b
            else -> return null
        }
        val back = (arm + 2) % 4
        val out = g.edge(p, arm)
        val on = g.edge(g.next(p, arm), arm)
        val backEdge = g.edge(p, back)
        val settles = st[out] == PearlsLogic.OPEN || st[on] == PearlsLogic.OPEN ||
            backEdge >= 0 && st[backEdge] == PearlsLogic.OPEN
        if (!settles) return null
        return if (has(arm)) {
            message("teacher.pearls.black_axis.1", "DIRS_arm_" to (DIRS[arm]), "DIRS_back_" to (DIRS[back]))
        } else {
            message("teacher.pearls.black_axis.2", "DIRS_back_" to (DIRS[back]), "value2" to (blackReason(g, pearls, st, p, back)), "DIRS_arm_" to (DIRS[arm]))
        }
    }

    private fun blackReason(g: PearlsGrid, pearls: String, st: IntArray, p: Int, d: Int): Message {
        val out = g.edge(p, d)
        if (out < 0) return message("teacher.pearls.black_reason.1")
        if (st[out] == PearlsLogic.CROSS) return message("teacher.pearls.black_reason.2")
        val q = g.next(p, d)
        if (pearls[q] == PearlsLogic.BLACK) return message("teacher.pearls.black_reason.3")
        val on = g.edge(q, d)
        if (on < 0) return message("teacher.pearls.black_reason.4")
        if (st[on] == PearlsLogic.CROSS) return message("teacher.pearls.black_reason.5")
        return message("teacher.pearls.black_reason.6")
    }

    private fun whiteReason(g: PearlsGrid, st: IntArray, p: Int, axis: Int): Message {
        for (d in intArrayOf(axis, axis + 2)) {
            val e = g.edge(p, d)
            if (e < 0) return message("teacher.pearls.white_reason.1")
            if (st[e] == PearlsLogic.CROSS) return message("teacher.pearls.white_reason.2")
        }
        for (d in intArrayOf((axis + 1) % 4, (axis + 3) % 4)) {
            val e = g.edge(p, d)
            if (e >= 0 && st[e] == PearlsLogic.LINE) return message("teacher.pearls.white_reason.3")
        }
        return message("teacher.pearls.white_reason.4")
    }

    /** The square beside [s] at the far end of edge [e] (which belongs to that neighbour). */
    private fun neighbourOn(g: PearlsGrid, s: Int, e: Int): Int {
        for (d in 0 until 4) {
            val q = g.next(s, d)
            if (q >= 0 && (g.endA[e] == q || g.endB[e] == q)) return q
        }
        return s
    }

    private val DIRS = listOf(message("teacher.pearls._d_i_r_s.1"), message("teacher.pearls._d_i_r_s.2"), message("teacher.pearls._d_i_r_s.3"), message("teacher.pearls._d_i_r_s.4"))

    // ---- words ------------------------------------------------------------------------------

    /** "at row 2, column 3". */

    /** "row 2, column 3". */
}
