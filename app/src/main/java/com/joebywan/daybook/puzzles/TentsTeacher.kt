package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.TeachStep
import com.joebywan.daybook.core.asMessage
import com.joebywan.daybook.core.cap
import com.joebywan.daybook.core.i18n.Message
import com.joebywan.daybook.core.i18n.message
import com.joebywan.daybook.core.lineNameMessage

/**
 * Tents, reasoned a fact at a time.
 *
 * [deduce] is handed what the player can see (trees, clues, their marks) and nothing else, so a step it
 * explains cannot lean on the stored answer. The answer is read only in [teach]: to find a tent mark that
 * no answer keeps (sound because a shipped board has one answer, as Inequality's) and for [FALLBACK], which
 * says openly that it points at the answer.
 *
 * It mirrors [TentsLogic.deduce]: the same five rules, but one fact (a tent, or a group of squares to cross
 * out) at a time, in this order, restarting after each:
 * 1. [NO_TREE] squares with no tree beside them are grass;
 * 2. [LINE_DONE] / [LINE_FULL] a row or column with all its tents placed (rest grass) or with exactly as
 *    many possible squares as tents still owed (all tents);
 * 3. [TOUCH] squares around a tent are grass;
 * 4. [ONE_SPOT] a tree with one square left for its tent;
 * 5. [SHARED] a square a tent on which would touch every square its tree could use;
 * 6. [SERVED] a square whose trees already all have their tents.
 *
 * Knowledge starts from the player's *tent* marks (once they are not mistakes) and what the rules give;
 * crosses are notes and never read as knowledge. A fact the board already shows (every target marked) is
 * skipped, so the hint is the first thing the player has not yet put down. Every fact is explained, so a
 * board the generator shipped (the ladder finishes it) never reaches [FALLBACK]; `TentsTeachingTest` measures.
 *
 * Highlight indices: squares `0 until n*n`, row clue r = `n*n + r`, column clue c = `n*n + n + c`.
 */
internal object TentsTeacher {

    const val NO_TREE = "no-tree"
    const val LINE_DONE = "line-done"
    const val LINE_FULL = "line-full"
    const val TOUCH = "touch"
    const val ONE_SPOT = "one-spot"
    const val SHARED = "shared"
    const val SERVED = "served"
    const val FALLBACK = "fallback"
    const val MISTAKE = "mistake"

    val TECHNIQUES = listOf(NO_TREE, LINE_DONE, LINE_FULL, TOUCH, ONE_SPOT, SHARED, SERVED, FALLBACK)

    const val MAX_NUDGE = 70
    const val MAX_EXPLANATION = 200

    private const val TENT = TentsLogic.TENT
    private const val GRASS = TentsLogic.GRASS

    class Step(
        override val technique: String,
        /** What the targets become: TENT, GRASS, or 0 to clear (a mistake). */
        val value: Int,
        override val targets: Set<Int>,
        override val focus: Set<Int>,
        override val cited: Set<Int>,
        nudge: CharSequence,
        explanation: CharSequence,
    ) : TeachStep {

        override val nudgeMessage: Message = nudge.asMessage()
        override val explanationMessage: Message = explanation.asMessage()
        override val nudge: String get() = nudgeMessage.english
        override val explanation: String get() = explanationMessage.english
    }

    // ---- the whole hint: mistakes, then reasoning, then the honest fallback ---------------------------

    fun teach(s: TentsState): Step? {
        if (s.solved) return null
        mistake(s)?.let { return it }
        deduce(s.size, s.trees, s.rowCounts, s.colCounts, s.seen)?.let { return it }
        val i = s.solution.indices.firstOrNull { s.solution[it] && s.marks[it] != TENT } ?: return null
        return Step(
            FALLBACK, TENT, setOf(i), setOf(i), emptySet(),
            message("teacher.tents.teach.1"),
            message("teacher.tents.teach.2"),
        )
    }

    /** The [Deduction] for the play screen; `Tents.teach` is just this. */
    fun deduction(s: TentsState): Deduction? = teach(s)?.let(::deduction)

    /** [step] as the play screen's [Deduction] (Training builds its practices' steps with it too). */
    fun deduction(step: Step): Deduction {
        val mistake = step.technique == MISTAKE
        val v = step.value
        return Deduction(
            step,
            mistake = mistake,
            fallback = step.technique == FALLBACK,
            applyTo = { now -> step.targets.fold(now as TentsState) { acc, i -> acc.withMark(i, v) } },
            reachedBy = { now ->
                val t = now as TentsState
                if (mistake) step.targets.all { t.marks[it] != TENT } else step.targets.all { t.seen[it] == v }
            },
        )
    }

    // ---- mistakes -------------------------------------------------------------------------------------

    private fun mistake(s: TentsState): Step? {
        val n = s.size
        val wrong = s.marks.indices.firstOrNull { s.marks[it] == TENT && !s.solution[it] } ?: return null
        val tents = s.marks.map { it == TENT }
        fun step(why: Message, cited: Set<Int>) =
            Step(MISTAKE, 0, setOf(wrong), setOf(wrong), cited, message("teacher.tents.step.1"), message("teacher.tents.step.2", "why" to (why)))
        if (TentsLogic.orth(n, wrong).none { s.trees[it] }) return step(message("teacher.tents.mistake.1"), emptySet())
        val near = TentsLogic.around(n, wrong).filter { tents[it] }
        if (near.isNotEmpty()) return step(message("teacher.tents.mistake.2"), near.toSet())
        val r = wrong / n
        val c = wrong % n
        if (s.rowTents(r) > s.rowCounts[r]) return step(message("teacher.tents.mistake.3", "r_1" to (r + 1), "s_rowCounts_r_" to (s.rowCounts[r])), setOf(n * n + r))
        if (s.colTents(c) > s.colCounts[c]) return step(message("teacher.tents.mistake.4", "c_1" to (c + 1), "s_colCounts_c_" to (s.colCounts[c])), setOf(n * n + n + c))
        return step(message("teacher.tents.mistake.5"), emptySet())
    }

    // ---- reasoning ------------------------------------------------------------------------------------

    /** The first fact the ladder gives that the board does not already show, or null when it has no more. */
    fun deduce(n: Int, trees: List<Boolean>, rowCounts: List<Int>, colCounts: List<Int>, marks: List<Int>): Step? {
        val st = IntArray(n * n)
        for (i in st.indices) if (!trees[i] && marks[i] == TENT) st[i] = TENT
        while (true) {
            val f = nextFact(n, trees, rowCounts, colCounts, st) ?: return null
            for (i in f.targets) st[i] = f.value
            if (!f.targets.all { marks[it] == f.value }) return f
        }
    }

    /**
     * Every step of [technique] on the board, taking [marks]' tents and grass as known (a practice board's are its walk's
     * own sound steps): Training passes a move that completes any of them. The search stops once past [technique]'s place
     * in the ladder.
     */
    fun steps(n: Int, trees: List<Boolean>, rowCounts: List<Int>, colCounts: List<Int>, marks: List<Int>, technique: String): List<Step> {
        val st = IntArray(n * n) { if (!trees[it] && (marks[it] == TENT || marks[it] == GRASS)) marks[it] else 0 }
        // The ladder looks at each line for either line rule, so those two share a place.
        fun rank(t: String) = TECHNIQUES.indexOf(if (t == LINE_FULL) LINE_DONE else t)
        return facts(n, trees, rowCounts, colCounts, st)
            .takeWhile { rank(it.technique) <= rank(technique) }
            .filter { it.technique == technique }
            .toList()
    }

    /**
     * The deepest technique (an index into [TECHNIQUES]) a player walking [s] from blank by hints alone needs, or null when
     * the hints get stuck. The generator's tier gate (`TentsLogic.fitsTier`), so it walks exactly as the hints do.
     */
    fun depth(s: TentsState): Int? {
        var cur = s.copy(marks = List(s.size * s.size) { 0 }, moves = 0)
        var deepest = -1
        while (!cur.solved) {
            val f = deduce(cur.size, cur.trees, cur.rowCounts, cur.colCounts, cur.seen) ?: return null
            deepest = maxOf(deepest, TECHNIQUES.indexOf(f.technique))
            cur = f.targets.fold(cur) { acc, i -> acc.withMark(i, f.value) }
        }
        return deepest
    }

    internal fun nextFact(n: Int, trees: List<Boolean>, rowCounts: List<Int>, colCounts: List<Int>, st: IntArray): Step? {
        fun free(i: Int) = !trees[i] && st[i] == 0
        val treeIdx = trees.indices.filter { trees[it] }
        /** The squares beside tree [t] its tent could still go on. */
        fun choices(t: Int) = TentsLogic.orth(n, t).filter { !trees[it] && st[it] != GRASS }
        /** A tent on [c] beside only one tree, so that tree's. */
        fun owned(c: Int) = !trees[c] && st[c] == TENT && TentsLogic.orth(n, c).count { trees[it] } == 1

        // 1. No tree beside it.
        val bare = st.indices.filter { free(it) && TentsLogic.orth(n, it).none { j -> trees[j] } }
        if (bare.isNotEmpty()) return Step(
            NO_TREE, GRASS, bare.toSet(), bare.toSet(), emptySet(),
            message("teacher.tents.next_fact.1"),
            message("teacher.tents.next_fact.2"),
        )

        // 2. A row or column that is full or done.
        for (li in 0 until 2 * n) {
            val k = li % n
            val line = if (li < n) List(n) { k * n + it } else List(n) { it * n + k }
            val want = if (li < n) rowCounts[k] else colCounts[k]
            val clue = if (li < n) n * n + k else n * n + n + k
            val need = want - line.count { st[it] == TENT }
            val open = line.filter { free(it) }
            if (open.isEmpty() || need < 0 || need > open.size) continue
            val name = lineNameMessage(n, li)
            val placed = line.filter { st[it] == TENT }.toSet()
            if (need == 0) return Step(
                LINE_DONE, GRASS, open.toSet(), line.toSet() + clue, placed,
                message("teacher.tents.next_fact.3", "name" to (name)),
                message("teacher.tents.next_fact.4", "name_cap_" to (name.cap()), "want" to (want)),
            )
            if (need == open.size) return Step(
                LINE_FULL, TENT, open.toSet(), line.toSet() + clue, placed,
                message("teacher.tents.next_fact.5", "name" to (name)),
                message("teacher.tents.next_fact.6", "name_cap_" to (name.cap()), "plural_need_tent_" to (message("teacher.tents.count.tent", "count" to (need))), "value3" to (if (need == 1) message("teacher.tents.next_fact.extra.2") else message("teacher.tents.next_fact.extra.3", "need" to (need))), "value4" to (if (need == 1) message("teacher.tents.next_fact.extra.4") else message("teacher.tents.next_fact.extra.5"))),
            )
        }

        // 3. Around a tent.
        for (t in st.indices) {
            if (trees[t] || st[t] != TENT) continue
            val around = TentsLogic.around(n, t).filter { free(it) }
            if (around.isNotEmpty()) return Step(
                TOUCH, GRASS, around.toSet(), setOf(t) + around, setOf(t),
                message("teacher.tents.next_fact.7"),
                message("teacher.tents.next_fact.8"),
            )
        }

        // 4. A tree with one square left.
        for (t in treeIdx) {
            val choices = choices(t)
            if (choices.size == 1 && st[choices[0]] == 0) return Step(
                ONE_SPOT, TENT, choices.toSet(), setOf(t, choices[0]), setOf(t),
                message("teacher.tents.next_fact.9"),
                message("teacher.tents.next_fact.10"),
            )
        }

        // 5. A square that would touch every choice of a tree.
        for (t in treeIdx) {
            val choices = choices(t)
            if (choices.isEmpty()) continue
            val xs = st.indices.filter { x -> free(x) && x !in choices && choices.all { it in TentsLogic.around(n, x) } }
            if (xs.isNotEmpty()) return Step(
                SHARED, GRASS, xs.toSet(), setOf(t) + choices + xs, setOf(t) + choices,
                message("teacher.tents.next_fact.11"),
                message("teacher.tents.next_fact.12"),
            )
        }

        // 6. Every tree beside it already has its tent.
        val served = treeIdx.filter { t -> TentsLogic.orth(n, t).any { owned(it) } }.toSet()
        for (x in st.indices) {
            if (!free(x)) continue
            val near = TentsLogic.orth(n, x).filter { trees[it] }
            if (near.isNotEmpty() && near.all { it in served }) {
                val owners = near.flatMap { t -> TentsLogic.orth(n, t).filter { owned(it) } }
                return Step(
                    SERVED, GRASS, setOf(x), setOf(x) + near, near.toSet() + owners,
                    message("teacher.tents.next_fact.13"),
                    message("teacher.tents.next_fact.14", "value1" to (if (near.size == 1) message("teacher.tents.next_fact.extra.6") else message("teacher.tents.next_fact.extra.7")), "value2" to (if (near.size == 1) message("teacher.tents.next_fact.extra.8") else message("teacher.tents.next_fact.extra.9"))),
                )
            }
        }
        return null
    }

    /**
     * Every fact on [st], in [nextFact]'s order (its first is [nextFact]'s), lazily; [st] must not change meanwhile. The same
     * rules as [nextFact], kept apart because the generator's tier gate walks [nextFact] and a sequence there costs time;
     * `TentsTrainingTest` holds the two to the same first step.
     */
    internal fun facts(n: Int, trees: List<Boolean>, rowCounts: List<Int>, colCounts: List<Int>, st: IntArray): Sequence<Step> = sequence {
        fun free(i: Int) = !trees[i] && st[i] == 0
        val treeIdx = trees.indices.filter { trees[it] }
        /** The squares beside tree [t] its tent could still go on. */
        fun choices(t: Int) = TentsLogic.orth(n, t).filter { !trees[it] && st[it] != GRASS }
        /** A tent on [c] beside only one tree, so that tree's. */
        fun owned(c: Int) = !trees[c] && st[c] == TENT && TentsLogic.orth(n, c).count { trees[it] } == 1

        // 1. No tree beside it.
        val bare = st.indices.filter { free(it) && TentsLogic.orth(n, it).none { j -> trees[j] } }
        if (bare.isNotEmpty()) yield(Step(
            NO_TREE, GRASS, bare.toSet(), bare.toSet(), emptySet(),
            message("teacher.tents.facts.1"),
            message("teacher.tents.facts.2"),
        ))

        // 2. A row or column that is full or done.
        for (li in 0 until 2 * n) {
            val k = li % n
            val line = if (li < n) List(n) { k * n + it } else List(n) { it * n + k }
            val want = if (li < n) rowCounts[k] else colCounts[k]
            val clue = if (li < n) n * n + k else n * n + n + k
            val need = want - line.count { st[it] == TENT }
            val open = line.filter { free(it) }
            if (open.isEmpty() || need < 0 || need > open.size) continue
            val name = lineNameMessage(n, li)
            val placed = line.filter { st[it] == TENT }.toSet()
            if (need == 0) yield(Step(
                LINE_DONE, GRASS, open.toSet(), line.toSet() + clue, placed,
                message("teacher.tents.facts.3", "name" to (name)),
                message("teacher.tents.facts.4", "name_cap_" to (name.cap()), "want" to (want)),
            ))
            if (need == open.size) yield(Step(
                LINE_FULL, TENT, open.toSet(), line.toSet() + clue, placed,
                message("teacher.tents.facts.5", "name" to (name)),
                message("teacher.tents.facts.6", "name_cap_" to (name.cap()), "plural_need_tent_" to (message("teacher.tents.count.tent", "count" to (need))), "value3" to (if (need == 1) message("teacher.tents.facts.extra.2") else message("teacher.tents.facts.extra.3", "need" to (need))), "value4" to (if (need == 1) message("teacher.tents.facts.extra.4") else message("teacher.tents.facts.extra.5"))),
            ))
        }

        // 3. Around a tent.
        for (t in st.indices) {
            if (trees[t] || st[t] != TENT) continue
            val around = TentsLogic.around(n, t).filter { free(it) }
            if (around.isNotEmpty()) yield(Step(
                TOUCH, GRASS, around.toSet(), setOf(t) + around, setOf(t),
                message("teacher.tents.facts.7"),
                message("teacher.tents.facts.8"),
            ))
        }

        // 4. A tree with one square left.
        for (t in treeIdx) {
            val choices = choices(t)
            if (choices.size == 1 && st[choices[0]] == 0) yield(Step(
                ONE_SPOT, TENT, choices.toSet(), setOf(t, choices[0]), setOf(t),
                message("teacher.tents.facts.9"),
                message("teacher.tents.facts.10"),
            ))
        }

        // 5. A square that would touch every choice of a tree.
        for (t in treeIdx) {
            val choices = choices(t)
            if (choices.isEmpty()) continue
            val xs = st.indices.filter { x -> free(x) && x !in choices && choices.all { it in TentsLogic.around(n, x) } }
            if (xs.isNotEmpty()) yield(Step(
                SHARED, GRASS, xs.toSet(), setOf(t) + choices + xs, setOf(t) + choices,
                message("teacher.tents.facts.11"),
                message("teacher.tents.facts.12"),
            ))
        }

        // 6. Every tree beside it already has its tent.
        val served = treeIdx.filter { t -> TentsLogic.orth(n, t).any { owned(it) } }.toSet()
        for (x in st.indices) {
            if (!free(x)) continue
            val near = TentsLogic.orth(n, x).filter { trees[it] }
            if (near.isNotEmpty() && near.all { it in served }) {
                val owners = near.flatMap { t -> TentsLogic.orth(n, t).filter { owned(it) } }
                yield(Step(
                    SERVED, GRASS, setOf(x), setOf(x) + near, near.toSet() + owners,
                    message("teacher.tents.facts.13"),
                    message("teacher.tents.facts.14", "value1" to (if (near.size == 1) message("teacher.tents.facts.extra.6") else message("teacher.tents.facts.extra.7")), "value2" to (if (near.size == 1) message("teacher.tents.facts.extra.8") else message("teacher.tents.facts.extra.9"))),
                ))
            }
        }
    }
}
