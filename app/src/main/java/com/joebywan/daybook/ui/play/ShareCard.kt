package com.joebywan.daybook.ui.play

import com.joebywan.daybook.core.i18n.text
import com.joebywan.daybook.core.i18n.SavedText
import com.joebywan.daybook.core.i18n.SavedArgument
import com.joebywan.daybook.core.i18n.savedText

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.core.PuzzleType
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
data class ShareLine(
    val marks: List<Mark>, val hinted: Boolean = false, val tail: String = "", val tailSource: SavedText? = null,
) {
    fun localized(): ShareLine = copy(tail = tailSource?.resolve() ?: tail)
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
    /** Legacy cards keep their original snapshot; only new cards have facts that can be translated safely. */
    val source: ShareCardText? = null,
) {
    fun localized(): ShareCard = source?.let {
        // Restored metadata may name a catalog key from a newer app; the snapshot is still usable.
        runCatching { copy(header = it.header.resolve(), lines = lines.map { line -> line.localized() }, stats = it.stats.resolve(),
            key = it.key.map { (mark, name) -> mark to name.resolve() }, nudge = it.nudge.resolve()) }.getOrDefault(this)
    } ?: this

    val text: String
        get() {
            val card = localized()
            return buildString {
                append(card.header).append('\n')
                card.lines.forEach { append(it.text(card.spaced)).append('\n') }
                append(card.stats).append('\n')
                append(card.key.joinToString("  ") { "${it.first.text} ${it.second}" }).append('\n')
                append(SHARE_URL)
            }
        }
}

/** Spoiler-free presentation facts only: no board, answer, play history or reward calculation. */
@Serializable
data class ShareCardText(
    val header: SavedText, val stats: ShareStats, val key: List<Pair<Mark, SavedText>>, val nudge: SavedText,
)

@Serializable
data class ShareStats(
    val clock: String? = null, val solved: Int? = null, val hints: Int? = null, val bonus: Int = 0, val streak: Int = 0,
) {
    fun resolve(): String = buildList {
        clock?.let { add(it) }
        solved?.let { add(text("ui.share_card.solved_today", "value1" to it)) }
        hints?.let { add(text("count.hints", "count" to it)) }
        if (bonus > 0) add("+" + text("count.bonus_words", "count" to bonus))
        if (streak >= 2) add(text("count.day_streak", "count" to streak))
    }.joinToString(" · ")
}

private fun renderedShareCard(
    header: SavedText, lines: List<ShareLine>, stats: ShareStats, key: List<Pair<Mark, SavedText>>,
    nudge: SavedText, spaced: Boolean = false,
): ShareCard = ShareCard(header.resolve(), lines, stats.resolve(), key.map { (mark, name) -> mark to name.resolve() },
    nudge.resolve(), spaced, ShareCardText(header, stats, key, nudge))

private fun puzzleNames(
    ids: List<String>, nameOf: (String) -> String = { PuzzleRegistry.byId(it)?.displayName ?: it },
    separator: String? = null, sort: Boolean = true,
): SavedArgument = if (ids.all { PuzzleRegistry.byId(it)?.displayName == nameOf(it) }) SavedArgument.Puzzles(ids, separator, sort)
    else ids.map(nameOf).let { SavedArgument.Literal((if (sort) it.sorted() else it).joinToString(separator ?: text("list.and"))) }

private fun shareDate(date: String, value: LocalDate?): SavedArgument =
    value?.let { SavedArgument.Date(it, "d MMM") } ?: SavedArgument.Literal(date)

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
 * from an older save). [dateValue] retains a known date for language changes; a caller-supplied label alone stays literal. Spoiler-free: marks, pips and counts only, never a square, piece, word or number of the board.
 */
fun shareCard(
    puzzle: PuzzleType, tier: Difficulty, date: String?, clock: String, streak: Int,
    state: PuzzleState, log: String, hints: Int, level: Int? = null, dateValue: LocalDate? = null,
): ShareCard {
    val moves = PlayLog.played(log)
    val undone = moves.count { it.undone }
    var suffix: Any = ""
    val lines: List<ShareLine>
    val key: List<Pair<Mark, SavedText>>
    // A daily comes back tomorrow; a random or level board does not, so its nudges look to the next one instead.
    val next = savedText(if (date != null) "share.next.daily" else "share.next.random")
    // Rows are the final guesses; the log's live moves are the last of them (an older save logged none before).
    fun hintedRows(guessed: Int): (Int) -> Boolean {
        val live = moves.filter { !it.undone && !it.wrong }
        val offset = guessed - live.size
        return { row -> live.getOrNull(row - offset)?.hinted == true }
    }
    fun guessNudge(guessed: Int) =
        if (guessed == 1) savedText("ui.share_card.first_guess_nothing_to_improve_on") else savedText("share.guess_nudge", "count" to (guessed), "next" to (next))
    // Every nudge below is for a solve without hints; one with them gets the same line whatever the puzzle.
    val own: SavedText
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
            key = listOf(Mark.GREEN to savedText("ui.share_card.right_place"), Mark.YELLOW to savedText("ui.share_card.wrong_place"), Mark.RED to savedText("ui.share_card.hint"))
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
            key = listOf(Mark.EXACT to savedText("ui.share_card.right_colour_and_slot"), Mark.CLOSE to savedText("ui.share_card.right_colour"), Mark.RED to savedText("ui.share_card.hint"))
            own = guessNudge(guessed)
        }
        is SetsState -> {
            lines = listOf(ShareLine(moves.map { it.mark() }))
            key = listOf(Mark.GREEN to savedText("ui.share_card.a_set"), Mark.WHITE to savedText("ui.share_card.not_a_set"), Mark.YELLOW to savedText("ui.share_card.taken_back"), Mark.RED to savedText("ui.share_card.hint"))
            val misses = moves.count { it.wrong }
            val off = listOfNotNull(
                misses.takeIf { it > 0 }?.let { if (it == 1) savedText("ui.share_card.1_pick_that_wasn_t_a") else savedText("share.misses", "count" to (it)) },
                undone.takeIf { it > 0 }?.let { savedText("share.sets_undone", "count" to (it)) },
            )
            own = if (off.isEmpty()) savedText("ui.share_card.every_pick_a_set_nothing_to") else savedText("share.off_nudge", "off" to SavedArgument.Messages(off), "next" to (next))
        }
        is ChessState -> {
            suffix = savedText("ui.share_card.mate_in", "state_mateIn" to (state.mateIn))
            lines = listOf(ShareLine(moves.map { it.mark() }, tail = "#"))
            key = listOf(Mark.GREEN to savedText("ui.share_card.move"), Mark.YELLOW to savedText("ui.share_card.taken_back"), Mark.RED to savedText("ui.share_card.hint"))
            own = when {
                undone > 0 -> savedText("share.moves_undone", "count" to (undone), "next" to (next))
                else -> savedText("ui.share_card.a_clean_mate_nothing_to_improve")
            }
        }
        is TargetState -> {
            val steps = state.log.size
            val tail = savedText("share.steps_par", "count" to steps, "par" to state.par)
            lines = listOf(ShareLine(moves.map { it.mark() }, tail = tail.resolve(), tailSource = tail))
            key = listOf(Mark.GREEN to savedText("ui.share_card.step"), Mark.YELLOW to savedText("ui.share_card.taken_back"), Mark.RED to savedText("ui.share_card.hint"))
            own = when {
                undone > 0 -> savedText("share.steps_undone", "count" to (undone), "next" to (next))
                steps > state.par -> savedText("share.par_nudge", "par" to (state.par), "next" to (next))
                else -> savedText("ui.share_card.on_par_nothing_taken_back_nothing")
            }
        }
        // No hints and no undo: the lives are the whole story.
        is PointersState -> {
            val lost = PointersLogic.LIVES - state.lives
            lines = listOf(ShareLine(List(state.lives) { Mark.HEART } + List(lost) { Mark.LOST }))
            key = listOf(Mark.HEART to savedText("ui.share_card.life_left"), Mark.LOST to savedText("ui.share_card.life_lost"))
            own = if (lost == 0) savedText("ui.share_card.not_a_single_bump") else savedText("share.bumps", "count" to (lost), "next" to (next))
        }
        else -> {
            lines = listOf(ShareLine(strip(moves)))
            key = listOf(Mark.GREEN to savedText("ui.share_card.own_move"), Mark.YELLOW to savedText("ui.share_card.taken_back"), Mark.RED to savedText("ui.share_card.hint"))
            own = when {
                undone > 0 -> savedText("share.moves_undone", "count" to (undone), "next" to (next))
                else -> savedText("ui.share_card.a_clean_solve_nothing_to_improve")
            }
        }
    }
    return renderedShareCard(
        header = if (level != null) savedText("ui.share_card.daybook_level", "level" to (level), "puzzle_displayName" to puzzleNames(listOf(puzzle.id), { puzzle.displayName }), "tier_label" to SavedArgument.Tiers(listOf(tier)), "suffix" to (suffix))
            else savedText("share.header", "puzzle" to puzzleNames(listOf(puzzle.id), { puzzle.displayName }), "tier" to SavedArgument.Tiers(listOf(tier)), "date" to (date?.let { shareDate(it, dateValue) } ?: savedText("share.random_board")), "suffix" to (suffix)),
        lines = lines,
        stats = ShareStats(clock = clock, hints = hints.takeIf { it > 0 }, bonus = bonusCount(state), streak = streak),
        key = key,
        nudge = if (hints > 0) savedText(if (date != null) "share.hints_nudge.daily" else "share.hints_nudge.random") else own,
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
    nameOf: (String) -> String = { PuzzleRegistry.byId(it)?.displayName ?: it }, dateValue: LocalDate? = null,
): ShareCard? {
    val done = completions.filter { it.day == today }.map { it.puzzleId to it.difficulty }.toSet()
    val tiers = Difficulty.entries.map { tier -> tier to ids.filter { (it to tier) in done } }.filter { it.second.isNotEmpty() }
    if (tiers.isEmpty()) return null
    val lines = tiers.flatMap { (tier, solved) ->
        val what = if (solved.size <= DAY_NAMES) puzzleNames(solved, nameOf, separator = ", ", sort = false)
            else SavedArgument.Nested(savedText("count.puzzles", "count" to solved.size))
        val tail = savedText("share.day_row", "tier" to SavedArgument.Tiers(listOf(tier)), "what" to what)
        listOf(ShareLine(emptyList(), tail = tail.resolve(), tailSource = tail)) + solved.chunked(13).map { row -> ShareLine(row.map { Mark.GREEN }) }
    }
    // The preview's one line about the rest of the day: only when a tier is done or one or two are left at it.
    val left = tiers.map { (tier, solved) -> tier to ids.filter { it !in solved } }.minBy { it.second.size }
    val nudge = when (left.second.size) {
        0 -> savedText("ui.share_card.every_daily_done_today", "left_first_label" to SavedArgument.Tiers(listOf(left.first)))
        1, 2 -> savedText("share.remaining", "count" to (left.second.size), "names" to puzzleNames(left.second, nameOf, sort = false), "tier" to SavedArgument.Tiers(listOf(left.first)))
        else -> savedText("ui.share_card.well_played_same_time_tomorrow")
    }
    return renderedShareCard(
        header = savedText("ui.share_card.daybook_today_s_dailies", "date" to shareDate(date, dateValue)),
        lines = lines,
        stats = ShareStats(solved = tiers.sumOf { it.second.size }, streak = streak),
        key = listOf(Mark.GREEN to savedText("ui.share_card.solved")),
        nudge = nudge,
    )
}

/**
 * Today's three ([com.joebywan.daybook.core.TodaysThree]) at [tier] in one card, null until one of [three] is solved today:
 * a mark per puzzle in order, the time and hints of those solved. [clock] formats seconds. Spoiler-free: which are done.
 */
fun threeShareCard(
    three: List<String>, completions: List<Completion>, today: LocalDate, tier: Difficulty, date: String, streak: Int,
    clock: (Int) -> String, dateValue: LocalDate? = null,
): ShareCard? {
    val solves = three.map { id -> completions.firstOrNull { it.puzzleId == id && it.day == today && it.difficulty == tier } }
    val done = solves.filterNotNull()
    if (done.isEmpty()) return null
    val hints = done.sumOf { it.hints }
    return renderedShareCard(
        header = savedText("ui.share_card.daybook_today_s_three", "date" to shareDate(date, dateValue), "tier_label" to SavedArgument.Tiers(listOf(tier))),
        lines = listOf(ShareLine(solves.map { if (it == null) Mark.WHITE else Mark.GREEN }, tail = "  ${done.size}/${three.size}")),
        stats = ShareStats(clock = clock(done.sumOf { it.seconds }), hints = hints.takeIf { it > 0 }, streak = streak),
        key = listOf(Mark.GREEN to savedText("ui.share_card.solved"), Mark.WHITE to savedText("ui.share_card.not_yet")),
        nudge = if (done.size == three.size) savedText("ui.share_card.all_three_done_a_tidy_day") else savedText("ui.share_card.a_fine_start_on_today_s"),
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
    return renderedShareCard(
        header = savedText("ui.share_card.daybook_level_9b9efb", "level" to (level), "got_size" to (got.size), "plan_size" to (plan.size)),
        lines = listOf(ShareLine(plan.map { id -> by[id]?.let { if (it.hints > 0) Mark.RED else Mark.GREEN } ?: Mark.WHITE })),
        stats = ShareStats(clock = clock(got.sumOf { it.seconds }), hints = hints),
        key = listOf(Mark.GREEN to savedText("ui.share_card.no_hints"), Mark.RED to savedText("ui.share_card.hints")),
        nudge = if (hints == 0) savedText("ui.share_card.every_board_your_own_work") else savedText("ui.share_card.level_done_on_to_level", "level" to (level), "level_1" to (level + 1)),
    )
}

/** The bonus words a board has counted, on the puzzles that count them. */
internal fun bonusCount(state: PuzzleState): Int = when (state) {
    is WordWheelState -> state.bonus.size
    is DwindleState -> state.bonus.size
    else -> 0
}
