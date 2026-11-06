package com.joebywan.daybook.core

import com.joebywan.daybook.core.i18n.message
import com.joebywan.daybook.puzzles.Lits
import com.joebywan.daybook.puzzles.LitsState
import com.joebywan.daybook.puzzles.LitsTeacher

/**
 * LITS' course, on [MoveDrill]: every step shades squares. The player shades them, or the squares of any other step of
 * the same technique (tap by tap or in a drag); a square
 * the teacher calls a mistake ends the run, which for LITS means no answer under the win check's rules keeps it (no
 * connectivity rule, PR #15). Boards come from [Lits.generateVerified] only, never [Lits.generate]'s unproved floor.
 */
val LitsTraining: Course = Course(
    puzzle = Lits,
    explanations = linkedMapOf(
        LitsTeacher.WHOLE_REGION to message("training.lits_whole_region"),
        LitsTeacher.OVERLAP to message("training.lits_overlap"),
        LitsTeacher.AVOID_BLOCK to message("training.lits_avoid_block"),
        LitsTeacher.LETTER_CLASH to message("training.lits_letter_clash"),
        LitsTeacher.NEIGHBOUR to message("training.lits_neighbour"),
        LitsTeacher.WHAT_IF to message("training.lits_what_if"),
    ),
    // Standard reaches the first four (the whole region on 10 of 60 boards), Hard the neighbour (28 of 60). The what-if
    // is drawn from Expert: Hard reaches it on 3 boards in 60, Expert on 25.
    tierFor = tierByIndex({ LitsTraining.techniques }, hard = 4, expert = 5),
    // LitsTeacher.deduce has no parameter through which the answer could reach it.
    drill = MoveDrill(
        view = { (it as LitsState).shaded },
        next = { s -> (s as LitsState).let { l -> LitsTeacher.deduce(l.width, l.height, l.region, l.shaded)?.let { Lits.deduction(it) } } },
        every = { s, t -> (s as LitsState).let { l -> LitsTeacher.instances(l.width, l.height, l.region, l.shaded, t).map { Lits.deduction(it) } } },
    ),
    badge = "L",
    // The first practice seed whose Expert board's walk needs all six. Pinned by LitsTrainingTest.
    examIndex = 5,
    generate = { seed, tier -> Lits.generateVerified(seed, tier) },
)
