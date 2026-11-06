package com.joebywan.daybook.core

import com.joebywan.daybook.core.i18n.message
import com.joebywan.daybook.puzzles.Kings
import com.joebywan.daybook.puzzles.KingsState
import com.joebywan.daybook.puzzles.KingsTeacher

/**
 * Kings' course, the first on [MoveDrill]: the teacher crowns and crosses rather than placing digits. A practice is a
 * board walked by the hints' own steps; the player makes the step, or any other step of the same technique on the board
 * (crown the square, or cross out every square it names, one at a time or in a sweep), and a cross or crown the teacher
 * calls a mistake ends the run.
 */
val KingsTraining: Course = Course(
    puzzle = Kings,
    explanations = linkedMapOf(
        KingsTeacher.LAST_SQUARE to message("training.kings_last_square"),
        KingsTeacher.LOCKED to message("training.kings_locked"),
        KingsTeacher.WOULD_EMPTY to message("training.kings_would_empty"),
        KingsTeacher.CONFINED to message("training.kings_confined"),
        KingsTeacher.WHAT_IF to message("training.kings_what_if"),
    ),
    // Kings.fitsTier: Standard reaches up to "would empty", Hard needs two colours confined, Expert a what-if (10 of 30).
    tierFor = tierByIndex({ KingsTraining.techniques }, hard = 3, expert = 4),
    // KingsTeacher.deduce has no parameter through which the answer could reach it.
    drill = MoveDrill(
        view = { (it as KingsState).marks },
        next = { s -> (s as KingsState).let { k -> KingsTeacher.deduce(k.size, k.region, k.marks)?.let { Kings.deduction(k, it) } } },
        every = { s, t -> (s as KingsState).let { k -> KingsTeacher.steps(k.size, k.region, k.marks, t).map { Kings.deduction(k, it) } } },
    ),
    badge = "K",
    // The first practice seed whose Expert board's walk needs all five. Pinned by KingsTrainingTest.
    examIndex = 1,
)
