package com.joebywan.daybook.core

import com.joebywan.daybook.core.i18n.text
import com.joebywan.daybook.core.i18n.SavedText

/** Longest technique name, so "You learned <name>." stays within two lines on a phone. */
const val TECHNIQUE_NAME_MAX = 40

/**
 * What the finished frame calls each teacher's techniques (`Deduction.technique`), keyed by puzzle id: a short noun
 * phrase in the words the hints use, so the name is the one the player was taught. Mistakes and fallbacks have none.
 *
 * Each puzzle's list is in the order praise prefers, the last the most worth naming: the teacher's ladder, simplest
 * first, with steps that only point at a word or a guess (Lexicon's, Tower's, Word Wheel's clue, Mosaic's fill within
 * the limit) moved to the front. `TechniqueNamesTest` fails for a teacher's technique without a name.
 * Steps in [NOT_PRAISED] keep their name but are never praised.
 */
private val NAME_KEYS: Map<String, Map<String, String>> = mapOf(
    "sudoku" to linkedMapOf(
        "full-house" to "technique.sudoku.full-house",
        "hidden-single-box" to "technique.sudoku.hidden-single-box",
        "hidden-single-line" to "technique.sudoku.hidden-single-line",
        "naked-single" to "technique.sudoku.naked-single",
        "locked-candidates" to "technique.sudoku.locked-candidates",
        "naked-pair" to "technique.sudoku.naked-pair",
        "hidden-pair" to "technique.sudoku.hidden-pair",
        "what-if" to "technique.sudoku.what-if",
    ),
    "kings" to linkedMapOf(
        "last-square" to "technique.kings.last-square",
        "locked-to-a-line" to "technique.kings.locked-to-a-line",
        "would-empty" to "technique.kings.would-empty",
        "confined" to "technique.kings.confined",
        "what-if" to "technique.kings.what-if",
    ),
    "mambo" to linkedMapOf(
        "pair" to "technique.mambo.pair",
        "sandwich" to "technique.mambo.sandwich",
        "link" to "technique.mambo.link",
        "quota-met" to "technique.mambo.quota-met",
        "quota-almost-met" to "technique.mambo.quota-almost-met",
        "what-if" to "technique.mambo.what-if",
    ),
    "pipes" to linkedMapOf(
        "border" to "technique.pipes.border",
        "set-neighbour" to "technique.pipes.set-neighbour",
        "whichever-way" to "technique.pipes.whichever-way",
        "no-loop" to "technique.pipes.no-loop",
    ),
    "shikaku" to linkedMapOf(
        "only-fits" to "technique.shikaku.only-fits",
        "only-reaches" to "technique.shikaku.only-reaches",
        "common-cells" to "technique.shikaku.common-cells",
        "what-if" to "technique.shikaku.what-if",
    ),
    "mosaic" to linkedMapOf(
        "keeps-in-limit" to "technique.mosaic.keeps-in-limit",
        "finish" to "technique.mosaic.finish",
        "count" to "technique.mosaic.count",
        "biggest-swallow" to "technique.mosaic.biggest-swallow",
        "centre" to "technique.mosaic.centre",
    ),
    "sets" to linkedMapOf(
        "continue" to "technique.sets.continue",
        "fresh" to "technique.sets.fresh",
        "reuse" to "technique.sets.reuse",
    ),
    "atoms" to linkedMapOf(
        "one-neighbour" to "technique.atoms.one-neighbour",
        "all-forced" to "technique.atoms.all-forced",
        "at-least-one" to "technique.atoms.at-least-one",
        "crossing" to "technique.atoms.crossing",
        "isolation" to "technique.atoms.isolation",
        "only-way-out" to "technique.atoms.only-way-out",
        "what-if" to "technique.atoms.what-if",
    ),
    "lits" to linkedMapOf(
        "whole-region" to "technique.lits.whole-region",
        "overlap" to "technique.lits.overlap",
        "avoid-2x2" to "technique.lits.avoid-2x2",
        "letter-clash" to "technique.lits.letter-clash",
        "neighbour" to "technique.lits.neighbour",
        "what-if" to "technique.lits.what-if",
    ),
    "tower" to linkedMapOf(
        "opener" to "technique.tower.opener",
        "submit" to "technique.tower.submit",
        "consistent-guess" to "technique.tower.consistent-guess",
        "only-code-left" to "technique.tower.only-code-left",
        "one-change" to "technique.tower.one-change",
        "only-colour-left" to "technique.tower.only-colour-left",
        "pips-accounted" to "technique.tower.pips-accounted",
        "what-if" to "technique.tower.what-if",
    ),
    "words" to linkedMapOf(
        "opener" to "technique.words.opener",
        "submit" to "technique.words.submit",
        "best-guess" to "technique.words.best-guess",
        "only-word-left" to "technique.words.only-word-left",
        "pinned-letter" to "technique.words.pinned-letter",
    ),
    "nonogram" to linkedMapOf(
        "full-line" to "technique.nonogram.full-line",
        "line-finished" to "technique.nonogram.line-finished",
        "overlap" to "technique.nonogram.overlap",
        "line-logic" to "technique.nonogram.line-logic",
    ),
    "inequality" to linkedMapOf(
        "last-square" to "technique.inequality.last-square",
        "only-digit" to "technique.inequality.only-digit",
        "only-place" to "technique.inequality.only-place",
        "sign-bound" to "technique.inequality.sign-bound",
        "sign-place" to "technique.inequality.sign-place",
        "naked-pair" to "technique.inequality.naked-pair",
        "hidden-pair" to "technique.inequality.hidden-pair",
    ),
    "chess" to linkedMapOf(
        "mate-in-one" to "technique.chess.mate-in-one",
        "forcing-key" to "technique.chess.forcing-key",
        "quiet-key" to "technique.chess.quiet-key",
    ),
    "tents" to linkedMapOf(
        "no-tree" to "technique.tents.no-tree",
        "line-done" to "technique.tents.line-done",
        "line-full" to "technique.tents.line-full",
        "touch" to "technique.tents.touch",
        "one-spot" to "technique.tents.one-spot",
        "shared" to "technique.tents.shared",
        "served" to "technique.tents.served",
    ),
    "regions" to linkedMapOf(
        "last-square" to "technique.regions.last-square",
        "only-digit" to "technique.regions.only-digit",
        "only-place" to "technique.regions.only-place",
        "locked-candidate" to "technique.regions.locked-candidate",
    ),
    "cages" to linkedMapOf(
        "full-house" to "technique.cages.full-house",
        "cage-last" to "technique.cages.cage-last",
        "hidden-single-box" to "technique.cages.hidden-single-box",
        "hidden-single-line" to "technique.cages.hidden-single-line",
        "naked-single" to "technique.cages.naked-single",
        "cage-combination" to "technique.cages.cage-combination",
        "forty-five" to "technique.cages.forty-five",
        "linked-totals" to "technique.cages.linked-totals",
        "cage-chain" to "technique.cages.cage-chain",
    ),
    "target" to linkedMapOf(
        "step" to "technique.target.step",
        "finish" to "technique.target.finish",
    ),
    "cipher" to linkedMapOf(
        "leading-carry" to "technique.cipher.leading-carry",
        "column" to "technique.cipher.column",
        "only-letter" to "technique.cipher.only-letter",
        "column-pair" to "technique.cipher.column-pair",
        "columns-together" to "technique.cipher.columns-together",
        "pair-what-if" to "technique.cipher.pair-what-if",
    ),
    "hexwords" to linkedMapOf(
        "biggest" to "technique.hexwords.biggest",
        "hold-back" to "technique.hexwords.hold-back",
        "rare" to "technique.hexwords.rare",
        "unchecked" to "technique.hexwords.unchecked",
    ),
    "dwindle" to linkedMapOf(
        "clue" to "technique.dwindle.clue",
        "through" to "technique.dwindle.through",
        "length" to "technique.dwindle.length",
        "all-tiles" to "technique.dwindle.all-tiles",
    ),
    "wordwheel" to linkedMapOf(
        "clue" to "technique.wordwheel.clue",
        "pinned" to "technique.wordwheel.pinned",
        "crossing" to "technique.wordwheel.crossing",
    ),
    "tally" to linkedMapOf(
        "line-done" to "technique.tally.line-done",
        "too-big" to "technique.tally.too-big",
        "too-much" to "technique.tally.too-much",
        "all-left" to "technique.tally.all-left",
        "every-way" to "technique.tally.every-way",
        "what-if" to "technique.tally.what-if",
    ),
    "sand" to linkedMapOf(
        "finish" to "technique.sand.finish",
        "gather" to "technique.sand.gather",
        "spare" to "technique.sand.spare",
        "plan" to "technique.sand.plan",
    ),
    "mines" to linkedMapOf(
        "all-found" to "technique.mines.all-found",
        "all-mines" to "technique.mines.all-mines",
        "subset" to "technique.mines.subset",
        "pair" to "technique.mines.pair",
        "count" to "technique.mines.count",
        "count-edge" to "technique.mines.count-edge",
        "what-if" to "technique.mines.what-if",
    ),
    "dominoes" to linkedMapOf(
        "only-partner" to "technique.dominoes.only-partner",
        "only-place" to "technique.dominoes.only-place",
        "only-domino" to "technique.dominoes.only-domino",
        "region-value" to "technique.dominoes.region-value",
        "counting" to "technique.dominoes.counting",
        "what-if" to "technique.dominoes.what-if",
    ),
    "lamps" to linkedMapOf(
        "zero" to "technique.lamps.zero",
        "done" to "technique.lamps.done",
        "room" to "technique.lamps.room",
        "only-light" to "technique.lamps.only-light",
        "starve" to "technique.lamps.starve",
        "dark" to "technique.lamps.dark",
        "what-if" to "technique.lamps.what-if",
    ),
    "mini" to linkedMapOf(
        "clue" to "technique.mini.clue",
        "one-word" to "technique.mini.one-word",
        "crossing" to "technique.mini.crossing",
        "all-words" to "technique.mini.all-words",
    ),
    "codeword" to linkedMapOf(
        "one-word" to "technique.codeword.one-word",
        "run-agrees" to "technique.codeword.run-agrees",
        "crossing" to "technique.codeword.crossing",
        "all-words" to "technique.codeword.all-words",
    ),
    "pearls" to linkedMapOf(
        "black-pearl" to "technique.pearls.black-pearl",
        "white-pearl" to "technique.pearls.white-pearl",
        "white-turn" to "technique.pearls.white-turn",
        "one-way" to "technique.pearls.one-way",
        "pearl-ways" to "technique.pearls.pearl-ways",
        "dead-end" to "technique.pearls.dead-end",
        "small-loop" to "technique.pearls.small-loop",
        "what-if" to "technique.pearls.what-if",
    ),
    "whoswho" to linkedMapOf(
        "clue" to "technique.whoswho.clue",
        "only" to "technique.whoswho.only",
        "link" to "technique.whoswho.link",
        "house" to "technique.whoswho.house",
        "what-if" to "technique.whoswho.what-if",
    ),
)

/**
 * Steps that are not a skill the player now has: "You learned <name>." would not make sense of them. They point at a word,
 * a guess, a set or a move rather than teach a way of seeing one, so a solve whose hints showed only these keeps the
 * generic hint line. A few are here because a hint can never give them, so the Techniques page could never tick them:
 * Cipher's only letter (ten letters; no listed sum has more than nine), Pearls' two ways in (the pearl's own rule always
 * speaks first), Tents' grass around a tent (the game sprouts it, and the teacher reads the sprouted board).
 */
private val NOT_PRAISED: Map<String, Set<String>> = mapOf(
    "mosaic" to setOf("keeps-in-limit", "finish"),
    "sets" to setOf("continue", "fresh"),
    "tower" to setOf("opener", "submit", "consistent-guess", "only-code-left"),
    "words" to setOf("opener", "submit", "best-guess", "only-word-left"),
    "target" to setOf("step", "finish"),
    "wordwheel" to setOf("clue"),
    "hexwords" to setOf("unchecked"),
    "dwindle" to setOf("clue"),
    "mini" to setOf("clue"),
    "sand" to setOf("finish"),
    "cipher" to setOf("only-letter"),
    "pearls" to setOf("pearl-ways"),
    "tents" to setOf("touch"),
)

/** The techniques of [puzzleId] that praise may name. */
fun praisedTechniquesOf(puzzleId: String): Set<String> = techniqueNamesOf(puzzleId).keys - NOT_PRAISED[puzzleFamilyId(puzzleId)].orEmpty()

/** [puzzleId]'s technique names in praise order; empty for a puzzle without a teacher. */
fun techniqueNamesOf(puzzleId: String): Map<String, String> = NAME_KEYS[puzzleFamilyId(puzzleId)].orEmpty().mapValues { (_, key) -> text(key) }

/** A retained celebration needs the same catalog key, rather than yesterday's rendered name. */
internal fun techniqueNameText(puzzleId: String, technique: String): SavedText? =
    NAME_KEYS[puzzleFamilyId(puzzleId)]?.get(technique)?.let { SavedText(it) }

/** The name of the one of [ids] most worth naming, or null when none has a name (only mistakes, say). */
fun techniqueToPraise(puzzleId: String, ids: Collection<String>): String? =
    techniqueNamesOf(puzzleId).entries.lastOrNull { it.key in ids && it.key in praisedTechniquesOf(puzzleId) }?.value
