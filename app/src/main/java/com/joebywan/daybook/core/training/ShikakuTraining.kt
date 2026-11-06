package com.joebywan.daybook.core

import com.joebywan.daybook.core.i18n.message
import com.joebywan.daybook.puzzles.Shikaku
import com.joebywan.daybook.puzzles.ShikakuState
import com.joebywan.daybook.puzzles.ShikakuTeacher

/**
 * Shikaku's course, on [MoveDrill]: every step is one rectangle to draw. The player draws it, or any other rectangle the
 * same technique settles (drag corner to corner); a rectangle the teacher calls a mistake ends the run, and a correct
 * rectangle the technique does not settle plays on.
 */
val ShikakuTraining: Course = Course(
    puzzle = Shikaku,
    explanations = linkedMapOf(
        ShikakuTeacher.ONLY_FITS to message("training.shikaku_only_fits"),
        ShikakuTeacher.ONLY_REACHES to message("training.shikaku_only_reaches"),
        ShikakuTeacher.COMMON_CELLS to message("training.shikaku_common_cells"),
        ShikakuTeacher.WHAT_IF to message("training.shikaku_what_if"),
    ),
    // Standard boards reach the first two; Hard is the easiest tier that reaches the other two.
    tierFor = tierByIndex({ ShikakuTraining.techniques }, hard = 2, expert = 4),
    // ShikakuTeacher.deduce has no parameter through which the answer could reach it.
    drill = MoveDrill(
        next = { s -> (s as ShikakuState).let { k -> ShikakuTeacher.deduce(k.width, k.height, k.clues, k.blocks)?.let { Shikaku.deduction(it) } } },
        every = { s, t -> (s as ShikakuState).let { k -> ShikakuTeacher.instances(k.width, k.height, k.clues, k.blocks, t).map { Shikaku.deduction(it) } } },
    ),
    badge = "2x3",
    // The first practice seed whose Expert board's walk needs all four. Pinned by ShikakuTrainingTest.
    examIndex = 15,
    generate = { seed, tier -> Shikaku.generateVerified(seed, tier) },
)
