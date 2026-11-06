package com.joebywan.daybook.core

import com.joebywan.daybook.core.i18n.message
import com.joebywan.daybook.puzzles.DominoLogic
import com.joebywan.daybook.puzzles.DominoState
import com.joebywan.daybook.puzzles.DominoTeacher
import com.joebywan.daybook.puzzles.Dominoes

/**
 * Dominoes' course, on [MoveDrill]: every step lays one domino from the tray. The player lays it, or any other domino
 * the same technique settles (either way round for a double); a domino the teacher calls a mistake ends the run, and a
 * correct domino the technique does not settle plays on.
 */
val DominoesTraining: Course = Course(
    puzzle = Dominoes,
    explanations = linkedMapOf(
        DominoTeacher.ONLY_PARTNER to message("training.domino_only_partner"),
        DominoTeacher.ONLY_PLACE to message("training.domino_only_place"),
        DominoTeacher.ONLY_DOMINO to message("training.domino_only_domino"),
        DominoTeacher.REGION_VALUE to message("training.domino_region_value"),
        DominoTeacher.COUNTING to message("training.domino_counting"),
        DominoTeacher.WHAT_IF to message("training.domino_what_if"),
    ),
    // Each tier needs exactly one level (Dominoes.neededTechnique): Standard region values, Hard counting, Expert the what-if.
    tierFor = tierByIndex({ DominoesTraining.techniques }, hard = 4, expert = 5),
    // DominoTeacher.deduce reads the layout (everything but the answer) and the placements.
    drill = MoveDrill(
        next = { s -> (s as DominoState).let { d -> DominoTeacher.deduce(d.layout, d.placed)?.let { Dominoes.deduction(it) } } },
        every = { s, t -> (s as DominoState).let { d -> DominoTeacher.instances(d.layout, d.placed, t).map { Dominoes.deduction(it) } } },
    ),
    badge = "6|6",
    // The first practice seed whose Expert board's walk needs all six. Pinned by DominoesTrainingTest.
    examIndex = 2,
    generate = { seed, tier -> DominoLogic.generateVerified(seed, tier) },
)
