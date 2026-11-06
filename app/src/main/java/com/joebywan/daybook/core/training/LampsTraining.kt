package com.joebywan.daybook.core

import com.joebywan.daybook.core.i18n.message
import com.joebywan.daybook.puzzles.Lamps
import com.joebywan.daybook.puzzles.LampsLogic
import com.joebywan.daybook.puzzles.LampsState
import com.joebywan.daybook.puzzles.LampsTeacher

/**
 * Lamps' course: the player places the step's lamp or crosses out its squares. A lamp no answer keeps is the teacher's
 * mistake. The teacher reads crosses as notes, so in a game a wrong cross is never flagged; here a cross on a square
 * where the answer has a lamp is wrong (the board has one answer, so that is "no legal answer keeps this").
 */
val LampsTraining: Course = Course(
    puzzle = Lamps,
    explanations = linkedMapOf(
        LampsTeacher.ZERO to message("training.lamps_zero"),
        LampsTeacher.DONE to message("training.lamps_done"),
        LampsTeacher.ROOM to message("training.lamps_room"),
        LampsTeacher.ONLY_LIGHT to message("training.lamps_only_light"),
        LampsTeacher.STARVE to message("training.lamps_starve"),
        LampsTeacher.DARK to message("training.lamps_dark"),
        LampsTeacher.WHAT_IF to message("training.lamps_what_if"),
    ),
    // Standard reaches the four direct facts, Hard the starved number and the dark square (51 and 22 of 60), Expert the what-if.
    tierFor = tierByIndex({ LampsTraining.techniques }, hard = 4, expert = 6),
    // LampsTeacher.deduce is handed the walls, numbers and marks: never the answer.
    drill = MoveDrill(
        view = { (it as LampsState).marks.toList() },
        next = { s -> (s as LampsState).let { l -> LampsTeacher.deduce(l.size, l.walls, l.marks)?.let(LampsTeacher::deduction) } },
        every = { s, t -> (s as LampsState).let { l -> LampsTeacher.steps(l.size, l.walls, l.marks, t).map(LampsTeacher::deduction) } },
        wrong = { s -> (s as LampsState).marks.indices.any { s.marks[it] == LampsLogic.CROSS && s.solution[it] == '1' } },
    ),
    badge = "L",
    // The first practice seed whose Expert board's walk needs all seven (31 of the first 60). Pinned by LampsTrainingTest.
    examIndex = 4,
)
