package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.TeachStep
import com.joebywan.daybook.core.asMessage
import com.joebywan.daybook.core.cap
import com.joebywan.daybook.core.fitMessage
import com.joebywan.daybook.core.i18n.Message
import com.joebywan.daybook.core.i18n.message
import com.joebywan.daybook.core.joinedMessages
import com.joebywan.daybook.core.lineNameMessage
import com.joebywan.daybook.core.listedMessage

/**
 * Tally, reasoned the way a person reasons it: one row or column at a time, and on Expert one
 * supposition followed a few lines further.
 *
 * Every board Daybook deals is finished by exactly this ladder ([TallyLogic.accepts]: techniques
 * 1-4 are [TallyLogic.shortcuts], 5 is [TallyLogic.forced], 6 is [TallyLogic.firstWhatIf]), so a
 * step always exists on a board whose marks are right, and there is no fallback to measure.
 *
 * [deduce] is handed the board as the player sees it (the digits, the targets and the marks) and
 * has no parameter through which the answer could arrive. The answer is read in one place,
 * [mistake], to decide which of the player's marks cannot be right; sound because every shipped
 * board has exactly one answer.
 *
 * Techniques, simplest first:
 * 1. [DONE] — the circled numbers already make the target, so everything else in the line goes.
 * 2. [TOO_BIG] — a number bigger than what the line still needs (target less the circled) goes.
 * 3. [TOO_MUCH] — a number bigger than the line's surplus (what is left less the target) stays:
 *    removing it would take away too much.
 * 4. [ALL_LEFT] — what is left in the line makes the target exactly, so all of it stays. Such a
 *    line already shows green, so circling it is tidying up; it comes after the shortcuts that
 *    move an unfinished line on (found on the emulator: a nearly finished board's hint said
 *    "circle the 6 and 2" in a green row while the last row still had numbers to remove).
 * 5. [WAYS] — everything else one line says: every way to make the target uses this number, or
 *    none does. The general case, which the four above are the common shortcuts of.
 * 6. [WHAT_IF] — suppose one number stays (or goes), follow at most [TallyLogic.MAX_LINKS] lines
 *    from it, and some line is left with no way to make its target, so the number does the
 *    opposite. Only Expert boards need it, and every Expert board does at least once (as every
 *    Hard board needs 5 and no Standard board does).
 *
 * Within a technique the first line wins, rows top to bottom and then columns left to right, so a
 * board gets the same hint on every platform.
 *
 * ## Highlight indices
 * Squares are `row * size + column`. Row `r`'s target is `size * size + r`, column `c`'s is
 * `size * size + size + c` ([TallyLogic.rowTargetIndex], [TallyLogic.colTargetIndex]).
 */
internal object TallyTeacher {

    const val DONE = "line-done"
    const val ALL_LEFT = "all-left"
    const val TOO_BIG = "too-big"
    const val TOO_MUCH = "too-much"
    const val WAYS = "every-way"
    const val WHAT_IF = "what-if"
    const val MISTAKE = "mistake"

    /** Every technique in the order [deduce] prefers them, for reports. */
    val TECHNIQUES = listOf(DONE, TOO_BIG, TOO_MUCH, ALL_LEFT, WAYS, WHAT_IF)

    /** What the hint panel has room for (the test pins it). */
    const val MAX_NUDGE = 70
    const val MAX_EXPLANATION = 200

    /** One step: what to circle, remove or clear, where to look, and why. [clears] is only ever a mistake. */
    class Step(
        override val technique: String,
        val stays: List<Int> = emptyList(),
        val goes: List<Int> = emptyList(),
        val clears: List<Int> = emptyList(),
        override val focus: Set<Int>,
        override val cited: Set<Int>,
        nudge: CharSequence,
        explanation: CharSequence,
    ) : TeachStep {
        override val nudgeMessage: Message = nudge.asMessage()
        override val explanationMessage: Message = explanation.asMessage()
        override val nudge: String get() = nudgeMessage.english
        override val explanation: String get() = explanationMessage.english


        override val targets: Set<Int> get() = (stays + goes + clears).toSet()
    }

    fun teach(s: TallyState): Step? {
        if (s.solved) return null
        mistake(s)?.let { return it }
        return deduce(s.size, TallyLogic.digitArray(s), TallyLogic.targets(s), s.marks)
    }

    // ---- mistakes -------------------------------------------------------------------------

    /** The first mark, in reading order, that the board's one answer disagrees with. */
    private fun mistake(s: TallyState): Step? {
        val n = s.size
        for (i in s.marks.indices) {
            val mark = s.marks[i]
            val removedWrongly = mark == TallyLogic.REMOVED && s.keep[i] == '1'
            val keptWrongly = mark == TallyLogic.KEPT && s.keep[i] == '0'
            if (!removedWrongly && !keptWrongly) continue
            val what = message("teacher.tally.mistake.1", "s_digitAt_i_" to (s.digitAt(i)))
            return Step(
                technique = MISTAKE,
                clears = listOf(i),
                focus = setOf(i),
                cited = setOf(TallyLogic.rowTargetIndex(n, i / n), TallyLogic.colTargetIndex(n, i % n)),
                nudge = message("teacher.tally.mistake.2"),
                explanation = if (removedWrongly) {
                    message("teacher.tally.mistake.3", "what" to (what))
                } else {
                    message("teacher.tally.mistake.4", "what" to (what))
                },
            )
        }
        return null
    }

    // ---- reasoning ------------------------------------------------------------------------

    /**
     * The next step reasoned from the digits, the [targets] (rows then columns) and the [marks]
     * alone, or null when nothing can be settled (a finished board, or one with a wrong mark).
     */
    fun deduce(n: Int, digits: IntArray, targets: List<Int>, marks: String): Step? {
        val grid = TallyLogic.markState(marks)
        var best: Step? = null
        var bestRank = Int.MAX_VALUE
        for (line in 0 until 2 * n) {
            val step = lineStep(n, digits, targets, grid, line) ?: continue
            val rank = TECHNIQUES.indexOf(step.technique)
            // Strictly better only, so on a tie the earlier line stays.
            if (rank < bestRank) {
                best = step
                bestRank = rank
            }
        }
        if (best != null) return best
        val w = TallyLogic.firstWhatIf(n, digits, targets, grid) ?: return null
        return whatIfStep(n, digits, targets, w)
    }

    /**
     * Every line's step, rows then columns, and when no line has one, every what-if within [TallyLogic.MAX_LINKS] (not only
     * [deduce]'s shortest). Training reads them all: any instance of a technique counts.
     */
    fun steps(n: Int, digits: IntArray, targets: List<Int>, marks: String): List<Step> {
        val grid = TallyLogic.markState(marks)
        val lines = (0 until 2 * n).mapNotNull { lineStep(n, digits, targets, grid, it) }
        if (lines.isNotEmpty()) return lines
        return (0 until n * n).filter { grid[it] == TallyLogic.OPEN }.flatMap { i ->
            listOf(TallyLogic.STAY, TallyLogic.GO).mapNotNull { a -> TallyLogic.whatIf(n, digits, targets, grid, i, a)?.let { whatIfStep(n, digits, targets, it) } }
        }
    }

    private fun lineStep(n: Int, digits: IntArray, targets: List<Int>, grid: IntArray, line: Int): Step? {
        val at = TallyLogic.lineCells(n, line)
        val values = IntArray(n) { digits[at[it]] }
        val state = IntArray(n) { grid[at[it]] }
        val target = targets[line]
        val found = TallyLogic.forced(values, state, target) ?: return null
        if (found.all { it == TallyLogic.OPEN }) return null

        val name = lineNameMessage(n, line)
        val cap = name.cap()
        val open = (0 until n).filter { state[it] == TallyLogic.OPEN }
        val kept = (0 until n).filter { state[it] == TallyLogic.STAY }.sumOf { values[it] }
        val left = (0 until n).filter { state[it] != TallyLogic.GO }.sumOf { values[it] }
        val focus = at.toSet() + (n * n + line) // line's target: rows' then columns' follow the squares

        fun step(technique: String, stays: List<Int>, goes: List<Int>, nudge: Message, why: Message) = Step(
            technique = technique,
            stays = stays.map { at[it] },
            goes = goes.map { at[it] },
            focus = focus,
            cited = focus,
            nudge = nudge,
            explanation = why,
        )

        if (kept == target) {
            return step(
                DONE, emptyList(), open, message("teacher.tally.line_step.1", "cap" to (cap), "target" to (target)),
                message("teacher.tally.line_step.2", "name" to (name), "target" to (target), "names_values_open_" to (names(values, open))),
            )
        }
        if (left == target) {
            return step(
                ALL_LEFT, open, emptyList(), message("teacher.tally.line_step.3", "name" to (name)),
                message("teacher.tally.line_step.4", "name" to (name), "target" to (target), "names_values_open_" to (names(values, open))),
            )
        }
        val need = target - kept
        val tooBig = open.filter { values[it] > need }
        if (tooBig.isNotEmpty()) {
            val needs = if (kept == 0) message("teacher.tally.line_step.5", "cap" to (cap), "target" to (target)) else message("teacher.tally.line_step.6", "cap" to (cap), "target" to (target), "kept" to (kept), "need" to (need))
            return step(
                TOO_BIG, emptyList(), tooBig, message("teacher.tally.line_step.7", "cap" to (cap)),
                message("teacher.tally.line_step.8", "needs" to (needs), "names_values_tooBig_" to (names(values, tooBig)), "value3" to (if (tooBig.size == 1) message("teacher.tally.line_step.extra.1") else message("teacher.tally.line_step.extra.2")), "value4" to (if (tooBig.size == 1) message("teacher.tally.line_step.extra.3") else message("teacher.tally.line_step.extra.4"))),
            )
        }
        val excess = left - target
        val tooMuch = open.filter { values[it] > excess }
        if (tooMuch.isNotEmpty()) {
            return step(
                TOO_MUCH, tooMuch, emptyList(), message("teacher.tally.line_step.9", "cap" to (cap)),
                message("teacher.tally.line_step.10", "name" to (name), "left" to (left), "excess" to (excess), "target" to (target), "names_values_tooMuch_or_" to (names(values, tooMuch, message("teacher.tally.line_step.extra.5"))), "value6" to (if (tooMuch.size == 1) message("teacher.tally.line_step.extra.6") else message("teacher.tally.line_step.extra.7")), "value7" to (if (tooMuch.size == 1) message("teacher.tally.line_step.extra.8") else message("teacher.tally.line_step.extra.9"))),
            )
        }

        val stays = (0 until n).filter { found[it] == TallyLogic.STAY }
        val goes = (0 until n).filter { found[it] == TallyLogic.GO }
        val ways = TallyLogic.ways(values, state, target)
        val how = if (ways.size <= 3) {
            message("teacher.tally.line_step.11", "cap" to (cap), "target" to (target), "value3" to (ways.map { m -> (0 until n).filter { m shr it and 1 == 1 }.map { message("teacher.tally.line_step.extra.11", "values_it_" to (values[it])) }.joinedMessages("+") }.joinedMessages(message("teacher.tally.line_step.extra.10"))))
        } else {
            message("teacher.tally.line_step.13", "ways_size" to (ways.size), "target" to (target), "name" to (name))
        }
        val verdict = when {
            stays.isNotEmpty() && goes.isNotEmpty() ->
                message("teacher.tally.line_step.14", "names_values_stays_" to (names(values, stays)), "names_values_goes_or_" to (names(values, goes, message("teacher.tally.line_step.extra.12"))))
            stays.isNotEmpty() -> message("teacher.tally.line_step.15", "names_values_stays_" to (names(values, stays)), "value2" to (if (stays.size == 1) message("teacher.tally.line_step.extra.13") else message("teacher.tally.line_step.extra.14")))
            else -> message("teacher.tally.line_step.16", "names_values_goes_or_" to (names(values, goes, message("teacher.tally.line_step.extra.15"))), "value2" to (if (goes.size == 1) message("teacher.tally.line_step.extra.16") else message("teacher.tally.line_step.extra.17")))
        }
        val why = fitMessage(
            listOf(
                how + verdict,
                message("teacher.tally.line_step.17", "ways_size" to (ways.size), "target" to (target), "name" to (name), "verdict" to (verdict)),
                message("teacher.tally.line_step.18", "target" to (target), "name" to (name), "stays_size_goes_size" to (stays.size + goes.size)),
            ),
            MAX_EXPLANATION,
        )
        return step(WAYS, stays, goes, message("teacher.tally.line_step.19", "name" to (name), "target" to (target)), why)
    }

    private fun whatIfStep(n: Int, digits: IntArray, targets: List<Int>, w: TallyLogic.WhatIf): Step {
        val i = w.square
        val stayed = w.assume == TallyLogic.STAY
        val square = message("teacher.tally.what_if_step.1", "digits_i_" to (digits[i]))
        val suppose = message("teacher.tally.what_if_step.2", "square" to (square), "value2" to (if (stayed) message("teacher.tally.what_if_step.extra.1") else message("teacher.tally.what_if_step.extra.2")))
        val fail = message("teacher.tally.what_if_step.3", "lineName_n_w_line_" to (lineNameMessage(n, w.line)), "targets_w_line_" to (targets[w.line]))
        val verdict = message("teacher.tally.what_if_step.4", "value1" to (if (stayed) message("teacher.tally.what_if_step.extra.3") else message("teacher.tally.what_if_step.extra.4")))

        fun linkText(link: TallyLogic.Link, full: Boolean): Message {
            val line = lineNameMessage(n, link.line)
            if (!full) return message("teacher.tally.link_text.1", "line" to (line), "value2" to (link.stays.size + link.goes.size))
            val at = TallyLogic.lineCells(n, link.line)
            val values = IntArray(n) { digits[at[it]] }
            val keep = link.stays.map { at.indexOf(it) }
            val lose = link.goes.map { at.indexOf(it) }
            return line + message("teacher.tally.link_text.2") + listOfNotNull(
                keep.takeIf { it.isNotEmpty() }?.let { message("teacher.tally.link_text.3", "names_values_it_" to (names(values, it))) },
                lose.takeIf { it.isNotEmpty() }?.let { message("teacher.tally.link_text.4", "names_values_it_" to (names(values, it))) },
            ).joinedMessages(message("teacher.tally.link_text.5"))
        }

        fun sentence(full: Boolean): Message {
            val parts = w.links.map { linkText(it, full) } + fail
            val chain = listedMessage(parts, oxford = true)
            return message("teacher.tally.sentence.extra.1", "suppose" to (suppose), "chain" to (chain), "verdict" to (verdict))
        }
        val why = fitMessage(listOf(sentence(true), sentence(false), message("teacher.tally.what_if_step.5", "suppose" to (suppose), "w_links_size" to (w.links.size), "fail" to (fail), "verdict" to (verdict))), MAX_EXPLANATION)

        val cited = w.links.flatMap { it.stays + it.goes }.toSet() + (n * n + w.line)
        return Step(
            technique = WHAT_IF,
            stays = if (stayed) emptyList() else listOf(i),
            goes = if (stayed) listOf(i) else emptyList(),
            focus = setOf(i),
            cited = cited,
            nudge = message("teacher.tally.what_if_step.6", "digits_i_" to (digits[i]), "value2" to (if (stayed) message("teacher.tally.what_if_step.extra.5") else message("teacher.tally.what_if_step.extra.6"))),
            explanation = why,
        )
    }

    // ---- words ----------------------------------------------------------------------------

    /**
     * "the 8", "the 8 and 3", "the 8, 3 and 1", with a square number where the line repeats a
     * digit ("the 4 in square 2"), or a count when there are many. [joiner] is "and" or "or".
     */
    private fun names(values: IntArray, positions: List<Int>, joiner: Message = message("teacher.tally.names.1")): Message {
        if (positions.size > 4) return message("teacher.tally.names.2", "positions_size" to (positions.size))
        val repeated = positions.any { p -> values.count { it == values[p] } > 1 }
        val parts = if (repeated) {
            positions.map { message("teacher.tally.names.3", "values_it_" to (values[it]), "it_1" to (it + 1)) }
        } else {
            positions.map { message("teacher.tally.names.extra.1", "values_it_" to (values[it])) }
        }
        val list = listedMessage(parts, joiner)
        return message("teacher.tally.names.4", "list" to (list))
    }
}
