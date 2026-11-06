package com.joebywan.daybook.core

import com.joebywan.daybook.core.i18n.message
import com.joebywan.daybook.puzzles.Tally
import com.joebywan.daybook.puzzles.TallyLogic
import com.joebywan.daybook.puzzles.TallyState
import com.joebywan.daybook.puzzles.TallyTeacher

/**
 * Tally's course, on [MoveDrill]: the player removes or circles the step's numbers (one at a time or in a sweep), and a
 * mark the board's one answer disagrees with ends the run, a wrong circle included, as the hints flag it.
 */
val TallyTraining: Course = Course(
    puzzle = Tally,
    explanations = linkedMapOf(
        TallyTeacher.DONE to message("training.tally_done"),
        TallyTeacher.TOO_BIG to message("training.tally_too_big"),
        TallyTeacher.TOO_MUCH to message("training.tally_too_much"),
        TallyTeacher.ALL_LEFT to message("training.tally_all_left"),
        TallyTeacher.WAYS to message("training.tally_ways"),
        TallyTeacher.WHAT_IF to message("training.tally_what_if"),
    ),
    // TallyLogic.accepts: Standard needs only the four shortcuts, every Hard board the ways, every Expert a what-if.
    tierFor = tierByIndex({ TallyTraining.techniques }, hard = 4, expert = 5),
    // TallyTeacher.deduce reads the digits, the targets and the marks only; the targets are printed on the board.
    // Any line's instance of the technique (or any what-if within the hint's reach) is right, not only the hint's pick.
    drill = MoveDrill(
        view = { (it as TallyState).marks.toList() },
        next = { s ->
            (s as TallyState).let { t ->
                TallyTeacher.deduce(t.size, TallyLogic.digitArray(t), TallyLogic.targets(t), t.marks)?.let { Tally.deduction(t, it) }
            }
        },
        every = { s, technique ->
            (s as TallyState).let { t ->
                TallyTeacher.steps(t.size, TallyLogic.digitArray(t), TallyLogic.targets(t), t.marks)
                    .filter { it.technique == technique }.map { Tally.deduction(t, it) }
            }
        },
    ),
    badge = "T",
    // The first practice seed whose Expert board's walk needs all six. Pinned by TallyTrainingTest.
    examIndex = 0,
)
