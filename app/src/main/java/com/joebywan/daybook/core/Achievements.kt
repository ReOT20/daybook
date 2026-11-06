package com.joebywan.daybook.core

import com.joebywan.daybook.data.Completion
import com.joebywan.daybook.core.i18n.text
import com.joebywan.daybook.core.i18n.SavedText
import com.joebywan.daybook.core.i18n.SavedArgument
import com.joebywan.daybook.core.i18n.savedText
import com.joebywan.daybook.core.i18n.SUPPORTED_LANGUAGES
import com.joebywan.daybook.platform.platformLanguage
import kotlinx.datetime.LocalDate

/**
 * Achievements (`docs/REWARDS.md`). Nothing is stored: [Achievement.earned] is a predicate over the whole
 * completion history, and a solve "earns" one when it is false over the history before the solve and true
 * after it ([newlyEarned]). Every predicate is monotone (more history never un-earns it), so an old player
 * whose history already satisfies one simply sees it earned and is never told about it: only the solve that
 * flips it announces it. The exceptions are the two registry-wide ones: add a puzzle and they need that
 * puzzle too, so they read as unearned again until it is played.
 *
 * Pure and platform-free (the web compiles it). `puzzleIds` is a parameter so tests can fake the registry.
 */
/** Which badge shape an achievement wears (`ui/stats/AchievementBadge.kt`). */
enum class AchievementCategory { STREAK, PUZZLE_STREAK, COVERAGE, MILESTONE }

class Achievement(
    val id: String,
    val category: AchievementCategory,
    private val titleText: () -> String,
    private val descriptionText: () -> String,
    /** For a Training achievement: whether the training record (`TrainingRecord`, `puzzleId|technique`) earns it. */
    val trained: ((training: Set<String>) -> Boolean)? = null,
    val earned: (history: List<Completion>, today: LocalDate, puzzleIds: List<String>) -> Boolean,
) {
    // The registry survives language changes; presentation must not be frozen when it is first initialized.
    val title: String get() = titleText()
    val description: String get() = descriptionText()
}

private fun playedDays(h: List<Completion>) = h.mapNotNullTo(HashSet()) { it.streakDay }

private fun streak(n: Int, titleKey: String) = Achievement(
    "streak$n", AchievementCategory.STREAK, { text(titleKey) },
    { text("achievement.streak.description", "count" to n) },
) { h, today, _ -> streakOf(playedDays(h), today).best >= n }

private fun puzzleStreak(n: Int, titleKey: String) = Achievement(
    "puzzleStreak$n", AchievementCategory.PUZZLE_STREAK, { text(titleKey) },
    { text("achievement.puzzle_streak.description", "count" to n) },
) { h, today, _ ->
    h.groupBy { it.puzzleId }.values.any { c -> streakOf(playedDays(c), today).best >= n }
}

/** The top tier is whatever the last [Difficulty] is. */
private val TOP = Difficulty.entries.last()

val ACHIEVEMENTS: List<Achievement> = listOf(
    Achievement("solves1", AchievementCategory.MILESTONE, { text("achievement.solves1.title") }, { text("achievement.solves1.description") }) { h, _, _ -> h.isNotEmpty() },
    Achievement("solves100", AchievementCategory.MILESTONE, { text("achievement.solves100.title") }, { text("achievement.solves100.description") }) { h, _, _ -> h.size >= 100 },
    streak(3, "achievement.streak3.title"),
    streak(7, "achievement.streak7.title"),
    streak(14, "achievement.streak14.title"),
    streak(30, "achievement.streak30.title"),
    streak(60, "achievement.streak60.title"),
    streak(100, "achievement.streak100.title"),
    streak(365, "achievement.streak365.title"),
    puzzleStreak(7, "achievement.puzzle_streak7.title"),
    puzzleStreak(30, "achievement.puzzle_streak30.title"),
    Achievement("days30", AchievementCategory.MILESTONE, { text("achievement.days30.title") }, { text("achievement.days30.description") }) { h, _, _ -> playedDays(h).size >= 30 },
    Achievement("firstTop", AchievementCategory.MILESTONE, { text("achievement.first_top.title", "tier" to TOP.label) }, { text("achievement.first_top.description", "tier" to TOP.label) }) { h, _, _ ->
        h.any { it.difficulty == TOP }
    },
    Achievement("cleanTop", AchievementCategory.MILESTONE, { text("achievement.clean_top.title", "tier" to TOP.label) }, { text("achievement.clean_top.description", "tier" to TOP.label) }) { h, _, _ ->
        h.any { it.difficulty == TOP && it.hints == 0 }
    },
    Achievement("oneOfEach", AchievementCategory.COVERAGE, { text("achievement.one_of_each.title") }, { text("achievement.one_of_each.description") }) { h, _, ids ->
        h.mapTo(HashSet()) { puzzleFamilyId(it.puzzleId) }.containsAll(ids.map(::puzzleFamilyId))
    },
    Achievement("fullSet", AchievementCategory.COVERAGE, { text("achievement.full_set.title") }, { text("achievement.full_set.description") }) { h, _, _ ->
        h.filter { it.day != null }.groupBy { it.puzzleId to it.day }
            .any { (_, c) -> c.mapTo(HashSet()) { it.difficulty }.size == Difficulty.entries.size }
    },
    // Accept old and localized schedules independently of the current menu, so switching language never un-earns one.
    Achievement("todaysThree", AchievementCategory.COVERAGE, { text("achievement.todays_three.title") }, { text("achievement.todays_three.description") }) { h, _, _ ->
        h.filter { it.day != null }.groupBy { it.day!! to it.difficulty }
            .any { (k, c) ->
                val solved = c.mapTo(HashSet()) { it.puzzleId }
                solved.containsAll(TodaysThree.of(k.first)) || solved.containsAll(TodaysThree.starter(k.first)) ||
                    SUPPORTED_LANGUAGES.any { language ->
                        solved.containsAll(TodaysThree.of(k.first, language.tag)) ||
                            solved.containsAll(TodaysThree.starter(k.first, language.tag))
                    }
            }
    },
    // Within a week, not one day: the puzzles are a menu, not a day's quota. The old one-day rule is a case of this one,
    // so nobody who earned it loses it.
    Achievement("allPuzzles", AchievementCategory.COVERAGE, { text("achievement.all_puzzles.title") }, { text("achievement.all_puzzles.description") }) { h, _, ids ->
        ids.isNotEmpty() && h.mapNotNullTo(HashSet()) { it.day }.any { start ->
            weekFrom(h, start).mapTo(HashSet(), ::puzzleFamilyId).containsAll(ids.map(::puzzleFamilyId))
        }
    },
) +
    // Training (docs/TRAINING_SPEC.md): read off the training record, never the completions. Two per course, in
    // Training.COURSES' order, which only ever grows at the end, so these are appended only.
    Training.COURSES.flatMap { listOf(trained(it), graduate(it)) }

/** "<Puzzle> trained": every technique of [c] passed in Training. */
private fun trained(c: Course) = Achievement(
    "${c.id}Trained", AchievementCategory.MILESTONE, { text("achievement.trained.title", "puzzle" to c.puzzle.displayName) },
    { text("achievement.trained.description", "puzzle" to c.puzzle.displayName) },
    earned = { _, _, _ -> false },
    trained = { t -> c.allPassed(t) },
)

/** "<Puzzle> graduate": then [c]'s final board solved. */
private fun graduate(c: Course) = Achievement(
    "${c.id}Graduate", AchievementCategory.MILESTONE, { text("achievement.graduate.title", "puzzle" to c.puzzle.displayName) },
    { text("achievement.graduate.description") },
    earned = { _, _, _ -> false },
    trained = { t -> c.passed(t, Training.EXAM) },
)

/** The puzzles with a daily solved in [h] for one of the seven days from [start]. */
private fun weekFrom(h: List<Completion>, start: LocalDate): Set<String> =
    h.filter { c -> c.day?.let { it.toEpochDays() - start.toEpochDays() in 0..6 } == true }.mapTo(HashSet()) { it.puzzleId }

private fun registryIds() = PuzzleRegistry.all.map { it.id }

/** Every achievement [history] (and [training], the training record) has earned. */
fun earnedAchievements(
    history: List<Completion>, today: LocalDate, puzzleIds: List<String> = registryIds(), training: Set<String> = emptySet(),
): List<Achievement> = ACHIEVEMENTS.filter { it.earned(history, today, puzzleIds) || it.trained?.invoke(training) == true }

/** What [solve] earns: true after it, false before. History that already satisfied one never re-fires it. */
fun newlyEarned(
    history: List<Completion>, solve: Completion, today: LocalDate, puzzleIds: List<String> = registryIds(),
): List<Achievement> {
    val before = earnedAchievements(history, today, puzzleIds).map { it.id }.toSet()
    return earnedAchievements(history + solve, today, puzzleIds).filter { it.id !in before }
}

/**
 * "Only X to go": one achievement 1 or 2 steps away, for the finished frame. [left] is the number of
 * steps, [text] the whole line. Positive phrasing only, and nothing for a streak that has lapsed.
 */
class ToGo(val id: String, val left: Int, val text: String, val textSource: SavedText? = null)

private fun names(ids: List<String>, nameOf: (String) -> String): String =
    ids.map(nameOf).sorted().joinToString(text("list.and"))

/** The lifetime "one of each" line: says "haven't tried", never "left", so it cannot read as today's checklist. */
private fun untried(missing: List<String>, nameOf: (String) -> String): String =
    text("achievement.remaining.untried", "count" to missing.size, "names" to names(missing, nameOf))

private fun puzzleName(id: String) = PuzzleRegistry.byId(id)?.displayName ?: id

/**
 * How far [id] is from being earned over [history], as a [ToGo], or null when it is earned, far (more than 2),
 * or not a counting kind (first solve, top tier). Pure. A streak counts only while the run is alive.
 */
fun remaining(
    id: String, history: List<Completion>, today: LocalDate,
    puzzleIds: List<String> = registryIds(), nameOf: (String) -> String = ::puzzleName,
): ToGo? {
    val a = ACHIEVEMENTS.firstOrNull { it.id == id } ?: return null
    if (a.earned(history, today, puzzleIds)) return null
    fun go(left: Int, text: String, source: SavedText) = if (left in 1..2) ToGo(id, left, text, source) else null
    fun liveStreak(days: Set<LocalDate>) = streakOf(days, today).takeIf { it.alive }?.current ?: 0
    fun missingFrom(solved: Set<String>): List<String> {
        val families = solved.mapTo(HashSet(), ::puzzleFamilyId)
        return puzzleIds.distinctBy(::puzzleFamilyId).filter { puzzleFamilyId(it) !in families }
            .map { PuzzleRegistry.localizedId(it, platformLanguage()) }
    }
    fun visible(puzzleId: String) = PuzzleRegistry.localizedId(puzzleId, platformLanguage()) == puzzleId
    val dailies = history.filter { it.day == today }
    return when {
        id == "solves100" -> (100 - history.size).let { go(it, text("achievement.remaining.solves100", "count" to it), savedText("achievement.remaining.solves100", "count" to it)) }
        id == "days30" -> (30 - playedDays(history).size).let { go(it, text("achievement.remaining.days30", "count" to it), savedText("achievement.remaining.days30", "count" to it)) }
        id.startsWith("streak") -> {
            val n = id.removePrefix("streak").toInt()
            (n - liveStreak(playedDays(history))).let { go(it, text("achievement.remaining.streak", "count" to it, "target" to n), savedText("achievement.remaining.streak", "count" to it, "target" to n)) }
        }
        id.startsWith("puzzleStreak") -> {
            val n = id.removePrefix("puzzleStreak").toInt()
            history.groupBy { it.puzzleId }
                .filterKeys(::visible)
                .map { (p, c) -> p to n - liveStreak(playedDays(c)) }
                .filter { it.second in 1..2 }.minWithOrNull(compareBy({ it.second }, { it.first }))
                ?.let { (p, left) -> go(left, text("achievement.remaining.puzzle_streak", "count" to left, "target" to n, "puzzle" to nameOf(p)),
                    savedText("achievement.remaining.puzzle_streak", "count" to left, "target" to n, "puzzle" to SavedArgument.Puzzles(listOf(p)))) }
        }
        id == "oneOfEach" -> {
            val missing = missingFrom(history.mapTo(HashSet()) { it.puzzleId })
            go(missing.size, untried(missing, nameOf),
                savedText("achievement.remaining.untried", "count" to missing.size, "names" to SavedArgument.Puzzles(missing)))
        }
        id == "fullSet" -> dailies.groupBy { it.puzzleId }
            .filterKeys(::visible)
            .map { (p, c) -> p to Difficulty.entries - c.mapTo(HashSet()) { it.difficulty }.toSet() }
            .filter { it.second.size in 1..2 }.minWithOrNull(compareBy({ it.second.size }, { it.first }))
            ?.let { (p, miss) ->
                go(miss.size, text("achievement.remaining.full_set", "count" to miss.size, "puzzle" to nameOf(p), "tiers" to miss.joinToString(text("list.and")) { it.label }),
                    savedText("achievement.remaining.full_set", "count" to miss.size, "puzzle" to SavedArgument.Puzzles(listOf(p)), "tiers" to SavedArgument.Tiers(miss)))
            }
        // The week ending today: the only one a solve from now on can still finish.
        id == "allPuzzles" -> {
            val missing = missingFrom(weekFrom(history, LocalDate.fromEpochDays(today.toEpochDays() - 6)))
            if (missing.size == puzzleIds.distinctBy(::puzzleFamilyId).size) null
            else go(missing.size, text("achievement.remaining.all_puzzles", "count" to missing.size, "names" to names(missing, nameOf)),
                savedText("achievement.remaining.all_puzzles", "count" to missing.size, "names" to SavedArgument.Puzzles(missing)))
        }
        else -> null
    }
}

/** The single most useful near goal once [solve] is recorded: fewest steps, then list order. */
fun nearestToGo(
    history: List<Completion>, solve: Completion, today: LocalDate,
    puzzleIds: List<String> = registryIds(), nameOf: (String) -> String = ::puzzleName,
): ToGo? {
    val after = history + solve
    return ACHIEVEMENTS.mapNotNull { remaining(it.id, after, today, puzzleIds, nameOf) }.minByOrNull { it.left }
}
