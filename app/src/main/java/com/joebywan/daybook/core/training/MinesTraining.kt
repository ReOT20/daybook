package com.joebywan.daybook.core

import com.joebywan.daybook.core.i18n.message
import com.joebywan.daybook.puzzles.Mines
import com.joebywan.daybook.puzzles.MinesState
import com.joebywan.daybook.puzzles.MinesTeacher

/**
 * Mines' course: the player reveals the step's safe squares or flags its mines. A flag on a safe square is the teacher's
 * mistake; a mine revealed loses the board in a game, and here it is simply wrong: the verdict ends the board (no longer
 * interactive, so its Try again is not offered) and the next board is one tap away.
 */
val MinesTraining: Course = Course(
    puzzle = Mines,
    explanations = linkedMapOf(
        MinesTeacher.ALL_FOUND to message("training.mines_all_found"),
        MinesTeacher.ALL_MINES to message("training.mines_all_mines"),
        MinesTeacher.SUBSET to message("training.mines_subset"),
        MinesTeacher.PAIR to message("training.mines_pair"),
        MinesTeacher.EDGE to message("training.mines_edge"),
        MinesTeacher.WHAT_IF to message("training.mines_what_if"),
    ),
    // Standard needs single numbers, Hard two numbers (subset 57, pair 33 of 60), Expert the count against the numbers (57)
    // and the what-if (4 of 60, still under a quarter of a second a board). "Counting the mines left" is left out: no
    // walk reaches it.
    tierFor = tierByIndex({ MinesTraining.techniques }, hard = 2, expert = 4),
    // MinesTeacher.deduce is handed the revealed numbers, the mines hit and the flags: never where a hidden mine is. The
    // walk's flags are its own sound steps, so they count as found mines here: otherwise the teacher (which never trusts a
    // player's flag) can offer a subset on a board where the flags already show a single number's step.
    drill = MoveDrill(
        view = { (it as MinesState).marks.toList() },
        next = { s ->
            (s as MinesState).let { m ->
                MinesTeacher.deduce(m.width, m.height, MinesTeacher.visibleNumbers(m), known(m), known(m), m.total)?.let(MinesTeacher::deduction)
            }
        },
        every = { s, t ->
            (s as MinesState).let { m ->
                MinesTeacher.steps(m.width, m.height, MinesTeacher.visibleNumbers(m), known(m), m.total, t).map(MinesTeacher::deduction)
            }
        },
        wrong = { it.failed },
    ),
    badge = "#mine",
    // The first practice seed whose Expert board's walk needs all six (1 of the first 60). Pinned by MinesTrainingTest.
    examIndex = 44,
)

/** The mines a practice board shows: those hit and the walk's flags (its own sound steps, never a guess). */
private fun known(m: MinesState) = BooleanArray(m.width * m.height) { it in m.bumps || m.isFlag(it) }
