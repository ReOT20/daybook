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
import com.joebywan.daybook.core.Verdict

/**
 * Dwindle, reasoned from what the player can see: the letters, each tile's uses left, the lengths of the open dashes and the
 * words already tried. A word *can* be a goal if it is on the public list ([WheelWords.grid]), has an open dash, was not tried,
 * has exactly one way on the full grid (every goal does: the rules say so) and that way is all live tiles.
 *
 * - [THROUGH]: a tile whose uses left equal the words that can run through it: each must be a goal.
 * - [LENGTH]: a length whose open dashes equal the words of that length that can be goals.
 * - [CLUE]: neither. The fallback: reads a stored goal through the tile with the fewest spare words, and says so.
 *
 * Every step gives a clue, never the word, and glows no more than the one tile (or the dashes): the word's own tiles would
 * spell it. Only "Show me" enters it. No mistake exists: a word is a goal, a bonus word, or refused.
 *
 * Every loop walks squares and lists by index, so the same board gets the same hint on every platform.
 *
 * Highlight indices: tiles `row * width + col`; dash [DASH] + i for goal `i`.
 */
internal object DwindleTeacher {

    const val THROUGH = "through"
    const val LENGTH = "length"
    const val ALL_TILES = "all-tiles"
    const val CLUE = "clue"

    /** Every step [teach] can produce, simplest first. */
    val TECHNIQUES = listOf(THROUGH, LENGTH, ALL_TILES, CLUE)

    private const val MAX_NODES = 8_000
    private const val MAX_LENGTH = 7

    const val DASH = 1000
    const val MAX_NUDGE = 70
    const val MAX_EXPLANATION = 200

    class Step(
        override val technique: String,
        val word: String,
        nudge: CharSequence,
        explanation: CharSequence,
        override val focus: Set<Int>,
        override val cited: Set<Int> = emptySet(),
    ) : TeachStep {
        override val nudgeMessage: Message = nudge.asMessage()
        override val explanationMessage: Message = explanation.asMessage()
        override val nudge: String get() = nudgeMessage.english
        override val explanation: String get() = explanationMessage.english


        override val targets: Set<Int> get() = focus
    }

    /** A word that can be a goal, and its one way. */
    class Candidate(val word: String, val cells: List<Int>)

    /** The words that can still be goals, by length then list order. Nothing here can see the goals. */
    fun candidates(width: Int, height: Int, letters: String, counts: IntArray, openLengths: List<Int>, tried: List<String>, diagonal: Boolean = false, contentLanguage: String = "en"): List<Candidate> =
        openLengths.distinct().sorted().flatMap { n ->
            WheelWords.grid(n, contentLanguage).mapNotNull { w ->
                if (w in tried || !WheelWords.spellable(w, letters, contentLanguage)) return@mapNotNull null
                val v = DwindleLogic.ways(letters, width, height, w, diagonal = diagonal)
                if (v is Verdict.ExactlyOne && v.value.all { counts[it] > 0 }) Candidate(w, v.value) else null
            }
        }

    /** A forced word from visible facts only: no parameter through which a goal could arrive except the dashes' lengths. */
    fun deduce(width: Int, height: Int, letters: String, counts: IntArray, openLengths: List<Int>, tried: List<String>, diagonal: Boolean = false, contentLanguage: String = "en"): Step? {
        val all = candidates(width, height, letters, counts, openLengths, tried, diagonal, contentLanguage)
        for (t in counts.indices) {
            val c = counts[t]
            if (c == 0) continue
            val through = all.filter { t in it.cells }
            if (through.size != c) continue
            val word = through[0].word
            return Step(
                THROUGH, word,
                nudge = message("teacher.dwindle.deduce.1"),
                explanation = if (c == 1) message("teacher.dwindle.deduce.2", "value1" to (clueFor(word, contentLanguage)))
                    else message("teacher.dwindle.deduce.3", "c" to (c), "value2" to (clueFor(word, contentLanguage))),
                focus = setOf(t),
            )
        }
        for (n in openLengths.distinct().sorted()) {
            val fits = all.filter { it.word.length == n }
            val open = openLengths.count { it == n }
            if (fits.size != open) continue
            val word = fits[0].word
            return Step(
                LENGTH, word,
                nudge = message("teacher.dwindle.deduce.4", "n" to (n)),
                explanation = (if (open == 1) message("teacher.dwindle.deduce.5", "n" to (n)) else message("teacher.dwindle.deduce.6", "open" to (open), "n" to (n))) +
                    message("teacher.dwindle.deduce.7", "open" to (open), "value2" to (if (open == 1) message("teacher.dwindle.deduce.extra.1") else message("teacher.dwindle.deduce.extra.2")), "value3" to (clueFor(word, contentLanguage))),
                focus = emptySet(),
            )
        }
        val forced = (covers(all, counts, openLengths) as? Covers.Forced)?.candidate ?: return null
        val tile = forced.cells.minBy { counts[it] }
        return Step(
            ALL_TILES, forced.word,
            nudge = message("teacher.dwindle.deduce.8"),
            explanation = message("teacher.dwindle.deduce.9", "value1" to (clueFor(forced.word, contentLanguage))),
            focus = setOf(tile),
        )
    }

    /** What the search over every way of using up the tiles says. Only [Forced] names a word: running out of nodes is not a proof. */
    sealed interface Covers {
        class Forced(val candidate: Candidate) : Covers
        data object Open : Covers
        data object Truncated : Covers
    }

    /**
     * Every choice of candidates that uses each tile exactly its count and each open dash once: is some candidate in all of
     * them? Branches on a word through the tile with the least to spare, propagating "tile has exactly as many words left as uses"
     * and "tile used up". Gives up after [MAX_NODES], since a hint must be quick.
     */
    fun covers(all: List<Candidate>, counts: IntArray, openLengths: List<Int>): Covers {
        val inAll = BooleanArray(all.size) { true }
        var solutions = 0
        var nodes = 0
        val through = Array(counts.size) { t -> all.indices.filter { t in all[it].cells } }
        // status: 0 open, 1 chosen, 2 refused. dashes: open dashes per length.
        fun go(status: IntArray, left: IntArray, dashes: IntArray): Boolean {
            if (++nodes > MAX_NODES) return false
            var changed = true
            while (changed) {
                changed = false
                for (t in left.indices) {
                    val open = through[t].filter { status[it] == 0 }
                    if (left[t] < 0 || open.size < left[t]) return true
                    if (open.isEmpty()) continue
                    if (left[t] == 0 || open.size == left[t]) {
                        val take = left[t] > 0
                        for (w in open) {
                            status[w] = if (take) 1 else 2
                            if (take) {
                                for (c in all[w].cells) left[c]--
                                if (--dashes[all[w].word.length] < 0) return true
                            }
                        }
                        changed = true
                    }
                }
                for (w in all.indices) if (status[w] == 0 && dashes[all[w].word.length] == 0) {
                    status[w] = 2
                    changed = true
                }
            }
            val pick = left.indices.filter { left[it] > 0 }.minByOrNull { t -> through[t].count { status[it] == 0 } - left[t] }
            if (pick == null) {
                if (dashes.any { it != 0 }) return true
                solutions++
                for (w in all.indices) if (status[w] != 1) inAll[w] = false
                return inAll.any { it }
            }
            val w = through[pick].first { status[it] == 0 }
            val inStatus = status.copyOf().also { it[w] = 1 }
            val inLeft = left.copyOf().also { l -> for (c in all[w].cells) l[c]-- }
            val inDashes = dashes.copyOf().also { it[all[w].word.length]-- }
            if (inDashes[all[w].word.length] >= 0 && !go(inStatus, inLeft, inDashes)) return false
            status[w] = 2
            return go(status, left, dashes)
        }
        val dashes = IntArray(MAX_LENGTH + 1)
        for (n in openLengths) dashes[n]++
        val finished = go(IntArray(all.size), counts.copyOf(), dashes)
        if (!finished && nodes > MAX_NODES) return Covers.Truncated
        val i = inAll.indexOfFirst { it }
        return if (solutions > 0 && i >= 0) Covers.Forced(all[i]) else Covers.Open
    }

    fun teach(s: DwindleState): Step? {
        if (s.solved) return null
        val counts = s.counts()
        val open = s.goals.filter { it.word !in s.found }
        val tried = s.found + s.bonus
        val lengths = open.map { it.word.length }
        val step = deduce(s.width, s.height, s.letters, counts, lengths, tried, s.diagonal, s.contentLanguage) ?: clue(s, counts, open, lengths, tried)
        // A length step names dashes; only the state knows which of them are open.
        return if (step.technique != LENGTH) step else Step(
            step.technique, step.word, step.nudgeMessage, step.explanationMessage,
            s.goals.indices.filter { s.goals[it].word !in s.found && s.goals[it].word.length == step.word.length }.map { DASH + it }.toSet(),
        )
    }

    /** Nothing is forced: the tile with the fewest words to spare, with a goal through it given by its clue. Reads a stored goal. */
    private fun clue(s: DwindleState, counts: IntArray, open: List<DwindleGoal>, lengths: List<Int>, tried: List<String>): Step {
        val all = candidates(s.width, s.height, s.letters, counts, lengths, tried, s.diagonal, s.contentLanguage)
        val tile = counts.indices.filter { counts[it] > 0 }.minBy { t -> all.count { t in it.cells } - counts[t] }
        val word = open.first { tile in it.cells }.word
        val n = all.count { tile in it.cells }
        return Step(
            CLUE, word,
            nudge = message("teacher.dwindle.clue.1"),
            explanation = message("teacher.dwindle.clue.2", "n" to (n), "value2" to (clueFor(word, s.contentLanguage))),
            focus = setOf(tile),
        )
    }

    /** "“Lift” (5)": a crossword clue to [word] without the word. Only "Show me" enters it. */
    private fun clueFor(word: String, contentLanguage: String) = message("teacher.dwindle.clue_for.extra.1", "value1" to (WheelWords.clue(word, contentLanguage)), "word_length" to (word.length))

    /** The [Deduction] for the play screen; `Dwindle.teach` is just this. */
    fun deduction(s: DwindleState): Deduction? {
        val step = teach(s) ?: return null
        return Deduction(
            step,
            fallback = step.technique == CLUE,
            applyTo = { now -> (now as DwindleState).enter(step.word) },
            reachedBy = { now -> step.word in (now as DwindleState).found },
        )
    }
}
