package com.joebywan.daybook.core

import com.joebywan.daybook.core.i18n.message
import com.joebywan.daybook.puzzles.Pearls
import com.joebywan.daybook.puzzles.PearlsState
import com.joebywan.daybook.puzzles.PearlsTeacher

/**
 * Pearls' course on the move drill: the player draws the lines and crosses the edges the technique settles, at the hint's
 * square or any other the technique settles (`PearlsTeacher.steps`; a what-if is only the hint's). A line or a
 * cross the board's one loop disagrees with is the teacher's mistake and ends the run (sound: every board has one loop).
 *
 * "A pearl with two ways in" is left out: no walk ever reaches it (0 of 30 boards on any tier; the pearls' and squares'
 * other rules always settle it first), so there is no board to practise it on.
 */
val PearlsTraining: Course = Course(
    puzzle = Pearls,
    explanations = linkedMapOf(
        PearlsTeacher.BLACK_PEARL to message("training.pearls_black_pearl"),
        PearlsTeacher.WHITE_PEARL to message("training.pearls_white_pearl"),
        PearlsTeacher.WHITE_TURN to message("training.pearls_white_turn"),
        PearlsTeacher.ONE_WAY to message("training.pearls_one_way"),
        PearlsTeacher.DEAD_END to message("training.pearls_dead_end"),
        PearlsTeacher.SMALL_LOOP to message("training.pearls_small_loop"),
        PearlsTeacher.WHAT_IF to message("training.pearls_what_if"),
    ),
    // PearlsLogic.specFor: Standard the pearls' and squares' own rules, Hard must need the one loop, Expert a what-if.
    tierFor = tierByIndex({ PearlsTraining.techniques }, hard = 5, expert = 6),
    // PearlsTeacher.deduce is handed the pearls and the edges; the loop never reaches it.
    drill = MoveDrill(
        // A step names an edge as a square past the board's (PearlsTeacher.edgeIndex).
        view = { s -> (s as PearlsState).let { p -> List<Any?>(p.size * p.size) { null } + p.edges.toList() } },
        next = { s -> (s as PearlsState).let { p -> PearlsTeacher.deduce(p.size, p.pearls, p.edges)?.let { Pearls.deduction(p, it) } } },
        every = { s, t -> (s as PearlsState).let { p -> PearlsTeacher.steps(p.size, p.pearls, p.edges, t).map { Pearls.deduction(p, it) } } },
    ),
    badge = "O",
    examIndex = 0,
)
