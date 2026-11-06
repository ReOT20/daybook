package com.joebywan.daybook.core.words

import com.joebywan.daybook.core.Rng

/**
 * Frozen Russian word content for separately identified Russian puzzles. Display-language changes
 * never select this data. Canonical boards use 32 letters: Ё is entered and displayed as Е on a
 * board, while [spelling] preserves dictionary spelling in prose. See tools/words/ru/README.md.
 */
object RussianWords {
    const val ALPHABET: String = "абвгдежзийклмнопрстуфхцчшщъыьэюя"

    /** Strip dictionary stress, fold case and Ё, but never turn foreign letters into Cyrillic. */
    fun normalize(word: String): String = word.trim().lowercase().replace("\u0301", "").replace('ё', 'е')

    private data class Entry(val spelling: String, val clue: String)

    private val entries: Map<String, Entry> by lazy {
        RussianWordData.CLUES.associate { line ->
            val parts = line.split('\t', limit = 3)
            parts[0] to Entry(parts[1], parts[2])
        }
    }

    /** Proper spelling in explanatory text; guessed inflections without a clue keep canonical spelling. */
    fun spelling(word: String): String = normalize(word).let { entries[it]?.spelling ?: it }

    /** Every answer/grid word has a clue. Asking for an unknown clue is a programming error. */
    fun clue(word: String): String = entries.getValue(normalize(word)).clue

    fun answers(length: Int): List<String> = when (length) {
        3 -> RussianWordData.ANSWERS_3
        4 -> RussianWordData.ANSWERS_4
        5 -> RussianWordData.ANSWERS_5
        6 -> RussianWordData.ANSWERS_6
        7 -> RussianWordData.ANSWERS_7
        else -> error("Russian word length must be in 3..7: $length")
    }

    /** Broad dictionary: genuine inflections and uncommon words are accepted as typed guesses. */
    fun guesses(length: Int): List<String> = when (length) {
        3 -> RussianWordData.GUESSES_3
        4 -> RussianWordData.GUESSES_4
        5 -> RussianWordData.GUESSES_5
        6 -> RussianWordData.GUESSES_6
        7 -> RussianWordData.GUESSES_7
        else -> error("Russian word length must be in 3..7: $length")
    }

    /** Screened, clued grid words include familiar nouns, verbs, adjectives and function words. */
    fun grid(length: Int): List<String> = when (length) {
        3 -> RussianWordData.GRID_3
        4 -> RussianWordData.GRID_4
        5 -> RussianWordData.GRID_5
        6 -> RussianWordData.GRID_6
        7 -> RussianWordData.GRID_7
        else -> error("Russian grid word length must be in 3..7: $length")
    }

    /** Vocabulary-feasible wheel roots; layout, uniqueness and teaching still need independent proof. */
    fun roots(length: Int): List<String> = when (length) {
        5 -> RussianWordData.ROOTS_5
        6 -> RussianWordData.ROOTS_6
        7 -> RussianWordData.ROOTS_7
        else -> error("Russian wheel length must be in 5..7: $length")
    }

    fun isWord(word: String): Boolean {
        val key = normalize(word)
        return key.length in 3..7 && guesses(key.length).binarySearch(key) >= 0
    }

    /** Each tile can be used once. A repeated letter needs that many copies in the wheel. */
    fun spellable(word: String, wheel: String): Boolean {
        val key = normalize(word)
        val letters = IntArray(ALPHABET.length)
        for (c in normalize(wheel)) {
            val index = ALPHABET.indexOf(c)
            if (index < 0) return false
            letters[index]++
        }
        for (c in key) {
            val index = ALPHABET.indexOf(c)
            if (index < 0 || --letters[index] < 0) return false
        }
        return key.isNotEmpty()
    }

    fun gridWordsFrom(wheel: String): List<String> {
        val key = normalize(wheel)
        return (3..minOf(7, key.length)).flatMap { grid(it) }.filter { spellable(it, key) }.sorted()
    }

    /** Random/level selection; callers retain their own stable Russian puzzle ID/seed namespace. */
    fun answer(seed: Long, length: Int): String = Rng(seed).pick(answers(length))

    /**
     * Non-repeating daily rounds. A five-letter round lasts 632 days; a four-letter round 437.
     * The caller supplies a day index from the Russian variant's own immutable cutover date.
     * This does not alter English SeedHash or interpret the legacy English cycle tag.
     */
    fun dailyAnswer(dayIndex: Long, length: Int, stream: Int = 0): String {
        require(dayIndex >= 0) { "Russian daily day index must be nonnegative" }
        val words = answers(length)
        val round = dayIndex / words.size
        val index = (dayIndex % words.size).toInt()
        val seed = DAILY_NAMESPACE xor (round * ROUND_MIX) xor length.toLong() xor (stream.toLong() shl 32)
        return Rng(seed).shuffled(words)[index]
    }

    private const val DAILY_NAMESPACE = 0x5255535349414eL // "RUSSIAN": independent of English seeds.
    private const val ROUND_MIX = -0x61c8864680b583ebL
}
