package com.joebywan.daybook.ui.play

import com.joebywan.daybook.core.i18n.tr
import com.joebywan.daybook.core.i18n.russianPlural
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.plural
import com.joebywan.daybook.data.Completion
import com.joebywan.daybook.data.LevelSolve
import com.joebywan.daybook.puzzles.ChessState
import com.joebywan.daybook.puzzles.LexiconMark
import com.joebywan.daybook.puzzles.LexiconState
import com.joebywan.daybook.puzzles.PointersLogic
import com.joebywan.daybook.puzzles.PointersState
import com.joebywan.daybook.puzzles.PuzzleState
import com.joebywan.daybook.puzzles.SetsState
import com.joebywan.daybook.puzzles.TargetState
import com.joebywan.daybook.puzzles.TowerState
import com.joebywan.daybook.puzzles.DwindleState
import com.joebywan.daybook.puzzles.WordWheelState
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * The play log: what happened in a game, in order, one character per event, kept in [com.joebywan.daybook.data.SavedGame.log]
 * beside the undo stack. It is never `PuzzleState`. The share graphic is read from it ([shareCard]).
 *
 * `m` a move, `w` a claim that was not a set (Sets: no board change, so Undo never takes it back), `h` a hint opened
 * (one per counted hint), `u` Undo took back the latest live move (or, with none since, the latest restart, handing its
 * moves back), `r` Restart took back every live move.
 */
object PlayLog {
    const val MOVE = 'm'
    const val WRONG = 'w'
    const val HINT = 'h'
    const val UNDO = 'u'
    const val RESTART = 'r'

    /**
     * What a push from [prev] to [next] adds to the log. A move is a change of board ([PuzzleType.sameBoard]), so picking
     * a cell or a card is not one; in Lexicon and Tower only a submitted guess is (typing a letter or a peg is not), and in
     * Target a push that takes operations back ("Show me" on a mistake) is that many undos.
     */
    fun step(puzzle: PuzzleType, prev: PuzzleState, next: PuzzleState): String = when {
        next is SetsState && next.lastWrong -> WRONG.toString()
        next is LexiconState && prev is LexiconState -> if (next.guesses.size > prev.guesses.size) MOVE.toString() else ""
        next is TowerState && prev is TowerState -> if (next.guesses.size > prev.guesses.size) MOVE.toString() else ""
        next is TargetState && prev is TargetState -> (next.log.size - prev.log.size).let {
            if (it > 0) MOVE.toString() else UNDO.toString().repeat(-it)
        }
        puzzle.sameBoard(prev, next) -> ""
        else -> MOVE.toString()
    }

    /** One move the log records: a hint opened since the move before makes it [hinted]. */
    class Played(val wrong: Boolean, val hinted: Boolean) {
        var undone = false
            internal set
    }

    /** Every move in [log], in order, each knowing whether it was later taken back. */
    fun played(log: String): List<Played> {
        val all = mutableListOf<Played>()
        val live = ArrayDeque<Played>()
        // What each restart took back, so an Undo of the restart can hand it back.
        val restarts = ArrayDeque<List<Played>>()
        var hint = false
        for (c in log) when (c) {
            HINT -> hint = true
            MOVE, WRONG -> {
                val p = Played(c == WRONG, hint)
                hint = false
                all += p
                if (c == MOVE) live.addLast(p)
            }
            UNDO -> if (live.isNotEmpty()) {
                live.removeLast().undone = true
            } else {
                restarts.removeLastOrNull()?.let { back -> back.forEach { it.undone = false }; live.addAll(back) }
            }
            RESTART -> { live.forEach { it.undone = true }; restarts.addLast(live.toList()); live.clear() }
        }
        return all
    }
}

/** One symbol of the graphic: its emoji (or pip) in the shared text; the preview draws it natively. */
enum class Mark(val text: String) {
    // Squares in rising severity: a strip square shows the worst of its moves.
    GREEN("🟩"), WHITE("⬜"), YELLOW("🟨"), RED("🟥"),
    /** Tower: right colour, right slot. */
    EXACT("●"),
    /** Tower: right colour, wrong slot. */
    CLOSE("○"),
    /** Tower: a peg that scored nothing. */
    NONE("·"),
    /** Pointers: a life left. */
    HEART("❤️"),
    /** Pointers: a life lost to a bump. */
    LOST("🖤"),
}

/** A line of the graphic: [marks], then a red square when a hint came before it ([hinted]), then [tail] as written. */
@Serializable
data class ShareLine(val marks: List<Mark>, val hinted: Boolean = false, val tail: String = "") {
    fun text(spaced: Boolean): String =
        marks.joinToString(if (spaced) " " else "") { it.text } + (if (hinted) " " + Mark.RED.text else "") + tail
}

/** Everything the share preview shows and the text it sends. Pure data; serialisable, as the stats screen keeps some ([com.joebywan.daybook.data.KeptCard]). */
@Serializable
data class ShareCard(
    val header: String,
    val lines: List<ShareLine>,
    val stats: String,
    val key: List<Pair<Mark, String>>,
    val nudge: String,
    /** Tower's pips are spaced apart in the text. */
    val spaced: Boolean = false,
) {
    val text: String
        get() = buildString {
            append(header).append('\n')
            lines.forEach { append(it.text(spaced)).append('\n') }
            append(stats).append('\n')
            append(key.joinToString("  ") { "${it.first.text} ${it.second}" }).append('\n')
            append(SHARE_URL)
        }
}

/** Worst first: a hint, then a taken-back move, then a claim that was not a set. */
private fun PlayLog.Played.mark() = when {
    hinted -> Mark.RED
    undone -> Mark.YELLOW
    wrong -> Mark.WHITE
    else -> Mark.GREEN
}

/**
 * Ten squares, each a tenth of [moves] in order (one per move under ten), each the worst of its tenth:
 * red (a hint) over yellow (undone) over green.
 */
internal fun strip(moves: List<PlayLog.Played>): List<Mark> {
    val n = moves.size
    if (n <= 10) return moves.map { it.mark() }
    return List(10) { i -> moves.subList(i * n / 10, (i + 1) * n / 10).maxOf { it.mark() } }
}

/**
 * The share card for a solve: [puzzle] finished in [state], with the play [log] ([PlayLog]). [date] is the daily's date
 * already formatted, null for a random board or a [level] board (whose header names the level where the daily has the date); [hints] is the game's counted hints (the log may have started part way,
 * from an older save). Spoiler-free: marks, pips and counts only, never a square, piece, word or number of the board.
 */
fun shareCard(
    puzzle: PuzzleType, tier: Difficulty, date: String?, clock: String, streak: Int,
    state: PuzzleState, log: String, hints: Int, level: Int? = null,
): ShareCard {
    val moves = PlayLog.played(log)
    val undone = moves.count { it.undone }
    var suffix = ""
    val lines: List<ShareLine>
    val key: List<Pair<Mark, String>>
    // A daily comes back tomorrow; a random or level board does not, so its nudges look to the next one instead.
    val next = if (date != null) "tomorrow" else "next time"
    val nextRussian = if (date != null) "завтра" else "в следующий раз"
    // Rows are the final guesses; the log's live moves are the last of them (an older save logged none before).
    fun hintedRows(guessed: Int): (Int) -> Boolean {
        val live = moves.filter { !it.undone && !it.wrong }
        val offset = guessed - live.size
        return { row -> live.getOrNull(row - offset)?.hinted == true }
    }
    fun guessNudge(guessed: Int) =
        if (guessed == 1) tr("First guess. Nothing to improve on.", "С первой попытки. Лучше уже некуда.") else tr("No hints, ${plural(guessed, "guess", "guesses")}. Fewer $next?", "Без подсказок, $guessed ${russianPlural(guessed, "попытка", "попытки", "попыток")}. Сможете меньше $nextRussian?")
    // Every nudge below is for a solve without hints; one with them gets the same line whatever the puzzle.
    val own: String
    when (state) {
        is LexiconState -> {
            val guessed = state.guesses.size
            val hinted = hintedRows(guessed)
            suffix = " · $guessed/${state.maxGuesses}"
            lines = state.allMarks.mapIndexed { row, marks ->
                ShareLine(marks.map {
                    when (it) { LexiconMark.CORRECT -> Mark.GREEN; LexiconMark.PRESENT -> Mark.YELLOW; else -> Mark.WHITE }
                }, hinted(row))
            }
            key = listOf(Mark.GREEN to tr("right place", "верное место"), Mark.YELLOW to tr("wrong place", "неверное место"), Mark.RED to tr("hint", "подсказка"))
            own = guessNudge(guessed)
        }
        is TowerState -> {
            val guessed = state.guesses.size
            val hinted = hintedRows(guessed)
            suffix = " · $guessed/${state.maxGuesses}"
            lines = state.guesses.mapIndexed { row, guess ->
                val f = state.score(guess)
                ShareLine(
                    List(f.exact) { Mark.EXACT } + List(f.misplaced) { Mark.CLOSE } +
                        List(state.slots - f.exact - f.misplaced) { Mark.NONE },
                    hinted(row),
                )
            }
            key = listOf(Mark.EXACT to tr("right colour and slot", "верный цвет и место"), Mark.CLOSE to tr("right colour", "верный цвет"), Mark.RED to tr("hint", "подсказка"))
            own = guessNudge(guessed)
        }
        is SetsState -> {
            lines = listOf(ShareLine(moves.map { it.mark() }))
            key = listOf(Mark.GREEN to tr("a set", "набор"), Mark.WHITE to tr("not a set", "не набор"), Mark.YELLOW to tr("taken back", "отменено"), Mark.RED to tr("hint", "подсказка"))
            val misses = moves.count { it.wrong }
            val off = listOfNotNull(
                misses.takeIf { it > 0 }?.let { if (it == 1) tr("1 pick that wasn't a set", "1 неудачный выбор") else tr("$it picks that weren't sets", "$it ${russianPlural(it, "неудачный выбор", "неудачных выбора", "неудачных выборов")}") },
                undone.takeIf { it > 0 }?.let { tr("${plural(it, "set")} taken back", "отменено наборов: $it") },
            )
            own = if (off.isEmpty()) tr("Every pick a set. Nothing to improve on.", "Каждый выбор — набор. Лучше уже некуда.") else tr("No hints, ${off.joinToString(" and ")}. Fewer $next?", "Без подсказок, ${off.joinToString(" и ")}. Сможете меньше $nextRussian?")
        }
        is ChessState -> {
            suffix = tr(" · mate in ${state.mateIn}", " · мат в ${state.mateIn}")
            lines = listOf(ShareLine(moves.map { it.mark() }, tail = "#"))
            key = listOf(Mark.GREEN to tr("move", "ход"), Mark.YELLOW to tr("taken back", "отменено"), Mark.RED to tr("hint", "подсказка"))
            own = when {
                undone > 0 -> tr("No hints, ${plural(undone, "move")} taken back. Fewer $next?", "Без подсказок, отменено ходов: $undone. Сможете меньше $nextRussian?")
                else -> tr("A clean mate. Nothing to improve on.", "Мат без отмен и подсказок. Лучше уже некуда.")
            }
        }
        is TargetState -> {
            val steps = state.log.size
            lines = listOf(ShareLine(moves.map { it.mark() }, tail = tr("   ${plural(steps, "step")} · par ${state.par}", "   $steps ${russianPlural(steps, "шаг", "шага", "шагов")} · минимум ${state.par}")))
            key = listOf(Mark.GREEN to tr("step", "шаг"), Mark.YELLOW to tr("taken back", "отменено"), Mark.RED to tr("hint", "подсказка"))
            own = when {
                undone > 0 -> tr("No hints, ${plural(undone, "step")} taken back. Fewer $next?", "Без подсказок, отменено шагов: $undone. Сможете меньше $nextRussian?")
                steps > state.par -> tr("No hints, nothing taken back. Par is ${state.par}: $next?", "Без подсказок и отмен. Минимум — ${state.par}: получится $nextRussian?")
                else -> tr("On par, nothing taken back. Nothing to improve on.", "За минимум ходов, без отмен. Лучше уже некуда.")
            }
        }
        // No hints and no undo: the lives are the whole story.
        is PointersState -> {
            val lost = PointersLogic.LIVES - state.lives
            lines = listOf(ShareLine(List(state.lives) { Mark.HEART } + List(lost) { Mark.LOST }))
            key = listOf(Mark.HEART to tr("life left", "жизнь осталась"), Mark.LOST to tr("life lost", "жизнь потеряна"))
            own = if (lost == 0) tr("Not a single bump.", "Ни одного столкновения.") else tr("${plural(lost, "bump")}. None $next?", "$lost ${russianPlural(lost, "столкновение", "столкновения", "столкновений")}. Сможете без них $nextRussian?")
        }
        else -> {
            lines = listOf(ShareLine(strip(moves)))
            key = listOf(Mark.GREEN to tr("own move", "свой ход"), Mark.YELLOW to tr("taken back", "отменено"), Mark.RED to tr("hint", "подсказка"))
            own = when {
                undone > 0 -> tr("No hints, ${plural(undone, "move")} taken back. Fewer $next?", "Без подсказок, отменено ходов: $undone. Сможете меньше $nextRussian?")
                else -> tr("A clean solve. Nothing to improve on.", "Решено без подсказок и отмен. Лучше уже некуда.")
            }
        }
    }
    return ShareCard(
        header = if (level != null) tr("Daybook · Level $level · ${puzzle.displayName} · ${tier.label}$suffix", "Daybook · Уровень $level · ${puzzle.displayName} · ${tier.label}$suffix")
            else tr("Daybook · ${puzzle.displayName} · ${tier.label} · ${date ?: "random board"}$suffix", "Daybook · ${puzzle.displayName} · ${tier.label} · ${date ?: "случайное поле"}$suffix"),
        lines = lines,
        stats = clock +
            (if (hints > 0) tr(" · ${plural(hints, "hint")}", " · $hints ${russianPlural(hints, "подсказка", "подсказки", "подсказок")}") else "") +
            bonusCount(state).let { if (it > 0) tr(" · +${plural(it, "bonus word")}", " · +$it ${russianPlural(it, "бонусное слово", "бонусных слова", "бонусных слов")}") else "" } +
            if (streak >= 2) tr(" · $streak day streak", " · серия: $streak ${russianPlural(streak, "день", "дня", "дней")}") else "",
        key = key,
        nudge = if (hints > 0) tr("The hints got you there. Try ${if (date != null) "tomorrow's" else "the next one"} with fewer.", "Подсказки помогли справиться. Попробуйте ${if (date != null) "завтра" else "в следующий раз"} обойтись меньшим числом подсказок.") else own,
        spaced = state is TowerState,
    )
}

/** Most names a tier's line of the day's card lists before it gives a count instead. */
private const val DAY_NAMES = 3

/**
 * Today's dailies in one card, null until one is solved. Per tier with a solve: a line naming what was solved
 * ("Standard · Kings, Sudoku", or "Standard · 6 puzzles" past [DAY_NAMES]), then a green mark per solve, 13 a line so a
 * messenger never wraps one. Only what was done: never a count out of [ids] or a square for a puzzle not played, since
 * the puzzles are a menu, not a quota. [date] is today, formatted. Spoiler-free: which puzzles are done, never a board.
 */
fun dayShareCard(
    ids: List<String>, completions: List<Completion>, today: LocalDate, date: String, streak: Int,
    nameOf: (String) -> String = { PuzzleRegistry.byId(it)?.displayName ?: it },
): ShareCard? {
    val done = completions.filter { it.day == today }.map { it.puzzleId to it.difficulty }.toSet()
    val tiers = Difficulty.entries.map { tier -> tier to ids.filter { (it to tier) in done } }.filter { it.second.isNotEmpty() }
    if (tiers.isEmpty()) return null
    val lines = tiers.flatMap { (tier, solved) ->
        val what = if (solved.size <= DAY_NAMES) solved.joinToString(", ") { nameOf(it) } else tr("${solved.size} puzzles", "${solved.size} ${russianPlural(solved.size, "задача", "задачи", "задач")}")
        listOf(ShareLine(emptyList(), tail = "${tier.label} · $what")) + solved.chunked(13).map { row -> ShareLine(row.map { Mark.GREEN }) }
    }
    // The preview's one line about the rest of the day: only when a tier is done or one or two are left at it.
    val left = tiers.map { (tier, solved) -> tier to ids.filter { it !in solved } }.minBy { it.second.size }
    val nudge = when (left.second.size) {
        0 -> tr("Every ${left.first.label} daily done today.", "Все ежедневные задачи на сложности «${left.first.label}» решены.")
        1, 2 -> tr("${left.second.joinToString(" and ") { nameOf(it) }} ${if (left.second.size == 1) "is" else "are"} all that's left " +
            "for every ${left.first.label} daily today.",
            "Осталось решить ${left.second.joinToString(" и ") { nameOf(it) }}, чтобы завершить все ежедневные задачи на сложности «${left.first.label}».")
        else -> tr("Well played. Same time tomorrow?", "Отличная игра. До завтра?")
    }
    return ShareCard(
        header = tr("Daybook · $date · today's dailies", "Daybook · $date · ежедневные задачи"),
        lines = lines,
        stats = tr("${tiers.sumOf { it.second.size }} solved today", "Сегодня решено: ${tiers.sumOf { it.second.size }}") + if (streak >= 2) tr(" · $streak day streak", " · серия: $streak ${russianPlural(streak, "день", "дня", "дней")}") else "",
        key = listOf(Mark.GREEN to tr("solved", "решено")),
        nudge = nudge,
    )
}

/**
 * Today's three ([com.joebywan.daybook.core.TodaysThree]) at [tier] in one card, null until one of [three] is solved today:
 * a mark per puzzle in order, the time and hints of those solved. [clock] formats seconds. Spoiler-free: which are done.
 */
fun threeShareCard(
    three: List<String>, completions: List<Completion>, today: LocalDate, tier: Difficulty, date: String, streak: Int,
    clock: (Int) -> String,
): ShareCard? {
    val solves = three.map { id -> completions.firstOrNull { it.puzzleId == id && it.day == today && it.difficulty == tier } }
    val done = solves.filterNotNull()
    if (done.isEmpty()) return null
    val hints = done.sumOf { it.hints }
    return ShareCard(
        header = tr("Daybook · $date · Today's three · ${tier.label}", "Daybook · $date · Тройка дня · ${tier.label}"),
        lines = listOf(ShareLine(solves.map { if (it == null) Mark.WHITE else Mark.GREEN }, tail = "  ${done.size}/${three.size}")),
        stats = clock(done.sumOf { it.seconds }) + (if (hints > 0) tr(" · ${plural(hints, "hint")}", " · $hints ${russianPlural(hints, "подсказка", "подсказки", "подсказок")}") else "") +
            if (streak >= 2) tr(" · $streak day streak", " · серия: $streak ${russianPlural(streak, "день", "дня", "дней")}") else "",
        key = listOf(Mark.GREEN to tr("solved", "решено"), Mark.WHITE to tr("not yet", "ещё не решено")),
        nudge = if (done.size == three.size) tr("All three done. A tidy day's work.", "Все три решены. Отличная работа за день.") else tr("A fine start on today's three.", "Хорошее начало тройки дня."),
    )
}

/**
 * A finished level in one card: a mark per board of [plan] (puzzle ids, in order), green unaided, red with hints, from the
 * level's stored [solves]; the total time and hints. [clock] formats seconds. Spoiler-free: counts only.
 */
fun levelShareCard(level: Int, plan: List<String>, solves: List<LevelSolve>, clock: (Int) -> String): ShareCard {
    val by = solves.filter { it.level == level }.associateBy { it.puzzleId }
    val got = plan.mapNotNull { by[it] }
    val hints = got.sumOf { it.hints }
    return ShareCard(
        header = tr("Daybook · Level $level · ${got.size}/${plan.size}", "Daybook · Уровень $level · ${got.size}/${plan.size}"),
        lines = listOf(ShareLine(plan.map { id -> by[id]?.let { if (it.hints > 0) Mark.RED else Mark.GREEN } ?: Mark.WHITE })),
        stats = tr("${clock(got.sumOf { it.seconds })} · ${plural(hints, "hint")}", "${clock(got.sumOf { it.seconds })} · $hints ${russianPlural(hints, "подсказка", "подсказки", "подсказок")}"),
        key = listOf(Mark.GREEN to tr("no hints", "без подсказок"), Mark.RED to tr("hints", "подсказки")),
        nudge = if (hints == 0) tr("Every board your own work.", "Каждое поле решено своими силами.") else tr("Level $level done. On to level ${level + 1}.", "Уровень $level пройден. Вперёд, к уровню ${level + 1}."),
    )
}

/** The bonus words a board has counted, on the puzzles that count them. */
internal fun bonusCount(state: PuzzleState): Int = when (state) {
    is WordWheelState -> state.bonus.size
    is DwindleState -> state.bonus.size
    else -> 0
}
