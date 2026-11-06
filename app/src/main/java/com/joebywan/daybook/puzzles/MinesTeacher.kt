package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.TeachStep
import com.joebywan.daybook.core.asMessage
import com.joebywan.daybook.core.i18n.Message
import com.joebywan.daybook.core.i18n.message
import com.joebywan.daybook.core.joinedMessages
import com.joebywan.daybook.core.neighbours8

/**
 * Mines, reasoned a step at a time (`docs/MINES_SPEC.md`, "The ladder").
 *
 * [deduce] is handed what the player can see (the revealed numbers, the mines hit, their flags) and nothing else, so a
 * step it explains cannot lean on where a hidden mine is. Flags are never knowledge (they may be wrong): they only let a
 * step whose mines are all flagged count as already shown, so the hint is the first fact the player has not put down.
 * The answer is read only in [teach]: for a flag on a safe square (a mistake, sound because a shipped board has one
 * answer) and for [FALLBACK], which says openly that it points at the answer.
 *
 * The ladder is [MinesLogic.nextFact], the generator's own, so a shipped board never reaches [FALLBACK]; `MinesTeachingTest`
 * measures it. Every loop walks squares in index order.
 *
 * Highlight indices: squares `0 until width * height`.
 */
internal object MinesTeacher {

    const val ALL_FOUND = "all-found"
    const val ALL_MINES = "all-mines"
    const val SUBSET = "subset"
    const val PAIR = "pair"
    const val COUNT = "count"
    const val EDGE = "count-edge"
    const val WHAT_IF = "what-if"
    const val FALLBACK = "fallback"
    const val MISTAKE = "mistake"

    val TECHNIQUES = listOf(ALL_FOUND, ALL_MINES, SUBSET, PAIR, COUNT, EDGE, WHAT_IF, FALLBACK)

    const val MAX_NUDGE = 70
    const val MAX_EXPLANATION = 230

    class Step(
        override val technique: String,
        /** Squares to reveal. */
        val safe: Set<Int>,
        /** Squares to flag; for a mistake, the flag to take off. */
        val mines: Set<Int>,
        override val focus: Set<Int>,
        override val cited: Set<Int>,
        nudge: CharSequence,
        explanation: CharSequence,
    ) : TeachStep {
        override val nudgeMessage: Message = nudge.asMessage()
        override val explanationMessage: Message = explanation.asMessage()
        override val nudge: String get() = nudgeMessage.english
        override val explanation: String get() = explanationMessage.english


        override val targets: Set<Int> get() = safe + mines
    }

    // ---- the whole hint: mistakes, then reasoning, then the honest fallback -----------------------------------------

    fun teach(s: MinesState): Step? {
        if (s.solved || s.failed) return null
        mistake(s)?.let { return it }
        deduce(s.width, s.height, visibleNumbers(s), BooleanArray(s.width * s.height) { it in s.bumps }, BooleanArray(s.width * s.height) { s.isFlag(it) }, s.total)
            ?.let { return it }
        val n = s.width * s.height
        val open = (0 until n).filter { !s.isMine(it) && !s.isOpen(it) && !s.isFlag(it) }
        val i = open.firstOrNull { j -> neighbours8(s.width, s.height, j).any { s.isOpen(it) } } ?: open.firstOrNull() ?: return null
        return Step(FALLBACK, setOf(i), emptySet(), setOf(i), emptySet(), message("teacher.mines.teach.1"), message("teacher.mines.teach.2"))
    }

    /** Each square's number when it is revealed, else -1: what the player can read off the board. */
    fun visibleNumbers(s: MinesState) = IntArray(s.width * s.height) { if (s.isOpen(it)) s.number(it) else -1 }

    /** The [Deduction] for the play screen; `Mines.teach` is just this. */
    fun deduction(s: MinesState): Deduction? = teach(s)?.let(::deduction)

    /** [step] as the play screen's [Deduction] (Training builds its practices' steps with it too). */
    fun deduction(step: Step): Deduction {
        val mistake = step.technique == MISTAKE
        return Deduction(
            step,
            mistake = mistake,
            fallback = step.technique == FALLBACK,
            applyTo = { now -> apply(now as MinesState, step) },
            reachedBy = { now -> reached(now as MinesState, step) },
        )
    }

    /** "Show me": the safe squares revealed and the mines flagged (a mistake's flag taken off), one state. */
    fun apply(s: MinesState, step: Step): MinesState {
        val m = s.marks.toCharArray()
        if (step.technique == MISTAKE) {
            step.mines.forEach { if (m[it] == MinesLogic.FLAG) m[it] = MinesLogic.HIDDEN }
        } else {
            step.mines.forEach { if (m[it] == MinesLogic.HIDDEN && it !in s.bumps) m[it] = MinesLogic.FLAG }
            step.safe.forEach { MinesLogic.openInPlace(s.width, s.height, s.mines, m, it) }
        }
        val marks = m.concatToString()
        return if (marks == s.marks) s else s.copy(marks = marks, moves = s.moves + 1)
    }

    /** Whether the player has made the step: its safe squares revealed (or, with none, its mines flagged). */
    fun reached(s: MinesState, step: Step): Boolean = when {
        step.technique == MISTAKE -> step.mines.none { s.isFlag(it) }
        step.safe.isNotEmpty() -> step.safe.all { s.isOpen(it) }
        else -> step.mines.all { s.isFlag(it) || it in s.bumps }
    }

    // ---- mistakes ---------------------------------------------------------------------------------------------------

    private fun mistake(s: MinesState): Step? {
        val n = s.width * s.height
        val wrong = (0 until n).firstOrNull { s.isFlag(it) && !s.isMine(it) } ?: return null
        val over = neighbours8(s.width, s.height, wrong).firstOrNull { j ->
            s.isOpen(j) && neighbours8(s.width, s.height, j).count { s.isFlag(it) || it in s.bumps } > s.number(j)
        }
        val why = when {
            over == null -> message("teacher.mines.mistake.1")
            s.number(over) == 0 -> message("teacher.mines.mistake.2")
            else -> message("teacher.mines.mistake.3", "s_number_over_" to (s.number(over)))
        }
        return Step(MISTAKE, emptySet(), setOf(wrong), setOf(wrong), setOfNotNull(over), message("teacher.mines.mistake.4"), message("teacher.mines.mistake.5", "why" to (why)))
    }

    // ---- reasoning --------------------------------------------------------------------------------------------------


    private fun others(k: Int) = if (k == 1) message("teacher.mines.others.1") else message("teacher.mines.others.2", "k" to (k))

    /** Where square [b] lies from [a], on a board [w] wide: "above", "to the right", "up and to the left"... */
    private fun where(w: Int, a: Int, b: Int): Message {
        val dr = b / w - a / w
        val dc = b % w - a % w
        val side = if (dc < 0) message("teacher.mines.where.1") else message("teacher.mines.where.2")
        return when {
            dc == 0 -> if (dr < 0) message("teacher.mines.where.3") else message("teacher.mines.where.4")
            dr == 0 -> side
            else -> message("teacher.mines.where.5", "if_dr_0_up_else_down_" to (if (dr < 0) message("teacher.mines.where.extra.1") else message("teacher.mines.where.extra.2")), "side" to (side))
        }
    }

    /**
     * The first step the ladder gives that the board does not already show, or null when it has no more. [num] holds
     * each revealed square's number (-1 hidden), [hit] the mines revealed, [flags] the player's flags, [total] the mines.
     */
    fun deduce(w: Int, h: Int, num: IntArray, hit: BooleanArray, flags: BooleanArray, total: Int): Step? {
        val n = w * h
        val st = IntArray(n) { if (num[it] >= 0) MinesLogic.SAFE else if (hit[it]) MinesLogic.MINE else MinesLogic.UNKNOWN }
        while (true) {
            val f = MinesLogic.nextFact(w, h, num, st, total) ?: return null
            if (f.safe.isEmpty() && f.mines.all { flags[it] }) {
                f.mines.forEach { st[it] = MinesLogic.MINE }
                continue
            }
            return explain(w, h, num, st, total, f)
        }
    }

    /**
     * Every step of [technique] on the board, each explained, from the same visible facts as [deduce] (with [hit] the mines
     * taken as known): Training passes a move that completes any of them. Facts are found in the ladder's order and the
     * search stops once past [technique]'s place in it.
     */
    fun steps(w: Int, h: Int, num: IntArray, hit: BooleanArray, total: Int, technique: String): List<Step> {
        val st = IntArray(w * h) { if (num[it] >= 0) MinesLogic.SAFE else if (hit[it]) MinesLogic.MINE else MinesLogic.UNKNOWN }
        // The ladder tries single numbers together, then pairs of numbers together, then the count, the edge, the what-if.
        fun rank(t: String) = when (t) { ALL_FOUND, ALL_MINES -> 0; SUBSET, PAIR -> 1; COUNT -> 2; EDGE -> 3; else -> 4 }
        return MinesLogic.facts(w, h, num, st, total)
            .takeWhile { rank(it.technique) <= rank(technique) }
            .filter { it.technique == technique }
            .map { explain(w, h, num, st, total, it) }
            .toList()
    }

    private fun explain(w: Int, h: Int, num: IntArray, st: IntArray, total: Int, f: MinesLogic.Fact): Step {
        fun need(i: Int) = num[i] - neighbours8(w, h, i).count { st[it] == MinesLogic.MINE }
        fun found(i: Int) = neighbours8(w, h, i).count { st[it] == MinesLogic.MINE }
        val safe = f.safe.toSet()
        val mines = f.mines.toSet()
        val cited = f.cited.toSet()
        val targets = safe + mines
        val move = if (safe.isNotEmpty()) message("teacher.mines.explain.1", "value1" to (if (safe.size == 1) message("teacher.mines.explain.extra.1") else message("teacher.mines.explain.extra.2"))) else message("teacher.mines.explain.2", "value1" to (if (mines.size == 1) message("teacher.mines.explain.extra.3") else message("teacher.mines.explain.extra.4")))
        return when (f.technique) {
            ALL_FOUND -> {
                val i = f.cited[0]
                val text = if (num[i] == 0) message("teacher.mines.explain.3", "move" to (move))
                else message("teacher.mines.explain.4", "num_i_" to (num[i]), "value2" to (if (num[i] == 1) message("teacher.mines.explain.extra.5") else message("teacher.mines.explain.extra.6", "num_i_" to (num[i]))), "move" to (move))
                Step(ALL_FOUND, safe, mines, cited, cited, message("teacher.mines.explain.5", "num_i_" to (num[i])), text)
            }
            ALL_MINES -> {
                val i = f.cited[0]
                val k = need(i)
                val text = if (found(i) == 0) message("teacher.mines.explain.6", "num_i_" to (num[i]), "plural_k_hidden_square_" to (message("teacher.mines.count.hidden_square", "count" to (k))), "value3" to (if (k == 1) message("teacher.mines.explain.extra.8") else message("teacher.mines.explain.extra.9")), "move" to (move))
                else message("teacher.mines.explain.7", "num_i_" to (num[i]), "plural_k_mine_" to (message("teacher.mines.count.mine", "count" to (k))), "plural_k_hidden_square_" to (message("teacher.mines.count.hidden_square", "count" to (k))), "value4" to (if (k == 1) message("teacher.mines.explain.extra.12") else message("teacher.mines.explain.extra.13")), "move" to (move))
                Step(ALL_MINES, safe, mines, cited, cited, message("teacher.mines.explain.8", "num_i_" to (num[i])), text)
            }
            SUBSET -> {
                val (a, b) = f.cited
                val diff = need(b) - need(a)
                val k = targets.size
                val what = when {
                    diff == 0 -> if (k == 1) message("teacher.mines.explain.9") else message("teacher.mines.explain.10")
                    k == 1 -> message("teacher.mines.explain.11")
                    else -> message("teacher.mines.explain.12")
                }
                val text = message("teacher.mines.explain.13", "num_a_" to (num[a]), "need_a_" to (need(a)), "value3" to (if (need(a) == 1) message("teacher.mines.explain.extra.14") else message("teacher.mines.explain.extra.15")), "num_b_" to (num[b]), "where_w_a_b_" to (where(w, a, b)), "need_b_" to (need(b)), "others_k_" to (others(k)), "if_k_1_holds_else_hold_" to (if (k == 1) message("teacher.mines.explain.extra.16") else message("teacher.mines.explain.extra.17")), "diff" to (diff), "what" to (what), "move" to (move))
                Step(SUBSET, safe, mines, setOf(a), cited, message("teacher.mines.explain.14", "num_a_" to (num[a]), "num_b_" to (num[b]), "where_w_a_b_" to (where(w, a, b))), text)
            }
            PAIR -> {
                val (a, b) = f.cited
                val k = mines.size
                val text = message("teacher.mines.explain.15", "num_b_" to (num[b]), "where_w_a_b_" to (where(w, a, b)), "need_b_" to (need(b)), "num_a_" to (num[a]), "need_a_" to (need(a)), "others_k_" to (others(k)), "value7" to (if (k == 1) message("teacher.mines.explain.extra.18") else message("teacher.mines.explain.extra.19")))
                Step(PAIR, safe, mines, setOf(a), cited, message("teacher.mines.explain.16", "num_a_" to (num[a]), "num_b_" to (num[b]), "where_w_a_b_" to (where(w, a, b))), text)
            }
            COUNT -> {
                val text = if (f.left == 0) message("teacher.mines.explain.17", "total" to (total), "move" to (move))
                else message("teacher.mines.explain.18", "plural_f_left_mine_" to (message("teacher.mines.count.mine", "count" to (f.left))), "if_f_left_1_is_else_are_" to (if (f.left == 1) message("teacher.mines.explain.extra.21") else message("teacher.mines.explain.extra.22")), "value3" to (message("teacher.mines.count.hidden_square", "count" to (f.left))), "value4" to (if (f.left == 1) message("teacher.mines.explain.extra.24") else message("teacher.mines.explain.extra.25")), "move" to (move))
                Step(COUNT, safe, mines, targets, cited, message("teacher.mines.explain.19"), text)
            }
            EDGE -> {
                val text = message("teacher.mines.explain.20", "plural_f_left_mine_" to (message("teacher.mines.count.mine", "count" to (f.left))), "if_f_left_1_is_else_are_" to (if (f.left == 1) message("teacher.mines.explain.extra.27") else message("teacher.mines.explain.extra.28")), "f_left" to (f.left), "move" to (move))
                Step(EDGE, safe, mines, cited, cited, message("teacher.mines.explain.21"), text)
            }
            WHAT_IF -> {
                val x = targets.first()
                // The first number the supposition sets off touches it; each after that touches what the one before settled.
                val said = f.chain.mapIndexed { k, (i, m) ->
                    message("teacher.mines.explain.extra.29", "value1" to (if (k == 0) message("teacher.mines.explain.nested.1", "num_i_" to (num[i])) else message("teacher.mines.explain.nested.2", "num_i_" to (num[i]))), "value2" to (if (m) message("teacher.mines.explain.nested.3") else message("teacher.mines.explain.nested.4")))
                }
                val end = message("teacher.mines.explain.22", "value1" to (if (f.chain.isEmpty()) message("teacher.mines.explain.extra.30", "num_f_broke_" to (num[f.broke])) else message("teacher.mines.explain.extra.31", "num_f_broke_" to (num[f.broke]))), "value2" to (if (f.tooMany) message("teacher.mines.explain.extra.32") else message("teacher.mines.explain.extra.33")))
                val chain = (said + end).joinedMessages(", ")
                val text = message("teacher.mines.explain.23", "value1" to (if (safe.isNotEmpty()) message("teacher.mines.explain.extra.34") else message("teacher.mines.explain.extra.35")), "chain" to (chain), "value3" to (if (safe.isNotEmpty()) message("teacher.mines.explain.extra.36") else message("teacher.mines.explain.extra.37")))
                Step(WHAT_IF, safe, mines, setOf(x), cited, message("teacher.mines.explain.24", "value1" to (if (safe.isNotEmpty()) message("teacher.mines.explain.extra.38") else message("teacher.mines.explain.extra.39"))), text)
            }
            else -> error(message("teacher.mines.explain.25", "f_technique" to (f.technique)))
        }
    }
}
