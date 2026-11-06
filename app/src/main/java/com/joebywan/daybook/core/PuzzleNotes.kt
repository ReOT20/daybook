package com.joebywan.daybook.core

import com.joebywan.daybook.core.i18n.text
import com.joebywan.daybook.core.i18n.SavedText

/**
 * Longest note, in characters. The finished frame wraps text rather than clipping it, and the existing
 * lines top out near 58, so a note this long stays on one line on a phone.
 */
const val NOTE_MAX = 64

/**
 * One true technique tip per puzzle, hand-checked against the rules and the teachers. Keyed by puzzle id;
 * a puzzle without an entry fails `PuzzleNotesTest`. No history or attribution: only things the rules make true.
 */
private val NOTES: Map<String, List<String>> = mapOf(
    "sudoku" to listOf(
        "puzzle.sudoku.tip.1",
        "puzzle.sudoku.tip.2",
        "puzzle.sudoku.tip.3",
        "puzzle.sudoku.tip.4",
        "puzzle.sudoku.tip.5",
    ),
    "kings" to listOf(
        "puzzle.kings.tip.1",
        "puzzle.kings.tip.2",
        "puzzle.kings.tip.3",
        "puzzle.kings.tip.4",
    ),
    "mambo" to listOf(
        "puzzle.mambo.tip.1",
        "puzzle.mambo.tip.2",
        "puzzle.mambo.tip.3",
        "puzzle.mambo.tip.4",
    ),
    "pipes" to listOf(
        "puzzle.pipes.tip.1",
        "puzzle.pipes.tip.2",
        "puzzle.pipes.tip.3",
    ),
    "shikaku" to listOf(
        "puzzle.shikaku.tip.1",
        "puzzle.shikaku.tip.2",
        "puzzle.shikaku.tip.3",
        "puzzle.shikaku.tip.4",
    ),
    "mosaic" to listOf(
        "puzzle.mosaic.tip.1",
        "puzzle.mosaic.tip.2",
        "puzzle.mosaic.tip.3",
    ),
    "sets" to listOf(
        "puzzle.sets.tip.1",
        "puzzle.sets.tip.2",
        "puzzle.sets.tip.3",
    ),
    "atoms" to listOf(
        "puzzle.atoms.tip.1",
        "puzzle.atoms.tip.2",
        "puzzle.atoms.tip.3",
    ),
    "snap" to listOf(
        "puzzle.snap.tip.1",
        "puzzle.snap.tip.2",
        "puzzle.snap.tip.3",
    ),
    "lits" to listOf(
        "puzzle.lits.tip.1",
        "puzzle.lits.tip.2",
        "puzzle.lits.tip.3",
    ),
    "tower" to listOf(
        "puzzle.tower.tip.1",
        "puzzle.tower.tip.2",
        "puzzle.tower.tip.3",
        "puzzle.tower.tip.4",
    ),
    "words" to listOf(
        "puzzle.words.tip.1",
        "puzzle.words.tip.2",
        "puzzle.words.tip.3",
        "puzzle.words.tip.4",
    ),
    "nonogram" to listOf(
        "puzzle.nonogram.tip.1",
        "puzzle.nonogram.tip.2",
        "puzzle.nonogram.tip.3",
        "puzzle.nonogram.tip.4",
    ),
    "chess" to listOf(
        "puzzle.chess.tip.1",
        "puzzle.chess.tip.2",
        "puzzle.chess.tip.3",
        "puzzle.chess.tip.4",
    ),
    "inequality" to listOf(
        "puzzle.inequality.tip.1",
        "puzzle.inequality.tip.2",
        "puzzle.inequality.tip.3",
        "puzzle.inequality.tip.4",
    ),
    "regions" to listOf(
        "puzzle.regions.tip.1",
        "puzzle.regions.tip.2",
        "puzzle.regions.tip.3",
        "puzzle.regions.tip.4",
    ),
    "tents" to listOf(
        "puzzle.tents.tip.1",
        "puzzle.tents.tip.2",
        "puzzle.tents.tip.3",
        "puzzle.tents.tip.4",
    ),
    "cages" to listOf(
        "puzzle.cages.tip.1",
        "puzzle.cages.tip.2",
        "puzzle.cages.tip.3",
        "puzzle.cages.tip.4",
        "puzzle.cages.tip.5",
    ),
    "target" to listOf(
        "puzzle.target.tip.1",
        "puzzle.target.tip.2",
        "puzzle.target.tip.3",
        "puzzle.target.tip.4",
        "puzzle.target.tip.5",
    ),
    "cipher" to listOf(
        "puzzle.cipher.tip.1",
        "puzzle.cipher.tip.2",
        "puzzle.cipher.tip.3",
        "puzzle.cipher.tip.4",
        "puzzle.cipher.tip.5",
    ),
    "codeword" to listOf(
        "puzzle.codeword.tip.1",
        "puzzle.codeword.tip.2",
        "puzzle.codeword.tip.3",
        "puzzle.codeword.tip.4",
        "puzzle.codeword.tip.5",
    ),
    "mini" to listOf(
        "puzzle.mini.tip.1",
        "puzzle.mini.tip.2",
        "puzzle.mini.tip.3",
        "puzzle.mini.tip.4",
        "puzzle.mini.tip.5",
    ),
    "hexwords" to listOf(
        "puzzle.hexwords.tip.1",
        "puzzle.hexwords.tip.2",
        "puzzle.hexwords.tip.3",
        "puzzle.hexwords.tip.4",
        "puzzle.hexwords.tip.5",
    ),
    "dwindle" to listOf(
        "puzzle.dwindle.tip.1",
        "puzzle.dwindle.tip.2",
        "puzzle.dwindle.tip.3",
        "puzzle.dwindle.tip.4",
        "puzzle.dwindle.tip.5",
    ),
    "wordwheel" to listOf(
        "puzzle.wordwheel.tip.1",
        "puzzle.wordwheel.tip.2",
        "puzzle.wordwheel.tip.3",
        "puzzle.wordwheel.tip.4",
        "puzzle.wordwheel.tip.5",
    ),
    "tally" to listOf(
        "puzzle.tally.tip.1",
        "puzzle.tally.tip.2",
        "puzzle.tally.tip.3",
        "puzzle.tally.tip.4",
    ),
    "sand" to listOf(
        "puzzle.sand.tip.1",
        "puzzle.sand.tip.2",
        "puzzle.sand.tip.3",
        "puzzle.sand.tip.4",
    ),
    "pointers" to listOf(
        "puzzle.pointers.tip.1",
        "puzzle.pointers.tip.2",
        "puzzle.pointers.tip.3",
        "puzzle.pointers.tip.4",
    ),
    "mines" to listOf(
        "puzzle.mines.tip.1",
        "puzzle.mines.tip.2",
        "puzzle.mines.tip.3",
        "puzzle.mines.tip.4",
    ),
    "dominoes" to listOf(
        "puzzle.dominoes.tip.1",
        "puzzle.dominoes.tip.2",
        "puzzle.dominoes.tip.3",
        "puzzle.dominoes.tip.4",
    ),
    "lamps" to listOf(
        "puzzle.lamps.tip.1",
        "puzzle.lamps.tip.2",
        "puzzle.lamps.tip.3",
        "puzzle.lamps.tip.4",
    ),
    "pearls" to listOf(
        "puzzle.pearls.tip.1",
        "puzzle.pearls.tip.2",
        "puzzle.pearls.tip.3",
        "puzzle.pearls.tip.4",
    ),
    "whoswho" to listOf(
        "puzzle.whoswho.tip.1",
        "puzzle.whoswho.tip.2",
        "puzzle.whoswho.tip.3",
        "puzzle.whoswho.tip.4",
    ),
)

/** The note for [puzzleId] on [epochDay]: pure, so the same day always says the same thing. Null for an unknown id. */
fun puzzleNote(puzzleId: String, epochDay: Long): String? = puzzleNoteText(puzzleId, epochDay)?.resolve()

internal fun puzzleNoteText(puzzleId: String, epochDay: Long): SavedText? {
    val notes = NOTES[puzzleFamilyId(puzzleId)] ?: return null
    return SavedText(notes[(epochDay + puzzleId.sumOf { it.code }).mod(notes.size.toLong()).toInt()])
}

internal fun notesOf(puzzleId: String): List<String> = NOTES[puzzleFamilyId(puzzleId)]?.map { text(it) } ?: emptyList()
