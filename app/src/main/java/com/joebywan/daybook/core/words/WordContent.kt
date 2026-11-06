package com.joebywan.daybook.core.words

/** Explicit puzzle-content language. This never reads the app's display locale. */
object WordContent {
    const val ENGLISH = "en"
    const val RUSSIAN = "ru"

    fun alphabet(language: String = ENGLISH): String = when (language) {
        ENGLISH -> "abcdefghijklmnopqrstuvwxyz"
        RUSSIAN -> RussianWords.ALPHABET
        else -> error("Unsupported puzzle-content language: $language")
    }

    fun index(letter: Char, language: String = ENGLISH): Int =
        if (language == ENGLISH) letter - 'a' else alphabet(language).indexOf(letter)

    /** English callers already supply canonical input; keep every legacy English byte unchanged. */
    fun normalize(word: String, language: String = ENGLISH): String = when (language) {
        ENGLISH -> word
        RUSSIAN -> RussianWords.normalize(word)
        else -> error("Unsupported puzzle-content language: $language")
    }
}
