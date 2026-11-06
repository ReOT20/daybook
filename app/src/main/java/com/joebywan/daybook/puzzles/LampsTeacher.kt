package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.TeachStep
import com.joebywan.daybook.core.asMessage
import com.joebywan.daybook.core.i18n.Message
import com.joebywan.daybook.core.i18n.message
import com.joebywan.daybook.core.joinedMessages
import com.joebywan.daybook.core.orthogonalReading
import com.joebywan.daybook.puzzles.LampsLogic.Fact
import com.joebywan.daybook.puzzles.LampsLogic.LAMP
import com.joebywan.daybook.puzzles.LampsLogic.NO

/**
 * Lamps, reasoned a fact at a time: exactly [LampsLogic.nextFact]'s ladder, which is also what the generator ships a
 * board only when it finishes, so a step always exists on a board whose lamps are right.
 *
 * [deduce] is handed what the player can see (the walls, the numbers, their marks) and nothing else, so a step cannot
 * lean on the stored answer. The answer is read only in [teach]: to find a lamp no answer keeps (sound because a shipped
 * board has one answer) and for [FALLBACK], which says openly that it points at the answer.
 *
 * Knowledge starts from the player's lamps (once none is a mistake); crosses are notes and are never read. A fact the
 * board already shows (every square it rules out crossed) is skipped, so the hint is the first thing the player has not
 * yet put down. Every loop walks squares in index order, so a board gets the same hint on every platform.
 *
 * Highlight indices are squares, `row * size + col`; a number is cited by its wall's square.
 */
internal object LampsTeacher {

    const val ZERO = LampsLogic.ZERO
    const val DONE = LampsLogic.DONE
    const val ROOM = LampsLogic.ROOM
    const val ONLY_LIGHT = LampsLogic.ONLY_LIGHT
    const val STARVE = LampsLogic.STARVE
    const val DARK = LampsLogic.DARK
    const val WHAT_IF = LampsLogic.WHAT_IF
    const val FALLBACK = "fallback"
    const val MISTAKE = "mistake"

    val TECHNIQUES = listOf(ZERO, DONE, ROOM, ONLY_LIGHT, STARVE, DARK, WHAT_IF, FALLBACK)

    const val MAX_NUDGE = 70

    /** More than most teachers: a what-if names its chain, and the popover scrolls past its room (Cipher's precedent). */
    const val MAX_EXPLANATION = 260

    class Step(
        override val technique: String,
        /** What the targets become: [LAMP], [NO] (a cross), or 0 to take a lamp away (a mistake). */
        val value: Int,
        override val targets: Set<Int>,
        override val focus: Set<Int>,
        override val cited: Set<Int>,
        nudge: CharSequence,
        explanation: CharSequence,
    ) : TeachStep {

        override val nudgeMessage: Message = nudge.asMessage()
        override val explanationMessage: Message = explanation.asMessage()
        override val nudge: String get() = nudgeMessage.english
        override val explanation: String get() = explanationMessage.english
    }

    fun teach(s: LampsState): Step? {
        if (s.solved) return null
        mistake(s)?.let { return it }
        deduce(s.size, s.walls, s.marks)?.let { return it }
        val i = s.solution.indices.firstOrNull { s.solution[it] == '1' && s.marks[it] != LampsLogic.LAMP_MARK } ?: return null
        return Step(FALLBACK, LAMP, setOf(i), setOf(i), emptySet(), message("teacher.lamps.teach.1"), message("teacher.lamps.teach.2"))
    }

    /** The [Deduction] for the play screen; `Lamps.teach` is just this. */
    fun deduction(s: LampsState): Deduction? = teach(s)?.let(::deduction)

    /** [step] as the play screen's [Deduction] (Training builds its practices' steps with it too). */
    fun deduction(step: Step): Deduction {
        val mistake = step.technique == MISTAKE
        return Deduction(
            step,
            mistake = mistake,
            fallback = step.technique == FALLBACK,
            applyTo = { now ->
                step.targets.fold(now as LampsState) { acc, i ->
                    when (step.value) {
                        LAMP -> acc.withMark(i, LampsLogic.LAMP_MARK)
                        NO -> if (acc.marks[i] == LampsLogic.LAMP_MARK) acc else acc.withMark(i, LampsLogic.CROSS)
                        else -> acc.withMark(i, LampsLogic.NONE)
                    }
                }
            },
            reachedBy = { now ->
                val t = now as LampsState
                val lit by lazy { t.lit() }
                step.targets.all {
                    when (step.value) {
                        LAMP -> t.marks[it] == LampsLogic.LAMP_MARK
                        NO -> t.marks[it] == LampsLogic.CROSS || (t.marks[it] == LampsLogic.NONE && lit[it])
                        else -> t.marks[it] != LampsLogic.LAMP_MARK
                    }
                }
            },
        )
    }

    // ---- mistakes -------------------------------------------------------------------------------

    private fun mistake(s: LampsState): Step? {
        val n = s.size
        val wrong = s.marks.indices.firstOrNull { s.marks[it] == LampsLogic.LAMP_MARK && s.solution[it] != '1' } ?: return null
        fun step(why: Message, cited: Set<Int>) =
            Step(MISTAKE, 0, setOf(wrong), setOf(wrong), cited, message("teacher.lamps.step.1"), message("teacher.lamps.step.2", "why" to (why)))
        val sees = LampsLogic.sight(n, s.walls, wrong).filter { s.marks[it] == LampsLogic.LAMP_MARK }
        if (sees.isNotEmpty()) return step(message("teacher.lamps.mistake.1"), sees.toSet())
        val over = orthogonalReading(wrong, n).firstOrNull { s.numberAt(it) >= 0 && s.lampsBeside(it) > s.numberAt(it) }
        if (over != null) return step(message("teacher.lamps.mistake.2", "s_numberAt_over_" to (s.numberAt(over))), setOf(over))
        return step(message("teacher.lamps.mistake.3"), emptySet())
    }

    // ---- reasoning ------------------------------------------------------------------------------

    /** The first fact the ladder gives that the board does not already show, or null when it has no more. */
    fun deduce(n: Int, walls: String, marks: String): Step? {
        val g = LampsLogic.Geo(n, walls)
        val k = IntArray(n * n) { if (g.white[it] && marks[it] == LampsLogic.LAMP_MARK) LAMP else LampsLogic.OPEN }
        while (true) {
            val f = LampsLogic.nextFact(g, k, LampsLogic.WHAT_IF_RUNG) ?: return null
            LampsLogic.apply(k, f)
            val shown = f.value == NO && f.targets.all { marks[it] == LampsLogic.CROSS }
            if (!shown) return explain(g, f)
        }
    }

    /**
     * Every step of [technique] on the board, each explained, taking [marks]' lamps and crosses as known (a practice board's
     * are its walk's own sound steps): Training passes a move that completes any of them.
     */
    fun steps(n: Int, walls: String, marks: String, technique: String): List<Step> {
        val g = LampsLogic.Geo(n, walls)
        val k = IntArray(n * n) {
            when {
                !g.white[it] -> LampsLogic.OPEN
                marks[it] == LampsLogic.LAMP_MARK -> LAMP
                marks[it] == LampsLogic.CROSS -> NO
                else -> LampsLogic.OPEN
            }
        }
        return LampsLogic.facts(g, k, technique).map { explain(g, it) }
    }

    private fun its(num: Int) = when (num) {
        1 -> message("teacher.lamps.its.1")
        2 -> message("teacher.lamps.its.2")
        else -> message("teacher.lamps.its.3", "num" to (num))
    }

    private fun explain(g: LampsLogic.Geo, f: Fact): Step {
        val t = f.targets.toSet()
        val num = if (f.wall >= 0) g.number[f.wall] else 0
        return when (f.technique) {
            ZERO -> Step(
                ZERO, NO, t, t + f.wall, setOf(f.wall),
                message("teacher.lamps.explain.1"),
                message("teacher.lamps.explain.2"),
            )
            DONE -> Step(
                DONE, NO, t, t + f.wall, setOf(f.wall) + g.beside[f.wall].filter { it !in t },
                message("teacher.lamps.explain.3", "num" to (num)),
                message("teacher.lamps.explain.4", "num" to (num), "its_num_" to (its(num))),
            )
            ROOM -> Step(
                ROOM, LAMP, t, t + f.wall, setOf(f.wall),
                message("teacher.lamps.explain.5", "num" to (num)),
                message("teacher.lamps.explain.6", "num" to (num), "value2" to (if (num > t.size) message("teacher.lamps.count.more_lamp", "count" to t.size) else message("teacher.lamps.count.lamp", "count" to t.size)), "plural_t_size_square_" to (message("teacher.lamps.count.square", "count" to (t.size))), "value4" to (if (t.size == 1) message("teacher.lamps.explain.extra.4") else message("teacher.lamps.explain.extra.5"))),
            )
            ONLY_LIGHT -> if (f.targets[0] == f.seen) Step(
                ONLY_LIGHT, LAMP, t, t, emptySet(),
                message("teacher.lamps.explain.7"),
                message("teacher.lamps.explain.8"),
            ) else Step(
                ONLY_LIGHT, LAMP, t, t + f.seen, setOf(f.seen),
                message("teacher.lamps.explain.9"),
                message("teacher.lamps.explain.10"),
            )
            STARVE -> Step(
                STARVE, NO, t, t + f.wall + f.lit, setOf(f.wall) + f.lit,
                message("teacher.lamps.explain.11", "num" to (num)),
                message("teacher.lamps.explain.12", "value1" to (if (f.lit.size == 1) message("teacher.lamps.explain.extra.6") else message("teacher.lamps.explain.extra.7", "f_lit_size" to (f.lit.size))), "num" to (num), "plural_num_lamp_" to (message("teacher.lamps.count.lamp", "count" to (num)))),
            )
            DARK -> Step(
                DARK, NO, t, t + f.seen + f.lit, setOf(f.seen) + f.lit,
                message("teacher.lamps.explain.13"),
                message("teacher.lamps.explain.14"),
            )
            else -> whatIf(g, f)
        }
    }

    private fun whatIf(g: LampsLogic.Geo, f: Fact): Step {
        val w = f.whatIf!!
        val lamp = w.assume == LAMP
        val cited = mutableSetOf<Int>()
        val chain = w.links.map { link ->
            cited += link.targets
            if (link.wall >= 0) cited += link.wall
            if (link.seen >= 0) cited += link.seen
            val num = if (link.wall >= 0) g.number[link.wall] else 0
            when (link.technique) {
                ROOM -> message("teacher.lamps.what_if.1", "num" to (num), "value2" to (if (link.targets.size == 1) message("teacher.lamps.what_if.extra.1") else message("teacher.lamps.what_if.extra.2", "link_targets_size" to (link.targets.size))))
                ONLY_LIGHT -> message("teacher.lamps.what_if.2")
                else -> message("teacher.lamps.what_if.3", "num" to (num), "its_num_" to (its(num)))
            }
        }
        cited += w.broken.at
        val brokenNum = g.number[w.broken.at]
        val end = when (w.broken.kind) {
            LampsLogic.OVER -> message("teacher.lamps.what_if.4", "brokenNum" to (brokenNum))
            LampsLogic.SHORT -> message("teacher.lamps.what_if.5", "brokenNum" to (brokenNum), "plural_brokenNum_lamp_" to (message("teacher.lamps.count.lamp", "count" to (brokenNum))))
            else -> message("teacher.lamps.what_if.6")
        }
        val start = if (lamp) message("teacher.lamps.what_if.7") else message("teacher.lamps.what_if.8")
        val then = (chain + end).let { parts ->
            if (parts.size == 1) parts[0] else parts.dropLast(1).joinedMessages(message("teacher.lamps.what_if.9")) + message("teacher.lamps.what_if.10") + parts.last()
        }
        val verdict = if (lamp) message("teacher.lamps.what_if.11") else message("teacher.lamps.what_if.12")
        return Step(
            WHAT_IF, f.value, setOf(w.square), setOf(w.square), cited - w.square,
            if (lamp) message("teacher.lamps.what_if.13") else message("teacher.lamps.what_if.14"),
            message("teacher.lamps.what_if.15", "start" to (start), "then" to (then), "verdict" to (verdict)),
        )
    }
}
