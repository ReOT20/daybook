package com.joebywan.daybook.core

import com.joebywan.daybook.core.i18n.text

/** Longest tier line, in characters: one line in the rules sheet on a phone. */
const val TIER_NOTE_MAX = 60

/**
 * What the player will see change between the tiers, one line each for Standard, Hard and Expert, keyed by puzzle id.
 * Only what shows on the board (sizes, counts, what is given) or, where every tier looks alike, which kind of step it
 * needs; never "harder", never a number the player cannot see. A puzzle without three lines fails `TierNotesTest`.
 * Taken from each puzzle's tier spec: change a tier, change its line.
 */
private val TIER_NOTES: Map<String, List<String>> = mapOf(
    "sudoku" to listOf(
        "puzzle.sudoku.tier.1",
        "puzzle.sudoku.tier.2",
        "puzzle.sudoku.tier.3",
    ),
    "kings" to listOf("puzzle.kings.tier.1", "puzzle.kings.tier.2", "puzzle.kings.tier.3"),
    "mambo" to listOf("puzzle.mambo.tier.1", "puzzle.mambo.tier.2", "puzzle.mambo.tier.3"),
    "pipes" to listOf("puzzle.pipes.tier.1", "puzzle.pipes.tier.2", "puzzle.pipes.tier.3"),
    "shikaku" to listOf(
        "puzzle.shikaku.tier.1",
        "puzzle.shikaku.tier.2",
        "puzzle.shikaku.tier.3",
    ),
    "mosaic" to listOf("puzzle.mosaic.tier.1", "puzzle.mosaic.tier.2", "puzzle.mosaic.tier.3"),
    "sets" to listOf("puzzle.sets.tier.1", "puzzle.sets.tier.2", "puzzle.sets.tier.3"),
    "atoms" to listOf("puzzle.atoms.tier.1", "puzzle.atoms.tier.2", "puzzle.atoms.tier.3"),
    "snap" to listOf("puzzle.snap.tier.1", "puzzle.snap.tier.2", "puzzle.snap.tier.3"),
    "lits" to listOf("puzzle.lits.tier.1", "puzzle.lits.tier.2", "puzzle.lits.tier.3"),
    "tower" to listOf("puzzle.tower.tier.1", "puzzle.tower.tier.2", "puzzle.tower.tier.3"),
    "words" to listOf("puzzle.words.tier.1", "puzzle.words.tier.2", "puzzle.words.tier.3"),
    "nonogram" to listOf("puzzle.nonogram.tier.1", "puzzle.nonogram.tier.2", "puzzle.nonogram.tier.3"),
    "inequality" to listOf("puzzle.inequality.tier.1", "puzzle.inequality.tier.2", "puzzle.inequality.tier.3"),
    "chess" to listOf("puzzle.chess.tier.1", "puzzle.chess.tier.2", "puzzle.chess.tier.3"),
    "tents" to listOf("puzzle.tents.tier.1", "puzzle.tents.tier.2", "puzzle.tents.tier.3"),
    "regions" to listOf("puzzle.regions.tier.1", "puzzle.regions.tier.2", "puzzle.regions.tier.3"),
    "cages" to listOf(
        "puzzle.cages.tier.1",
        "puzzle.cages.tier.2",
        "puzzle.cages.tier.3",
    ),
    "target" to listOf(
        "puzzle.target.tier.1",
        "puzzle.target.tier.2",
        "puzzle.target.tier.3",
    ),
    "cipher" to listOf(
        "puzzle.cipher.tier.1",
        "puzzle.cipher.tier.2",
        "puzzle.cipher.tier.3",
    ),
    "hexwords" to listOf(
        "puzzle.hexwords.tier.1",
        "puzzle.hexwords.tier.2",
        "puzzle.hexwords.tier.3",
    ),
    "wordwheel" to listOf(
        "puzzle.wordwheel.tier.1",
        "puzzle.wordwheel.tier.2",
        "puzzle.wordwheel.tier.3",
    ),
    "dwindle" to listOf(
        "puzzle.dwindle.tier.1",
        "puzzle.dwindle.tier.2",
        "puzzle.dwindle.tier.3",
    ),
    "tally" to listOf("puzzle.tally.tier.1", "puzzle.tally.tier.2", "puzzle.tally.tier.3"),
    "sand" to listOf("puzzle.sand.tier.1", "puzzle.sand.tier.2", "puzzle.sand.tier.3"),
    "pointers" to listOf("puzzle.pointers.tier.1", "puzzle.pointers.tier.2", "puzzle.pointers.tier.3"),
    "dominoes" to listOf("puzzle.dominoes.tier.1", "puzzle.dominoes.tier.2", "puzzle.dominoes.tier.3"),
    "mines" to listOf("puzzle.mines.tier.1", "puzzle.mines.tier.2", "puzzle.mines.tier.3"),
    "lamps" to listOf("puzzle.lamps.tier.1", "puzzle.lamps.tier.2", "puzzle.lamps.tier.3"),
    "codeword" to listOf("puzzle.codeword.tier.1", "puzzle.codeword.tier.2", "puzzle.codeword.tier.3"),
    "pearls" to listOf("puzzle.pearls.tier.1", "puzzle.pearls.tier.2", "puzzle.pearls.tier.3"),
    "whoswho" to listOf(
        "puzzle.whoswho.tier.1",
        "puzzle.whoswho.tier.2",
        "puzzle.whoswho.tier.3",
    ),
    "mini" to listOf("puzzle.mini.tier.1", "puzzle.mini.tier.2", "puzzle.mini.tier.3"),
    "mini-ru" to listOf("puzzle.mini_ru.tier.1", "puzzle.mini_ru.tier.2", "puzzle.mini_ru.tier.3"),
)

/** The three lines for [puzzleId], Standard first; empty for an unknown id. */
fun tierNotes(puzzleId: String): List<String> =
    (TIER_NOTES[puzzleId] ?: TIER_NOTES[puzzleFamilyId(puzzleId)])?.map { text(it) } ?: emptyList()
