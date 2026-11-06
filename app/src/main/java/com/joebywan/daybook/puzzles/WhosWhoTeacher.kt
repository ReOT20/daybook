package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.i18n.message
import com.joebywan.daybook.core.i18n.literalMessage
import com.joebywan.daybook.core.listedMessage
import com.joebywan.daybook.core.joinedMessages
import com.joebywan.daybook.core.numberWordMessage

import com.joebywan.daybook.core.i18n.Message
import com.joebywan.daybook.core.asMessage

import com.joebywan.daybook.core.cap
import com.joebywan.daybook.core.TeachStep
import com.joebywan.daybook.core.listed
import com.joebywan.daybook.puzzles.WhosWhoLogic.Break
import com.joebywan.daybook.puzzles.WhosWhoLogic.Step

/**
 * Who's Who, reasoned the way a person reasons it: one clue at a time against what the table already shows, and on
 * Expert one supposition followed a few steps.
 *
 * The ladder is [WhosWhoLogic.deduce], the generator's own, so every shipped board is finished by it and there is
 * no fallback to measure. This file only says each step in words and marks where to look.
 *
 * [deduce] is handed the board as the player sees it (the clues and the marks) and has no parameter through which
 * the answer could arrive. The answer is read in one place, [mistake], to decide which of the player's marks cannot
 * be right; sound because every shipped board has exactly one answer.
 *
 * Techniques, simplest first: [CLUE] (a clue that names a person settles them), [ONLY] (one item left in a cell, or
 * one person left for an item), [LINK] (two things a clue ties together, carried through what is known), [HOUSE]
 * (next to and left of against the houses still possible), [WHAT_IF] (suppose a tick; a few steps later something has
 * nothing left).
 *
 * ## Highlight indices
 * Table cell `person * columns + column`; clue `k` is [CLUE_BASE] `+ k`.
 */
internal object WhosWhoTeacher {

    const val CLUE = "clue"
    const val ONLY = "only"
    const val LINK = "link"
    const val HOUSE = "house"
    const val WHAT_IF = "what-if"
    const val MISTAKE = "mistake"

    /** Every technique in the order [deduce] prefers them, for reports. */
    val TECHNIQUES = listOf(CLUE, ONLY, LINK, HOUSE, WHAT_IF)

    const val CLUE_BASE = 1000

    /** What the hint panel has room for (the test pins it; the popover scrolls past four lines). */
    const val MAX_NUDGE = 70
    const val MAX_EXPLANATION = 260

    fun name(technique: Int): String = TECHNIQUES[technique]

    /** One step: marks to tick, cross or clear (mark indices), where to look, and why. */
    class Lesson(
        override val technique: String,
        val ticks: List<Int> = emptyList(),
        val crosses: List<Int> = emptyList(),
        val clears: List<Int> = emptyList(),
        override val focus: Set<Int>,
        override val cited: Set<Int>,
        override val targets: Set<Int>,
        nudge: CharSequence,
        explanation: CharSequence,
    ) : TeachStep {

        override val nudgeMessage: Message = nudge.asMessage()
        override val explanationMessage: Message = explanation.asMessage()
        override val nudge: String get() = nudgeMessage.english
        override val explanation: String get() = explanationMessage.english
    }

    fun teach(s: WhosWhoState): Lesson? {
        if (s.solved) return null
        mistake(s)?.let { return it }
        return deduce(s, s.marks)
    }

    // ---- mistakes -------------------------------------------------------------------------------

    /** The first mark, in order, that the board's one answer disagrees with. */
    private fun mistake(s: WhosWhoState): Lesson? {
        val n = s.n
        for (at in s.marks.indices) {
            val mark = s.marks[at]
            if (mark == WhosWhoLogic.EMPTY) continue
            val cell = at / n
            val person = cell / s.columns
            val column = cell % s.columns
            val slot = at % n
            val right = s.answer[column][person] == slot
            if (mark == WhosWhoLogic.TICK && right || mark == WhosWhoLogic.CROSS && !right) continue
            val ref = (column + 1) * n + slot
            val who = WhosWhoText.name(s, person)
            return Lesson(
                technique = MISTAKE,
                clears = listOf(at),
                focus = setOf(cell),
                cited = emptySet(),
                targets = setOf(cell),
                nudge = message("teacher.whoswho.mistake.1"),
                explanation = if (mark == WhosWhoLogic.TICK) {
                    message("teacher.whoswho.mistake.2", "who" to (who), "WhosWhoLogic_base_s_ref_" to (WhosWhoText.base(s, ref)))
                } else {
                    message("teacher.whoswho.mistake.3", "who" to (who), "WhosWhoLogic_base_s_ref_" to (WhosWhoText.base(s, ref)))
                },
            )
        }
        return null
    }

    // ---- reasoning ------------------------------------------------------------------------------

    /**
     * The next step reasoned from the clues and the [marks] alone, or null when nothing can be settled (a finished
     * board, or one with a wrong mark). [s] supplies the words; its answer is not read.
     */
    fun deduce(s: WhosWhoState, marks: String): Lesson? {
        val puzzle = WhosWhoLogic.puzzleOf(s)
        val grid = WhosWhoLogic.gridOf(s.n, s.columns, marks)
        if (WhosWhoLogic.broken(puzzle, grid) != null) return null
        val step = WhosWhoLogic.deduce(puzzle, grid) ?: WhosWhoLogic.whatIfStep(puzzle, grid, WhosWhoLogic.LONG_LINKS) ?: return null
        return lesson(s, grid, step)
    }

    /**
     * Every step of [technique] on the board, reasoned from the clues and the [marks] alone, as [deduce] would say each
     * (Training counts any of them as finding the technique). A what-if is every open slot whose supposition runs out
     * within [WhosWhoLogic.LONG_LINKS] simpler steps.
     */
    fun steps(s: WhosWhoState, marks: String, technique: String): List<Lesson> {
        val puzzle = WhosWhoLogic.puzzleOf(s)
        val grid = WhosWhoLogic.gridOf(s.n, s.columns, marks)
        if (WhosWhoLogic.broken(puzzle, grid) != null) return emptyList()
        val found = when (technique) {
            CLUE -> WhosWhoLogic.clueSteps(puzzle, grid)
            ONLY -> WhosWhoLogic.onlySteps(puzzle, grid)
            LINK -> WhosWhoLogic.linkSteps(puzzle, grid)
            HOUSE -> WhosWhoLogic.houseSteps(puzzle, grid)
            WHAT_IF -> sequence {
                for (c in 0 until s.n * s.columns) for (slot in 0 until s.n) {
                    if (grid.placed[c] < 0 && grid.cand[c] shr slot and 1 == 1) {
                        WhosWhoLogic.suppose(puzzle, grid, c * s.n + slot, WhosWhoLogic.LONG_LINKS)?.let { yield(it) }
                    }
                }
            }
            else -> emptySequence()
        }
        return found.map { lesson(s, grid, it) }.toList()
    }

    private fun cellsOf(s: WhosWhoState, marks: List<Int>) = marks.map { it / s.n }.toSet()

    private fun lesson(s: WhosWhoState, grid: WhosWhoLogic.Grid, step: Step): Lesson {
        val technique = name(step.technique)
        val targets = cellsOf(s, step.ticks + step.crosses)
        val clue = if (step.clue >= 0) setOf(CLUE_BASE + step.clue) else emptySet()
        val cited = clue + citedCells(s, grid, step)
        val nudge = when (step.technique) {
            WhosWhoLogic.ONLY -> if (step.person >= 0) message("teacher.whoswho.lesson.1", "s_names_step_person_" to (WhosWhoText.name(s, step.person))) else message("teacher.whoswho.lesson.2")
            WhosWhoLogic.WHAT_IF -> message("teacher.whoswho.lesson.3", "value1" to (WhosWhoText.name(s, cellOf(s, step.supposed) / s.columns)))
            else -> message("teacher.whoswho.lesson.4", "step_clue_1" to (step.clue + 1))
        }
        return Lesson(
            technique = technique,
            ticks = step.ticks,
            crosses = step.crosses,
            focus = targets + clue,
            cited = cited - targets,
            targets = targets,
            nudge = nudge,
            explanation = explain(s, grid, step),
        )
    }

    private fun cellOf(s: WhosWhoState, mark: Int) = mark / s.n

    /** Cells the reasoning leans on: the known holder's cell of the clue's other item, where there is one. */
    private fun citedCells(s: WhosWhoState, grid: WhosWhoLogic.Grid, step: Step): Set<Int> {
        if (step.partner < 0 || step.partner / s.n == 0) return emptySet()
        val column = step.partner / s.n - 1
        return (0 until s.n).filter { grid.has(it, step.partner) }.map { it * s.columns + column }.toSet()
    }

    // ---- words ----------------------------------------------------------------------------------

    private fun person(s: WhosWhoState, mark: Int) = WhosWhoText.name(s, mark / s.n / s.columns)

    private fun itemOf(s: WhosWhoState, mark: Int) = (mark / s.n % s.columns + 1) * s.n + mark % s.n

    private fun clueLine(s: WhosWhoState, k: Int) = message("teacher.whoswho.clue_line.1", "k_1" to (k + 1), "value2" to (WhosWhoText.clueText(s, k)))

    /** "cross the cat off for Ann", "cross the fish and the bird off for Ann", "cross the baker off for Ben and Dev". */
    private fun crossOff(s: WhosWhoState, crosses: List<Int>): Message {
        val people = crosses.map { person(s, it) }.distinctBy { it.english }
        val things = crosses.map { WhosWhoText.thing(s, itemOf(s, it)) }.distinctBy { it.english }
        return message("teacher.whoswho.cross_off.1", "listed_things_" to (listedMessage(things)), "listed_people_" to (listedMessage(people)))
    }

    /** What a NEXT / LEFT clue asks of the partner of the side that holds [item]: "next to", "somewhere right of". */
    private fun relation(s: WhosWhoState, clue: WhoClue, item: Int): Message = when {
        clue.kind == WhosWhoLogic.NEXT -> message("teacher.whoswho.relation.1")
        item == clue.a -> message("teacher.whoswho.relation.2")
        else -> message("teacher.whoswho.relation.3")
    }

    private fun explain(s: WhosWhoState, grid: WhosWhoLogic.Grid, step: Step): Message {
        val n = s.n
        return when (step.technique) {
            WhosWhoLogic.CLUE -> {
                val action = if (step.ticks.isNotEmpty()) {
                    message("teacher.whoswho.explain.1", "value1" to (WhosWhoText.thing(s, itemOf(s, step.ticks[0]))), "person_s_step_ticks_0_" to (person(s, step.ticks[0])))
                } else {
                    crossOff(s, step.crosses).cap() + "."
                }
                message("teacher.whoswho.explain.extra.1", "clueLine_s_step_clue_" to (clueLine(s, step.clue)), "action" to (action))
            }
            WhosWhoLogic.ONLY -> {
                val mark = step.ticks[0]
                val who = person(s, mark)
                val item = itemOf(s, mark)
                val column = item / n - 1
                if (step.person >= 0) {
                    message("teacher.whoswho.explain.2", "value1" to (WhosWhoText.noun(s.groups[column])), "who" to (who), "WhosWhoLogic_has_s_item_" to (WhosWhoText.has(s, item)))
                } else {
                    message("teacher.whoswho.explain.3", "who" to (who), "value2" to (WhosWhoText.thing(s, item)), "WhosWhoLogic_has_s_item_" to (WhosWhoText.has(s, item)))
                }
            }
            WhosWhoLogic.LINK -> {
                val clue = s.clues[step.clue]
                val line = clueLine(s, step.clue)
                when {
                    step.ticks.isNotEmpty() -> {
                        val who = WhosWhoText.name(s, step.person)
                        message("teacher.whoswho.explain.4", "line" to (line), "who" to (who), "value3" to (WhosWhoText.has(s, step.partner)), "value4" to (WhosWhoText.has(s, step.item)))
                    }
                    step.person >= 0 && clue.kind == WhosWhoLogic.NOT -> {
                        val who = WhosWhoText.name(s, step.person)
                        message("teacher.whoswho.explain.5", "line" to (line), "who" to (who), "value3" to (WhosWhoText.has(s, step.partner)), "crossOff_s_step_crosses_" to (crossOff(s, step.crosses)))
                    }
                    step.person >= 0 -> {
                        val who = WhosWhoText.name(s, step.person)
                        message("teacher.whoswho.explain.6", "line" to (line), "who" to (who), "value3" to (WhosWhoText.has(s, clue.a)), "crossOff_s_step_crosses_" to (crossOff(s, step.crosses)))
                    }
                    clue.kind == WhosWhoLogic.SAME -> {
                        val people = listedMessage(step.crosses.map { person(s, it) })
                        message("teacher.whoswho.explain.7", "line" to (line), "value2" to (WhosWhoText.thing(s, step.partner).cap()), "people" to (people), "crossOff_s_step_crosses_" to (crossOff(s, step.crosses)))
                    }
                    else -> {
                        val people = listedMessage(step.crosses.map { person(s, it) })
                        message("teacher.whoswho.explain.8", "line" to (line), "value2" to (WhosWhoText.thing(s, clue.b).cap()), "value3" to (WhosWhoText.thing(s, clue.c)), "people" to (people), "crossOff_s_step_crosses_" to (crossOff(s, step.crosses)))
                    }
                }
            }
            WhosWhoLogic.HOUSE -> {
                val clue = s.clues[step.clue]
                val line = clueLine(s, step.clue)
                val rel = relation(s, clue, step.item)
                val partner = WhosWhoText.base(s, step.partner)
                if (step.person >= 0) {
                    val who = WhosWhoText.name(s, step.person)
                    val houses = listedMessage(step.crosses.map { message("teacher.whoswho.explain.9", "it_n_1" to (it % n + 1)) }, "or")
                    val known = if (step.item / n == 0) "" else message("teacher.whoswho.explain.10", "who" to (who), "value2" to (WhosWhoText.has(s, step.item)))
                    message("teacher.whoswho.explain.extra.2", "line" to (line), "value2" to (message("teacher.whoswho.explain.11", "known" to (known), "partner" to (partner), "rel" to (rel), "houses" to (houses), "crossOff_s_step_crosses_" to (crossOff(s, step.crosses))).cap()))
                } else {
                    val people = step.crosses.map { person(s, it) }
                    val them = if (people.size == 1) people[0] else message("teacher.whoswho.explain.12")
                    message("teacher.whoswho.explain.13", "line" to (line), "listed_people_" to (listedMessage(people)), "partner" to (partner), "rel" to (rel), "them" to (them), "crossOff_s_step_crosses_" to (crossOff(s, step.crosses)))
                }
            }
            else -> {
                val supposed = step.supposed
                val who = person(s, supposed)
                val chain = if (step.chain.size <= WhosWhoLogic.MAX_LINKS) step.chain.map { link(s, it) } else {
                    val clues = step.chain.mapNotNull { if (it.clue >= 0) it.clue + 1 else null }.distinct().sorted()
                    listOf(message("teacher.whoswho.explain.14", "value1" to (listedMessage(clues.map { "$it" }))))
                }
                val end = when (val b = step.broke) {
                    is Break.EmptyCell -> message("teacher.whoswho.explain.15", "s_names_b_person_" to (WhosWhoText.name(s, b.person)), "value2" to (WhosWhoText.noun(s.groups[b.column])))
                    is Break.NoHolder -> message("teacher.whoswho.explain.16", "value1" to (WhosWhoText.base(s, (b.column + 1) * n + b.slot)))
                    is Break.Clue -> message("teacher.whoswho.explain.17", "b_clue_1" to (b.clue + 1))
                    null -> message("teacher.whoswho.explain.18")
                }
                val steps = listedMessage(chain + end, oxford = true)
                message("teacher.whoswho.explain.19", "who" to (who), "value2" to (WhosWhoText.has(s, itemOf(s, supposed))), "steps" to (steps), "crossOff_s_step_crosses_" to (crossOff(s, step.crosses)))
            }
        }
    }

    /** One step of a what-if, as a clause: "Ann is the baker (clue 2)", "Ben can't have the cat (clue 4)". */
    private fun link(s: WhosWhoState, step: Step): Message {
        val why = if (step.clue >= 0) message("teacher.whoswho.link.1", "step_clue_1" to (step.clue + 1)) else ""
        if (step.ticks.isNotEmpty()) {
            val mark = step.ticks[0]
            return message("teacher.whoswho.link.extra.1", "person_s_mark_" to (person(s, mark)), "value2" to (WhosWhoText.has(s, itemOf(s, mark))), "why" to (why))
        }
        val people = step.crosses.map { person(s, it) }.distinctBy { it.english }
        val items = step.crosses.map { itemOf(s, it) }.distinct()
        val what = WhosWhoText.base(s, items[0]) + if (items.size > 1) message("teacher.whoswho.link.2", "value1" to (listedMessage(items.drop(1).map { WhosWhoText.thing(s, it) }, "or"))) else literalMessage("")
        return message("teacher.whoswho.link.3", "listed_people_" to (listedMessage(people)), "what" to (what), "why" to (why))
    }

}
