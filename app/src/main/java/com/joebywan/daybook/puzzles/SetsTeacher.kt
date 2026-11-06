package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.TeachStep
import com.joebywan.daybook.core.asMessage
import com.joebywan.daybook.core.cap
import com.joebywan.daybook.core.i18n.Message
import com.joebywan.daybook.core.i18n.message
import com.joebywan.daybook.core.joinedMessages

/**
 * Sets, taught the way a person finds a set: pick two cards, and work out trait by trait what the
 * third must be.
 *
 * Any two cards fix their third exactly — for each trait, two alike need a third alike, two
 * different need the one value left over — so finding a set is not a search over triples but a
 * walk over four traits, and that walk is what the explanation shows. It defines one card, and the
 * hint only ever opens a pair whose card is on the table.
 *
 * Sets has no hidden answer: every card is face up, and the sets still to find follow from the
 * cards and the ones already claimed. So [deduce] and [mistake] reason from the board as the player
 * sees it (cards, found sets, picked cards, and the pick the board just rejected), and there is no
 * fallback — an unfound set is always one the walk can explain.
 *
 * Steps, simplest first:
 * 1. [MISTAKE] — the pick the board just rejected, with the trait that broke it; or picked cards
 *    that cannot end in a new set, which are let go.
 * 2. [CONTINUE] — the player has picked one or two cards that do lead to a new set; build on them.
 * 3. [FRESH] — a set none of whose cards has been used yet.
 * 4. [REUSE] — only sets through a tinted card are left; the nudge says a used card still counts.
 *
 * Orders never come from a hash: sets are ranked by an explicit key, then by index.
 */
internal object SetsTeacher {

    const val MISTAKE = "mistake"
    const val CONTINUE = "continue"
    const val FRESH = "fresh"
    const val REUSE = "reuse"

    /** Every technique, for reports. There is no fallback; see the class comment. */
    val TECHNIQUES = listOf(MISTAKE, CONTINUE, FRESH, REUSE)

    /**
     * One step. [claim] is the set to claim (sorted), empty for a mistake; [release] the picked
     * cards a mistake asks the player to let go, empty when there is nothing to take back.
     */
    class Step(
        override val technique: String,
        val claim: List<Int> = emptyList(),
        val release: List<Int> = emptyList(),
        /** For a rejected pick: the three cards, so [isReached] can tell when the player moved on. */
        val rejected: List<Int> = emptyList(),
        override val focus: Set<Int>,
        override val cited: Set<Int>,
        override val targets: Set<Int>,
        nudge: CharSequence,
        explanation: CharSequence,
    ) : TeachStep {
        override val nudgeMessage: Message = nudge.asMessage()
        override val explanationMessage: Message = explanation.asMessage()
        override val nudge: String get() = nudgeMessage.english
        override val explanation: String get() = explanationMessage.english


        val mistake: Boolean get() = technique == MISTAKE
    }

    // ---- words -----------------------------------------------------------------------------------

    private val counts = listOf(message("teacher.sets.counts.1"), message("teacher.sets.counts.2"), message("teacher.sets.counts.3"))
    private val shadings = listOf(message("teacher.sets.shadings.1"), message("teacher.sets.shadings.2"), message("teacher.sets.shadings.3"))
    private val colourNames = listOf(message("teacher.sets.colour_names.1"), message("teacher.sets.colour_names.2"), message("teacher.sets.colour_names.3"))
    private val shapes = listOf(message("teacher.sets.shapes.1"), message("teacher.sets.shapes.2"), message("teacher.sets.shapes.3"))
    private val shapesWithArticle = listOf(message("teacher.sets.shapes_with_article.1"), message("teacher.sets.shapes_with_article.2"), message("teacher.sets.shapes_with_article.3"))

    /** "three outlined violet ovals": count, shading, colour, shape — the order the walk runs in. */
    fun describeMessage(c: Card): Message =
        message("teacher.sets.describe_message.extra.1", "counts_c_count_" to (counts[c.count]), "shadings_c_shading_" to (shadings[c.shading]), "colourNames_c_colour_" to (colourNames[c.colour]), "shapes_c_shape_" to (shapes[c.shape]), "value5" to (if (c.count > 0) message("teacher.sets.describe.1") else ""))

    /** The one card that makes a set with [a] and [b]. */
    fun third(a: Card, b: Card): Card {
        fun v(x: Int, y: Int) = if (x == y) x else 3 - x - y
        return Card(v(a.count, b.count), v(a.shape, b.shape), v(a.shading, b.shading), v(a.colour, b.colour))
    }

    /** One clause per trait, in [describe]'s order: what [a] and [b] say the third must be. */
    private fun walk(a: Card, b: Card): Message {
        val t = third(a, b)
        // Two different values are named low to high whichever card came first, so the same pair
        // always reads the same way: "one and two need three", never "two and one".
        fun clause(x: Int, y: Int, need: Int, names: List<Message>, alike: Message) =
            if (x == y) alike else message("teacher.sets.clause.1", "names_minOf_x_y_" to (names[minOf(x, y)]), "names_maxOf_x_y_" to (names[maxOf(x, y)]), "names_need_" to (names[need]))
        val parts = listOf(
            clause(a.count, b.count, t.count, counts, message("teacher.sets.walk.1", "counts_a_count_" to (counts[a.count]))),
            clause(a.shading, b.shading, t.shading, shadings, message("teacher.sets.walk.2", "shadings_a_shading_" to (shadings[a.shading]))),
            clause(a.colour, b.colour, t.colour, colourNames, message("teacher.sets.walk.3", "colourNames_a_colour_" to (colourNames[a.colour]))),
            clause(a.shape, b.shape, t.shape, shapesWithArticle, message("teacher.sets.walk.4", "shapes_a_shape_" to (shapes[a.shape]))),
        )
        return parts.joinedMessages("; ").cap()
    }

    /** The walk from [a] and [b], ending on the card it defines. Used by hints and the walkthrough. */
    fun walkToThirdMessage(a: Card, b: Card): Message = message("teacher.sets.walk_to_third.1", "walk_a_b_" to (walk(a, b)), "describe_third_a_b_" to (describeMessage(third(a, b))))

    private val traitNames = listOf(message("teacher.sets.trait_names.1"), message("teacher.sets.trait_names.2"), message("teacher.sets.trait_names.3"), message("teacher.sets.trait_names.4"))

    private fun traitValue(c: Card, trait: Int) = when (trait) {
        0 -> c.count
        1 -> c.shading
        2 -> c.colour
        else -> c.shape
    }

    /** Traits, in [traitNames] order, on which [trio] is neither all alike nor all different. */
    fun brokenTraits(trio: List<Card>): List<Int> = (0..3).filter { t ->
        trio.map { traitValue(it, t) }.distinct().size == 2
    }

    /** "two are striped and one is solid", for a trait on which two of [trio] agree. */
    fun whyBrokenMessage(trio: List<Card>, trait: Int): Message {
        val values = trio.map { traitValue(it, trait) }
        val pair = values.first { v -> values.count { it == v } == 2 }
        val odd = values.first { it != pair }
        return when (trait) {
            0 -> message("teacher.sets.why_broken.1", "counts_pair_" to (counts[pair]), "if_pair_0_s_else_" to (if (pair > 0) message("teacher.sets.why_broken_message.extra.1") else ""), "counts_odd_" to (counts[odd]))
            1 -> message("teacher.sets.why_broken.2", "shadings_pair_" to (shadings[pair]), "shadings_odd_" to (shadings[odd]))
            2 -> message("teacher.sets.why_broken.3", "colourNames_pair_" to (colourNames[pair]), "colourNames_odd_" to (colourNames[odd]))
            else -> message("teacher.sets.why_broken.4", "shapes_pair_" to (shapes[pair]), "shapesWithArticle_odd_" to (shapesWithArticle[odd]))
        }
    }

    /** The whole sentence for a trio that is not a set. Shared with the walkthrough. */
    fun notASetMessage(trio: List<Card>): Message {
        val broken = brokenTraits(trio)
        val first = broken.first()
        val also = broken.drop(1).map { traitNames[it] }
        val tail = when (also.size) {
            0 -> ""
            1 -> message("teacher.sets.not_a_set.1", "also_0_cap_" to (also[0].cap()))
            else -> message("teacher.sets.not_a_set.2", "value1" to (also.dropLast(1).joinedMessages(", ").cap()), "also_last_" to (also.last()))
        }
        return message("teacher.sets.not_a_set.3", "value1" to (whyBrokenMessage(trio, first).cap()), "traitNames_first_" to (traitNames[first]), "tail" to (tail))
    }

    fun describe(c: Card): String = describeMessage(c).english
    fun walkToThird(a: Card, b: Card): String = walkToThirdMessage(a, b).english
    fun whyBroken(trio: List<Card>, trait: Int): String = whyBrokenMessage(trio, trait).english
    fun notASet(trio: List<Card>): String = notASetMessage(trio).english

    // ---- the whole hint --------------------------------------------------------------------------

    /** The next thing to show, or null on a solved board. */
    fun teach(s: SetsState): Step? {
        if (s.solved) return null
        return mistake(s) ?: deduce(s.cards, s.found, s.selected)
    }

    /**
     * The pick the board just rejected, or picked cards that cannot end in a set not yet claimed.
     * Everything here is on screen; nothing is compared with a stored answer.
     */
    fun mistake(s: SetsState): Step? {
        if (s.lastWrong && s.lastPick.size == 3) {
            val trio = s.lastPick.map { s.cards[it] }
            return Step(
                technique = MISTAKE,
                rejected = s.lastPick,
                focus = s.lastPick.toSet(),
                cited = emptySet(),
                targets = s.lastPick.toSet(),
                nudge = message("teacher.sets.mistake.1"),
                explanation = message("teacher.sets.mistake.2", "value1" to (Message { language -> notASetMessage(trio).resolve(language).replaceFirstChar { it.lowercaseChar() } })),
            )
        }
        val open = openSets(s.cards, s.found)
        when (s.selected.size) {
            1 -> {
                val card = s.selected[0]
                if (open.any { card in it }) return null
                val anywhere = Sets.allSets(s.cards).any { card in it }
                return Step(
                    technique = MISTAKE,
                    release = s.selected,
                    focus = s.selected.toSet(),
                    cited = emptySet(),
                    targets = s.selected.toSet(),
                    nudge = message("teacher.sets.mistake.3"),
                    explanation = if (anywhere) {
                        message("teacher.sets.mistake.4")
                    } else {
                        message("teacher.sets.mistake.5")
                    },
                )
            }
            2 -> {
                val (a, b) = s.selected
                val need = third(s.cards[a], s.cards[b])
                val c = s.cards.indexOf(need)
                if (c >= 0 && listOf(a, b, c).sorted() !in s.found) return null
                return Step(
                    technique = MISTAKE,
                    release = s.selected,
                    focus = s.selected.toSet(),
                    cited = if (c >= 0) setOf(c) else emptySet(),
                    targets = s.selected.toSet(),
                    nudge = message("teacher.sets.mistake.6"),
                    explanation = if (c < 0) {
                        message("teacher.sets.mistake.7", "describe_need_" to (describeMessage(need)))
                    } else {
                        message("teacher.sets.mistake.8")
                    },
                )
            }
        }
        return null
    }

    /** Sets on the board not yet claimed, in index order. */
    private fun openSets(cards: List<Card>, found: List<List<Int>>): List<List<Int>> =
        Sets.allSets(cards).filter { it !in found }

    /**
     * The step to take next, reasoned from the cards, the sets already claimed and the cards the
     * player has picked. [selected] must already be known not to be a mistake.
     */
    fun deduce(cards: List<Card>, found: List<List<Int>>, selected: List<Int>): Step? {
        val open = openSets(cards, found)
        if (open.isEmpty()) return null
        val used = found.flatten().toSet()

        if (selected.isNotEmpty()) {
            val set = open.firstOrNull { it.containsAll(selected) } ?: return null
            val pair = if (selected.size == 2) selected else listOf(selected[0], set.first { it != selected[0] })
            val third = set.first { it !in pair }
            return Step(
                technique = CONTINUE,
                claim = set,
                focus = pair.toSet(),
                cited = pair.toSet(),
                targets = setOf(third),
                nudge = if (selected.size == 2) message("teacher.sets.deduce.1")
                else message("teacher.sets.deduce.2"),
                explanation = walkToThirdMessage(cards[pair[0]], cards[pair[1]]),
            )
        }

        // Fewest used cards first (a fresh set is the plain case), then the most traits alike,
        // which is the set a person spots first. Ties go to index order.
        // sortedWith is stable, so ties keep the index order allSets gives.
        val set = open.sortedWith(
            compareBy<List<Int>>({ t -> t.count { it in used } }, { t -> -alike(t.map { cards[it] }) })
        ).first()
        val reused = set.filter { it in used }
        // Show a used card in the pair when there is one, so the nudge that says "a tinted card
        // still counts" is pointing at one.
        val pair = if (reused.isNotEmpty() && reused.size < 3) {
            listOf(reused[0], set.first { it !in used })
        } else {
            set.take(2)
        }
        val third = set.first { it !in pair }
        return Step(
            technique = if (reused.isEmpty()) FRESH else REUSE,
            claim = set,
            focus = pair.toSet(),
            cited = pair.toSet(),
            targets = setOf(third),
            nudge = if (reused.isEmpty()) message("teacher.sets.deduce.3") else message("teacher.sets.deduce.4"),
            explanation = walkToThirdMessage(cards[pair[0]], cards[pair[1]]),
        )
    }

    /** How many traits a trio shares outright (all alike). */
    private fun alike(trio: List<Card>): Int = (0..3).count { t -> trio.map { traitValue(it, t) }.distinct().size == 1 }

    // ---- the move --------------------------------------------------------------------------------

    /** "Show me": claim the set, or let go of the picked cards. One state, so one undo entry. */
    fun apply(s: SetsState, step: Step): SetsState = when {
        step.claim.isNotEmpty() -> if (step.claim in s.found) s else
            (Sets.withoutSelection(s) as SetsState).copy(found = s.found + listOf(step.claim), moves = s.moves + 1)
        step.release.isNotEmpty() -> if (s.selected.none { it in step.release }) s else
            (Sets.withoutSelection(s) as SetsState).copy(selected = s.selected - step.release.toSet(), moves = s.moves + 1)
        // A rejected pick has already been undone by the board; there is nothing to take back, and
        // handing back the same state keeps "Show me" from costing an undo entry.
        else -> s
    }

    /** Whether [s] already has this step's result, however the player got there. */
    fun isReached(s: SetsState, step: Step): Boolean = when {
        step.claim.isNotEmpty() -> step.claim in s.found
        step.release.isNotEmpty() -> !s.selected.containsAll(step.release)
        else -> !(s.lastWrong && s.lastPick == step.rejected)
    }
}
