package com.joebywan.daybook.core

import com.joebywan.daybook.core.i18n.message
import com.joebywan.daybook.puzzles.Tents
import com.joebywan.daybook.puzzles.TentsLogic
import com.joebywan.daybook.puzzles.TentsState
import com.joebywan.daybook.puzzles.TentsTeacher

/**
 * Tents' course: the player pitches the step's tents or lays its grass. A tent no answer keeps is the teacher's mistake.
 * The teacher reads grass as notes, so in a game wrong grass is never flagged; here grass on a square where the answer
 * has a tent is wrong (the board has one answer, so that is "no legal answer keeps this"). The game's hints are unchanged.
 */
val TentsTraining: Course = Course(
    puzzle = Tents,
    explanations = linkedMapOf(
        TentsTeacher.NO_TREE to message("training.tents_no_tree"),
        TentsTeacher.LINE_DONE to message("training.tents_line_done"),
        TentsTeacher.LINE_FULL to message("training.tents_line_full"),
        TentsTeacher.ONE_SPOT to message("training.tents_one_spot"),
        TentsTeacher.SHARED to message("training.tents_shared"),
        TentsTeacher.SERVED to message("training.tents_served"),
    ),
    // Standard reaches no tree and the lines, Hard one spot, shared and served (52, 3 and 11 of 60). "Grass around a tent" is
    // left out: a tent's grass sprouts by itself (TentsState.seen), so that step is always already shown.
    tierFor = tierByIndex({ TentsTraining.techniques }, hard = 3, expert = 6),
    // TentsTeacher.deduce is handed the trees, clues and marks as shown: never the answer.
    drill = MoveDrill(
        view = { (it as TentsState).seen },
        next = { s ->
            (s as TentsState).let { t -> TentsTeacher.deduce(t.size, t.trees, t.rowCounts, t.colCounts, t.seen)?.let(TentsTeacher::deduction) }
        },
        every = { s, k ->
            (s as TentsState).let { t -> TentsTeacher.steps(t.size, t.trees, t.rowCounts, t.colCounts, t.seen, k).map(TentsTeacher::deduction) }
        },
        wrong = { s -> (s as TentsState).marks.indices.any { s.marks[it] == TentsLogic.GRASS && s.solution[it] } },
    ),
    badge = "T",
    // The first practice seed whose Expert board's walk needs all six (5 of the first 60). Pinned by TentsTrainingTest.
    examIndex = 34,
)
