package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.i18n.message
import com.joebywan.daybook.core.i18n.literalMessage
import com.joebywan.daybook.core.listedMessage
import com.joebywan.daybook.core.joinedMessages
import com.joebywan.daybook.core.numberWordMessage

import com.joebywan.daybook.core.i18n.Message
import com.joebywan.daybook.core.asMessage

import com.joebywan.daybook.core.cap
import com.joebywan.daybook.core.TeachStep
import com.joebywan.daybook.core.listed
import com.joebywan.daybook.core.numberWord

/**
 * Tower (Mastermind), reasoned the way a person reasons it: from the scores already on the board.
 *
 * [deduce] is handed exactly what the player sees — the submitted guesses, the pips each one
 * scored, and the row being built — and has no parameter through which the hidden code could reach
 * it. Unlike Kings, Tower never needs the answer at all: a pending row that contradicts the scores
 * is a mistake whatever the code is, and the last resort is not "here is part of the answer" but
 * "here is a guess that fits every score so far", which is honest reasoning too, just not reasoning
 * that fits in a sentence. It is still flagged [CONSISTENT] so the coverage test can count it.
 *
 * The facts it teaches are facts about one slot ("slot 2 is red"), because that is a move the
 * player can make: place that peg in the row they are building. They come from a small set of
 * named rules, each short enough to say in a line:
 *
 * - [ONE_CHANGE] — two guesses differ in one slot only and the filled count moved, so the slot was
 *   right in the guess that scored higher.
 * - [ONLY_LEFT] — every colour but one is ruled out of a slot. Colours are ruled out of a slot by a
 *   guess that scored nothing (its colours are nowhere), by a guess with a pip for every peg (the
 *   code uses only its colours), by a guess with no filled pips (none of
 *   its pegs is in place), by two guesses that differ only there and scored the same (neither colour
 *   is right there), or by a guess whose filled pips are all used up by slots already known.
 * - [ACCOUNTED] — a guess's filled pips must sit on the pegs not already ruled out, because exactly
 *   that many are left.
 * - [WHAT_IF] — supposing a slot were anything but one colour, a step or two of the rules above
 *   leaves some guess unable to score what it scored, or some slot with no colour.
 *
 * Only facts that can be explained are ever learned: a fact whose sentence would not fit the hint
 * panel is not added, so nothing later can lean on a fact the player was never shown. Facts are
 * taught in the order they were derived, so one that cites another ("slot 1 is green") only ever
 * comes after it.
 *
 * Orders never come from a hash: guesses, slots and colours are walked by index, so the same board
 * gets the same hint on every platform (CLAUDE.md, "Hash iteration order").
 */
internal object TowerTeacher {

    const val MISTAKE = "mistake"
    const val OPENER = "opener"
    const val ONE_CHANGE = "one-change"
    const val ONLY_LEFT = "only-colour-left"
    const val ACCOUNTED = "pips-accounted"
    const val WHAT_IF = "what-if"
    const val SUBMIT = "submit"
    const val ONLY_CODE = "only-code-left"
    const val CONSISTENT = "consistent-guess"

    /** The one-sentence slot facts. */
    val FACTS = listOf(ONE_CHANGE, ONLY_LEFT, ACCOUNTED, WHAT_IF)

    /** Every step [deduce] can produce, for reports. */
    val TECHNIQUES = listOf(OPENER, ONE_CHANGE, ONLY_LEFT, ACCOUNTED, WHAT_IF, SUBMIT, ONLY_CODE, CONSISTENT, MISTAKE)

    /**
     * How many rule steps a [WHAT_IF] may walk through between its supposition and the
     * contradiction. As with Kings, past two it stops being a sentence anyone can hold while
     * looking at the board — and the panel's length limit usually says so first.
     */
    const val MAX_CHAIN = 2

    /**
     * The hint panel shows four lines of body text, about 170 characters on a phone. A fact whose
     * explanation is longer is not a one-sentence fact and is not learned.
     */
    const val MAX_EXPLANATION = 170

    // ---- what the indices in a Deduction mean on Tower's board -----------------------------------
    // A submitted guess's peg is `guess * slots + slot`; the rest sit in their own ranges so a
    // highlight can name pips, the working row, a swatch or Submit without clashing with a peg.

    const val PIPS = 1000
    const val CURRENT = 2000
    const val SWATCH = 3000
    const val SUBMIT_BUTTON = 4000

    fun peg(slots: Int, guess: Int, slot: Int) = guess * slots + slot

    /** Names for [Tower.palette], index for index. */
    val colourNames = listOf(message("teacher.tower.colour_names.1"), message("teacher.tower.colour_names.2"), message("teacher.tower.colour_names.3"), message("teacher.tower.colour_names.4"), message("teacher.tower.colour_names.5"), message("teacher.tower.colour_names.6"), message("teacher.tower.colour_names.7"), message("teacher.tower.colour_names.8"))

    /** What a step asks the player to do. */
    sealed interface Move {
        /** Put [colour] in [slot] of the row being built. */
        data class Place(val slot: Int, val colour: Int) : Move

        /** Build this whole row. */
        data class Fill(val code: List<Int>) : Move

        /** Take these pegs back out of the row. */
        data class Clear(val slots: List<Int>) : Move

        data object Submit : Move
    }

    class Step(
        override val technique: String,
        val move: Move,
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
    }

    /** Mastermind's score, with no stored code: [code] is whichever code is being tested. */
    fun score(guess: List<Int>, code: List<Int>, colours: Int): Feedback {
        var exact = 0
        val codeLeft = IntArray(colours)
        val guessLeft = IntArray(colours)
        for (i in guess.indices) {
            if (guess[i] == code[i]) exact++ else {
                codeLeft[code[i]]++
                guessLeft[guess[i]]++
            }
        }
        var misplaced = 0
        for (c in 0 until colours) misplaced += minOf(codeLeft[c], guessLeft[c])
        return Feedback(exact, misplaced)
    }

    /** Whether [code] would have drawn every score on the board. */
    fun consistent(code: List<Int>, guesses: List<List<Int>>, feedback: List<Feedback>, colours: Int): Boolean =
        guesses.indices.all { score(guesses[it], code, colours) == feedback[it] }

    // ---- why a colour is ruled out of a slot -----------------------------------------------------

    private sealed interface Why {
        /** The guesses this reason cites. */
        val rows: List<Int>
            get() = when (this) {
                is Nothing -> listOf(g)
                is Complete -> listOf(g)
                is NoFilled -> listOf(g)
                is SameScore -> listOf(a, b)
                is Accounted -> listOf(g)
                is Known -> emptyList()
            }

        /** Guess [g] scored no pips at all, so none of its colours is in the code. */
        data class Nothing(val g: Int) : Why

        /** Guess [g] scored a pip for every peg, so the code uses only its colours. */
        data class Complete(val g: Int) : Why

        /** Guess [g] had this colour in this slot and scored no filled pips. */
        data class NoFilled(val g: Int) : Why

        /** Guesses [a] and [b] differ only in this slot and have the same filled count. */
        data class SameScore(val a: Int, val b: Int) : Why

        /** Guess [g]'s filled pips are all taken by slots already known. */
        data class Accounted(val g: Int) : Why

        /** The slot is already known to be [colour]. */
        data class Known(val colour: Int) : Why
    }

    /** A slot the scores pin down, and the sentence that says why. */
    private class Fact(
        val slot: Int,
        val colour: Int,
        val technique: String,
        nudge: CharSequence,
        explanation: CharSequence,
        val focus: Set<Int>,
        val cited: Set<Int>,
    ) {

        val nudgeMessage: Message = nudge.asMessage()
        val explanationMessage: Message = explanation.asMessage()
        val nudge: String get() = nudgeMessage.english
        val explanation: String get() = explanationMessage.english
    }

    /**
     * Everything the named rules can learn from the scores, closed under each other. Holds only
     * explainable facts — see the class comment.
     */
    private class Knowledge(
        val slots: Int,
        val colours: Int,
        val guesses: List<List<Int>>,
        val feedback: List<Feedback>,
    ) {
        private val absent = arrayOfNulls<Why>(colours)
        private val ruledOut = Array(slots) { arrayOfNulls<Why>(colours) }
        private val known = arrayOfNulls<Fact>(slots)
        val facts = mutableListOf<Fact>()

        init {
            learn()
        }

        /** Why [colour] cannot be in [slot], or null if nothing the rules know rules it out. */
        fun excluded(slot: Int, colour: Int): Why? {
            known[slot]?.let { return if (it.colour != colour) Why.Known(it.colour) else null }
            return absent[colour] ?: ruledOut[slot][colour]
        }

        private fun add(f: Fact) {
            known[f.slot] = f
            facts += f
        }

        private fun learn() {
            val n = guesses.size
            for (g in 0 until n) {
                val fb = feedback[g]
                if (fb.exact + fb.misplaced == 0) {
                    for (c in guesses[g]) if (absent[c] == null) absent[c] = Why.Nothing(g)
                }
                if (fb.exact + fb.misplaced == slots) {
                    for (c in 0 until colours) if (c !in guesses[g] && absent[c] == null) absent[c] = Why.Complete(g)
                }
                if (fb.exact == 0) {
                    for (i in 0 until slots) {
                        val c = guesses[g][i]
                        if (ruledOut[i][c] == null) ruledOut[i][c] = Why.NoFilled(g)
                    }
                }
            }
            // Pairs of guesses one peg apart. Unequal filled counts pin the slot outright; equal
            // ones rule both colours out of it.
            for (a in 0 until n) for (b in a + 1 until n) {
                val slot = onlyDifference(guesses[a], guesses[b]) ?: continue
                val ea = feedback[a].exact
                val eb = feedback[b].exact
                if (ea == eb) {
                    for (c in listOf(guesses[a][slot], guesses[b][slot])) {
                        if (ruledOut[slot][c] == null) ruledOut[slot][c] = Why.SameScore(a, b)
                    }
                } else if (known[slot] == null) {
                    val hi = if (ea > eb) a else b
                    val lo = if (ea > eb) b else a
                    val colour = guesses[hi][slot]
                    val name = colourNames[colour]
                    add(
                        Fact(
                            slot, colour, ONE_CHANGE,
                            nudge = message("teacher.tower.learn.1", "a_1" to (a + 1), "b_1" to (b + 1)),
                            explanation = message("teacher.tower.learn.2", "a_1" to (a + 1), "b_1" to (b + 1), "slot_1" to (slot + 1), "lo_1" to (lo + 1), "hi_1" to (hi + 1), "name" to (name)),
                            focus = rowCells(a) + rowCells(b),
                            cited = setOf(peg(slots, a, slot), peg(slots, b, slot), PIPS + a, PIPS + b),
                        ),
                    )
                }
            }
            var changed = true
            while (changed) {
                changed = accounted() || onlyLeft() || whatIf()
            }
        }

        /**
         * One [WHAT_IF]: the shortest supposition "slot i isn't x" that the rules walk into a
         * contradiction, over every open slot and colour. True if it learned anything.
         */
        private fun whatIf(): Boolean {
            for (chain in 0..MAX_CHAIN) {
                for (i in 0 until slots) {
                    if (known[i] != null) continue
                    for (x in 0 until colours) {
                        if (excluded(i, x) != null) continue
                        val sim = sim()
                        sim.out[i][x] = true
                        val steps = mutableListOf<Pair<Message, Int>>()
                        var clash = sim.contradiction()
                        while (clash == null && steps.size < chain) {
                            steps += sim.derive() ?: break
                            clash = sim.contradiction()
                        }
                        if (clash == null || steps.size != chain) continue
                        val name = colourNames[x]
                        val walk = steps.map { it.first }.joinedMessages(message("teacher.tower.what_if.1"))
                        val text = message("teacher.tower.what_if.2", "i_1" to (i + 1), "name" to (name), "value3" to ((if (walk.isEmpty()) "" else message("teacher.tower.what_if.extra.1", "walk" to (walk)))), "clash_first" to (clash.first))
                        if (text.length > MAX_EXPLANATION) continue
                        val rows = (steps.map { it.second } + clash.second).filter { it >= 0 }.distinct().sorted()
                        add(
                            Fact(
                                i, x, WHAT_IF,
                                nudge = message("teacher.tower.what_if.4", "i_1" to (i + 1)),
                                explanation = text,
                                focus = rows.flatMap { rowCells(it) }.toSet() + (CURRENT + i),
                                cited = rows.map { PIPS + it }.toSet(),
                            ),
                        )
                        return true
                    }
                }
            }
            return false
        }

        private fun sim() = Sim(
            slots, colours, guesses, feedback,
            IntArray(slots) { known[it]?.colour ?: -1 },
            BooleanArray(colours) { absent[it] != null },
            Array(slots) { i -> BooleanArray(colours) { ruledOut[i][it] != null } },
        )

        private fun rowCells(g: Int): Set<Int> = (0 until slots).map { peg(slots, g, it) }.toSet() + (PIPS + g)

        /** One [ACCOUNTED] pass over the guesses; true if it learned anything. */
        private fun accounted(): Boolean {
            var learned = false
            for (g in guesses.indices) {
                val guess = guesses[g]
                val f = feedback[g].exact
                val right = (0 until slots).filter { known[it]?.colour == guess[it] }
                val wrong = (0 until slots).filter { it !in right && excluded(it, guess[it]) != null }
                val open = (0 until slots).filter { it !in right && it !in wrong }
                if (open.isEmpty()) continue
                if (f == right.size) {
                    // Every filled pip is spoken for; the rest of this guess's pegs are out of place.
                    for (j in open) if (ruledOut[j][guess[j]] == null) {
                        ruledOut[j][guess[j]] = Why.Accounted(g)
                        learned = true
                    }
                } else if (f - right.size == open.size) {
                    for (j in open) {
                        if (known[j] != null) continue
                        val text = accountedText(g, right, wrong, open, j)
                        if (text.length > MAX_EXPLANATION) continue
                        val reasonRows = wrong.mapNotNull { excluded(it, guess[it]) }.flatMap { it.rows }
                        add(
                            Fact(
                                j, guess[j], ACCOUNTED,
                                nudge = message("teacher.tower.accounted.1", "g_1" to (g + 1)),
                                explanation = text,
                                focus = rowCells(g),
                                cited = rowCells(g) + reasonRows.map { PIPS + it },
                            ),
                        )
                        learned = true
                    }
                }
            }
            return learned
        }

        private fun accountedText(g: Int, right: List<Int>, wrong: List<Int>, open: List<Int>, slot: Int): Message {
            val guess = guesses[g]
            val f = feedback[g].exact
            fun pegs(list: List<Int>) = listedMessage(list.map { message("teacher.tower.pegs.1", "colourNames_guess_it_" to (colourNames[guess[it]]), "it_1" to (it + 1)) })
            val parts = buildList {
                if (right.isNotEmpty()) {
                    add(pegs(right) + if (right.size == 1) message("teacher.tower.accounted_text.1") else message("teacher.tower.accounted_text.2", "value1" to (numberWordMessage(right.size, zero = "no", upTo = 5))))
                }
                if (wrong.isNotEmpty()) {
                    val reasons = wrong.map { whyClause(it, excluded(it, guess[it])!!) }.distinctBy { it.english }
                    val which = when {
                        right.isNotEmpty() -> ""
                        wrong.size == 1 -> message("teacher.tower.accounted_text.3")
                        else -> message("teacher.tower.accounted_text.4")
                    }
                    add(pegs(wrong) + message("teacher.tower.accounted_text.5", "which" to (which), "listed_reasons_" to (listedMessage(reasons))))
                }
            }
            val rest = if (open.size == 1) message("teacher.tower.accounted_text.6") else message("teacher.tower.accounted_text.7")
            return message("teacher.tower.accounted_text.8", "g_1" to (g + 1), "count_f_filled_pip_" to (count(f, "filled pip")), "value3" to (parts.joinedMessages(message("teacher.tower.join_and")).cap()), "rest" to (rest), "slot_1" to (slot + 1), "colourNames_guess_slot_" to (colourNames[guess[slot]]))
        }

        /** One [ONLY_LEFT] pass over the slots; true if it learned anything. */
        private fun onlyLeft(): Boolean {
            var learned = false
            for (i in 0 until slots) {
                if (known[i] != null) continue
                val left = (0 until colours).filter { excluded(i, it) == null }
                if (left.size != 1) continue
                val colour = left[0]
                val reasons = (0 until colours).filter { it != colour }.map { it to excluded(i, it)!! }
                val text = onlyLeftText(i, colour, reasons)
                if (text.length > MAX_EXPLANATION) continue
                val rows = reasons.flatMap { it.second.rows }.distinct().sorted()
                add(
                    Fact(
                        i, colour, ONLY_LEFT,
                        nudge = message("teacher.tower.only_left.1", "i_1" to (i + 1)),
                        explanation = text,
                        focus = rows.map { peg(slots, it, i) }.toSet() + (CURRENT + i),
                        cited = rows.flatMap { listOf(peg(slots, it, i), PIPS + it) }.toSet(),
                    ),
                )
                learned = true
            }
            return learned
        }

        /**
         * "Slot 2 can't be red or blue (guess 1 scored nothing) or green (guess 3 had it there, no
         * filled pip). That leaves yellow." Colours that share a kind of reason share a clause.
         */
        private fun onlyLeftText(slot: Int, colour: Int, reasons: List<Pair<Int, Why>>): Message {
            // Group by the kind of reason, in the order the kinds first appear.
            val groups = linkedMapOf<String, MutableList<Pair<Int, Why>>>()
            for (r in reasons) {
                val key = when (val w = r.second) {
                    is Why.Nothing -> "nothing"
                    is Why.Complete -> "complete ${w.g}"
                    is Why.NoFilled -> "nofilled"
                    is Why.SameScore -> "same ${w.a} ${w.b}"
                    is Why.Accounted -> "accounted"
                    is Why.Known -> "known"
                }
                groups.getOrPut(key) { mutableListOf() } += r
            }
            val clauses = groups.values.map { group ->
                val names = listedMessage(group.map { colourNames[it.first] }, "or")
                val why = group.map { it.second }
                val rows = why.flatMap { it.rows }.distinct().sorted()
                val guessesWord = guessList(rows)
                val clause = when (val w = why[0]) {
                    is Why.Nothing -> message("teacher.tower.only_left_text.1", "guessesWord" to (guessesWord))
                    is Why.Complete -> message("teacher.tower.only_left_text.2", "w_g_1" to (w.g + 1))
                    is Why.NoFilled ->
                        message("teacher.tower.only_left_text.3", "guessesWord" to (guessesWord), "value2" to (if (group.size == 1) message("teacher.tower.only_left_text.extra.1") else message("teacher.tower.only_left_text.extra.2")))
                    is Why.SameScore -> message("teacher.tower.only_left_text.4", "w_a_1" to (w.a + 1), "w_b_1" to (w.b + 1))
                    is Why.Accounted -> message("teacher.tower.only_left_text.5", "guessesWord" to (guessesWord), "value2" to (if (rows.size == 1) message("teacher.tower.only_left_text.extra.3") else message("teacher.tower.only_left_text.extra.4")))
                    is Why.Known -> ""
                }
                message("teacher.tower.only_left_text.extra.5", "names" to (names), "clause" to (clause))
            }
            return message("teacher.tower.only_left_text.6", "slot_1" to (slot + 1), "listed_clauses_or_" to (listedMessage(clauses, "or")), "colourNames_colour_" to (colourNames[colour]))
        }

        /** A reason as a short clause, for inside brackets. */
        fun whyClause(slot: Int, w: Why): Message = when (w) {
            is Why.Nothing -> message("teacher.tower.why_clause.1", "w_g_1" to (w.g + 1))
            is Why.Complete -> message("teacher.tower.why_clause.2", "w_g_1" to (w.g + 1))
            is Why.NoFilled -> message("teacher.tower.why_clause.3", "w_g_1" to (w.g + 1))
            is Why.SameScore -> message("teacher.tower.why_clause.4", "w_a_1" to (w.a + 1), "w_b_1" to (w.b + 1))
            is Why.Accounted -> message("teacher.tower.why_clause.5", "w_g_1" to (w.g + 1))
            is Why.Known -> message("teacher.tower.why_clause.6", "slot_1" to (slot + 1), "colourNames_w_colour_" to (colourNames[w.colour]))
        }

        /** A reason as a full sentence, for a mistake. */
        fun whySentence(slot: Int, colour: Int, w: Why): Message {
            val name = colourNames[colour]
            return when (w) {
                is Why.Nothing -> message("teacher.tower.why_sentence.1", "w_g_1" to (w.g + 1), "name" to (name))
                is Why.Complete -> message("teacher.tower.why_sentence.2", "w_g_1" to (w.g + 1), "name" to (name))
                is Why.NoFilled -> message("teacher.tower.why_sentence.3", "w_g_1" to (w.g + 1), "name" to (name), "slot_1" to (slot + 1))
                is Why.SameScore -> message("teacher.tower.why_sentence.4", "w_a_1" to (w.a + 1), "w_b_1" to (w.b + 1), "slot_1" to (slot + 1), "value4" to (colourNames[guesses[w.a][slot]]), "value5" to (colourNames[guesses[w.b][slot]]))
                is Why.Accounted -> message("teacher.tower.why_sentence.5", "w_g_1" to (w.g + 1), "name" to (name), "slot_1" to (slot + 1))
                is Why.Known -> {
                    val head = message("teacher.tower.why_sentence.6", "slot_1" to (slot + 1), "colourNames_w_colour_" to (colourNames[w.colour]), "name" to (name))
                    val why = known[slot]!!.explanationMessage
                    if (head.length + 1 + why.length <= MAX_EXPLANATION) message("teacher.tower.why_sentence.extra.1", "head" to (head), "why" to (why)) else head
                }
            }
        }
    }

    /**
     * The rules again, stripped of their words, for walking a supposition. Each [derive] applies
     * one rule and says what it found; [contradiction] says what can no longer happen. Both return
     * the guess they lean on (or -1) so the hint can point at it.
     */
    private class Sim(
        val slots: Int,
        val colours: Int,
        val guesses: List<List<Int>>,
        val feedback: List<Feedback>,
        val known: IntArray,
        val absent: BooleanArray,
        val out: Array<BooleanArray>,
    ) {
        fun excluded(i: Int, c: Int) = (known[i] >= 0 && known[i] != c) || absent[c] || out[i][c]

        fun contradiction(): Pair<Message, Int>? {
            for (i in 0 until slots) {
                if (known[i] < 0 && (0 until colours).all { excluded(i, it) }) {
                    return message("teacher.tower.contradiction.1", "i_1" to (i + 1)) to -1
                }
            }
            for (g in guesses.indices) {
                val guess = guesses[g]
                val f = feedback[g].exact
                val right = (0 until slots).count { known[it] == guess[it] }
                val open = (0 until slots).count { known[it] != guess[it] && !excluded(it, guess[it]) }
                if (right > f) return message("teacher.tower.contradiction.2", "g_1" to (g + 1), "count_f_filled_pip_" to (count(f, "filled pip"))) to g
                if (right + open < f) return message("teacher.tower.contradiction.3", "g_1" to (g + 1), "count_f_filled_pip_" to (count(f, "filled pip"))) to g
            }
            // Pips of either kind count colours: guess g can't match more of a colour than the code
            // could still hold, nor fewer than the code is already known to hold.
            for (g in guesses.indices) {
                val guess = guesses[g]
                val t = feedback[g].exact + feedback[g].misplaced
                var most = 0
                var least = 0
                for (c in 0 until colours) {
                    val inGuess = guess.count { it == c }
                    if (inGuess == 0) continue
                    most += minOf(inGuess, (0 until slots).count { !excluded(it, c) })
                    least += minOf(inGuess, known.count { it == c })
                }
                if (most < t) return message("teacher.tower.contradiction.4", "g_1" to (g + 1), "count_t_pip_" to (count(t, "pip"))) to g
                if (least > t) return message("teacher.tower.contradiction.5", "g_1" to (g + 1), "count_t_pip_" to (count(t, "pip"))) to g
            }
            return null
        }

        fun derive(): Pair<Message, Int>? {
            for (g in guesses.indices) {
                val guess = guesses[g]
                val f = feedback[g].exact
                val right = (0 until slots).filter { known[it] == guess[it] }
                val open = (0 until slots).filter { known[it] != guess[it] && !excluded(it, guess[it]) }
                if (open.isEmpty()) continue
                if (f == right.size) {
                    open.forEach { out[it][guess[it]] = true }
                    return message("teacher.tower.derive.1", "g_1" to (g + 1)) to g
                }
                if (f - right.size == open.size) {
                    open.forEach { known[it] = guess[it] }
                    val where = (if (open.size == 1) message("teacher.tower.derive.2") else message("teacher.tower.derive.3")) + listedMessage(open.map { "${it + 1}" })
                    return message("teacher.tower.derive.4", "g_1" to (g + 1), "where" to (where)) to g
                }
            }
            for (i in 0 until slots) {
                if (known[i] >= 0) continue
                val left = (0 until colours).filter { !excluded(i, it) }
                if (left.size == 1) {
                    known[i] = left[0]
                    return message("teacher.tower.derive.5", "i_1" to (i + 1), "colourNames_left_0_" to (colourNames[left[0]])) to -1
                }
            }
            return null
        }
    }

    /** The one slot where [a] and [b] differ, or null if they differ in none or several. */
    private fun onlyDifference(a: List<Int>, b: List<Int>): Int? {
        var slot = -1
        for (i in a.indices) {
            if (a[i] == b[i]) continue
            if (slot >= 0) return null
            slot = i
        }
        return slot.takeIf { it >= 0 }
    }

    // ---- the whole hint ----------------------------------------------------------------------------

    /**
     * The next thing to show this player, or null once the code is cracked.
     *
     * Takes no code on purpose — see the class comment. Mistakes come first: a row that cannot be
     * the code is taken back before anything new is taught.
     */
    fun deduce(
        slots: Int,
        colours: Int,
        guesses: List<List<Int>>,
        feedback: List<Feedback>,
        current: List<Int>,
    ): Step? {
        require(guesses.size == feedback.size && current.size == slots)
        if (feedback.any { it.exact == slots }) return null
        val k = Knowledge(slots, colours, guesses, feedback)
        val space = lazy { codeSpace(slots, colours).filter { consistent(it, guesses, feedback, colours) }.toList() }
        mistake(k, current, space)?.let { return it }
        if (current.none { it < 0 }) return submit(slots, guesses, space)
        if (guesses.isEmpty()) return opener(slots, current)
        k.facts.firstOrNull { current[it.slot] != it.colour }?.let { f ->
            return Step(
                technique = f.technique,
                move = Move.Place(f.slot, f.colour),
                focus = f.focus,
                cited = f.cited,
                targets = setOf(CURRENT + f.slot),
                nudge = f.nudgeMessage,
                explanation = f.explanationMessage,
            )
        }
        return fill(slots, colours, current, space.value)
    }

    /** A peg in the row being built that the scores rule out, or a row no code can be. */
    private fun mistake(k: Knowledge, current: List<Int>, space: Lazy<List<List<Int>>>): Step? {
        val slots = k.slots
        val placed = current.indices.filter { current[it] >= 0 }
        if (placed.isEmpty()) return null

        for (i in placed) {
            val why = k.excluded(i, current[i]) ?: continue
            return clear(
                listOf(i),
                nudge = message("teacher.tower.mistake.1"),
                explanation = k.whySentence(i, current[i], why) + message("teacher.tower.mistake.2"),
                rows = why.rows,
            )
        }

        if (placed.size == slots) {
            val g = k.guesses.indices.firstOrNull {
                score(k.guesses[it], current, k.colours) != k.feedback[it]
            } ?: return null
            val would = score(k.guesses[g], current, k.colours)
            return clear(
                placed,
                nudge = message("teacher.tower.mistake.3", "g_1" to (g + 1)),
                explanation = message("teacher.tower.mistake.4", "g_1" to (g + 1), "pips_would_" to (pips(would)), "pips_k_feedback_g_" to (pips(k.feedback[g]))),
                rows = listOf(g),
            )
        }

        fun fits(pegs: List<Int>) = space.value.any { code -> pegs.all { code[it] == current[it] } }
        if (fits(placed)) return null
        val culprit = placed.firstOrNull { i -> fits(placed - i) }
        return clear(
            if (culprit != null) listOf(culprit) else placed,
            nudge = if (culprit != null) message("teacher.tower.mistake.5") else message("teacher.tower.mistake.6"),
            explanation = message("teacher.tower.mistake.7", "value1" to ((if (culprit != null) message("teacher.tower.mistake.extra.1") else message("teacher.tower.mistake.extra.2")))),
            rows = emptyList(),
        )
    }

    private fun clear(slotsToClear: List<Int>, nudge: CharSequence, explanation: CharSequence, rows: List<Int>) = Step(
        technique = MISTAKE,
        move = Move.Clear(slotsToClear),
        focus = slotsToClear.map { CURRENT + it }.toSet(),
        cited = rows.map { PIPS + it }.toSet(),
        targets = slotsToClear.map { CURRENT + it }.toSet(),
        nudge = nudge,
        explanation = explanation,
    )

    private fun submit(slots: Int, guesses: List<List<Int>>, space: Lazy<List<List<Int>>>): Step {
        val row = (0 until slots).map { CURRENT + it }.toSet()
        val explanation = when {
            guesses.isEmpty() -> message("teacher.tower.submit.1")
            space.value.size == 1 -> message("teacher.tower.submit.2")
            else -> message("teacher.tower.submit.3")
        }
        return Step(
            technique = SUBMIT,
            move = Move.Submit,
            focus = row,
            cited = row,
            targets = setOf(SUBMIT_BUTTON),
            nudge = message("teacher.tower.submit.4"),
            explanation = explanation,
        )
    }

    /** Pairs of colours, the classic Mastermind opener: red red blue blue (green). */
    private fun opener(slots: Int, current: List<Int>): Step {
        val code = List(slots) { if (current[it] >= 0) current[it] else it / 2 }
        val empty = current.indices.filter { current[it] < 0 }
        return Step(
            technique = OPENER,
            move = Move.Fill(code),
            focus = empty.map { CURRENT + it }.toSet(),
            cited = emptySet(),
            targets = empty.map { CURRENT + it }.toSet(),
            nudge = message("teacher.tower.opener.1"),
            explanation = message("teacher.tower.opener.2", "value1" to (listedMessage(code.map { colourNames[it] }))),
        )
    }

    /**
     * No single slot can be read off the scores, so suggest a whole row that fits them all — the
     * one that splits the remaining codes most evenly, of a sample, so the suggestion is a good
     * guess rather than merely a legal one. Still reasoning only from the board.
     */
    private fun fill(slots: Int, colours: Int, current: List<Int>, space: List<List<Int>>): Step {
        val placed = current.indices.filter { current[it] >= 0 }
        val fitting = space.filter { code -> placed.all { code[it] == current[it] } }
        val code = if (fitting.size <= 2) fitting.first() else bestSplit(fitting, colours)
        val empty = current.indices.filter { current[it] < 0 }
        val names = listedMessage(code.map { colourNames[it] })
        val only = fitting.size == 1
        return Step(
            technique = if (only) ONLY_CODE else CONSISTENT,
            move = Move.Fill(code),
            focus = empty.map { CURRENT + it }.toSet(),
            cited = emptySet(),
            targets = empty.map { CURRENT + it }.toSet(),
            nudge = if (only) message("teacher.tower.fill.1") else message("teacher.tower.fill.2"),
            explanation = when {
                only && space.size == 1 -> message("teacher.tower.fill.3", "names" to (names))
                only -> message("teacher.tower.fill.4", "names" to (names))
                else -> message("teacher.tower.fill.5", "names" to (names))
            },
        )
    }

    private fun bestSplit(codes: List<List<Int>>, colours: Int): List<Int> {
        val tries = spread(codes, 40)
        val against = spread(codes, 300)
        var best = tries[0]
        var bestWorst = Int.MAX_VALUE
        val buckets = IntArray(64)
        for (t in tries) {
            buckets.fill(0)
            var worst = 0
            for (c in against) {
                val fb = score(t, c, colours)
                val key = fb.exact * 8 + fb.misplaced
                buckets[key]++
                if (buckets[key] > worst) worst = buckets[key]
            }
            if (worst < bestWorst) {
                bestWorst = worst
                best = t
            }
        }
        return best
    }

    /** Up to [n] members of [list], evenly spaced, in order. */
    private fun <T> spread(list: List<T>, n: Int): List<T> =
        if (list.size <= n) list else List(n) { list[(it.toLong() * list.size / n).toInt()] }

    /** Every code, slot 1 most significant, so the list is in reading order. */
    private fun codeSpace(slots: Int, colours: Int): Sequence<List<Int>> {
        var total = 1
        repeat(slots) { total *= colours }
        return (0 until total).asSequence().map { index ->
            var rest = index
            val code = IntArray(slots)
            for (i in slots - 1 downTo 0) {
                code[i] = rest % colours
                rest /= colours
            }
            code.toList()
        }
    }

    // ---- words ---------------------------------------------------------------------------------------

    private fun pips(fb: Feedback) = message("teacher.tower.pips.1", "fb_exact" to (fb.exact), "fb_misplaced" to (fb.misplaced))

    private fun guessList(rows: List<Int>): Message =
        (if (rows.size == 1) message("teacher.tower.guess_list.1") else message("teacher.tower.guess_list.2")) + listedMessage(rows.map { (it + 1).toString() })

    private fun count(n: Int, noun: String): Message = message(
        if (noun == "filled pip") "teacher.tower.count.filled" else "teacher.tower.count.pip",
        "count" to n, "number" to numberWordMessage(n, zero = "no", upTo = 5),
    )
}
