package com.joebywan.daybook.core

import com.joebywan.daybook.core.i18n.message
import com.joebywan.daybook.puzzles.Mambo
import com.joebywan.daybook.puzzles.MamboState
import com.joebywan.daybook.puzzles.MamboTeacher

/**
 * Mambo's course, on [MoveDrill]: the player places the step's suns or moons (a tap cycles moon, sun, empty), and a
 * symbol no legal answer keeps (`Mambo.mistakes`) ends the run.
 */
val MamboTraining: Course = Course(
    puzzle = Mambo,
    explanations = linkedMapOf(
        MamboTeacher.PAIR to message("training.mambo_pair"),
        MamboTeacher.SANDWICH to message("training.mambo_sandwich"),
        MamboTeacher.LINK to message("training.mambo_link"),
        MamboTeacher.QUOTA to message("training.mambo_quota"),
        MamboTeacher.ALMOST to message("training.mambo_almost"),
        MamboTeacher.WHAT_IF to message("training.mambo_what_if"),
    ),
    // MamboTeacher.depth: Standard falls to the first four, every Hard board needs one short, every Expert a what-if.
    tierFor = tierByIndex({ MamboTraining.techniques }, hard = 4, expert = 5),
    // MamboTeacher.deduce has no parameter through which the answer could reach it.
    // A sun is two taps, through a moon that may be wrong: a wrong verdict waits as long as the board's own caption.
    // Any instance of the technique on the position is right, not only the hint's pick.
    drill = MoveDrill(
        view = { (it as MamboState).cells },
        settleMillis = Mambo.SETTLE_MILLIS,
        next = { s -> (s as MamboState).let { m -> MamboTeacher.deduce(m.size, m.cells, m.links)?.let { Mambo.deduction(m, it) } } },
        every = { s, technique ->
            (s as MamboState).let { m ->
                MamboTeacher.steps(m.size, m.cells, m.links).filter { it.technique == technique }.map { Mambo.deduction(m, it) }.toList()
            }
        },
    ),
    badge = "&",
    // The first practice seed whose Expert board's walk needs all six. Pinned by MamboTrainingTest.
    examIndex = 0,
)
