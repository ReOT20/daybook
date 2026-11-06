package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.i18n.message
import com.joebywan.daybook.core.i18n.literalMessage
import com.joebywan.daybook.core.listedMessage
import com.joebywan.daybook.core.joinedMessages
import com.joebywan.daybook.core.numberWordMessage

import com.joebywan.daybook.core.i18n.Message
import com.joebywan.daybook.core.asMessage

import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.TeachStep
import kotlin.math.abs

/**
 * Mate, taught by the prover. Indices in `focus` / `cited` / `targets` are the board's own squares, 0..63
 * (a1 = 0, rank * 8 + file), plus the four promotion-picker buttons at 100 + kind (2 N, 3 B, 4 R, 5 Q).
 *
 * [deduce] takes only the visible position and the number of moves left; the prover is the reasoning, so there is no
 * stored answer in reach and NO FALLBACK EXISTS to measure (as Nonogram): if the player's moves still allow a forced
 * mate, [ChessRules.forcedMate] names every key and one of them is explained. A step is MATE_IN_ONE, FORCING_KEY or
 * QUIET_KEY (the same, for a key that is not a check).
 *
 * A mistake is a move after which the defence has an answer to every plan ([ChessRules.forcedMate] is `None`: proved,
 * never `Truncated`, and never "differs from the data"). Mistakes outrank steps. A legal move that keeps a forced
 * mate is never one, so a second mating line, or a non-shortest one, is accepted. Every loop is in move or square
 * order; the one hash map in the prover is only looked up.
 */
internal object ChessTeacher {
    const val MATE_IN_ONE = "mate-in-one"
    const val FORCING_KEY = "forcing-key"
    const val QUIET_KEY = "quiet-key"
    const val MISTAKE = "mistake"
    val TECHNIQUES = listOf(MATE_IN_ONE, FORCING_KEY, QUIET_KEY)

    const val MAX_EXPLANATION = 200
    const val MAX_NUDGE = 70

    /** [move] is the key to play, or (for a mistake) the move to take back. */
    class Step(
        override val technique: String,
        val move: Int,
        val keys: List<Int>,
        override val focus: Set<Int>,
        override val cited: Set<Int>,
        nudge: CharSequence,
        explanation: CharSequence,
    ) : TeachStep {
        override val nudgeMessage: Message = nudge.asMessage()
        override val explanationMessage: Message = explanation.asMessage()
        override val nudge: String get() = nudgeMessage.english
        override val explanation: String get() = explanationMessage.english


        override val targets: Set<Int>
            get() {
                val squares = setOf(ChessRules.from(move), ChessRules.to(move))
                val promo = ChessRules.promo(move)
                return if (promo != 0 && technique != MISTAKE) squares + (100 + promo) else squares
            }
    }

    private val KIND = listOf("", message("teacher.chess.kind.1"), message("teacher.chess.kind.2"), message("teacher.chess.kind.3"), message("teacher.chess.kind.4"), message("teacher.chess.kind.5"), message("teacher.chess.kind.6"))
    private fun side(white: Boolean) = if (white) message("teacher.chess.side.1") else message("teacher.chess.side.2")
    /** Black's moves are written "...Kg8"; White's plain. [p] is the position the move is made in. */
    private fun dots(p: ChessPosition) = if (p.whiteToMove) "" else "..."

    /** "Rd1", "pawn d4": a piece and its square, for naming what covers a square. */
    private fun named(p: ChessPosition, s: Int): Message {
        val k = abs(p.pieceAt(s))
        return if (k == 1) message("teacher.chess.named.1", "ChessRules_name_s_" to (ChessRules.name(s))) else literalMessage("${ChessPosition.LETTERS[k]}${ChessRules.name(s)}")
    }

    // ---- replaying what the player has done ------------------------------------------------------------

    /** The board the player faces. A mistake is the first move that gave the mate up, with the board around it. */
    class Line(
        val position: ChessPosition,
        val remaining: Int,
        val mated: Boolean,
        val mistakeIndex: Int,
        val before: ChessPosition?,
        val bad: Int,
        val after: ChessPosition?,
        val movesLeftAfter: Int,
    )

    fun replay(s: ChessState): Line? {
        var pos = ChessPosition.fromFen(s.start)
        for (i in s.played.indices) {
            val m = ChessRules.parseUci(pos, s.played[i]) ?: return null
            val c = ChessRules.play(pos, m)
            val rem = s.mateIn - (i + 1)
            if (ChessRules.isCheckmate(c)) return Line(c, 0, true, -1, null, 0, null, 0)
            val reply = if (rem > 0) ChessRules.defence(c, rem) else null
            val next = if (reply != null) ChessRules.play(c, reply) else null
            val keeps = next != null &&
                ChessRules.forcedMate(next, rem, Int.MAX_VALUE) !is ChessRules.Mate.None
            if (!keeps) return Line(c, rem, false, i, pos, m, c, rem)
            pos = next
        }
        return Line(pos, s.mateIn - s.played.size, false, -1, null, 0, null, 0)
    }

    // ---- the whole hint ----------------------------------------------------------------------------------

    fun teach(s: ChessState): Deduction? {
        val line = replay(s) ?: return null
        if (line.mated) return null
        val step = (if (line.mistakeIndex >= 0) mistake(line) else deduce(line.position, line.remaining)) ?: return null
        return deduction(s, step, if (step.technique == MISTAKE) line.mistakeIndex else s.played.size)
    }

    /**
     * [step] on [s] as the play screen's [Deduction]: a key played as move [index], or the move at [index] taken back
     * (Training builds its practices' steps with it too).
     */
    internal fun deduction(s: ChessState, step: Step, index: Int = s.played.size): Deduction {
        val base = s.played
        return Deduction(
            step,
            mistake = step.technique == MISTAKE,
            applyTo = { now ->
                now as ChessState
                if (step.technique == MISTAKE) now.copy(played = now.played.take(index), moves = minOf(now.moves, index))
                else now.copy(played = now.played + ChessRules.uci(step.move), moves = now.moves + 1)
            },
            reachedBy = { now ->
                now as ChessState
                if (step.technique == MISTAKE) now.played.take(index + 1) != base.take(index + 1)
                else now.played.size > index && now.played.take(index) == base &&
                    step.keys.any { ChessRules.uci(it) == now.played[index] }
            },
        )
    }

    private fun mistake(l: Line): Step {
        val before = l.before!!
        val after = l.after!!
        val san = ChessRules.san(before, l.bad)
        val who = side(!before.whiteToMove)
        val why = when {
            ChessRules.isStalemate(after) -> message("teacher.chess.mistake.1", "san" to (san), "who" to (who))
            l.movesLeftAfter <= 0 -> message("teacher.chess.mistake.2", "san" to (san))
            else -> {
                val r = ChessRules.defence(after, l.movesLeftAfter)
                val reply = if (r == null) "" else message("teacher.chess.mistake.3", "dots_after_" to (dots(after)), "ChessRules_san_after_r_" to (ChessRules.san(after, r)))
                message("teacher.chess.mistake.4", "san" to (san), "who" to (who), "reply" to (reply), "l_movesLeftAfter" to (l.movesLeftAfter))
            }
        }
        val f = setOf(ChessRules.from(l.bad), ChessRules.to(l.bad))
        return Step(MISTAKE, l.bad, emptyList(), f, f,
            message("teacher.chess.mistake.5"), fit(why))
    }

    /**
     * One step for the side to move, who has [remaining] moves; null when no forced mate is left. It explains [prefer]
     * when that is a key (Training's verdict on the key the player chose), else its own pick: a check first.
     */
    fun deduce(position: ChessPosition, remaining: Int, prefer: Int? = null): Step? {
        val res = ChessRules.forcedMate(position, remaining, Int.MAX_VALUE) as? ChessRules.Mate.Forced ?: return null
        if (res.keys.isEmpty()) return null
        val white = position.whiteToMove
        val key = prefer?.takeIf { it in res.keys }
            ?: res.keys.firstOrNull { ChessRules.inCheck(ChessRules.play(position, it)) } ?: res.keys.first()
        val c = ChessRules.play(position, key)
        val king = ChessRules.kingSq(c.sq, !white)
        val piece = message("teacher.chess.deduce.1", "value1" to (KIND[abs(position.pieceAt(ChessRules.from(key)))]), "value2" to (ChessRules.name(ChessRules.from(key))))
        return if (res.inMoves == 1) {
            val cited = mutableSetOf(king)
            val text = mateText(position, key, c, king, cited)
            Step(MATE_IN_ONE, key, res.keys, setOf(king, ChessRules.from(key)), cited,
                message("teacher.chess.deduce.2"), fit(text))
        } else {
            val check = ChessRules.inCheck(c)
            val text = forcingText(position, key, c, res.inMoves - 1, check)
            Step(if (check) FORCING_KEY else QUIET_KEY, key, res.keys, setOf(ChessRules.from(key)), setOf(king, ChessRules.to(key)),
                message("teacher.chess.deduce.3", "piece" to (piece)), fit(text))
        }
    }

    /** Never longer than the panel. */
    private fun fit(t: Message): Message =
        if (t.length <= MAX_EXPLANATION) t else Message { language ->
            if (language == "en") t.english.take(MAX_EXPLANATION - 1).trimEnd() + "." else t.resolve(language)
        }

    // ---- why a move is mate ------------------------------------------------------------------------------

    private fun mateText(p: ChessPosition, m: Int, c: ChessPosition, king: Int, cited: MutableSet<Int>): Message {
        val white = p.whiteToMove
        val san = ChessRules.san(p, m)
        val ownSign = if (white) -1 else 1
        // The king's own square must not shield the squares behind it from a checking line.
        val bare = ChessPosition(IntArray(64) { if (it == king) 0 else c.pieceAt(it) }, c.whiteToMove, c.castling, c.ep)
        val own = ArrayList<Int>()
        val covered = ArrayList<Pair<Int, Int>>() // square, first piece covering it
        for (s in 0..63) {
            if (s == king || abs(s % 8 - king % 8) > 1 || abs(s / 8 - king / 8) > 1) continue
            if (c.pieceAt(s) * ownSign > 0) own.add(s)
            else ChessRules.attackers(bare, s, white).firstOrNull()?.let { covered.add(s to it) }
        }
        val checkers = ChessRules.attackers(c, king, white)
        val end = if (checkers.size > 1) message("teacher.chess.mate_text.1")
        else message("teacher.chess.mate_text.2")
        cited.addAll(own); cited.addAll(covered.map { it.first }); cited.addAll(covered.map { it.second })
        cited.addAll(checkers)
        val ownPart = if (own.isEmpty()) literalMessage("") else message("teacher.chess.mate_text.3", "value1" to (own.joinToString(" ") { ChessRules.name(it) }))
        val covPart = covered.map { message("teacher.chess.mate_text.4", "value1" to (ChessRules.name(it.first)), "named_c_it_second_" to (named(c, it.second))) }.joinedMessages(", ")
        val full = message("teacher.chess.mate_text.5", "san" to (san), "value2" to (listOf(ownPart, covPart).filter { it.isNotEmpty() }.joinedMessages("; ")), "end" to (end))
        if (full.length <= MAX_EXPLANATION) return full
        val mid = message("teacher.chess.mate_text.6", "san" to (san), "own_size" to (own.size), "covered_size" to (covered.size), "end" to (end))
        if (mid.length <= MAX_EXPLANATION) return mid
        return message("teacher.chess.mate_text.7", "san" to (san), "end" to (end))
    }

    // ---- why a key forces mate ---------------------------------------------------------------------------

    private fun forcingText(p: ChessPosition, m: Int, c: ChessPosition, left: Int, check: Boolean): Message {
        val san = ChessRules.san(p, m)
        val replies = ChessRules.legalMoves(c)
        val who = side(c.whiteToMove)
        // the likeliest escapes first: checks, captures, then the rest, each group in move order
        val checks = replies.filter { ChessRules.inCheck(ChessRules.play(c, it)) }
        val rest = replies.filter { it !in checks }
        val ranked = checks + rest.filter { c.pieceAt(ChessRules.to(it)) != 0 } + rest.filter { c.pieceAt(ChessRules.to(it)) == 0 }
        val shown = ranked.take(2).mapNotNull { r ->
            val d = ChessRules.play(c, r)
            val res = ChessRules.forcedMate(d, left, Int.MAX_VALUE) as? ChessRules.Mate.Forced ?: return@mapNotNull null
            val first = dots(c) + ChessRules.san(c, r)
            if (res.inMoves == 1) literalMessage("$first ${ChessRules.san(d, res.keys.first())}")
            else message("teacher.chess.forcing_text.1", "first" to (first), "res_inMoves" to (res.inMoves))
        }
        val n = replies.size
        val head = (if (check) message("teacher.chess.forcing_text.2", "san" to (san)) else message("teacher.chess.forcing_text.3", "san" to (san))) +
            message("teacher.chess.forcing_text.4", "who" to (who), "value2" to (if (n == 1) message("teacher.chess.forcing_text.extra.2") else message("teacher.chess.forcing_text.extra.3", "n" to (n)))) +
            (if (n == 1) message("teacher.chess.forcing_text.5") else message("teacher.chess.forcing_text.6")) +
            if (left == 1) message("teacher.chess.forcing_text.7") else message("teacher.chess.forcing_text.8", "left" to (left))
        return head + if (shown.isEmpty()) literalMessage(".") else message("teacher.chess.forcing_text.9", "shown_joinToString_" to (shown.joinedMessages(", ")))
    }
}
