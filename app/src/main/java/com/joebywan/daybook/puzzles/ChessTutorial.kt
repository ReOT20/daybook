package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.i18n.message

import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.TutorialFrame

/**
 * The Mate walkthrough, played on the real board: tap a piece, tap its square, one emitted state per move
 * (selection and the promotion picker are the board's own transient state). Four hand-built positions, each with
 * one forced mate that `ChessTutorialTest` proves with an independent minimax. Highlight indices: squares 0..63
 * (a1 = 0, rank * 8 + file), promotion buttons 100 + kind (queen = 105).
 */
internal object ChessTutorial {
    /** White rook a1, Black king g8 behind f7 g7 h7: Ra8#. */
    const val BACK_RANK = "6k1/5ppp/8/8/8/8/8/R3K3 w - -"

    /** Kings a5 / a8 and a rook on d1: 1.Kb6 Kb8 (the only move) 2.Rd8#. */
    const val LADDER = "k7/8/8/K7/8/8/8/3R4 w - -"

    /** Pawn h7, Kf5 against Kh6: 1.h8=Q#, while h8=R+ lets the king out to g7. */
    const val PROMOTION = "8/7P/7k/5K2/8/8/8/8 w - -"

    /** Free play: Black's king in the corner, a king and rook to mate it in 2. */
    const val FREE = "7k/8/8/5K2/8/8/8/R7 w - -"

    fun sq(name: String) = (name[1] - '1') * 8 + (name[0] - 'a')

    private fun state(start: String, mateIn: Int, vararg played: String) =
        ChessState(start, "", mateIn, played.toList(), played.size)

    /** Accepts exactly the board that [start] reaches by playing [played]. */
    private fun only(start: String, mateIn: Int, vararg played: String): (PuzzleState) -> Boolean {
        val want = state(start, mateIn, *played)
        return { it is ChessState && it.start == want.start && it.mateIn == mateIn && it.played == want.played }
    }

    private fun hl(strong: Set<String> = emptySet(), soft: Set<String> = emptySet(), warning: Boolean = false) =
        BoardHighlight(strong.map(::sq).toSet(), soft.map(::sq).toSet(), warning)

    val frames: List<TutorialFrame> by lazy {
        val queen = 100 + 5
        listOf(
            TutorialFrame(
                state(BACK_RANK, 1),
                message("puzzle.chess.tutorial.1.caption"),
                hl(strong = setOf("g8"), soft = setOf("f7", "g7", "h7", "a1")),
            ),
            TutorialFrame(
                state(BACK_RANK, 1),
                message("puzzle.chess.tutorial.2.caption"),
                hl(strong = setOf("a1"), soft = setOf("a8")),
                accepts = only(BACK_RANK, 1, "a1a8"),
                retry = message("puzzle.chess.tutorial.2.retry"),
                done = message("puzzle.chess.tutorial.2.done"),
            ),
            TutorialFrame(
                state(LADDER, 2),
                message("puzzle.chess.tutorial.3.caption"),
                hl(strong = setOf("d1"), soft = setOf("d7")),
                accepts = only(LADDER, 2, "d1d7"),
                retry = message("puzzle.chess.tutorial.3.retry"),
                done = message("puzzle.chess.tutorial.3.done"),
            ),
            TutorialFrame(
                state(LADDER, 2),
                message("puzzle.chess.tutorial.4.caption"),
                hl(strong = setOf("a5"), soft = setOf("b6", "a7", "b7")),
                accepts = only(LADDER, 2, "a5b6"),
                retry = message("puzzle.chess.tutorial.4.retry"),
                done = message("puzzle.chess.tutorial.4.done"),
            ),
            TutorialFrame(
                state(LADDER, 2, "a5b6"),
                message("puzzle.chess.tutorial.5.caption"),
                hl(strong = setOf("d1"), soft = setOf("d8", "a7", "b7", "c7")),
                accepts = only(LADDER, 2, "a5b6", "d1d8"),
                retry = message("puzzle.chess.tutorial.5.retry"),
                done = message("puzzle.chess.tutorial.5.done"),
            ),
            TutorialFrame(
                state(PROMOTION, 1),
                message("puzzle.chess.tutorial.6.caption"),
                hl(strong = setOf("h7"), soft = setOf("h8", "g7")).let { it.copy(soft = it.soft + queen) },
                accepts = only(PROMOTION, 1, "h7h8q"),
                retry = message("puzzle.chess.tutorial.6.retry"),
                done = message("puzzle.chess.tutorial.6.done"),
            ),
            TutorialFrame(
                state(FREE, 2),
                message("puzzle.chess.tutorial.7.caption"),
                freePlay = true,
            ),
        )
    }
}
