package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.TeachStep
import com.joebywan.daybook.core.asMessage
import com.joebywan.daybook.core.i18n.Message
import com.joebywan.daybook.core.i18n.message
import com.joebywan.daybook.core.joinedMessages
import com.joebywan.daybook.core.numberWordMessage
import com.joebywan.daybook.core.orthogonal

/**
 * LITS, reasoned the way a person reasons it, one named step at a time.
 *
 * The same split as [KingsTeacher]. [deduce] is handed the board as the player sees it — the walls
 * and the shading — and has no parameter through which the stored answer could reach it. The answer
 * is read only in [teach]: to decide which shaded squares are mistakes, and for [FALLBACK], which
 * says openly that it is pointing at an answer.
 *
 * The rules reasoned from are the win check's, [Lits.isSolved]: one L, I, T or S per region, no 2x2
 * of shading, no two tetrominoes of one letter edge to edge across a wall. **Not** connectivity: the
 * win check stopped asking for it (PR #15), so a step that leaned on "the shading must join up" would
 * be arguing from a rule the board does not have. The generator's carve searches connected shadings, but a finished
 * board is proved again without that rule ([Lits.generateVerified]), so a shipped board has one answer under the real
 * rules. The fallback, which says so openly, is for where reasoning runs out.
 *
 * The player has one mark — shading — and the board crosses off what can't be shaded by itself
 * ([Lits.impossible]). So every step here ends in squares to shade; ruling a square out is never a
 * move on its own, only the reason for one. The candidate shapes each step reasons over are filtered
 * by exactly the rule the crosses use, so a step never argues from a cross the board isn't showing.
 *
 * Techniques, simplest first:
 * 1. [WHOLE_REGION] — a region of exactly four squares is its own tetromino.
 * 2. [OVERLAP] — every shape that fits the region covers these squares.
 * 3. [AVOID_BLOCK] / [LETTER_CLASH] — the same, once the shapes that would finish a 2x2 or touch a
 *    finished tetromino of their own letter are set aside.
 * 4. [NEIGHBOUR] — the same, once the shapes that would leave a neighbouring region no legal shape
 *    are set aside.
 * 5. [WHAT_IF] — leaving this square empty forces a short chain that ends with a region with no
 *    legal shape.
 *
 * Every loop walks regions, shapes and cells in index order; nothing reaches the output through a
 * hash's iteration order (CLAUDE.md, "Hash iteration order").
 */
internal object LitsTeacher {

    const val WHOLE_REGION = "whole-region"
    const val OVERLAP = "overlap"
    const val AVOID_BLOCK = "avoid-2x2"
    const val LETTER_CLASH = "letter-clash"
    const val NEIGHBOUR = "neighbour"
    const val WHAT_IF = "what-if"
    const val FALLBACK = "fallback"
    const val MISTAKE = "mistake"

    /** Every technique in the order [deduce] tries them, for reports. */
    val TECHNIQUES = listOf(WHOLE_REGION, OVERLAP, AVOID_BLOCK, LETTER_CLASH, NEIGHBOUR, WHAT_IF, FALLBACK)

    /**
     * How many forced shapes a [WHAT_IF] may walk through before it counts as a longer chain. The
     * same limit as Kings, for the same reason: past two, one sentence can't carry the argument.
     */
    const val MAX_CHAIN = 2

    /**
     * How many nodes a search for an answer may visit. The regions are small enough that the tree is
     * exhausted far inside this; if it ever is not, the result is [Search.Unknown], which nothing
     * reads as a proof (CLAUDE.md, "A truncated search is not a proof").
     */
    private const val SEARCH_BUDGET = 400_000

    /** One step: what to shade or clear, where to look, and why. [clears] only ever undoes a mistake. */
    class Step(
        override val technique: String,
        val shades: List<Int> = emptyList(),
        val clears: List<Int> = emptyList(),
        override val focus: Set<Int>,
        override val cited: Set<Int>,
        nudge: CharSequence,
        explanation: CharSequence,
    ) : TeachStep {
        override val nudgeMessage: Message = nudge.asMessage()
        override val explanationMessage: Message = explanation.asMessage()
        override val nudge: String get() = nudgeMessage.english
        override val explanation: String get() = explanationMessage.english


        override val targets: Set<Int> get() = (shades + clears).toSet()
    }

    // ---- the board's fixed geometry ----------------------------------------------------------

    private class Shape(val cells: List<Int>, val piece: Lits.Piece)

    /** Walls and every tetromino each region could ever hold. Regions are numbered 0 until [count]. */
    private class Layout(val w: Int, val h: Int, val region: List<Int>) {
        val n = w * h
        private val ids = region.distinct().sorted()
        val count = ids.size
        /** Region number of each cell, as an index into [cellsOf]. */
        val of = IntArray(n) { ids.binarySearch(region[it]) }
        val cellsOf: List<List<Int>> = List(count) { k -> (0 until n).filter { of[it] == k } }
        val shapes: List<List<Shape>> = List(count) { k ->
            Lits.tetrominoes(cellsOf[k], w, h).map { (cells, piece) -> Shape(cells.sorted(), piece) }
        }

        fun neighbours(cell: Int) = orthogonal(cell, w, h)

        /** Top-left corners of every 2x2 window containing [cell]. */
        fun windowsAt(cell: Int): List<Int> {
            val r = cell / w
            val c = cell % w
            return buildList {
                for (dr in -1..0) for (dc in -1..0) {
                    val rr = r + dr
                    val cc = c + dc
                    if (rr >= 0 && cc >= 0 && rr + 1 < h && cc + 1 < w) add(rr * w + cc)
                }
            }
        }

        fun window(corner: Int) = listOf(corner, corner + 1, corner + w, corner + w + 1)

        /** Regions other than [self] with a square touching [cells], corners included. */
        fun near(cells: List<Int>, self: Int): List<Int> {
            val out = BooleanArray(count)
            for (cell in cells) {
                val r = cell / w
                val c = cell % w
                for (dr in -1..1) for (dc in -1..1) {
                    val rr = r + dr
                    val cc = c + dc
                    if (rr in 0 until h && cc in 0 until w) out[of[rr * w + cc]] = true
                }
            }
            out[self] = false
            return (0 until count).filter { out[it] }
        }
    }

    // ---- what the player can see -------------------------------------------------------------

    /**
     * The shading on the board, plus — for a what-if — squares supposed to stay empty, and what
     * each region can still become. [cand] is the rule the crosses use ([Lits.impossible]): a shape
     * must cover the region's shading, avoid a 2x2 with the shading already down, and not sit
     * against a finished tetromino of its own letter. [cand1] also asks that every neighbouring
     * region still has a shape that can stand beside it.
     */
    private class Sight(val layout: Layout, val shaded: BooleanArray, val empty: BooleanArray) {
        val letters: List<Lits.Piece?> = Lits.letters(layout.w, layout.h, layout.region, shaded.asList())

        fun shadedIn(k: Int) = layout.cellsOf[k].filter { shaded[it] }

        fun complete(k: Int) = shadedIn(k).size >= 4

        /** Shapes that fit the region's shading and the supposed empties, before any rule. */
        fun geo(k: Int): List<Shape> {
            val have = shadedIn(k)
            return layout.shapes[k].filter { s -> s.cells.containsAll(have) && s.cells.none { empty[it] } }
        }

        fun fillsBlock(s: Shape): Boolean = s.cells.any { cell ->
            layout.windowsAt(cell).any { corner -> layout.window(corner).all { it in s.cells || shaded[it] } }
        }

        fun clashes(s: Shape): Boolean = s.cells.any { cell ->
            layout.neighbours(cell).any { next -> next !in s.cells && shaded[next] && letters[next] == s.piece }
        }

        private val candCache = arrayOfNulls<List<Shape>>(layout.count)
        fun cand(k: Int): List<Shape> =
            candCache[k] ?: geo(k).filter { !fillsBlock(it) && !clashes(it) }.also { candCache[k] = it }

        /**
         * Why [t] and [u], shapes of two different regions, cannot both stand: [BLOCK] when together
         * with the shading they fill a 2x2, [LETTER] when they are one letter edge to edge. Zero when
         * they can.
         */
        fun conflict(t: Shape, u: Shape): Int {
            var why = 0
            if (t.piece == u.piece && t.cells.any { a -> layout.neighbours(a).any { it in u.cells } }) why = why or LETTER
            val block = t.cells.any { a ->
                layout.windowsAt(a).any { corner ->
                    val win = layout.window(corner)
                    win.any { it in u.cells } && win.all { it in t.cells || it in u.cells || shaded[it] }
                }
            }
            if (block) why = why or BLOCK
            return why
        }

        /** Neighbouring regions that [t], a shape of region [k], would leave with no shape at all. */
        fun killers(k: Int, t: Shape): List<Int> =
            layout.near(t.cells, k).filter { b -> cand(b).none { u -> conflict(t, u) == 0 } }

        private val cand1Cache = arrayOfNulls<List<Shape>>(layout.count)
        fun cand1(k: Int): List<Shape> =
            cand1Cache[k] ?: cand(k).filter { killers(k, it).isEmpty() }.also { cand1Cache[k] = it }

        fun with(shade: Collection<Int> = emptyList(), keepEmpty: Collection<Int> = emptyList()): Sight {
            val s = shaded.copyOf().also { a -> shade.forEach { a[it] = true } }
            val e = empty.copyOf().also { a -> keepEmpty.forEach { a[it] = true } }
            return Sight(layout, s, e)
        }
    }

    /** [Lits.impossible]: the empty squares no candidate shape of their region ([Sight.cand]) covers. */
    fun impossible(width: Int, height: Int, region: List<Int>, shaded: List<Boolean>): Set<Int> {
        val layout = Layout(width, height, region)
        val sight = Sight(layout, shaded.toBooleanArray(), BooleanArray(layout.n))
        return (0 until layout.n).filter { c -> !shaded[c] && sight.cand(layout.of[c]).none { c in it.cells } }.toSet()
    }

    private const val BLOCK = 1
    private const val LETTER = 2

    private fun common(shapes: List<Shape>): List<Int> =
        if (shapes.isEmpty()) emptyList() else shapes[0].cells.filter { c -> shapes.all { c in it.cells } }

    // ---- the whole hint: mistakes, then reasoning, then the honest fallback --------------------

    /**
     * The next thing to show this player, or null on a solved board.
     *
     * A shaded square is a mistake only when no answer at all — under the win check's rules — keeps
     * it. A player who has wandered onto a *different* legal answer than the stored one has made no
     * mistake, and the win check would accept their board; calling it wrong would be the bug PR #15
     * fixed, back again in the hint panel.
     */
    fun teach(s: LitsState): Step? {
        if (s.solved) return null
        val layout = Layout(s.width, s.height, s.region)
        val shaded = s.shaded.toBooleanArray()
        val found = extend(layout, shaded, emptySet())
        mistake(s, layout, shaded, found)?.let { return it }
        deduce(s.width, s.height, s.region, s.shaded)?.let { return it }
        val reference = when {
            s.shaded.indices.all { !s.shaded[it] || s.solution[it] } -> s.solution.indices.filter { s.solution[it] }.toSet()
            found is Search.Found -> found.answer
            else -> return null
        }
        return fallback(layout, shaded, reference)
    }

    private sealed interface Search {
        data class Found(val answer: Set<Int>) : Search
        data object NoAnswer : Search
        data object Unknown : Search
    }

    /**
     * An answer that keeps every shaded square and shades none of [forbidden], by exhaustive search
     * under the win check's rules. [Search.NoAnswer] only once the tree is covered.
     */
    private fun extend(layout: Layout, shaded: BooleanArray, forbidden: Set<Int>): Search {
        val n = layout.n
        val options = (0 until layout.count).map { k ->
            val have = layout.cellsOf[k].filter { shaded[it] }
            layout.shapes[k].filter { s -> s.cells.containsAll(have) && s.cells.none { it in forbidden } }
        }
        if (options.any { it.isEmpty() }) return Search.NoAnswer
        val order = (0 until layout.count).sortedBy { options[it].size }
        val on = BooleanArray(n)
        val mark = arrayOfNulls<Lits.Piece>(n)
        var nodes = 0
        var truncated = false
        var answer: Set<Int>? = null

        fun blocks(s: Shape) = s.cells.any { cell ->
            layout.windowsAt(cell).any { corner -> layout.window(corner).all { on[it] } }
        }

        fun twins(s: Shape) = s.cells.any { cell ->
            layout.neighbours(cell).any { it !in s.cells && on[it] && mark[it] == s.piece }
        }

        fun walk(depth: Int) {
            if (answer != null || truncated) return
            if (nodes++ > SEARCH_BUDGET) {
                truncated = true
                return
            }
            if (depth == order.size) {
                answer = (0 until n).filter { on[it] }.toSet()
                return
            }
            for (s in options[order[depth]]) {
                s.cells.forEach { on[it] = true; mark[it] = s.piece }
                if (!blocks(s) && !twins(s)) walk(depth + 1)
                s.cells.forEach { on[it] = false; mark[it] = null }
                if (answer != null || truncated) return
            }
        }
        walk(0)
        val a = answer
        return when {
            a != null -> Search.Found(a)
            truncated -> Search.Unknown
            else -> Search.NoAnswer
        }
    }

    /**
     * A shaded square no answer keeps. When the search could not decide ([Search.Unknown]), falls
     * back to the stored answer rather than calling nothing a mistake.
     */
    private fun mistake(s: LitsState, layout: Layout, shaded: BooleanArray, found: Search): Step? {
        if (found is Search.Found) return null
        val offAnswer = s.shaded.indices.filter { s.shaded[it] && !s.solution[it] }
        if (offAnswer.isEmpty()) return null
        // Prefer the square whose clearing alone rescues the board: that one is the mistake, and
        // the others may only look wrong because of it.
        val rescuer = if (found is Search.NoAnswer) {
            offAnswer.firstOrNull { c ->
                extend(layout, shaded.copyOf().also { it[c] = false }, emptySet()) is Search.Found
            }
        } else {
            null
        }
        val known = when {
            rescuer != null -> Blame.RESCUES
            found is Search.NoAnswer -> Blame.STUCK
            else -> Blame.UNPROVED
        }
        return explainMistake(layout, shaded, rescuer ?: offAnswer.first(), known)
    }

    /**
     * What the search proved about a mistake, so its last-resort line claims no more: that clearing it
     * alone rescues the board; that the shading as a whole has no answer but no one square is to
     * blame; or nothing, because the search ran out ([Search.Unknown]).
     */
    private enum class Blame { RESCUES, STUCK, UNPROVED }

    private fun explainMistake(layout: Layout, shaded: BooleanArray, wrong: Int, known: Blame): Step {
        val k = layout.of[wrong]
        val region = layout.cellsOf[k]
        val sight = Sight(layout, shaded, BooleanArray(layout.n))
        val have = sight.shadedIn(k)
        fun step(explanation: Message, cited: Collection<Int>) = Step(
            technique = MISTAKE,
            clears = listOf(wrong),
            focus = setOf(wrong),
            cited = cited.toSet(),
            nudge = message("teacher.lits.step.1"),
            explanation = message("teacher.lits.step.2", "explanation" to (explanation)),
        )
        if (have.size > 4) return step(message("teacher.lits.explain_mistake.1"), region)
        layout.windowsAt(wrong).firstOrNull { corner -> layout.window(corner).all { shaded[it] } }?.let { corner ->
            return step(message("teacher.lits.explain_mistake.2"), layout.window(corner))
        }
        if (sight.geo(k).isEmpty()) {
            return step(message("teacher.lits.explain_mistake.3"), region)
        }
        sight.letters[wrong]?.let { piece ->
            val twin = region.flatMap { layout.neighbours(it) }
                .firstOrNull { layout.of[it] != k && shaded[it] && sight.letters[it] == piece }
            if (twin != null) {
                val theirs = layout.cellsOf[layout.of[twin]].filter { shaded[it] }
                return step(message("teacher.lits.explain_mistake.4", "article_piece_" to (article(piece)), "piece_name" to (piece.name)), have + theirs)
            }
        }
        if (sight.cand(k).isEmpty()) {
            return step(
                message("teacher.lits.explain_mistake.5"),
                region,
            )
        }
        return when (known) {
            Blame.RESCUES -> step(message("teacher.lits.explain_mistake.6"), emptyList())
            Blame.STUCK -> step(message("teacher.lits.explain_mistake.7"), emptyList())
            Blame.UNPROVED -> step(message("teacher.lits.explain_mistake.8"), emptyList())
        }
    }

    /**
     * Nothing short enough to explain applies, so point at a square of [reference] in the region
     * with the fewest shapes left. Says plainly which of two things is going on: a square every
     * remaining answer shades (reasoning exists, just longer than a hint), or a genuine choice
     * between answers (there is nothing to reason out).
     */
    private fun fallback(layout: Layout, shaded: BooleanArray, reference: Set<Int>): Step? {
        val sight = Sight(layout, shaded, BooleanArray(layout.n))
        val open = reference.sorted().filter { !shaded[it] }
        if (open.isEmpty()) return null
        val byTightness = open.sortedBy { sight.cand(layout.of[it]).size }
        // A square every answer shades if one exists; otherwise the first square some answer leaves
        // empty, so "a choice" is said of the very square pointed at; otherwise, unproved, the first.
        var pick = byTightness.first()
        var verdict: Search = Search.Unknown
        for (c in byTightness) {
            val v = extend(layout, shaded, setOf(c))
            if (v is Search.NoAnswer) {
                pick = c
                verdict = v
                break
            }
            if (verdict is Search.Unknown && v is Search.Found) {
                pick = c
                verdict = v
            }
        }
        val home = layout.cellsOf[layout.of[pick]].toSet()
        return when (verdict) {
            Search.NoAnswer -> Step(
                technique = FALLBACK, shades = listOf(pick), focus = home, cited = emptySet(),
                nudge = message("teacher.lits.fallback.1"),
                explanation = message("teacher.lits.fallback.2"),
            )
            is Search.Found -> Step(
                technique = FALLBACK, shades = listOf(pick), focus = home, cited = emptySet(),
                nudge = message("teacher.lits.fallback.3"),
                explanation = message("teacher.lits.fallback.4"),
            )
            Search.Unknown -> Step(
                technique = FALLBACK, shades = listOf(pick), focus = home, cited = emptySet(),
                nudge = message("teacher.lits.fallback.5"),
                explanation = message("teacher.lits.fallback.6"),
            )
        }
    }

    // ---- reasoning from what the player can see --------------------------------------------

    /**
     * The simplest step visible on this board, or null when none applies — including on a board
     * whose shading already leaves some region no legal shape, since nothing reasoned from a
     * contradiction is worth teaching.
     *
     * Takes no answer on purpose — see the class comment.
     */
    fun deduce(width: Int, height: Int, region: List<Int>, shaded: List<Boolean>): Step? {
        val layout = Layout(width, height, region)
        val sight = Sight(layout, shaded.toBooleanArray(), BooleanArray(layout.n))
        if ((0 until layout.count).any { sight.cand(it).isEmpty() }) return null
        return wholeRegion(sight)
            ?: overlap(sight)
            ?: filtered(sight)
            ?: neighbour(sight)
            ?: whatIf(sight)
    }

    /**
     * Every step of [technique] on this board, not only the one [deduce] would give: Training passes a move that makes
     * any of them. Empty on a board where no step can be taken. Takes no answer, as [deduce].
     */
    fun instances(width: Int, height: Int, region: List<Int>, shaded: List<Boolean>, technique: String): List<Step> {
        val layout = Layout(width, height, region)
        val sight = Sight(layout, shaded.toBooleanArray(), BooleanArray(layout.n))
        if ((0 until layout.count).any { sight.cand(it).isEmpty() }) return emptyList()
        val out = ArrayList<Step>()
        when (technique) {
            WHOLE_REGION -> wholeRegion(sight, out)
            OVERLAP -> overlap(sight, out)
            AVOID_BLOCK, LETTER_CLASH -> filtered(sight, out)
            NEIGHBOUR -> neighbour(sight, out)
            WHAT_IF -> whatIfs(sight) { x, chain -> out += whatIfStep(sight, x, chain) }
        }
        return out.filter { it.technique == technique }
    }

    /**
     * The deepest technique ([TECHNIQUES] index) a walk by [deduce] from the blank board needs to finish it,
     * or null when the walk gets stuck. The generator's tier gate ([Lits.build]): no answer reaches it.
     */
    fun depth(width: Int, height: Int, region: List<Int>): Int? {
        val shaded = BooleanArray(width * height)
        var deepest = -1
        while (!Lits.isSolved(width, height, region, shaded.asList())) {
            val step = deduce(width, height, region, shaded.asList()) ?: return null
            if (step.shades.all { shaded[it] }) return null
            deepest = maxOf(deepest, TECHNIQUES.indexOf(step.technique))
            for (c in step.shades) shaded[c] = true
        }
        return deepest
    }

    /**
     * A rung's steps go to [out] when it is given (every one, for Training's instances); without it the rung returns its
     * first step, as [deduce] always did. No sequence: [deduce] runs inside the generator's gate, where a coroutine per
     * rung call cost a quarter of the generation time.
     */
    private fun wholeRegion(s: Sight, out: MutableList<Step>? = null): Step? {
        for (k in 0 until s.layout.count) {
            val cells = s.layout.cellsOf[k]
            if (cells.size != 4 || s.complete(k)) continue
            val todo = cells.filter { !s.shaded[it] }
            Step(
                technique = WHOLE_REGION,
                shades = todo,
                focus = cells.toSet(),
                cited = cells.toSet(),
                nudge = message("teacher.lits.whole_region.1"),
                explanation = message("teacher.lits.whole_region.2", "value1" to (if (todo.size == 4) message("teacher.lits.whole_region.extra.1") else message("teacher.lits.whole_region.extra.2"))),
            ).let { if (out == null) return it else out += it }
        }
        return null
    }

    private fun overlap(s: Sight, out: MutableList<Step>? = null): Step? {
        for (k in 0 until s.layout.count) {
            if (s.complete(k)) continue
            val shapes = s.geo(k)
            val todo = common(shapes).filter { !s.shaded[it] }
            if (todo.isEmpty()) continue
            val cells = s.layout.cellsOf[k].toSet()
            val around = if (s.shadedIn(k).isEmpty()) "" else message("teacher.lits.overlap.1")
            Step(
                technique = OVERLAP,
                shades = todo,
                focus = cells,
                cited = cells,
                nudge = message("teacher.lits.overlap.2"),
                explanation = if (shapes.size == 1) {
                    message("teacher.lits.overlap.3", "around" to (around))
                } else {
                    message("teacher.lits.overlap.4", "around" to (around), "these_todo_" to (these(todo)), "they_todo_" to (they(todo)))
                },
            ).let { if (out == null) return it else out += it }
        }
        return null
    }

    /** [OVERLAP] after setting aside the shapes the 2x2 rule, the letter rule, or both, forbid. */
    private fun filtered(s: Sight, out: MutableList<Step>? = null): Step? {
        for (k in 0 until s.layout.count) {
            if (s.complete(k)) continue
            val geo = s.geo(k)
            val noBlock = geo.filter { !s.fillsBlock(it) }
            val noClash = geo.filter { !s.clashes(it) }
            val both = s.cand(k)
            val (technique, kept) = when {
                common(noBlock).any { !s.shaded[it] } -> AVOID_BLOCK to noBlock
                common(noClash).any { !s.shaded[it] } -> LETTER_CLASH to noClash
                common(both).any { !s.shaded[it] } -> LETTER_CLASH to both
                else -> continue
            }
            val todo = common(kept).filter { !s.shaded[it] }
            val dropped = geo.filter { it !in kept }
            val cells = s.layout.cellsOf[k].toSet()
            val witnesses = dropped.flatMap { witnessesOf(s, it) }.toSet()
            val blockDropped = dropped.any { s.fillsBlock(it) }
            val clashing = dropped.filter { s.clashes(it) }.map { it.piece }.distinct().sortedBy { it.ordinal }
            val why = when {
                clashing.isEmpty() -> message("teacher.lits.filtered.1")
                !blockDropped && clashing.size == 1 ->
                    message("teacher.lits.filtered.2", "article_clashing_0_" to (article(clashing[0])), "clashing_0_name" to (clashing[0].name))
                !blockDropped -> message("teacher.lits.filtered.3")
                else -> message("teacher.lits.filtered.4")
            }
            Step(
                technique = technique,
                shades = todo,
                focus = cells,
                cited = cells + witnesses,
                nudge = message("teacher.lits.filtered.5"),
                explanation = message("teacher.lits.filtered.6", "why" to (why), "these_todo_" to (these(todo)), "them_todo_" to (them(todo))),
            ).let { if (out == null) return it else out += it }
        }
        return null
    }

    /** The shaded squares outside [t]'s region that condemn it: its 2x2 partners and letter twins. */
    private fun witnessesOf(s: Sight, t: Shape): List<Int> = buildList {
        for (cell in t.cells) {
            for (corner in s.layout.windowsAt(cell)) {
                val win = s.layout.window(corner)
                if (win.all { it in t.cells || s.shaded[it] }) addAll(win.filter { it !in t.cells })
            }
            for (next in s.layout.neighbours(cell)) {
                if (next !in t.cells && s.shaded[next] && s.letters[next] == t.piece) {
                    addAll(s.layout.cellsOf[s.layout.of[next]].filter { s.shaded[it] })
                }
            }
        }
    }

    /** [OVERLAP] after setting aside the shapes that would leave a neighbouring region no shape. */
    private fun neighbour(s: Sight, out: MutableList<Step>? = null): Step? {
        for (k in 0 until s.layout.count) {
            if (s.complete(k)) continue
            val kept = s.cand1(k)
            val todo = common(kept).filter { !s.shaded[it] }
            if (todo.isEmpty()) continue
            val dropped = s.cand(k).filter { it !in kept }
            val victims = dropped.flatMap { s.killers(k, it) }.distinct().sorted()
            var why = 0
            for (t in dropped) for (b in s.killers(k, t)) for (u in s.cand(b)) why = why or s.conflict(t, u)
            val reason = when (why) {
                BLOCK -> message("teacher.lits.neighbour.1")
                LETTER -> message("teacher.lits.neighbour.2")
                else -> message("teacher.lits.neighbour.3")
            }
            val cells = s.layout.cellsOf[k].toSet()
            val next = victims.flatMap { s.layout.cellsOf[it] }.toSet()
            val whose = if (victims.size == 1) message("teacher.lits.neighbour.4") else message("teacher.lits.neighbour.5")
            Step(
                technique = NEIGHBOUR,
                shades = todo,
                focus = cells,
                cited = cells + next,
                nudge = message("teacher.lits.neighbour.6"),
                explanation = message("teacher.lits.neighbour.7", "whose" to (whose), "reason" to (reason), "these_todo_" to (these(todo)), "them_todo_" to (them(todo))),
            ).let { if (out == null) return it else out += it }
        }
        return null
    }

    /** A supposition followed through: squares it forces, region by region, to a region left empty. */
    private class Chain(val forced: List<Pair<Int, List<Int>>>, val emptied: Int)

    private fun chainToEmpty(start: Sight, changed: List<Int>): Chain? {
        var cur = start
        var touched = changed
        val forced = mutableListOf<Pair<Int, List<Int>>>()
        while (true) {
            val nearby = nearbyRegions(cur.layout, touched)
            nearby.firstOrNull { cur.cand1(it).isEmpty() }?.let { return Chain(forced.toList(), it) }
            if (forced.size == MAX_CHAIN) return null
            val (k, next) = nearby.firstNotNullOfOrNull { k ->
                if (cur.complete(k)) null else common(cur.cand1(k)).filter { !cur.shaded[it] }.ifEmpty { null }?.let { k to it }
            } ?: return null
            forced += k to next
            cur = cur.with(shade = next)
            touched = touched + next
        }
    }

    /**
     * Regions a change at [cells] can reach within a step: ones with a square within three of it.
     * Only limits where a what-if looks for its contradiction; every contradiction it reports is
     * checked in full.
     */
    private fun nearbyRegions(layout: Layout, cells: List<Int>): List<Int> {
        val out = BooleanArray(layout.count)
        for (cell in cells) {
            val r = cell / layout.w
            val c = cell % layout.w
            for (dr in -3..3) for (dc in -3..3) {
                val rr = r + dr
                val cc = c + dc
                if (rr in 0 until layout.h && cc in 0 until layout.w) out[layout.of[rr * layout.w + cc]] = true
            }
        }
        return (0 until layout.count).filter { out[it] }
    }

    /**
     * Every square whose staying empty leads to a region with no shape, handed to [found] with the chain that shows it.
     */
    private inline fun whatIfs(s: Sight, found: (Int, Chain) -> Unit) {
        for (x in 0 until s.layout.n) {
            if (s.shaded[x]) continue
            val k = s.layout.of[x]
            val shapes = s.cand1(k)
            // Squares no shape can use are the board's crosses; squares every shape uses were the
            // neighbour step's. Neither is a what-if.
            if (shapes.none { x in it.cells } || shapes.all { x in it.cells }) continue
            found(x, chainToEmpty(s.with(keepEmpty = listOf(x)), listOf(x)) ?: continue)
        }
    }

    private fun whatIf(s: Sight): Step? {
        var best: Pair<Int, Chain>? = null
        whatIfs(s) { x, chain ->
            // A chain of one or none is as short as any (an earlier one that short would have returned), so take it.
            if (chain.forced.size <= 1) return whatIfStep(s, x, chain)
            if (best == null || chain.forced.size < best!!.second.forced.size) best = x to chain
        }
        val (x, chain) = best ?: return null
        return whatIfStep(s, x, chain)
    }

    private fun whatIfStep(s: Sight, x: Int, chain: Chain): Step {
        val emptied = s.layout.cellsOf[chain.emptied]
        val home = s.layout.of[x]
        val steps = chain.forced.mapIndexed { i, (k, cells) ->
            val count = if (cells.size == 1) message("teacher.lits.what_if_step.1") else message("teacher.lits.what_if_step.2", "value1" to (numberWordMessage(cells.size, zero = message("teacher.lits.what_if_step.extra.1"), upTo = 4)))
            when {
                i == 0 && k == home -> message("teacher.lits.what_if_step.3", "count" to (count))
                i == 0 -> message("teacher.lits.what_if_step.4", "count" to (count))
                else -> message("teacher.lits.what_if_step.5", "value1" to (if (cells.size == 1) message("teacher.lits.what_if_step.extra.2") else numberWordMessage(cells.size, zero = message("teacher.lits.what_if_step.extra.3"), upTo = 4)))
            }
        }
        val tail = message("teacher.lits.what_if_step.6")
        return Step(
            technique = WHAT_IF,
            shades = listOf(x),
            focus = setOf(x),
            cited = (chain.forced.flatMap { it.second } + emptied).toSet(),
            nudge = message("teacher.lits.what_if_step.7"),
            explanation = message("teacher.lits.what_if_step.8", "value1" to ((if (steps.isEmpty()) tail else steps.joinedMessages(", ") + message("teacher.lits.what_if_step.extra.4")))),
        )
    }

    // ---- words ---------------------------------------------------------------------------------

    private fun article(p: Lits.Piece) = if (p == Lits.Piece.T) message("teacher.lits.article.1") else message("teacher.lits.article.2", "p_name" to (p.name))

    private fun these(cells: List<Int>) = if (cells.size == 1) message("teacher.lits.these.1") else message("teacher.lits.these.2")

    private fun them(cells: List<Int>) = if (cells.size == 1) message("teacher.lits.them.1") else message("teacher.lits.them.2")

    private fun they(cells: List<Int>) = if (cells.size == 1) message("teacher.lits.they.1") else message("teacher.lits.they.2")
}
