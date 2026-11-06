package com.joebywan.daybook.core

import com.joebywan.daybook.core.i18n.text

/**
 * Three tiers, matching how daily puzzles are usually laid out: one warm-up, one real, one nasty.
 * Each [PuzzleType] decides for itself what the tiers mean — bigger grid, more colours, fewer
 * clues, whatever fits that puzzle.
 */
enum class Difficulty(val englishLabel: String) {
    STANDARD("Standard"),
    HARD("Hard"),
    EXPERT("Expert");

    val label: String get() = text("difficulty.${name.lowercase()}")

    companion object {
        fun fromKey(key: String): Difficulty = entries.firstOrNull { it.name == key } ?: STANDARD
    }
}
