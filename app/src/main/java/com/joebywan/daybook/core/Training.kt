package com.joebywan.daybook.core

import com.joebywan.daybook.core.i18n.Message
import com.joebywan.daybook.core.i18n.message
import com.joebywan.daybook.core.i18n.text
import com.joebywan.daybook.data.KeyValueStore
import com.joebywan.daybook.puzzles.CageLogic
import com.joebywan.daybook.puzzles.CageState
import com.joebywan.daybook.puzzles.CageTeacher
import com.joebywan.daybook.puzzles.Cages
import com.joebywan.daybook.puzzles.Inequality
import com.joebywan.daybook.puzzles.PuzzleState
import com.joebywan.daybook.puzzles.InequalityState
import com.joebywan.daybook.puzzles.InequalityTeacher
import com.joebywan.daybook.puzzles.Regions
import com.joebywan.daybook.puzzles.RegionsLayout
import com.joebywan.daybook.puzzles.RegionsState
import com.joebywan.daybook.puzzles.RegionsTeacher
import com.joebywan.daybook.puzzles.Sudoku
import com.joebywan.daybook.puzzles.SudokuState
import com.joebywan.daybook.puzzles.SudokuTeacher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Training (`docs/TRAINING_SPEC.md`): per puzzle with a teacher, each technique it names, a short explanation in the hints'
 * voice, and practice boards on which the next hint step is exactly that technique. Pass a technique with [Training.RUN]
 * right in a row; pass them all, then solve the [Course.exam], a board whose own walk needs every one.
 *
 * Pure and platform-free (the web compiles it). Practice boards are found, not stored: real generated boards walked by
 * the teacher's ladder, in seed order, so the same index is the same board everywhere. How a walk steps and how a move is
 * judged is the course's [Drill]: [PlacementDrill] for teachers that place digits, [MoveDrill] for every other.
 */
class Course(
    val puzzle: PuzzleType,
    /** What each technique is, before the first board, in the ladder's order (the list shows them so). */
    val explanations: Map<String, Message>,
    /** The tier a technique's practice is drawn from: the easiest whose walks reach it (the generator gates depth). */
    private val tierFor: (String) -> Difficulty,
    val drill: Drill,
    /** The "<Puzzle> trained" badge's glyph (`ui/stats/AchievementBadge.kt`); the graduate's is the crown. */
    val badge: String,
    /** The index of the practice seed whose Expert board's walk needs every technique; null when none was found. */
    examIndex: Int?,
    /**
     * A seed's board on a tier, or null to skip the seed: a course whose `generate` can ship an unproved board passes its
     * `generateVerified`, so no practice or final board is one the generator has not proved (CLAUDE.md).
     */
    private val generate: (Long, Difficulty) -> PuzzleState? = puzzle::generate,
) {
    /** A course on a digit-placing teacher: [PlacementDrill]'s four parts, as the first four courses wrote them. */
    constructor(
        puzzle: PuzzleType,
        explanations: Map<String, Message>,
        tierFor: (String) -> Difficulty,
        stepper: (PuzzleState) -> (List<Int>) -> PlacementStep?,
        cellsOf: (PuzzleState) -> List<Int>,
        solutionOf: (PuzzleState) -> List<Int>,
        withGivens: (PuzzleState, List<Int>) -> PuzzleState,
        badge: String,
        examIndex: Int?,
    ) : this(puzzle, explanations, tierFor, PlacementDrill(stepper, cellsOf, solutionOf, withGivens), badge, examIndex)

    val id: String get() = puzzle.id

    /** The ladder's techniques, simplest first: the order the teacher tries them and the list shows them. */
    val techniques: List<String> = explanations.keys.toList()

    /**
     * One practice: [board], whose next hint step is [step] (that technique, from what is visible). For a [PlacementDrill]
     * the step is a [PlacementStep]; for a [MoveDrill] it is the [Deduction] whose `isReached` passes the practice.
     */
    class Practice(val board: PuzzleState, val step: TeachStep)

    /**
     * The practices for [technique], in order: per seed, the first position on that board's walk where the teacher's next
     * step is [technique].
     */
    fun practices(technique: String): Sequence<Practice> = sequence {
        val tier = tierFor(technique)
        for (k in 0 until Training.SEARCH) {
            val start = board(k, tier) ?: continue
            positionOn(start, technique)?.let { yield(it) }
        }
    }.distinctBy { it.board } // a puzzle that picks from a fixed list (Chess) can deal the same board twice

    /**
     * Per technique, the practices found this session and the next seed to search: they come from pure generators, so they
     * never go stale, and board n costs only the seeds after board n-1's. Read and written under [lock] only: on Android
     * two searches can be under way at once on `Dispatchers.Default` (a quick second "Another board"); the second waits and
     * then finds the first's work cached. Wasm has one thread, where the lock never waits. Keyed lookups only, never
     * iterated, so no hash order reaches a board.
     */
    private val found = HashMap<String, MutableList<Practice>>()
    private val searched = HashMap<String, Int>()
    private val lock = Mutex()

    /** The [index]th practice for [technique], wrapping round if the search ran out. Null only if it found none. */
    suspend fun practice(technique: String, index: Int): Practice? = lock.withLock {
        val list = found.getOrPut(technique) { ArrayList() }
        var k = searched[technique] ?: 0
        if (index >= list.size && k < Training.SEARCH) {
            val tier = tierFor(technique)
            while (index >= list.size && k < Training.SEARCH) {
                board(k, tier)?.let { b -> positionOn(b, technique)?.let { p -> if (list.none { it.board == p.board }) list += p } }
                k++
            }
            searched[technique] = k
        }
        list.getOrNull(index) ?: list.getOrNull(index % list.size.coerceAtLeast(1))
    }

    /** Practice seed [k]'s board on [tier], or null when the generator proved none for it. */
    fun board(k: Int, tier: Difficulty): PuzzleState? = generate(Training.seed(k), tier)

    private fun positionOn(start: PuzzleState, technique: String): Practice? =
        drill.walk(start).firstOrNull { it.step.technique == technique }

    /** The techniques a walk by the teacher's ladder from [start] uses. */
    fun techniquesUsed(start: PuzzleState): Set<String> = drill.walk(start).mapTo(LinkedHashSet()) { it.step.technique }

    /**
     * What the practice board's [after] (a state it emitted from [p]'s board) makes of [p]: true right, false wrong, null
     * play on. It may ask the teacher, which can take a while: call it off the main thread.
     */
    fun judge(p: Practice, after: PuzzleState): Boolean? = drill.judge(puzzle, p, after)

    /**
     * The step a right [after] made: [p]'s own, or the other instance of its technique the player chose, so the verdict
     * explains and outlines the player's move, not the teacher's pick. Off the main thread, like [judge].
     */
    fun made(p: Practice, after: PuzzleState): TeachStep = drill.made(p, after)

    /**
     * For an [after] that [judge] plays on: a note when the move works but the practice can no longer be judged (Mosaic's
     * fill that keeps the limit without being the technique), else null. It neither counts nor breaks the run.
     */
    fun aside(p: Practice, after: PuzzleState): String? = drill.aside(p, after)

    /**
     * For an [after] that [judge] plays on: a progress line when the move is a right part of a step of the technique that
     * is not finished yet ("Right so far: 3 of 12"), else null. It neither counts, ends the board nor breaks the run.
     */
    fun progress(p: Practice, after: PuzzleState): String? = drill.progress(p, after)

    internal val examSeed: Long? = examIndex?.let { Training.seed(it) }

    val hasExam: Boolean get() = examSeed != null

    /** The final board: a whole Expert board whose walk needs every technique, or null when this course has none. */
    fun exam(): PuzzleState? = examSeed?.let { generate(it, Difficulty.EXPERT) }

    /** The technique's name as a title: "A digit's only place in a box". */
    fun titleOf(technique: String): String =
        techniqueNamesOf(id)[technique]?.replaceFirstChar { it.uppercase() } ?: text("training.final_board")

    fun passed(record: Set<String>, technique: String) = TrainingRecord.entry(id, technique) in record

    /** Whether every technique is passed, which opens the final board. */
    fun allPassed(record: Set<String>) = techniques.all { passed(record, it) }

    /** How many of [techniques] are passed (the final board is not one of them). */
    fun passedCount(record: Set<String>) = techniques.count { passed(record, it) }

    /** Graduated: the final board passed, or every technique where the course has none. */
    fun graduated(record: Set<String>) = if (hasExam) passed(record, Training.EXAM) else allPassed(record)
}

/** Training as a whole, for Home: courses graduated of [courses], and techniques passed across them all. */
class TrainingTally(val graduated: Int, val courses: Int, val passed: Int)

object Training {
    /** Right answers in a row that pass a technique. A hinted board does not count, nor break the run. */
    const val RUN = 3

    /** The training record's entry for the exam, beside the techniques' ids. */
    const val EXAM = "exam"

    /** Training's seeds are their own range, far from any daily's hash, so no practice board is somebody's daily. */
    private const val SEED_BASE = 7_000_000L

    /**
     * Between one practice seed and the next. `generateVerified` tries `Rng(seed + attempt)`, so neighbouring seeds share
     * grids and would deal the same board twice; this keeps them apart.
     */
    private const val STRIDE = 1_000L

    internal fun seed(k: Int): Long = SEED_BASE + k * STRIDE

    /** Seeds tried before giving up on a technique. */
    internal const val SEARCH = 4000

    /**
     * Every course, in the order the list shows them. A new course is its own file (`core/training/<Name>Training.kt`)
     * and one line here, at the end: achievements and their badges follow this order, so append only.
     */
    val COURSES: List<Course> by lazy {
        listOf(
            SudokuTraining,
            CagesTraining,
            InequalityTraining,
            RegionsTraining,
            KingsTraining,
            PipesTraining,
            PearlsTraining,
            AtomsTraining,
            WhosWhoTraining,
            CipherTraining,
            ChessTraining,
            MosaicTraining,
            MamboTraining,
            NonogramTraining,
            TallyTraining,
            MinesTraining,
            LampsTraining,
            TentsTraining,
            ShikakuTraining,
            DominoesTraining,
            LitsTraining,
        )
    }

    fun of(puzzleId: String?): Course? = COURSES.firstOrNull { it.id == puzzleId }

    fun tally(record: Set<String>, courses: List<Course> = COURSES) =
        TrainingTally(courses.count { it.graduated(record) }, courses.size, courses.sumOf { it.passedCount(record) })
}

/** The tier for [technique] at its index in [course]: Standard below [hard], Hard below [expert], then Expert. */
internal fun tierByIndex(techniques: () -> List<String>, hard: Int, expert: Int): (String) -> Difficulty = { t ->
    val i = techniques().indexOf(t)
    when {
        i < hard -> Difficulty.STANDARD
        i < expert -> Difficulty.HARD
        else -> Difficulty.EXPERT
    }
}

val SudokuTraining: Course = Course(
    puzzle = Sudoku,
    explanations = linkedMapOf(
        SudokuTeacher.FULL_HOUSE to message("training.sudoku_full_house"),
        SudokuTeacher.HIDDEN_SINGLE_BOX to message("training.sudoku_hidden_single_box"),
        SudokuTeacher.HIDDEN_SINGLE_LINE to message("training.sudoku_hidden_single_line"),
        SudokuTeacher.NAKED_SINGLE to message("training.sudoku_naked_single"),
        SudokuTeacher.LOCKED to message("training.sudoku_locked"),
        SudokuTeacher.NAKED_PAIR to message("training.sudoku_naked_pair"),
        SudokuTeacher.HIDDEN_PAIR to message("training.sudoku_hidden_pair"),
        SudokuTeacher.WHAT_IF to message("training.sudoku_what_if"),
    ),
    // Sudoku.gate: Standard needs singles in a unit only, Hard a naked single, Expert an elimination or a what-if.
    tierFor = tierByIndex({ SudokuTraining.techniques }, hard = 3, expert = 4),
    stepper = { _ -> { cells -> SudokuTeacher.deduce(cells) } },
    cellsOf = { (it as SudokuState).cells },
    solutionOf = { (it as SudokuState).solution },
    withGivens = { s, cells -> SudokuState(givens = cells.map { it != 0 }, cells = cells, solution = (s as SudokuState).solution) },
    // One board in the first 400 practice seeds' Expert boards needs all eight. Pinned by SudokuTrainingTest.
    badge = "9",
    examIndex = 219,
)

val CagesTraining: Course = Course(
    puzzle = Cages,
    explanations = linkedMapOf(
        CageTeacher.FULL_HOUSE to message("training.cage_full_house"),
        CageTeacher.CAGE_LAST to message("training.cage_cage_last"),
        CageTeacher.HIDDEN_SINGLE_BOX to message("training.cage_hidden_single_box"),
        CageTeacher.HIDDEN_SINGLE_LINE to message("training.cage_hidden_single_line"),
        CageTeacher.NAKED_SINGLE to message("training.cage_naked_single"),
        CageTeacher.CAGE_COMBO to message("training.cage_cage_combo"),
        CageTeacher.FORTY_FIVE to message("training.cage_forty_five"),
        CageTeacher.LINKED_TOTALS to message("training.cage_linked_totals"),
        CageTeacher.CAGE_CHAIN to message("training.cage_cage_chain"),
    ),
    // The 45 rule and linked totals from Hard (88 and 42% of its boards reach them), the chain from Expert (all of them; 17% on Hard).
    tierFor = tierByIndex({ CagesTraining.techniques }, hard = 6, expert = 8),
    stepper = { s -> val b = CageLogic.Board((s as CageState).cageOf, s.sums); { cells -> CageTeacher.deduce(b, cells) } },
    cellsOf = { (it as CageState).cells },
    solutionOf = { (it as CageState).grid.solution },
    withGivens = { s, cells ->
        val c = s as CageState
        c.copy(grid = SudokuState(givens = cells.map { it != 0 }, cells = cells, solution = c.grid.solution))
    },
    // The first practice seed whose Expert board needs all nine. Pinned by TrainingTest.
    badge = "45",
    examIndex = 0,
)

val InequalityTraining: Course = Course(
    puzzle = Inequality,
    explanations = linkedMapOf(
        InequalityTeacher.LAST to message("training.inequality_last"),
        InequalityTeacher.ONLY_DIGIT to message("training.inequality_only_digit"),
        InequalityTeacher.ONLY_PLACE to message("training.inequality_only_place"),
        InequalityTeacher.SIGN_BOUND to message("training.inequality_sign_bound"),
        InequalityTeacher.SIGN_PLACE to message("training.inequality_sign_place"),
        InequalityTeacher.NAKED_PAIR to message("training.inequality_naked_pair"),
        InequalityTeacher.HIDDEN_PAIR to message("training.inequality_hidden_pair"),
    ),
    // InequalityLogic.specFor: Standard the five singles, Hard must need a naked pair, Expert a hidden pair.
    tierFor = tierByIndex({ InequalityTraining.techniques }, hard = 5, expert = 6),
    stepper = { s -> val q = s as InequalityState; { cells -> InequalityTeacher.deduce(q.size, cells, q.signs) } },
    cellsOf = { (it as InequalityState).cells },
    solutionOf = { (it as InequalityState).solution },
    withGivens = { s, cells -> (s as InequalityState).copy(givens = cells.map { it != 0 }, cells = cells) },
    // The first practice seed whose Expert board needs all seven (17 of the first 60 do). Pinned by TrainingTest.
    badge = "<",
    examIndex = 4,
)

val RegionsTraining: Course = Course(
    puzzle = Regions,
    explanations = linkedMapOf(
        RegionsTeacher.LAST to message("training.regions_last"),
        RegionsTeacher.ONLY_DIGIT to message("training.regions_only_digit"),
        RegionsTeacher.ONLY_PLACE to message("training.regions_only_place"),
        RegionsTeacher.LOCKED to message("training.regions_locked"),
    ),
    // RegionsLogic.specFor: Standard the three singles, Hard and Expert must take locked steps.
    tierFor = tierByIndex({ RegionsTraining.techniques }, hard = 3, expert = 4),
    stepper = { s -> val l = RegionsLayout((s as RegionsState).size, s.region); { cells -> RegionsTeacher.deduce(l, cells) } },
    cellsOf = { (it as RegionsState).cells },
    solutionOf = { (it as RegionsState).solution },
    withGivens = { s, cells -> (s as RegionsState).copy(givens = cells.map { it != 0 }, cells = cells) },
    // Every Hard and Expert board needs all four; the first practice seed's Expert board. Pinned by TrainingTest.
    badge = "5",
    examIndex = 0,
)

/** How a course walks a board by its teacher's steps, and judges a move on a practice board. */
interface Drill {
    /** The positions on a walk by the teacher's ladder from [start], each with its next step, until the ladder stops. */
    fun walk(start: PuzzleState): Sequence<Course.Practice>

    /** See [Course.judge]. */
    fun judge(puzzle: PuzzleType, p: Course.Practice, after: PuzzleState): Boolean?

    /** See [Course.made]. */
    fun made(p: Course.Practice, after: PuzzleState): TeachStep = p.step

    /** See [Course.aside]. */
    fun aside(p: Course.Practice, after: PuzzleState): String? = null

    /** See [Course.progress]. */
    fun progress(p: Course.Practice, after: PuzzleState): String? = null

    /**
     * How long the practice screen holds a "wrong" before showing it; a newer move in that time drops it. For a board
     * whose single moves pass through a wrong state (Mambo's tap cycle makes every sun a moon first), as the board's
     * own rule-break caption waits.
     */
    val settleMillis: Long get() = 0
}

/**
 * For a teacher that places digits (Sudoku, Cages, Inequality, Regions): the walk writes each step's digit in, and a
 * position's digits are all givens, so the walk's cannot be erased. A placed digit is judged at once: right when it is the
 * answer's, whether it is the step's or another square's, since the position has no simpler step anywhere (the teacher
 * tries every simpler technique on the whole board first), so a correct digit elsewhere took this technique or a harder
 * one. Judged against the stored answer, which is sound because these generators prove one answer.
 */
class PlacementDrill(
    /** The teacher's next step on a board shaped like the state, from [cells] alone (the answer is never read). */
    private val stepper: (PuzzleState) -> (List<Int>) -> PlacementStep?,
    /** The state's digits and its stored answer. */
    val cellsOf: (PuzzleState) -> List<Int>,
    val solutionOf: (PuzzleState) -> List<Int>,
    /** The state with [cells] in place, every digit a given (so the player cannot erase the walk's digits). */
    private val withGivens: (PuzzleState, List<Int>) -> PuzzleState,
) : Drill {
    override fun walk(start: PuzzleState): Sequence<Course.Practice> = sequence {
        val next = stepper(start)
        val grid = cellsOf(start).toMutableList()
        while (0 in grid) {
            val step = next(grid) ?: break
            yield(Course.Practice(withGivens(start, grid.toList()), step))
            grid[step.cell] = step.digit
        }
    }

    override fun judge(puzzle: PuzzleType, p: Course.Practice, after: PuzzleState): Boolean? {
        val was = cellsOf(p.board)
        val cells = cellsOf(after)
        val placed = cells.indices.firstOrNull { cells[it] != 0 && was[it] == 0 } ?: return null
        return cells[placed] == solutionOf(p.board)[placed]
    }

    // The teacher names one step per position, so a right digit elsewhere is told by its own square and digit alone.
    override fun made(p: Course.Practice, after: PuzzleState): TeachStep {
        val step = p.step as PlacementStep
        val was = cellsOf(p.board)
        val cells = cellsOf(after)
        val placed = cells.indices.firstOrNull { cells[it] != 0 && was[it] == 0 } ?: return step
        if (placed == step.cell) return step
        return PlacementStep(step.technique, placed, cells[placed], setOf(placed), emptySet(), "", message("training.placed_digit", "digit" to cells[placed]))
    }
}

/**
 * For every other teacher (kings, crosses, tents, shading, turns...): a step is the [Deduction] the teacher's next step
 * makes, so the walk is the hints' own "Show me" ([Deduction.apply]) from the generated board, and a position is simply the
 * walked state. Nothing is locked: a mark the walk made can be rubbed out, and the practice then waits until the step is
 * made again.
 *
 * Judged by the teacher, never against the stored answer: wrong when the teacher calls the new board a mistake (a
 * teacher's mistake means "no legal answer keeps this", which is what LITS's and Pipes' several-answer boards need, PR
 * #15); right when the move completes *any* step of the drilled technique on the position, the teacher's own pick or
 * another ([every]; the owner, 2026-10-28: "If the move satisfies the move that's asked for without being exactly what
 * the app assumed people would do, it's still right"); otherwise the player plays on (one cross of a step that crosses
 * three, an erasure, or a mark that happens to be true but that no step of the technique justifies). A true but
 * unjustified mark is not passed, because on these boards a lone cross is right by chance on most squares; it gets no
 * verdict, since nothing can tell it from the first cross of a step the player is still making.
 *
 * A step that takes several moves (Tents' "no tree" can grass twelve squares) says how far along it is, so the player is
 * not left without a word ([progress], the owner: no player left without feedback). Only a move that is part of an
 * instance of the technique counts, judged on [view]: every changed square must hold what that instance's own "Show me"
 * puts there, so it agrees with every answer; a true move no instance justifies still plays on silently, and the line
 * names a count, never which squares are left.
 */
class MoveDrill(
    override val settleMillis: Long = 0,
    /**
     * Wrong besides the teacher's mistakes: a lost board (Mines' mine), or a note the teacher never reads that no answer
     * keeps (Tents' grass, Lamps' cross where every answer has a lamp). The game's own hints are unchanged.
     */
    private val wrong: (PuzzleState) -> Boolean = { false },
    /**
     * The teacher's next step on the state as a [Deduction], from what the player can see: build it from the teacher's
     * visible-state entry point (one with no parameter for the answer), never from `teach`, which reads the answer.
     */
    private val next: (PuzzleState) -> Deduction?,
    /**
     * Every step of the technique visible on the state, as [Deduction]s, from the same visible state (Kings:
     * `KingsTeacher.steps`). Null keeps to [next]'s step alone, for a teacher with no such entry point yet.
     */
    private val every: ((PuzzleState, String) -> List<Deduction>)? = null,
    /**
     * The state's marks, one per index of a step's targets, compared with `==`: what [progress] reads to tell a right part
     * of a step. Null for a course whose every step is one move (a domino, a block, a chess move), which needs no progress.
     */
    private val view: ((PuzzleState) -> List<Any?>)? = null,
    /** What [progress]'s line asks next: Nonogram's says its crosses count, since in play they are only notes. */
    private val keepGoing: Message = message("training.keep_going"),
) : Drill {
    override fun walk(start: PuzzleState): Sequence<Course.Practice> = sequence {
        var s = start
        while (!s.solved) {
            val d = next(s) ?: break
            yield(Course.Practice(s, d))
            val moved = d.apply(s)
            if (moved == s) break
            s = moved
        }
    }

    // ponytail: [every] is re-derived per judged state; memoise it on the practice if a teacher's list gets slow.
    override fun judge(puzzle: PuzzleType, p: Course.Practice, after: PuzzleState): Boolean? = when {
        wrong(after) || puzzle.teach(after)?.mistake == true -> false
        (p.step as Deduction).isReached(after) -> true
        every?.invoke(p.board, p.step.technique)?.any { it.isReached(after) } == true -> true
        else -> null
    }

    override fun made(p: Course.Practice, after: PuzzleState): TeachStep =
        if ((p.step as Deduction).isReached(after)) p.step
        else every?.invoke(p.board, p.step.technique)?.firstOrNull { it.isReached(after) } ?: p.step

    // Per instance (the teacher's own first): the targets its Show me changes, how many of those the board already holds,
    // and whether every change the player made is one that Show me makes too (a flood or a sprout it makes as well counts).
    override fun progress(p: Course.Practice, after: PuzzleState): String? {
        val v = view ?: return null
        val step = p.step as Deduction
        val was = v(p.board)
        val now = v(after)
        val instances = sequenceOf(step) + sequence { yieldAll(every?.invoke(p.board, step.technique).orEmpty()) }
        return instances.firstNotNullOfOrNull { d ->
            val full = v(d.apply(p.board))
            val need = d.targets.filter { it < was.size && was[it] != full[it] }
            val done = need.count { now[it] == full[it] }
            val sound = now.indices.all { now[it] == was[it] || now[it] == full[it] }
            if (sound && done in 1 until need.size) progressLine(done, need.size, keepGoing) else null
        }
    }
}

/** The line for a step part made: how many of its squares, never which are left. */
internal fun progressLine(done: Int, of: Int, keepGoing: Message) =
    text("training.progress", "done" to done, "of" to of, "keepGoing" to keepGoing)

/**
 * What the player has passed in Training, as `puzzleId|technique` (or `puzzleId|exam`). Its own key set in the
 * progress store, never on a save; an older store has none.
 */
class TrainingRecord(private val store: KeyValueStore) {
    val passed: Flow<Set<String>> = store.stringSet(KEY)

    suspend fun pass(puzzleId: String, technique: String) {
        store.updateStringSet(KEY) { it + entry(puzzleId, technique) }
    }

    companion object {
        const val KEY = "training_passed"
        fun entry(puzzleId: String, technique: String): String = "$puzzleId|$technique"
    }
}

/** The Training achievements that passing [technique] of [puzzleId] earns, given the record [before] it. */
fun newlyTrained(before: Set<String>, puzzleId: String, technique: String): List<Achievement> {
    val after = before + TrainingRecord.entry(puzzleId, technique)
    return ACHIEVEMENTS.filter { a -> a.trained?.let { !it(before) && it(after) } == true }
}
