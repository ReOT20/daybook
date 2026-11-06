package com.joebywan.daybook.puzzles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.TutorialFrame
import com.joebywan.daybook.core.TutorialPath
import com.joebywan.daybook.core.i18n.message
import com.joebywan.daybook.core.i18n.text
import com.joebywan.daybook.core.words.WordContent

/** Native Russian content has separate IDs; the display locale never selects a word list or a seed. */
object RussianMini : PuzzleType by Mini {
    override val id = "mini-ru"
    override val displayName get() = text("puzzle.mini_ru.name")
    override val tagline get() = text("puzzle.mini_ru.tagline")
    override val rules get() = listOf(text("puzzle.mini_ru.rule.words")) + Mini.rules + text("puzzle.words_ru.rule.alphabet")
    override val keyboardHelp get() = listOf(text("puzzle.mini_ru.keyboard"))

    internal fun generateVerified(seed: Long, difficulty: Difficulty): MiniState? = MiniLogic.generateVerified(seed, difficulty, "ru")
    override fun generate(seed: Long, difficulty: Difficulty): MiniState =
        generateVerified(seed, difficulty) ?: MiniLogic.lastResort(difficulty, "ru")

    @Composable override fun Preview(modifier: Modifier) = RussianCrosswordMotif(modifier, listOf("НОС", "ОСА", "САД"))

    internal const val TUTORIAL_ANSWER = "нососасад"
    internal fun tutorialBoard() = MiniState(
        size = 3, answer = TUTORIAL_ANSWER,
        clues = MiniLogic.slots(3, TUTORIAL_ANSWER).map { MiniLogic.clueOf(MiniLogic.spelled(TUTORIAL_ANSWER, it), "ru")!! },
        letters = " ".repeat(9), contentLanguage = "ru",
    )

    /** Every typed prefix is a valid intermediate state; a stray letter outside the row is rejected. */
    private fun rowPath(base: MiniState, row: Int, word: String): TutorialPath {
        val stages: List<(PuzzleState) -> Boolean> = (0..word.length).map { length ->
            val expected = base.letters.toCharArray().also { letters ->
                for (i in 0 until length) letters[row * base.size + i] = word[i]
            }.concatToString()
            val accepts: (PuzzleState) -> Boolean = { state ->
                state is MiniState && state.contentLanguage == "ru" && state.letters == expected
            }
            accepts
        }
        return TutorialPath(*stages.toTypedArray())
    }

    override val tutorial: List<TutorialFrame> by lazy {
        val start = tutorialBoard()
        val first = start.withWord(0, "нос").copy(cursor = 2)
        val turned = first.select(0).select(0)
        val second = first.withWord(1, "оса").copy(cursor = 5)
        val firstPath = rowPath(start, 0, "нос")
        val secondPath = rowPath(turned, 1, "оса")
        val turn = TutorialPath(
            { it is MiniState && it.letters == first.letters && it.cursor == 0 && it.across },
            { it is MiniState && it.letters == first.letters && it.cursor == 0 && !it.across },
        )
        listOf(
            TutorialFrame(start.copy(letters = TUTORIAL_ANSWER), message("puzzle.mini_ru.tutorial.intro"),
                BoardHighlight(strong = setOf(0, 1, 2), soft = setOf(3, 6))),
            TutorialFrame(start, message("puzzle.mini_ru.tutorial.first", "clue" to start.clues[0]),
                BoardHighlight(strong = setOf(0, 1, 2)), steps = firstPath.steps,
                retry = message("puzzle.mini_ru.tutorial.first_retry"), done = message("puzzle.mini_ru.tutorial.first_done")),
            TutorialFrame(first, message("puzzle.mini_ru.tutorial.turn"),
                BoardHighlight(strong = setOf(0), soft = setOf(3, 6)), steps = turn.steps,
                retry = message("puzzle.mini_ru.tutorial.turn_retry"), done = message("puzzle.mini_ru.tutorial.turn_done")),
            TutorialFrame(turned, message("puzzle.mini_ru.tutorial.second", "clue" to start.clues[1]),
                BoardHighlight(strong = setOf(3, 4, 5)), steps = secondPath.steps,
                retry = message("puzzle.mini_ru.tutorial.second_retry"), done = message("puzzle.mini_ru.tutorial.second_done")),
            TutorialFrame(second, message("puzzle.mini_ru.tutorial.play"), freePlay = true,
                done = message("puzzle.mini_ru.tutorial.done")),
        )
    }
}

object RussianCodeword : PuzzleType by Codeword {
    override val id = "codeword-ru"
    override val displayName get() = text("puzzle.codeword_ru.name")
    override val tagline get() = text("puzzle.codeword_ru.tagline")
    override val rules get() = Codeword.rules.mapIndexed { index, rule ->
        if (index == 1) text("puzzle.codeword_ru.rule.words") else rule
    } + text("puzzle.words_ru.rule.alphabet")
    override val keyboardHelp get() = listOf(text("puzzle.codeword_ru.keyboard"))

    internal fun generateVerified(seed: Long, difficulty: Difficulty): CodewordState? = CodewordLogic.generateVerified(seed, difficulty, "ru")
    override fun generate(seed: Long, difficulty: Difficulty): CodewordState =
        generateVerified(seed, difficulty) ?: CodewordLogic.lastResort(difficulty, "ru")

    @Composable override fun Preview(modifier: Modifier) = RussianCrosswordMotif(modifier, listOf("Н4С", "4С1", "С1Д"))

    internal val TUTORIAL_NUMBERS = listOf(3, 4, 5, 4, 5, 1, 5, 1, 2)
    internal val TUTORIAL_SOLUTION = "аднос".map { WordContent.index(it, "ru") }
    internal fun tutorialBoard() = CodewordState(
        size = 3, numbers = TUTORIAL_NUMBERS, solution = TUTORIAL_SOLUTION, given = listOf(2, 3),
        letters = TUTORIAL_SOLUTION.indices.map { if (it in 1..2) TUTORIAL_SOLUTION[it] else CodewordLogic.EMPTY },
        contentLanguage = "ru",
    )

    override val tutorial: List<TutorialFrame> by lazy {
        val start = tutorialBoard()
        val o = WordContent.index('о', "ru")
        val s = WordContent.index('с', "ru")
        val first = start.withLetter(4, o)
        val second = first.withLetter(5, s)
        fun path(before: CodewordState, number: Int, letter: Int) = TutorialPath(
            { it is CodewordState && it.letters == before.letters && it.selected == number },
            { it is CodewordState && it.letters == before.withLetter(number, letter).letters },
        )
        val firstPath = path(start, 4, o)
        val secondPath = path(first, 5, s)
        listOf(
            TutorialFrame(start.copy(letters = TUTORIAL_SOLUTION), message("puzzle.codeword_ru.tutorial.intro"),
                BoardHighlight(strong = setOf(1, 3), soft = setOf(0))),
            TutorialFrame(start, message("puzzle.codeword_ru.tutorial.first"),
                glow = { now -> if ((now as CodewordState).selected == 4)
                    BoardHighlight(strong = setOf(CodewordLogic.letterKey(o)), soft = setOf(1, 3))
                    else BoardHighlight(strong = setOf(1, 3, CodewordLogic.key(4))) },
                steps = firstPath.steps,
                retry = message("puzzle.codeword_ru.tutorial.first_retry"), done = message("puzzle.codeword_ru.tutorial.first_done")),
            TutorialFrame(first, message("puzzle.codeword_ru.tutorial.second"),
                BoardHighlight(strong = setOf(2, 4, 6, CodewordLogic.key(5))), steps = secondPath.steps,
                retry = message("puzzle.codeword_ru.tutorial.second_retry"), done = message("puzzle.codeword_ru.tutorial.second_done")),
            TutorialFrame(second, message("puzzle.codeword_ru.tutorial.play"), freePlay = true,
                done = message("puzzle.codeword_ru.tutorial.done")),
        )
    }
}

object RussianCipher : PuzzleType by Cipher {
    override val id = "cipher-ru"
    override val displayName get() = text("puzzle.cipher_ru.name")
    override val tagline get() = text("puzzle.cipher_ru.tagline")
    override val rules get() = listOf(text("puzzle.cipher_ru.rule.words")) + Cipher.rules + text("puzzle.words_ru.rule.alphabet")
    override val keyboardHelp get() = listOf(text("puzzle.cipher_ru.keyboard"))

    internal fun generateVerified(seed: Long, difficulty: Difficulty): CipherState? = CipherLogic.generateVerified(seed, difficulty, "ru")
    override fun generate(seed: Long, difficulty: Difficulty): CipherState =
        generateVerified(seed, difficulty) ?: CipherLogic.lastResort(difficulty, "ru")

    @Composable override fun Preview(modifier: Modifier) = RussianCrosswordMotif(modifier, listOf("НОС", "СОН", "СЕНО"))

    internal val TUTORIAL_WORDS = listOf("нос", "сон", "сено")
    internal val TUTORIAL_SOLUTION = listOf(0, 8, 9, 1) // Е Н О С: 891 + 198 = 1089.
    internal fun tutorialBoard() = CipherState(
        words = TUTORIAL_WORDS, solution = TUTORIAL_SOLUTION,
        digits = List(4) { CipherLogic.EMPTY }, contentLanguage = "ru",
    )

    override val tutorial: List<TutorialFrame> by lazy {
        val start = tutorialBoard()
        val sum = start.sum
        val s = sum.letters.indexOf('с')
        val e = sum.letters.indexOf('е')
        val first = start.withDigit(s, 1)
        val second = first.withDigit(e, 0)
        fun path(before: CipherState, letter: Int, digit: Int) = TutorialPath(
            { it is CipherState && it.digits == before.digits && it.selected == letter },
            { it is CipherState && it.digits == before.withDigit(letter, digit).digits },
        )
        val firstPath = path(start, s, 1)
        val secondPath = path(first, e, 0)
        listOf(
            TutorialFrame(start, message("puzzle.cipher_ru.tutorial.intro"),
                BoardHighlight(strong = (0 until sum.letters.size).flatMap { sum.tilesOf(it) }.toSet())),
            TutorialFrame(start, message("puzzle.cipher_ru.tutorial.first"),
                glow = { now -> if ((now as CipherState).selected == s)
                    BoardHighlight(strong = setOf(CipherLogic.pad(1)), soft = sum.tilesOf(s))
                    else BoardHighlight(strong = sum.tilesOf(s)) },
                steps = firstPath.steps,
                retry = message("puzzle.cipher_ru.tutorial.first_retry"), done = message("puzzle.cipher_ru.tutorial.first_done")),
            TutorialFrame(first, message("puzzle.cipher_ru.tutorial.second"),
                BoardHighlight(strong = sum.tilesOf(e), soft = sum.columnTiles(2)), steps = secondPath.steps,
                retry = message("puzzle.cipher_ru.tutorial.second_retry"), done = message("puzzle.cipher_ru.tutorial.second_done")),
            TutorialFrame(second, message("puzzle.cipher_ru.tutorial.play"), freePlay = true,
                done = message("puzzle.cipher_ru.tutorial.done")),
        )
    }
}

/** A fixed motif is cheap and cannot consume a generator's random stream. */
@Composable
private fun RussianCrosswordMotif(modifier: Modifier, rows: List<String>) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (row in rows) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (letter in row) Text(letter.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}
