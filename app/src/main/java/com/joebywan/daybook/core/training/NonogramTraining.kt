package com.joebywan.daybook.core

import com.joebywan.daybook.core.i18n.message
import com.joebywan.daybook.puzzles.Nonogram
import com.joebywan.daybook.puzzles.NonogramLogic
import com.joebywan.daybook.puzzles.NonogramState
import com.joebywan.daybook.puzzles.NonogramTeacher

/**
 * Nonogram's course, on [MoveDrill]: the player fills and crosses the step's squares (one at a time or in a sweep), and
 * a mark no picture fitting the clues keeps ends the run, a wrong cross included, as the hints flag it.
 */
val NonogramTraining: Course = Course(
    puzzle = Nonogram,
    explanations = linkedMapOf(
        NonogramTeacher.FULL to message("training.nonogram_full"),
        NonogramTeacher.FINISHED to message("training.nonogram_finished"),
        NonogramTeacher.OVERLAP to message("training.nonogram_overlap"),
        NonogramTeacher.LINE to message("training.nonogram_line"),
    ),
    // Standard boards reach all four (TRAINING_SPEC.md, Measured).
    tierFor = { Difficulty.STANDARD },
    // NonogramTeacher.deduce reads the clues and the marks only; the clues are printed on the board.
    // Any line's instance of the technique is right, not only the hint's pick.
    drill = MoveDrill(
        view = { (it as NonogramState).cells.toList() },
        // A player who never crosses fills a step's squares and is left at "k of n" with no idea why (a player's report).
        keepGoing = message("training.nonogram_keep_going"),
        next = { s ->
            (s as NonogramState).let { g ->
                NonogramTeacher.deduce(g.width, g.height, NonogramLogic.rowClues(g), NonogramLogic.colClues(g), g.cells)
                    ?.let { Nonogram.deduction(g, it) }
            }
        },
        every = { s, technique ->
            (s as NonogramState).let { g ->
                NonogramTeacher.steps(g.width, g.height, NonogramLogic.rowClues(g), NonogramLogic.colClues(g), g.cells)
                    .filter { it.technique == technique }.map { Nonogram.deduction(g, it) }
            }
        },
    ),
    // Not "#": a glyph starting with it names a drawn shape (AchievementBadge).
    badge = "N",
    // The first practice seed whose Expert board's walk needs all four. Pinned by NonogramTrainingTest.
    examIndex = 0,
)
