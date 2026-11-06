package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.TutorialFrame
import com.joebywan.daybook.core.i18n.message
import com.joebywan.daybook.core.i18n.text

/** Separate identities and explicit saved content languages; changing display language never changes a board. */
object RussianLexicon : PuzzleType by Lexicon {
    override val id = "words-ru"
    override val displayName get() = text("puzzle.words_ru.name")
    override val tagline get() = text("puzzle.words_ru.tagline")
    override val rules get() = listOf(text("puzzle.words_ru.rule.1")) + Lexicon.rules.drop(1) + text("puzzle.words_ru.rule.alphabet")
    override val keyboardHelp get() = listOf(text("puzzle.words_ru.keyboard"))
    override fun generate(seed: Long, difficulty: Difficulty): LexiconState = LexiconRules.newBoard(seed, difficulty, "ru")

    internal fun tutorialBoard() = LexiconState(5, 6, "масло", guesses = listOf("лампа"), contentLanguage = "ru")
    override val tutorial: List<TutorialFrame> by lazy {
        val start = tutorialBoard()
        val next = start.copy(guesses = start.guesses + "салон")
        listOf(
            TutorialFrame(start, message("puzzle.words_ru.tutorial.1"),
                BoardHighlight(strong = setOf(1), soft = setOf(0, 2))),
            TutorialFrame(start, message("puzzle.words_ru.tutorial.2"),
                BoardHighlight(strong = setOf(3, 4))),
            TutorialFrame(start, message("puzzle.words_ru.tutorial.3"),
                accepts = { it is LexiconState && it.guesses == next.guesses && it.current.isEmpty() },
                retry = message("puzzle.words_ru.tutorial.retry"), done = message("puzzle.words_ru.tutorial.done")),
            TutorialFrame(next, message("puzzle.words_ru.tutorial.play"), freePlay = true),
        )
    }
}

object RussianWordWheel : PuzzleType by WordWheel {
    override val id = "wordwheel-ru"
    override val displayName get() = text("puzzle.wordwheel_ru.name")
    override val tagline get() = text("puzzle.wordwheel_ru.tagline")
    override val rules get() = WordWheel.rules + text("puzzle.words_ru.rule.alphabet")
    override val keyboardHelp get() = listOf(text("puzzle.wordwheel_ru.keyboard"))
    internal fun generateVerified(seed: Long, difficulty: Difficulty): WordWheelState? = WordWheelLogic.generateVerified(seed, difficulty, "ru")
    override fun generate(seed: Long, difficulty: Difficulty): WordWheelState = generateVerified(seed, difficulty) ?: WordWheelLogic.lastResort(difficulty, "ru")

    internal fun tutorialBoard() = WordWheelState(
        wheel = "лноса", width = 5, height = 4,
        slots = listOf(WheelSlot("салон", 1, 0, true), WheelSlot("слон", 0, 2, false),
            WheelSlot("нос", 1, 4, false), WheelSlot("сон", 1, 0, false)),
        contentLanguage = "ru",
    )
    override val tutorial: List<TutorialFrame> by lazy {
        val start = tutorialBoard()
        val first = start.enter("салон")
        val second = first.enter("сало")
        listOf(
            TutorialFrame(start, message("puzzle.wordwheel_ru.tutorial.1"),
                BoardHighlight(strong = start.slots[0].cells(start.width).toSet())),
            TutorialFrame(start, message("puzzle.wordwheel_ru.tutorial.2"),
                BoardHighlight(strong = WordWheelTeacher.letters(start.wheel, "салон")),
                accepts = { it is WordWheelState && it.found == listOf("салон") && it.bonus.isEmpty() },
                retry = message("puzzle.wordwheel_ru.tutorial.retry"), done = message("puzzle.wordwheel_ru.tutorial.done")),
            TutorialFrame(first, message("puzzle.wordwheel_ru.tutorial.3"),
                accepts = { it is WordWheelState && it.found == first.found && it.bonus == listOf("сало") },
                retry = message("puzzle.wordwheel_ru.tutorial.bonus_retry"), done = message("puzzle.wordwheel_ru.tutorial.bonus_done")),
            TutorialFrame(second, message("puzzle.wordwheel_ru.tutorial.play"), freePlay = true),
        )
    }
}

object RussianDwindle : PuzzleType by Dwindle {
    override val id = "dwindle-ru"
    override val displayName get() = text("puzzle.dwindle_ru.name")
    override val tagline get() = text("puzzle.dwindle_ru.tagline")
    override val rules get() = Dwindle.rules + text("puzzle.words_ru.rule.alphabet")
    override val keyboardHelp get() = listOf(text("puzzle.wordwheel_ru.keyboard"))
    internal fun generateVerified(seed: Long, difficulty: Difficulty): DwindleState? = DwindleLogic.generateVerified(seed, difficulty, "ru")
    override fun generate(seed: Long, difficulty: Difficulty): DwindleState = generateVerified(seed, difficulty) ?: DwindleLogic.lastResort(difficulty, "ru")

    internal fun tutorialBoard() = DwindleState(
        width = 3, height = 3, letters = "сиполеван",
        goals = listOf(DwindleGoal("сила", listOf(0, 1, 4, 7)), DwindleGoal("пена", listOf(2, 5, 8, 7)),
            DwindleGoal("сова", listOf(0, 3, 6, 7))),
        diagonal = false, contentLanguage = "ru",
    )
    override val tutorial: List<TutorialFrame> by lazy {
        val start = tutorialBoard()
        val bonus = start.enter("пила")
        val first = bonus.enter("сила")
        listOf(
            TutorialFrame(start, message("puzzle.dwindle_ru.tutorial.1")),
            TutorialFrame(start, message("puzzle.dwindle_ru.tutorial.2"), BoardHighlight(strong = setOf(2, 1, 4, 7)),
                accepts = { it is DwindleState && it.found.isEmpty() && it.bonus == listOf("пила") },
                retry = message("puzzle.dwindle_ru.tutorial.bonus_retry"), done = message("puzzle.dwindle_ru.tutorial.bonus_done")),
            TutorialFrame(bonus, message("puzzle.dwindle_ru.tutorial.3"), BoardHighlight(strong = setOf(0, 1, 4, 7)),
                accepts = { it is DwindleState && it.found == listOf("сила") && it.bonus == bonus.bonus },
                retry = message("puzzle.dwindle_ru.tutorial.retry"), done = message("puzzle.dwindle_ru.tutorial.done")),
            TutorialFrame(first, message("puzzle.dwindle_ru.tutorial.play"), freePlay = true),
        )
    }
}

object RussianHexwords : PuzzleType by Hexwords {
    override val id = "hexwords-ru"
    override val displayName get() = text("puzzle.hexwords_ru.name")
    override val tagline get() = text("puzzle.hexwords_ru.tagline")
    override val rules get() = Hexwords.rules + text("puzzle.words_ru.rule.alphabet")
    override val keyboardHelp get() = listOf(text("puzzle.hexwords_ru.keyboard"))
    internal fun generateVerified(seed: Long, difficulty: Difficulty): HexwordsState? = HexLogic.generateVerified(seed, difficulty, "ru")
    override fun generate(seed: Long, difficulty: Difficulty): HexwordsState = generateVerified(seed, difficulty) ?: HexLogic.lastResort(difficulty, "ru")

    internal fun tutorialBoard() = HexwordsState(rows = listOf(3, 4, 3), letters = "котлисадуб", target = 11, contentLanguage = "ru")
    override val tutorial: List<TutorialFrame> by lazy {
        val start = tutorialBoard()
        val first = start.take("кот", listOf(0, 1, 2))
        listOf(
            TutorialFrame(start, message("puzzle.hexwords_ru.tutorial.1")),
            TutorialFrame(start, message("puzzle.hexwords_ru.tutorial.2"), BoardHighlight(strong = setOf(0, 1, 2)),
                accepts = { it is HexwordsState && it.taken == first.taken },
                retry = message("puzzle.hexwords_ru.tutorial.retry"), done = message("puzzle.hexwords_ru.tutorial.done")),
            TutorialFrame(first, message("puzzle.hexwords_ru.tutorial.play"), freePlay = true),
        )
    }
}
