package com.joebywan.daybook.core

import com.joebywan.daybook.core.i18n.message
import com.joebywan.daybook.puzzles.WhosWho
import com.joebywan.daybook.puzzles.WhosWhoState
import com.joebywan.daybook.puzzles.WhosWhoTeacher

/**
 * Who's Who's course, on [MoveDrill]: the teacher ticks and crosses the table. Every step is logic over the clues and
 * the marks (same, not, next to, left of, either); the words are only labels, so no board needs knowing anything.
 */
val WhosWhoTraining: Course = Course(
    puzzle = WhosWho,
    explanations = linkedMapOf(
        WhosWhoTeacher.CLUE to message("training.whoswho_clue"),
        WhosWhoTeacher.ONLY to message("training.whoswho_only"),
        WhosWhoTeacher.LINK to message("training.whoswho_link"),
        WhosWhoTeacher.HOUSE to message("training.whoswho_house"),
        WhosWhoTeacher.WHAT_IF to message("training.whoswho_what_if"),
    ),
    // The generator's tiers: Standard takes clues and lone items, Hard links and houses, Expert a what-if.
    tierFor = tierByIndex({ WhosWhoTraining.techniques }, hard = 2, expert = 4),
    // WhosWhoTeacher.deduce reads the clues, the marks and the words; never the answer.
    drill = MoveDrill(
        view = { s -> (s as WhosWhoState).let { w -> w.marks.chunked(w.n) } },
        next = { s -> (s as WhosWhoState).let { w -> WhosWhoTeacher.deduce(w, w.marks)?.let { WhosWho.deduction(it) } } },
        every = { s, t -> (s as WhosWhoState).let { w -> WhosWhoTeacher.steps(w, w.marks, t).map { WhosWho.deduction(it) } } },
    ),
    badge = "?",
    // Every Expert board's walk needs all five (60 of the first 60); the first. Pinned by WhosWhoTrainingTest.
    examIndex = 0,
)
