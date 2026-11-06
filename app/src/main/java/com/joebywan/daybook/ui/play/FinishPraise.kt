package com.joebywan.daybook.ui.play

import com.joebywan.daybook.core.i18n.text
import com.joebywan.daybook.core.i18n.SavedText
import com.joebywan.daybook.core.i18n.SavedArgument
import com.joebywan.daybook.core.i18n.savedText
import com.joebywan.daybook.core.ACHIEVEMENTS
import com.joebywan.daybook.core.praisedTechniquesOf

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.core.Tones
import com.joebywan.daybook.core.nearestToGo
import com.joebywan.daybook.core.newlyEarned
import com.joebywan.daybook.core.puzzleNoteText
import com.joebywan.daybook.core.techniqueNamesOf
import com.joebywan.daybook.data.Completion
import com.joebywan.daybook.data.Stats
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * What the finished frame says (`docs/REWARDS.md`). [title] is the banner's headline (null: "Congratulations!", for a
 * frame nobody judged); [big] makes it louder (7, 30, 100 days); [lines] is at most one, so the frame stays calm.
 */
@Serializable
data class Praise(
    val title: String? = null,
    val big: Boolean = false,
    val lines: List<String> = emptyList(),
    /** Achievements this solve earned, in list order; the frame rotates through them. */
    val achievements: List<EarnedAchievement> = emptyList(),
    /** "Only X to go": at most one line of progress toward a near achievement. */
    val toGo: String? = null,
    /** What the Share button previews and sends ([shareCard]); null hides the button. */
    val share: ShareCard? = null,
    /** A second Share button, [setShareLabel], for the set this solve finished: a level, or today's three. */
    val setShare: ShareCard? = null,
    val setShareLabel: String = "",
    /**
     * The full show (all the confetti) rather than a light flourish: the first daily solve of today, an Expert solve, a
     * personal record, a streak milestone or an earned achievement. True by default, so a frame nobody judged stays loud.
     */
    val full: Boolean = true,
    /** The streak this solve extended, when [title] is "N day streak": the frame counts up to it. 0 otherwise. */
    val streak: Int = 0,
    /** This solve finished all three tiers of one puzzle on one date: the top show, above [full]. */
    val allTiers: Boolean = false,
    /** Optional for old saved frames: retain facts, then render in the current language. */
    val titleSource: SavedText? = null,
    val lineSources: List<SavedText>? = null,
    val toGoSource: SavedText? = null,
    val setShareLabelSource: SavedText? = null,
) {
    // A restored recipe from a newer app may be unreadable; keep the original earned frame.
    fun localized(): Praise = runCatching { copy(
        title = titleSource?.resolve() ?: title,
        lines = lineSources?.map { it.resolve() } ?: lines,
        toGo = toGoSource?.resolve() ?: toGo,
        setShareLabel = setShareLabelSource?.resolve() ?: setShareLabel,
        share = share?.localized(),
        setShare = setShare?.localized(),
        achievements = achievements.map { earned ->
            ACHIEVEMENTS.firstOrNull { it.id == earned.id }?.let { EarnedAchievement(it.id, it.title, it.description) } ?: earned
        },
    ) }.getOrDefault(this)

    /** The sound of this win: the chime, carried on further the bigger the show. */
    fun winKind(): Tones.Kind = when {
        allTiers -> Tones.Kind.ALL_TIERS
        full -> Tones.Kind.BIG_WIN
        else -> Tones.Kind.WIN
    }
}

/** What the frame shows for one achievement. */
@Serializable
data class EarnedAchievement(val id: String, val title: String, val description: String)

/** Streak lengths that get the loud treatment. */
private val STREAK_MILESTONES = setOf(7, 30, 100)

private const val MIN_SAMPLES = 5      // own solves at the tier before a percentile or "long" is meaningful
private const val PERCENT_FLOOR = 75   // "faster than N%" shown only from here up
private const val LONG_SECONDS = 600   // a solve this long always counts as one that was stuck with
private const val LONG_FLOOR = 240     // ...or twice the tier's median, but never under four minutes

/**
 * Pure. [history] is every completion recorded BEFORE this solve; [solve] is the one just made.
 * Everything is derived from [history], and every claim is true of it: records and percentiles are
 * against the player's own solves only (there is no population data), a tie is not a record, and a
 * percentile is rounded down.
 *
 * Streak (only for the daily played on [today]'s own date, and only on the first solve of that date,
 * which is the one that adds a day): "N day streak"; the window-saved wording when yesterday was missed
 * and the run goes on; a fresh start after a lapse says welcome back.
 *
 * Personal lines, in priority order, fastest at this tier; perseverance;
 * faster than N% of own solves; hints (praise, never a count); no hints ([techniqueLine] for the last two).
 *
 * [taught] is the technique of every hint opened on this board; [needed] is [com.joebywan.daybook.core.PuzzleType.neededTechnique].
 */
fun finishPraise(
    history: List<Completion>,
    solve: Completion,
    today: LocalDate,
    puzzleName: String,
    taught: List<String> = emptyList(),
    needed: String? = null,
): Praise {
    var title: SavedText? = null
    var big = false
    var streak = 0
    val lines = mutableListOf<SavedText>()

    val day = solve.day
    val firstToday = day != null && day == today && history.none { it.day == today }
    if (firstToday) {
        val before = Stats.streak(history, today)
        val after = Stats.streak(history + solve, today)
        val n = after.current
        val savedByWindow = n > 1 && history.none { it.streakDay == LocalDate.fromEpochDays(today.toEpochDays() - 1) }
        when {
            n == 1 && before.lapsed -> { title = savedText("ui.finish_praise.welcome_back"); lines += savedText("ui.finish_praise.a_new_streak_starts_today") }
            n == 1 -> { title = savedText("ui.finish_praise.day_one"); lines += savedText("ui.finish_praise.your_streak_starts_today") }
            else -> {
                title = savedText("count.day_streak", "count" to n)
                streak = n
                big = n in STREAK_MILESTONES
                if (savedByWindow) lines += savedText("ui.finish_praise.you_re_back_and_your_streak")
            }
        }
    }

    val tier = history.filter { it.puzzleId == solve.puzzleId && it.difficulty == solve.difficulty }
    val s = solve.seconds
    val personal = mutableListOf<SavedText>()
    val record = tier.isNotEmpty() && s < tier.minOf { it.seconds }
    if (record) personal += savedText(
        "ui.finish_praise.your_fastest_yet",
        "solve_difficulty_label" to SavedArgument.Tiers(listOf(solve.difficulty)),
        "puzzleName" to if (PuzzleRegistry.byId(solve.puzzleId)?.displayName == puzzleName)
            SavedArgument.Puzzles(listOf(solve.puzzleId)) else SavedArgument.Literal(puzzleName),
    )
    val sorted = tier.map { it.seconds }.sorted()
    val long = s >= LONG_SECONDS || (sorted.size >= MIN_SAMPLES && s >= LONG_FLOOR && s >= 2 * sorted[sorted.size / 2])
    if (long) personal += savedText("ui.finish_praise.that_one_fought_back_and_you")
    if (sorted.size >= MIN_SAMPLES && !record) {
        val pct = 100 * sorted.count { it > s } / sorted.size
        if (pct >= PERCENT_FLOOR) personal += savedText("ui.finish_praise.faster_than_of_your_own_solves", "pct" to (pct), "solve_difficulty_label" to SavedArgument.Tiers(listOf(solve.difficulty)))
    }
    personal += techniqueLineText(solve.puzzleId, solve.hints, taught, needed)
        ?: if (solve.hints > 0) savedText("ui.finish_praise.hints_are_how_a_move_sticks") else puzzleNoteText(solve.puzzleId, today.toEpochDays()) ?: savedText("ui.finish_praise.no_hints_needed")
    // Lowest priority: a no-hint solve with no record gets a technique note in place of the bare "No hints needed."

    // One line beneath the title (or beneath "Congratulations!"): praise is loud, not long.
    val earned = newlyEarned(history, solve, today).map { EarnedAchievement(it.id, it.title, it.description) }
    val near = nearestToGo(history, solve, today)
    val allTiers = finishedAllTiers(history, solve)
    val full = allTiers || firstToday || solve.difficulty == Difficulty.EXPERT || record || big || earned.isNotEmpty()
    val firstOfPuzzle = history.none { it.puzzleId == solve.puzzleId }
    if (allTiers) {
        val headline = SavedText("ui.finish_praise.all_three_tiers")
        val detail = SavedText("ui.finish_praise.standard_hard_and_expert_all_on")
        return Praise(headline.resolve(), big = true, listOf(detail.resolve()), earned, near?.text, allTiers = true,
            titleSource = headline, lineSources = listOf(detail), toGoSource = near?.textSource)
    }
    val heading = title ?: finishTitleText(solve, today, firstOfPuzzle, record)
    val details = (lines + personal).take(1)
    return Praise(heading.resolve(), big, details.map { it.resolve() }, earned, near?.text, full = full, streak = streak,
        titleSource = heading, lineSources = details, toGoSource = near?.textSource)
}

/**
 * Whether [solve] is the one that finishes every tier of its puzzle's boards for one date, today's or an archive day's. Derived,
 * nothing stored; a replay of a tier already done there is not it, and practice and level boards have no date.
 */
internal fun finishedAllTiers(history: List<Completion>, solve: Completion): Boolean {
    val day = solve.day ?: return false
    val done = history.filter { it.puzzleId == solve.puzzleId && it.day == day }.map { it.difficulty }
    return solve.difficulty !in done && (Difficulty.entries - solve.difficulty).all { it in done }
}

/** The longest title, in characters: what fits the banner between its two poppers at 360dp in headlineSmall. */
const val TITLE_MAX = 18

/** Hand-written titles for a solve with nothing rarer to say: unaided ones, and the rest. */
internal val TITLES_UNAIDED get() = listOf(text("ui.finish_praise.all_your_own_work"), text("ui.finish_praise.worked_it_out"), text("ui.finish_praise.clear_thinking"))
internal val TITLES_PLAIN get() = listOf(text("ui.finish_praise.nicely_done"), text("ui.finish_praise.well_solved"), text("ui.finish_praise.lovely_work"), text("ui.finish_praise.there_it_is"), text("ui.finish_praise.neatly_done"), text("ui.finish_praise.that_s_the_one"))
internal val TITLE_FIRST get() = text("ui.finish_praise.first_one_done")
internal val TITLE_RECORD get() = text("ui.finish_praise.a_new_best")
internal val TITLE_EXPERT get() = text("ui.finish_praise.expert_cracked")
internal val TITLE_ALL_TIERS get() = text("ui.finish_praise.all_three_tiers")

/**
 * The title when there is no streak to tell, rarest first: the first solve of this puzzle ever, a personal record at the
 * tier, an Expert solve, then a pool (unaided, or not). The pool line is picked by the day, puzzle and tier, never at
 * random, so the same solve always reads the same and a rotation cannot change it.
 */
internal fun finishTitle(solve: Completion, today: LocalDate, firstOfPuzzle: Boolean, record: Boolean): String =
    finishTitleText(solve, today, firstOfPuzzle, record).resolve()

private fun finishTitleText(solve: Completion, today: LocalDate, firstOfPuzzle: Boolean, record: Boolean): SavedText = when {
    firstOfPuzzle -> SavedText("ui.finish_praise.first_one_done")
    record -> SavedText("ui.finish_praise.a_new_best")
    solve.difficulty == Difficulty.EXPERT -> SavedText("ui.finish_praise.expert_cracked")
    else -> {
        val pool = if (solve.hints == 0) listOf("all_your_own_work", "worked_it_out", "clear_thinking")
            else listOf("nicely_done", "well_solved", "lovely_work", "there_it_is", "neatly_done", "that_s_the_one")
        var h = 0
        for (ch in "${(solve.day ?: today).toEpochDays()}|${solve.puzzleId}|${solve.difficulty.name}") h = h * 31 + ch.code
        SavedText("ui.finish_praise.${pool[(h % pool.size + pool.size) % pool.size]}")
    }
}

/**
 * The line that names the method, or null for the generic one. With hints: the most worth naming of the techniques the
 * hints showed ([taught]), never a count. Without: what the board itself needed ([needed]), only when that is more than
 * the puzzle's simplest step, because it is true of the board whatever the player did; it never claims the player used it.
 */
fun techniqueLine(puzzleId: String, hints: Int, taught: List<String>, needed: String?): String? =
    techniqueLineText(puzzleId, hints, taught, needed)?.resolve()

private fun techniqueLineText(puzzleId: String, hints: Int, taught: List<String>, needed: String?): SavedText? {
    val names = techniqueNamesOf(puzzleId)
    if (hints > 0) {
        val id = names.keys.lastOrNull { it in taught && it in praisedTechniquesOf(puzzleId) } ?: return null
        return savedText("ui.finish_praise.you_learned", "it" to SavedArgument.Technique(puzzleId, id))
    }
    if (needed == null || needed == names.keys.firstOrNull() || needed !in names) return null
    return savedText("ui.finish_praise.this_board_needed_solved_without_help", "it" to SavedArgument.Technique(puzzleId, needed))
}

/** Where a shared result points: the web build, which anyone can open without installing anything. */
const val SHARE_URL = "https://knowhowit.com.au/daybook/"

/** A puzzle's page in the player guide, at [technique]'s heading if given. The slash stays: a redirect could lose the anchor. */
fun guideUrl(puzzleId: String, technique: String? = null): String =
    "${SHARE_URL}guide/$puzzleId/" + if (technique != null) "#$technique" else ""
