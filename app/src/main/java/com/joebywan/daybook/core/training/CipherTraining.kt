package com.joebywan.daybook.core

import com.joebywan.daybook.core.i18n.message
import com.joebywan.daybook.puzzles.Cipher
import com.joebywan.daybook.puzzles.CipherState
import com.joebywan.daybook.puzzles.CipherTeacher

/**
 * Cipher's course, on [MoveDrill]: a step gives one letter its digit (select the letter, then tap the digit). "The only
 * letter left for a digit" is not here: no walk of the practice seeds ever reaches it (docs/TRAINING_SPEC.md).
 */
val CipherTraining: Course = Course(
    puzzle = Cipher,
    explanations = linkedMapOf(
        CipherTeacher.LEADING to message("training.cipher_leading"),
        CipherTeacher.COLUMN to message("training.cipher_column"),
        CipherTeacher.PAIR to message("training.cipher_pair"),
        CipherTeacher.CHAIN to message("training.cipher_chain"),
        CipherTeacher.WHAT_IF to message("training.cipher_what_if"),
    ),
    // The generator's tiers: Standard reaches the first three, Hard every column together, Expert the what-if.
    tierFor = tierByIndex({ CipherTraining.techniques }, hard = 3, expert = 4),
    // CipherTeacher.deduce is handed the sum and the placed digits; the answer cannot reach it.
    drill = MoveDrill(
        next = { s -> (s as CipherState).let { c -> CipherTeacher.deduce(c.sum, c.digits)?.let { Cipher.deduction(c, it) } } },
        every = { s, t -> (s as CipherState).let { c -> CipherTeacher.steps(c.sum, c.digits, t).map { Cipher.deduction(c, it) } } },
    ),
    badge = "A+B",
    // The first practice seed whose Expert walk needs all five (16 of the first 60). Pinned by CipherTrainingTest.
    examIndex = 0,
)
