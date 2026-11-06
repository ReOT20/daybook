package com.joebywan.daybook.core

import com.joebywan.daybook.core.i18n.message
import com.joebywan.daybook.puzzles.Pipes
import com.joebywan.daybook.puzzles.PipesState
import com.joebywan.daybook.puzzles.PipesTeacher
import com.joebywan.daybook.puzzles.PuzzleState

/**
 * Pipes' course on the move drill: the player turns a tile the technique settles until it sits its one way round. Any
 * tile of that technique counts (`PipesTeacher.steps`), not only the hint's.
 *
 * Judged its own way, because a tap turns a tile a quarter at a time: turning such a tile round to its way passes through
 * turns that are wrong, and those are the player getting there, not a mistake. So a tile the technique settles is never
 * judged wrong (it is right once it reads its turn); any other tile the player turns is wrong when it runs full and no
 * finished network has it that way, the teacher's own mistake ("no legal answer keeps this rotation", never "differs from
 * the stored answer"). A turn elsewhere that some network keeps, or a dry one, plays on.
 */
private object PipesDrill : Drill {
    // PipesTeacher.deduce and steps read only the tiles as they lie: no answer goes in.
    private val moves = MoveDrill(next = { s -> (s as PipesState).let { p -> PipesTeacher.deduce(p.width, p.height, p.cells)?.let(Pipes::deduction) } })

    override fun walk(start: PuzzleState) = moves.walk(start)

    // ponytail: re-derived per judged state (a Net run, a few ms); memoise on the practice if it ever shows.
    private fun instances(p: Course.Practice): List<Deduction> {
        val was = p.board as PipesState
        return listOf(p.step as Deduction) + PipesTeacher.steps(was.width, was.height, was.cells, p.step.technique).map(Pipes::deduction)
    }

    // The tile the player turned to its way: the hint's, or another the technique settles.
    override fun made(p: Course.Practice, after: PuzzleState): TeachStep = instances(p).firstOrNull { it.isReached(after) } ?: p.step

    override fun judge(puzzle: PuzzleType, p: Course.Practice, after: PuzzleState): Boolean? {
        val was = p.board as PipesState
        val now = after as PipesState
        val instances = instances(p)
        val settling = instances.flatMapTo(HashSet()) { it.targets } // membership only
        val wet = Pipes.filled(now)
        val wrong = now.cells.indices.any { c ->
            c !in settling && now.cells[c] != was.cells[c] && c in wet &&
                PipesTeacher.solve(now.width, now.height, now.cells, c to now.cells[c]) == null
        }
        return when {
            wrong -> false
            instances.any { it.isReached(now) } -> true
            else -> null
        }
    }
}

val PipesTraining: Course = Course(
    puzzle = Pipes,
    explanations = linkedMapOf(
        PipesTeacher.BORDER to message("training.pipes_border"),
        PipesTeacher.SET_NEIGHBOUR to message("training.pipes_set_neighbour"),
        PipesTeacher.WHICHEVER_WAY to message("training.pipes_whichever_way"),
        PipesTeacher.NO_LOOP to message("training.pipes_no_loop"),
    ),
    // The generator gates no tier by technique, only by size: Standard's small boards reach all four (no loop on 8 of 60).
    tierFor = { Difficulty.STANDARD },
    drill = PipesDrill,
    badge = "+",
    examIndex = 1,
)
