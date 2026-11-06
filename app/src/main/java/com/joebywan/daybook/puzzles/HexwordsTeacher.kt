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

/**
 * Hexwords, reasoned from what the player can see: the letters still on the board and the points still needed. There is no
 * stored answer, so no step reads one.
 *
 * - [BIGGEST]: the biggest common word on the board still leaves the target in reach. Given by its crossword clue.
 * - [HOLD_BACK]: the biggest word would fall short, proved by the search; the biggest that does not is given by its clue, and
 *   the trap's hexes are marked. This is the game's lesson.
 * - [RARE]: only a word off the common list keeps the target in reach (the player has already spent common words). No clue
 *   exists for it, so the step gives its length and first hex.
 * - [UNCHECKED], the fallback: the search ran out of budget, so nothing is proved; the biggest word is offered and the step
 *   says it has not checked. It still reads no stored word.
 * - [MISTAKE]: an accepted-word search says [HexReach.No] from what is left. Never from [HexReach.Truncated]. The step names the
 *   earliest word after which the target could not be reached, and Show me puts it and those after it back.
 *
 * Only "Show me" spells a word. Highlight indices are hexes.
 */
internal object HexwordsTeacher {

    const val BIGGEST = "biggest"
    const val HOLD_BACK = "hold-back"
    const val RARE = "rare"
    const val UNCHECKED = "unchecked"
    const val MISTAKE = "mistake"

    val TECHNIQUES = listOf(BIGGEST, HOLD_BACK, RARE, UNCHECKED, MISTAKE)
    val FALLBACKS = listOf(UNCHECKED)

    /** Positions one hint may look at: about 50 ms on a desktop JVM. A step past it says so. */
    const val BUDGET = 300_000
    const val MAX_NUDGE = 70
    const val MAX_EXPLANATION = 200

    class Step(
        override val technique: String,
        /** The word to take; empty for a mistake. */
        val word: String,
        val path: List<Int>,
        /** For a mistake: how many words stay. */
        val keep: Int,
        nudge: CharSequence,
        explanation: CharSequence,
        override val focus: Set<Int>,
        override val cited: Set<Int>,
        override val targets: Set<Int>,
    ) : TeachStep {

        override val nudgeMessage: Message = nudge.asMessage()
        override val explanationMessage: Message = explanation.asMessage()
        override val nudge: String get() = nudgeMessage.english
        override val explanation: String get() = explanationMessage.english
    }

    fun teach(s: HexwordsState): Step? {
        if (s.solved) return null
        val nb = HexLogic.neighbours(s.rows)
        val n = s.letters.length
        val free = s.free
        val all = (1L shl n) - 1
        // A mistake is "no accepted word keeps it", so it is asked of every accepted word, from the whole board's roads.
        val accepted = HexSearch(HexLogic.roads(s.letters, nb, all, HexLogic.acceptedWords(s.contentLanguage)), n, BUDGET)
        if (accepted.reach(free, s.left) == HexReach.No) return mistake(s, accepted)

        val grid = HexLogic.roads(s.letters, nb, free, HexLogic.gridWords(s.contentLanguage))
        val search = HexSearch(grid, n, BUDGET)
        val biggest = grid.maxOfOrNull { it.points } ?: 0
        val trap = grid.firstOrNull { it.points == biggest }
        when (search.reach(free, s.left)) {
            is HexReach.Yes -> {
                for (r in grid.sortedByDescending { it.points }) {
                    if (search.reach(free xor r.mask, s.left - r.points) !is HexReach.Yes) continue
                    return if (r.points == biggest) big(r, s.contentLanguage) else holdBack(r, trap!!, s.contentLanguage)
                }
                return null // unreachable: a road's first word keeps its own road
            }
            HexReach.No -> {}
            HexReach.Truncated -> return trap?.let { unchecked(it) }
        }
        // Only an uncommon word keeps it.
        val wide = HexLogic.roads(s.letters, nb, free, HexLogic.acceptedWords(s.contentLanguage))
        val keep = wide.sortedByDescending { it.points }.firstOrNull {
            accepted.reach(free xor it.mask, s.left - it.points) is HexReach.Yes
        }
        return keep?.let { rare(it) } ?: wide.maxByOrNull { it.points }?.let { unchecked(it) }
    }

    private fun clueOf(word: String, contentLanguage: String) = message("teacher.hexwords.clue_of.extra.1", "value1" to (WheelWords.clue(word, contentLanguage)), "word_length" to (word.length))

    private fun big(r: HexRoad, contentLanguage: String) = Step(
        BIGGEST, r.word, r.path.toList(), 0,
        nudge = message("teacher.hexwords.big.1", "r_word_length" to (r.word.length)),
        explanation = message("teacher.hexwords.big.2", "r_points" to (r.points), "value2" to (clueOf(r.word, contentLanguage))),
        focus = setOf(r.path[0]), cited = emptySet(), targets = setOf(r.path[0]),
    )

    private fun holdBack(r: HexRoad, trap: HexRoad, contentLanguage: String) = Step(
        HOLD_BACK, r.word, r.path.toList(), 0,
        nudge = message("teacher.hexwords.hold_back.1"),
        explanation = message("teacher.hexwords.hold_back.2", "trap_word_length" to (trap.word.length), "r_points" to (r.points), "value3" to (clueOf(r.word, contentLanguage))),
        focus = setOf(r.path[0]), cited = trap.path.toSet(), targets = setOf(r.path[0]),
    )

    private fun rare(r: HexRoad) = Step(
        RARE, r.word, r.path.toList(), 0,
        nudge = message("teacher.hexwords.rare.1"),
        explanation = message("teacher.hexwords.rare.2", "r_word_length" to (r.word.length), "r_word_0_uppercaseChar_" to (r.word[0].uppercaseChar())),
        focus = setOf(r.path[0]), cited = emptySet(), targets = setOf(r.path[0]),
    )

    private fun unchecked(r: HexRoad) = Step(
        UNCHECKED, r.word, r.path.toList(), 0,
        nudge = message("teacher.hexwords.unchecked.1"),
        explanation = message("teacher.hexwords.unchecked.2", "r_word_length" to (r.word.length)),
        focus = setOf(r.path[0]), cited = emptySet(), targets = setOf(r.path[0]),
    )

    /** The earliest taken word that left the target out of reach; everything before it still reached. */
    private fun mistake(s: HexwordsState, accepted: HexSearch): Step {
        var free = (1L shl s.letters.length) - 1
        var need = s.target
        var k = 0
        for (t in s.taken) {
            for (i in t.path) free = free and (1L shl i).inv()
            need -= HexLogic.points(t.word.length)
            if (accepted.reach(free, need) == HexReach.No) break
            k++
        }
        val wrong = s.taken[minOf(k, s.taken.size - 1)]
        val word = wrong.word.uppercase()
        val hexes = s.taken.drop(k).flatMap { it.path }.toSet()
        return Step(
            MISTAKE, "", emptyList(), k,
            nudge = message("teacher.hexwords.mistake.1"),
            explanation = message("teacher.hexwords.mistake.2", "word" to (word), "value2" to (if (k < s.taken.size - 1) message("teacher.hexwords.mistake.extra.1") else "")),
            focus = wrong.path.toSet(), cited = hexes, targets = wrong.path.toSet(),
        )
    }

    fun deduction(s: HexwordsState): Deduction? {
        val step = teach(s) ?: return null
        if (step.technique == MISTAKE) {
            val keep = step.keep
            return Deduction(
                step,
                mistake = true,
                applyTo = { now -> (now as HexwordsState).let { if (it.taken.size > keep) it.withoutFrom(keep) else it } },
                reachedBy = { now -> (now as HexwordsState).taken.size <= keep },
            )
        }
        val had = s.taken.size
        return Deduction(
            step,
            fallback = step.technique in FALLBACKS,
            applyTo = { now ->
                val t = now as HexwordsState
                val free = t.free
                if (step.path.all { free shr it and 1L == 1L }) t.take(step.word, step.path) else t
            },
            reachedBy = { now -> (now as HexwordsState).taken.drop(had).any { it.word == step.word } },
        )
    }
}
