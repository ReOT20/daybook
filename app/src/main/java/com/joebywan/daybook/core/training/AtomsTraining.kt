package com.joebywan.daybook.core

import com.joebywan.daybook.core.i18n.message
import com.joebywan.daybook.puzzles.Atoms
import com.joebywan.daybook.puzzles.AtomsState
import com.joebywan.daybook.puzzles.AtomsTeacher

/**
 * Atoms' course on the move drill: the player lays the bond (or bonds) the technique forces, at the hint's atom or any
 * other atom it forces (`AtomsTeacher.steps`; a what-if is only the hint's). A bond past what the answer
 * has is the teacher's mistake and ends the run; one bond of a step that lays two plays on.
 *
 * "A group's only way out" is left out: no walk ever reaches it (0 of 30 boards on any tier; the simpler rungs always
 * settle those lines first), so there is no board to practise it on.
 */
val AtomsTraining: Course = Course(
    puzzle = Atoms,
    explanations = linkedMapOf(
        AtomsTeacher.ONE_NEIGHBOUR to message("training.atoms_one_neighbour"),
        AtomsTeacher.ALL_FORCED to message("training.atoms_all_forced"),
        AtomsTeacher.AT_LEAST_ONE to message("training.atoms_at_least_one"),
        AtomsTeacher.CROSSING to message("training.atoms_crossing"),
        AtomsTeacher.ISOLATION to message("training.atoms_isolation"),
        AtomsTeacher.WHAT_IF to message("training.atoms_what_if"),
    ),
    // Atoms.specFor: Standard needs up to "at least one" (7 of 60 boards reach it), Hard crossings and isolation, Expert
    // the what-if (15 of 60).
    tierFor = tierByIndex({ AtomsTraining.techniques }, hard = 3, expert = 5),
    // AtomsTeacher.deduce has no parameter through which the answer could reach it.
    drill = MoveDrill(
        view = { s -> (s as AtomsState).let { a -> List<Any?>(a.atoms.size) { null } + a.counts } },
        next = { s -> (s as AtomsState).let { a -> AtomsTeacher.deduce(a.atoms, a.pairs, a.counts)?.let(Atoms::deduction) } },
        every = { s, t -> (s as AtomsState).let { a -> AtomsTeacher.steps(a.atoms, a.pairs, a.counts, t).map(Atoms::deduction) } },
    ),
    badge = "=",
    examIndex = 1,
)
