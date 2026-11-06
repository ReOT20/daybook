package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.i18n.message
import com.joebywan.daybook.core.i18n.literalMessage
import com.joebywan.daybook.core.listedMessage
import com.joebywan.daybook.core.joinedMessages
import com.joebywan.daybook.core.numberWordMessage

import com.joebywan.daybook.core.i18n.Message
import com.joebywan.daybook.core.asMessage

import com.joebywan.daybook.core.listed
import com.joebywan.daybook.core.TeachStep
import com.joebywan.daybook.core.words.WordContent

/**
 * Lexicon, reasoned the way a person reasons it: from the marks already on the board.
 *
 * [teach] is handed exactly what the player sees: the guesses, the marks each drew, and the row
 * being typed. It has no parameter through which the hidden word could reach it, so a step cannot
 * lean on the answer, and it never needs it: a row that cannot be submitted is a mistake whatever
 * the word is, and the last resort is "here is a word that fits every mark", which is honest
 * reasoning that does not fit in a sentence. That last resort is flagged so the coverage test can
 * count it.
 *
 * What it teaches, simplest first:
 *
 * - [MISTAKE] — the row cannot be submitted because it is not a word. Any word is allowed, so
 *   probing with one that cannot be the answer is never called wrong.
 * - [OPENER] — nothing is known yet, so one of the words that split the answer list best (which one is the board's
 *   [LexiconState.opener]), given as a clue.
 * - [PIN] — a letter known to be in the word has only as many slots left as it has copies, because
 *   every other slot is a green of another letter or was marked against it.
 * - [SUBMIT] — the row fits every mark; press Enter.
 * - [ONLY_WORD] / [CHOOSE] — pins have run out. Of the answer list, one word fits (type it), or
 *   several do and this guess leaves the fewest whichever way it is marked.
 *
 * Those three name a word, so they give it as a crossword-style clue ([clueFor]) and leave the
 * player to work it out; only "Show me" types it.
 *
 * A pin is a fact about the marks alone, so it holds for every word that fits them, on the answer
 * list or not. Only [ONLY_WORD] and [CHOOSE] read the answer list, and they say so.
 *
 * Nothing here walks a hash container: letters, slots and words are visited by index, so the same
 * board gets the same hint on every platform (CLAUDE.md, "Hash iteration order").
 */
internal object LexiconTeacher {

    const val MISTAKE = "mistake"
    const val OPENER = "opener"
    const val PIN = "pinned-letter"
    const val SUBMIT = "submit"
    const val ONLY_WORD = "only-word-left"
    const val CHOOSE = "best-guess"

    /** Every step [teach] can produce, for reports. */
    val TECHNIQUES = listOf(MISTAKE, OPENER, PIN, SUBMIT, ONLY_WORD, CHOOSE)

    /** The two steps that point at a word from the answer list rather than at a fact about a slot. */
    val FALLBACKS = listOf(ONLY_WORD, CHOOSE)

    /**
     * The hint panel shows four lines of body text, about 170 characters on a phone. A step whose
     * explanation would be longer is shortened rather than shown cut off.
     */
    const val MAX_EXPLANATION = 170

    /** The most guesses weighed when picking [CHOOSE]'s word; the rest of the list is a stride. */
    private const val POOL = 300

    /**
     * First guesses for an empty board, one per board by [LexiconState.opener], so the opening hint is not the same clue
     * every day. Each is an answer-list word among the best of its list at leaving few words: measured over every answer
     * (the mean number left, sum of squared outcome sizes over the list), five letters 48.4 for RAISE to 52.0 for STARE,
     * four letters 98.1 for TALE to 110.1 for LEAN, within 12% of the best. `LexiconTeachingTest` re-measures them.
     */
    fun openers(length: Int, contentLanguage: String = "en"): List<String> =
        if (contentLanguage == "ru") {
            // Measured over every Russian answer: all four are within 12% of the best mean words left.
            if (length == 4) listOf("кора", "сера", "нора", "кола")
            else listOf("тоска", "катер", "корма", "салон")
        } else if (length == 4) listOf("tale", "sale", "late", "real", "lane", "seal", "lean")
        else listOf("raise", "arise", "irate", "slate", "arose", "alert", "stare")

    /** The opener for [pick], a board's [LexiconState.opener]. */
    fun opener(length: Int, pick: Int = 0, contentLanguage: String = "en"): String = openers(length, contentLanguage).let { it[pick.mod(it.size)] }

    /** "Clue: Lift (5)": a clue to [word] without the word, ready to drop into an explanation. */
    private fun clueFor(word: String, contentLanguage: String = "en") = message("teacher.words.clue_for.1", "value1" to (WordList.clue(word, contentLanguage)), "word_length" to (word.length))

    // ---- what the indices in a Deduction mean on Lexicon's board -----------------------------------
    // A tile is `row * length + slot`, rows counting down the grid with the row being typed after the
    // submitted guesses, which is what `highlightGrid` expects. Keys and buttons have their own ranges.

    const val KEY = 3000
    const val ENTER = 4000
    const val DELETE = 4001

    fun tile(length: Int, row: Int, slot: Int) = row * length + slot

    fun key(letter: Char, contentLanguage: String = "en") = KEY + WordContent.index(letter, contentLanguage)

    /** What a step asks the player to do. */
    sealed interface Move {
        /** Take the row back to its first [keep] letters. */
        data class Retype(val keep: Int) : Move

        /** Type this whole word. It is done when a row that fits every mark is typed or submitted. */
        data class Fill(val word: String) : Move

        /** Type this opening word. It is done when any whole row is typed or submitted. */
        data class Open(val word: String) : Move

        /** Get [letter] into [slot]; "show me" types [word], which has it there and fits every mark. */
        data class Pin(val slot: Int, val letter: Char, val word: String) : Move

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

    /** Answer-list words that would have drawn every mark on the board. */
    fun candidates(length: Int, guesses: List<String>, marks: List<List<Int>>, contentLanguage: String = "en"): List<String> =
        WordList.answers(length, contentLanguage).filter { LexiconRules.consistent(it, guesses, marks) }

    /** What the marks say, slot by slot and letter by letter. */
    private class Clues(val length: Int, val guesses: List<String>, val marks: List<List<Int>>, val contentLanguage: String) {
        val alphabet = WordContent.alphabet(contentLanguage)
        /** The letter a green fixed in each slot, and the guess that showed it. */
        val green = arrayOfNulls<Char>(length)
        val greenGuess = IntArray(length) { -1 }

        /** At least this many of each letter: the most any one guess showed as green or yellow. */
        val min = IntArray(alphabet.length)

        /** Whether a slot is ruled out for a letter, and the guess that ruled it out. */
        val ruledBy = Array(length) { IntArray(alphabet.length) { -1 } }

        /** The first tile showing each letter as green or yellow, or -1. */
        val shownAt = IntArray(alphabet.length) { -1 }

        init {
            for (g in guesses.indices) {
                val seen = IntArray(alphabet.length)
                for (i in 0 until length) {
                    val k = WordContent.index(guesses[g][i], contentLanguage)
                    when (marks[g][i]) {
                        LexiconMark.CORRECT -> {
                            seen[k]++
                            if (green[i] == null) {
                                green[i] = guesses[g][i]
                                greenGuess[i] = g
                            }
                            if (shownAt[k] < 0) shownAt[k] = tile(length, g, i)
                        }
                        LexiconMark.PRESENT -> {
                            seen[k]++
                            if (ruledBy[i][k] < 0) ruledBy[i][k] = g
                            if (shownAt[k] < 0) shownAt[k] = tile(length, g, i)
                        }
                        else -> if (ruledBy[i][k] < 0) ruledBy[i][k] = g
                    }
                }
                for (k in alphabet.indices) if (seen[k] > min[k]) min[k] = seen[k]
            }
        }
    }

    fun teach(
        length: Int,
        guesses: List<String>,
        marks: List<List<Int>>,
        current: String,
        opener: Int = 0,
        contentLanguage: String = "en",
    ): Step? {
        val clues = Clues(length, guesses, marks, contentLanguage)
        mistake(clues, current)?.let { return it }
        if (guesses.isEmpty()) return opening(length, opener, contentLanguage)
        pin(clues, current)?.let { return it }
        return choose(clues, current)
    }

    // ---- mistakes ---------------------------------------------------------------------------------

    private fun mistake(c: Clues, current: String): Step? {
        val row = c.guesses.size
        if (current.length != c.length) return null
        if (!WordList.isWord(current, c.contentLanguage)) {
            return Step(
                MISTAKE, Move.Retype(c.length - 1),
                focus = (0 until c.length).map { tile(c.length, row, it) }.toSet(),
                cited = emptySet(),
                targets = setOf(DELETE),
                nudge = message("teacher.words.mistake.1"),
                explanation = message("teacher.words.mistake.2", "current_uppercase_" to (current.uppercase())),
            )
        }
        return null
    }

    // ---- opener -----------------------------------------------------------------------------------

    private fun opening(length: Int, pick: Int, contentLanguage: String): Step {
        val word = opener(length, pick, contentLanguage)
        return Step(
            OPENER, Move.Open(word),
            focus = (0 until length).map { tile(length, 0, it) }.toSet(),
            cited = emptySet(),
            targets = word.map { key(it, contentLanguage) }.toSet(),
            nudge = message("teacher.words.opening.1"),
            explanation = message("teacher.words.opening.2", "value1" to (clueFor(word, contentLanguage))),
        )
    }

    // ---- pinned letters ---------------------------------------------------------------------------

    private fun pin(c: Clues, current: String): Step? {
        val row = c.guesses.size
        for (k in c.alphabet.indices) {
            if (c.min[k] == 0) continue
            val letter = c.alphabet[k]
            val fixed = (0 until c.length).count { c.green[it] == letter }
            val need = c.min[k] - fixed
            if (need <= 0) continue
            val open = (0 until c.length).filter { c.green[it] == null && c.ruledBy[it][k] < 0 }
            if (open.size != need) continue
            val slot = open.firstOrNull { !(current.length > it && current[it] == letter) } ?: continue
            val word = wordWith(c, letter, slot) ?: continue
            val ruled = (0 until c.length).filter { c.green[it] == null && c.ruledBy[it][k] >= 0 }
            val taken = (0 until c.length).filter { c.green[it] != null && c.green[it] != letter }
            val cited = buildSet {
                add(c.shownAt[k])
                ruled.forEach { add(tile(c.length, c.ruledBy[it][k], it)) }
                taken.forEach { add(tile(c.length, c.greenGuess[it], it)) }
            }
            val shownTiles = (0 until c.guesses.size).flatMap { g ->
                (0 until c.length).filter { c.guesses[g][it] == letter && c.marks[g][it] != LexiconMark.ABSENT }
                    .map { tile(c.length, g, it) }
            }.toSet()
            val shout = letter.uppercaseChar()
            val where = if (need == 1) message("teacher.words.pin.1", "slot_1" to (slot + 1)) else message("teacher.words.pin.2", "listed_open_map_it_1_" to (listedMessage(open.map { it + 1 })))
            val long = buildList<Message> {
                add(message("teacher.words.pin.3", "shout" to (shout)))
                if (ruled.isNotEmpty()) add(message("teacher.words.pin.4", "slots_ruled_" to (slots(ruled))))
                if (ruled.isNotEmpty() && taken.isNotEmpty()) add(message("teacher.words.pin.5"))
                if (taken.isNotEmpty()) {
                    add(message("teacher.words.pin.6", "slots_taken_" to (slots(taken)), "value2" to (if (taken.size == 1) message("teacher.words.pin.extra.1") else message("teacher.words.pin.extra.2"))))
                    add(listedMessage(taken.map { c.green[it]!!.uppercaseChar() }))
                }
                add(if (need == 1) message("teacher.words.pin.7", "where" to (where)) else message("teacher.words.pin.8", "where" to (where)))
            }.joinedMessages("")
            val explanation = if (long.length <= MAX_EXPLANATION) long
            else message("teacher.words.pin.9", "shout" to (shout), "where" to (where))
            return Step(
                PIN, Move.Pin(slot, letter, word),
                focus = shownTiles,
                cited = cited,
                targets = setOf(tile(c.length, row, slot), key(letter, c.contentLanguage)),
                nudge = message("teacher.words.pin.10", "shout" to (shout)),
                explanation = explanation,
            )
        }
        return null
    }

    /** The best answer-list word that fits every mark and has [letter] in [slot], or null. */
    private fun wordWith(c: Clues, letter: Char, slot: Int): String? {
        val fits = candidates(c.length, c.guesses, c.marks, c.contentLanguage).filter { it[slot] == letter }
        return if (fits.isEmpty()) null else bestGuess(fits)
    }

    // ---- choosing a word --------------------------------------------------------------------------

    private fun choose(c: Clues, current: String): Step? {
        val row = c.guesses.size
        val fits = candidates(c.length, c.guesses, c.marks, c.contentLanguage)
        if (fits.isEmpty()) return null
        val rowFits = current.length == c.length && fits.contains(current)
        if (rowFits) {
            return Step(
                SUBMIT, Move.Submit,
                focus = (0 until c.length).map { tile(c.length, row, it) }.toSet(),
                cited = emptySet(),
                targets = setOf(ENTER),
                nudge = message("teacher.words.choose.1"),
                explanation = message("teacher.words.choose.2", "current_uppercase_" to (current.uppercase())),
            )
        }
        val lastRow = (0 until c.length).map { tile(c.length, row - 1, it) }.toSet()
        if (fits.size == 1) {
            val word = fits[0]
            return Step(
                ONLY_WORD, Move.Fill(word),
                focus = lastRow,
                cited = lastRow,
                targets = word.map { key(it, c.contentLanguage) }.toSet(),
                nudge = message("teacher.words.choose.3"),
                explanation = message("teacher.words.choose.4", "value1" to (clueFor(word, c.contentLanguage))),
            )
        }
        val word = bestGuess(fits)
        val shown = splitText(word, fits, c.contentLanguage)
        return Step(
            CHOOSE, Move.Fill(word),
            focus = lastRow,
            cited = lastRow,
            targets = word.map { key(it, c.contentLanguage) }.toSet(),
            nudge = message("teacher.words.choose.5"),
            explanation = shown,
        )
    }

    /**
     * The word of [fits] that leaves the fewest of them in its worst case (then in the most
     * outcomes, then first alphabetically, so the pick is the same everywhere). A long list is
     * thinned by a fixed stride rather than at random.
     */
    fun bestGuess(fits: List<String>): String {
        val pool = fits
        val stride = (pool.size + POOL - 1) / POOL
        var best = pool[0]
        var bestWorst = Int.MAX_VALUE
        var bestSpread = -1
        var i = 0
        while (i < pool.size) {
            val word = pool[i]
            val counts = outcomes(word, fits)
            val worst = counts.values.max()
            val spread = counts.size
            if (worst < bestWorst || (worst == bestWorst && spread > bestSpread)) {
                best = word
                bestWorst = worst
                bestSpread = spread
            }
            i += stride
        }
        return best
    }

    /**
     * Why [word] is the guess to make of [fits]: how its marks would split them. "7 words on the answer list fit every
     * mark. Try the one that goes "Lift" (5): it can be marked 5 ways, and none leaves more than 2." Read off the
     * candidates alone, as the pick is; shortened when the clue makes it too long for the panel.
     */
    private fun splitText(word: String, fits: List<String>, contentLanguage: String): Message {
        val counts = outcomes(word, fits)
        val worst = counts.values.max()
        val clue = clueFor(word, contentLanguage)
        val long = when {
            fits.size == 2 -> message("teacher.words.split_text.1", "clue" to (clue))
            worst == 1 -> message("teacher.words.split_text.2", "fits_size" to (fits.size), "clue" to (clue))
            else -> message("teacher.words.split_text.3", "fits_size" to (fits.size), "clue" to (clue), "counts_size" to (counts.size), "worst" to (worst))
        }
        val short = if (fits.size == 2) message("teacher.words.split_text.4", "clue" to (clue))
        else message("teacher.words.split_text.5", "fits_size" to (fits.size), "clue" to (clue), "worst" to (worst))
        return if (long.length <= MAX_EXPLANATION) long else short
    }

    /** How many of [fits] draw each mark code for [word]. A probe-only hash container: read for its size and values, never its order. */
    private fun outcomes(word: String, fits: List<String>): Map<Int, Int> {
        val counts = HashMap<Int, Int>()
        for (f in fits) {
            val code = LexiconRules.markCode(word, f)
            counts[code] = (counts[code] ?: 0) + 1
        }
        return counts
    }

    // ---- wording ----------------------------------------------------------------------------------

    private fun slots(slots: List<Int>): Message =
        (if (slots.size == 1) message("teacher.words.slots.1") else message("teacher.words.slots.2")) + listedMessage(slots.map { it + 1 })
}
