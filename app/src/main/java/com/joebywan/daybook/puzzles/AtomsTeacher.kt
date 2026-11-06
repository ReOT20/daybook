package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.TeachStep
import com.joebywan.daybook.core.asMessage
import com.joebywan.daybook.core.cap
import com.joebywan.daybook.core.i18n.Message
import com.joebywan.daybook.core.i18n.literalMessage
import com.joebywan.daybook.core.i18n.message
import com.joebywan.daybook.core.joinedMessages
import com.joebywan.daybook.core.listedMessage
import com.joebywan.daybook.core.numberWordMessage

/**
 * Atoms, reasoned the way a person reasons it, one named step at a time.
 *
 * As in [KingsTeacher], the split is the point. [deduce] is handed the board as the player sees it —
 * the atoms, the lines between them and the bonds already drawn — and has no parameter through
 * which the stored answer could reach it. The answer is read only in [teach]: to decide which of
 * the player's bonds are mistakes, and for [FALLBACK], which says openly that it is pointing at the
 * answer.
 *
 * The player can only ever *add* bonds (there is no "no bond here" mark), so every step is a bond
 * or two to lay down. The facts that rule a bond out — a crossing, a closed-off group — never reach
 * the board themselves; they are the reason a bond somewhere else becomes forced, and the step is
 * named after that reason.
 *
 * Techniques, simplest first:
 * 1. [ONE_NEIGHBOUR] — an atom with only one neighbour it can still bond with.
 * 2. [ALL_FORCED] — an atom that needs exactly as many bonds as its neighbours have room for.
 * 3. [AT_LEAST_ONE] — an atom that can leave only a little room unused, so any neighbour with more
 *    room than that must take a bond (the 3-with-two-neighbours rule).
 * 4. [CROSSING] — one of the above, once a bond already drawn blocks a line that crosses it.
 * 5. [ISOLATION] — one of the above, once a bond that would close a group off (1–1, 2=2, or a
 *    larger finished group) is ruled out.
 * 6. [ONLY_WAY_OUT] — a group of bonded atoms with only one line left to the rest.
 * 7. [WHAT_IF] — leaving a line unbonded forces a short chain that ends in an atom that can't
 *    reach its number, or a group closed off.
 *
 * Cell indices, as [com.joebywan.daybook.core.Deduction] and the board's highlight use them: atom
 * `a` is cell `a`, and line `p` is cell `atoms.size + p` — see [pairCell].
 *
 * Every loop walks atoms and lines in index order and nothing iterates a hash, so the same board
 * gets the same hint on every platform.
 */
internal object AtomsTeacher {

    const val ONE_NEIGHBOUR = "one-neighbour"
    const val ALL_FORCED = "all-forced"
    const val AT_LEAST_ONE = "at-least-one"
    const val CROSSING = "crossing"
    const val ISOLATION = "isolation"
    const val ONLY_WAY_OUT = "only-way-out"
    const val WHAT_IF = "what-if"
    const val FALLBACK = "fallback"
    const val MISTAKE = "mistake"

    /** Every technique in the order [deduce] tries them, for reports. */
    val TECHNIQUES = listOf(
        ONE_NEIGHBOUR, ALL_FORCED, AT_LEAST_ONE, CROSSING, ISOLATION, ONLY_WAY_OUT, WHAT_IF, FALLBACK,
    )

    /**
     * How many forced atoms a [WHAT_IF] may walk through. One sentence has to carry the whole
     * argument; past two it stops being something to hold in your head while looking at the board.
     */
    const val MAX_CHAIN = 2

    /** What the hint panel shows at once, in characters; wording that can run long shortens itself to fit. */
    private const val MAX_EXPLANATION = 200

    /** The highlight cell for line [pair] on a board of [atomCount] atoms. */
    fun pairCell(atomCount: Int, pair: Int) = atomCount + pair

    /**
     * One step: bonds to raise to at least a count, or a mistaken line to clear, and why.
     *
     * [raise] maps a line to the count it should reach; [clear] is only ever a mistake.
     */
    class Step(
        override val technique: String,
        val raise: Map<Int, Int> = emptyMap(),
        val clear: List<Int> = emptyList(),
        override val focus: Set<Int>,
        override val cited: Set<Int>,
        override val targets: Set<Int>,
        nudge: CharSequence,
        explanation: CharSequence,
    ) : TeachStep {

        override val nudgeMessage: Message = nudge.asMessage()
        override val explanationMessage: Message = explanation.asMessage()
        override val nudge: String get() = nudgeMessage.english
        override val explanation: String get() = explanationMessage.english
    }

    // ---- the board as the player sees it --------------------------------------------------------

    private const val BASIC = 0
    private const val WITH_CROSSING = 1
    private const val WITH_ISOLATION = 2

    /**
     * Atoms, lines and bonds. [lo] is what is drawn (a line can only gain bonds in a step);
     * [ceiling] caps a line below two, which only a [WHAT_IF] hypothesis does.
     */
    private class Sight(
        val atoms: List<Atom>,
        val pairs: List<Pair2>,
        val lo: IntArray,
        val ceiling: IntArray,
        val crossing: List<List<Int>>,
        val incident: List<List<Int>>,
    ) {
        val n get() = atoms.size

        fun other(p: Int, a: Int) = if (pairs[p].a == a) pairs[p].b else pairs[p].a

        fun degree(a: Int) = incident[a].sumOf { lo[it] }

        fun need(a: Int) = atoms[a].bonds - degree(a)

        fun crossedBy(p: Int): Int? = crossing[p].firstOrNull { lo[it] > 0 }

        /** The most bonds line [p] could end up with, from what [level] lets the player see. */
        fun hi(p: Int, level: Int): Int {
            val pr = pairs[p]
            var h = minOf(ceiling[p], lo[p] + maxOf(0, need(pr.a)), lo[p] + maxOf(0, need(pr.b)))
            if (level >= WITH_CROSSING && lo[p] == 0 && crossedBy(p) != null) h = 0
            if (level >= WITH_ISOLATION && h > lo[p] && closes(p, h - lo[p])) h -= 1
            return maxOf(h, lo[p])
        }

        fun cap(p: Int, level: Int) = hi(p, level) - lo[p]

        /**
         * Whether [k] more bonds on [p] would finish every atom in the group it joins while leaving
         * that group short of the whole molecule.
         */
        fun closes(p: Int, k: Int): Boolean {
            val pr = pairs[p]
            if (need(pr.a) != k || need(pr.b) != k) return false
            val group = component(pr.a, extra = p)
            if (group.size == n) return false
            return group.all { it == pr.a || it == pr.b || need(it) == 0 }
        }

        /** Atoms joined to [start] by drawn bonds (and by [extra], as if it were drawn). */
        fun component(start: Int, extra: Int = -1): List<Int> {
            val seen = BooleanArray(n)
            val stack = ArrayDeque<Int>()
            seen[start] = true
            stack.addLast(start)
            while (stack.isNotEmpty()) {
                val a = stack.removeLast()
                for (p in incident[a]) {
                    if (lo[p] == 0 && p != extra) continue
                    val b = other(p, a)
                    if (!seen[b]) {
                        seen[b] = true
                        stack.addLast(b)
                    }
                }
            }
            return (0 until n).filter { seen[it] }
        }

        /** The lines out of [group] that could still take a bond, each once, in order. */
        fun exits(group: List<Int>): List<Int> {
            val inside = BooleanArray(n).also { m -> group.forEach { m[it] = true } }
            return group.flatMap { a -> incident[a] }
                .filter { p -> inside[pairs[p].a] != inside[pairs[p].b] && cap(p, WITH_ISOLATION) > 0 }
                .distinct().sorted()
        }

        /** Every group of joined atoms, each listed once, in order of its lowest atom. */
        fun components(): List<List<Int>> {
            val done = BooleanArray(n)
            val out = mutableListOf<List<Int>>()
            for (a in 0 until n) {
                if (done[a]) continue
                val c = component(a)
                c.forEach { done[it] = true }
                out += c
            }
            return out
        }

        fun copy(ceilingAt: Int = -1, ceilingTo: Int = 0) = Sight(
            atoms, pairs, lo.copyOf(),
            ceiling.copyOf().also { if (ceilingAt >= 0) it[ceilingAt] = ceilingTo },
            crossing, incident,
        )
    }

    private fun sightOf(atoms: List<Atom>, pairs: List<Pair2>, counts: List<Int>): Sight {
        val crossing = pairs.indices.map { p -> pairs.indices.filter { q -> q != p && Atoms.crosses(atoms, pairs[p], pairs[q]) } }
        val incident = atoms.indices.map { a -> pairs.indices.filter { pairs[it].a == a || pairs[it].b == a } }
        return Sight(atoms, pairs, counts.toIntArray(), IntArray(pairs.size) { 2 }, crossing, incident)
    }

    // ---- the whole hint: mistakes, then reasoning, then the honest fallback ----------------------

    /** The next thing to show this player, or null on a solved board. */
    fun teach(s: AtomsState): Step? {
        if (s.solved) return null
        // Games saved before the generator checked its answers can carry one that breaks the rules
        // (see [Atoms.generate]). Its bonds cannot be trusted to call anything a mistake, or to
        // point at, so such a board gets reasoning only.
        if (!s.copy(counts = s.solution).solved) return deduce(s.atoms, s.pairs, s.counts)
        mistake(s)?.let { return it }
        return deduce(s.atoms, s.pairs, s.counts) ?: fallback(s)
    }

    /** A line carrying more bonds than the answer gives it. */
    private fun mistake(s: AtomsState): Step? {
        val wrong = s.pairs.indices.firstOrNull { s.counts[it] > s.solution[it] } ?: return null
        val n = s.atoms.size
        val pr = s.pairs[wrong]
        // Reason from the bonds the answer agrees with, plus this one: a second wrong bond could
        // otherwise be what makes this one look wrong.
        val counts = s.counts.indices.map { if (it == wrong) s.counts[it] else minOf(s.counts[it], s.solution[it]) }
        val sight = sightOf(s.atoms, s.pairs, counts)
        val howToClear = if (s.counts[wrong] == 2) message("teacher.atoms.mistake.1") else message("teacher.atoms.mistake.2")
        val line = pairCell(n, wrong)
        fun step(explanation: Message, cited: Set<Int>) = Step(
            technique = MISTAKE,
            clear = listOf(wrong),
            focus = setOf(line),
            cited = cited,
            targets = setOf(line),
            nudge = message("teacher.atoms.step.1"),
            explanation = message("teacher.atoms.step.extra.1", "explanation" to (explanation), "howToClear" to (howToClear)),
        )

        for (end in listOf(pr.a, pr.b)) {
            if (sight.need(end) < 0) {
                return step(message("teacher.atoms.mistake.3", "s_atoms_end_bonds" to (s.atoms[end].bonds)), setOf(end))
            }
        }
        val group = sight.component(pr.a)
        if (group.size < n && group.all { sight.need(it) == 0 }) {
            val what = if (group.size == 2) {
                message("teacher.atoms.mistake.4")
            } else {
                message("teacher.atoms.mistake.5")
            }
            return step(message("teacher.atoms.mistake.6", "what" to (what)), group.toSet())
        }
        propagate(sight)?.let { chain ->
            return step(message("teacher.atoms.mistake.7", "value1" to (chain.phrase(sight, lead = false))), chain.cited(sight))
        }
        return step(message("teacher.atoms.mistake.8"), emptySet())
    }

    /**
     * Nothing short enough to explain applies, so point at a line the answer bonds, next to the atom
     * with the least room to spare. Says plainly that it is doing this.
     */
    private fun fallback(s: AtomsState): Step? {
        val sight = sightOf(s.atoms, s.pairs, s.counts)
        val n = s.atoms.size
        val open = s.pairs.indices.filter { s.counts[it] < s.solution[it] }
        val target = open.minByOrNull { p ->
            val pr = s.pairs[p]
            minOf(spare(sight, pr.a), spare(sight, pr.b)) * 1000 + p
        } ?: return null
        val pr = s.pairs[target]
        val atom = if (spare(sight, pr.a) <= spare(sight, pr.b)) pr.a else pr.b
        return Step(
            technique = FALLBACK,
            raise = mapOf(target to s.counts[target] + 1),
            focus = setOf(atom),
            cited = setOf(pr.a, pr.b),
            targets = setOf(pairCell(n, target)),
            nudge = message("teacher.atoms.fallback.1", "s_atoms_atom_bonds" to (s.atoms[atom].bonds)),
            explanation = message("teacher.atoms.fallback.2"),
        )
    }

    private fun spare(s: Sight, a: Int): Int =
        s.incident[a].sumOf { s.cap(it, WITH_ISOLATION) } - s.need(a)

    // ---- reasoning from what the player can see ---------------------------------------------------

    /**
     * The simplest step visible on this board, or null when none of the techniques applies.
     *
     * Takes no answer on purpose — see the class comment. [counts] are taken as the player's; the
     * caller has already dealt with any that are mistakes.
     */
    fun deduce(atoms: List<Atom>, pairs: List<Pair2>, counts: List<Int>): Step? = deduce(sightOf(atoms, pairs, counts))

    private fun deduce(s: Sight): Step? {
        if ((0 until s.n).any { s.need(it) < 0 }) return null
        for (level in BASIC..WITH_ISOLATION) {
            val f = atomRule(s, level) ?: continue
            return explainAtom(s, f, level)
        }
        onlyWayOut(s)?.let { return it }
        return whatIf(s)
    }

    /**
     * The deepest technique, as its index in [TECHNIQUES], that a walk by [deduce] alone needs from the
     * empty board to the end, or null when the walk stops short (a hint would fall back). Each step holds
     * for every answer, so a finished walk has also found the only one. The generator gates tiers on it.
     */
    fun depth(atoms: List<Atom>, pairs: List<Pair2>): Int? {
        // One sight for the whole walk: the lines' geometry is worked out once, only the bonds change.
        val sight = sightOf(atoms, pairs, List(pairs.size) { 0 })
        var deepest = -1
        while (true) {
            val step = deduce(sight) ?: break
            deepest = maxOf(deepest, TECHNIQUES.indexOf(step.technique))
            step.raise.forEach { (p, to) -> sight.lo[p] = to }
        }
        return deepest.takeIf { atoms.indices.all { sight.need(it) == 0 } }
    }

    /** What one atom's count forces on its lines, at one level of sight. */
    private class Forced(
        val atom: Int,
        val rule: String,
        /** Line to its new count. */
        val raise: Map<Int, Int>,
        val need: Int,
        val room: Int,
    )

    /**
     * The first atom whose count forces a bond, simplest rule first: one neighbour, then all
     * forced, then at least one.
     */
    private fun atomRule(s: Sight, level: Int): Forced? {
        val found = atomFounds(s, level)
        return listOf(ONE_NEIGHBOUR, ALL_FORCED, AT_LEAST_ONE).firstNotNullOfOrNull { r -> found.firstOrNull { it.rule == r } }
    }

    /**
     * Every step of [technique] on the board as the player sees it, as [deduce] (Training accepts any of them): each atom
     * whose count forces a bond at that technique's level. Only the atom rules are listed; the rest are only ever the one
     * [deduce] picks.
     */
    fun steps(atoms: List<Atom>, pairs: List<Pair2>, counts: List<Int>, technique: String): List<Step> {
        val s = sightOf(atoms, pairs, counts)
        if ((0 until s.n).any { s.need(it) < 0 }) return emptyList()
        val level = when (technique) {
            ONE_NEIGHBOUR, ALL_FORCED, AT_LEAST_ONE -> BASIC
            CROSSING -> WITH_CROSSING
            ISOLATION -> WITH_ISOLATION
            else -> return emptyList()
        }
        return atomFounds(s, level).map { explainAtom(s, it, level) }.filter { it.technique == technique }
    }

    /** Every atom whose count forces a bond at [level], in atom order. */
    private fun atomFounds(s: Sight, level: Int): List<Forced> {
        val found = mutableListOf<Forced>()
        for (a in 0 until s.n) {
            val need = s.need(a)
            if (need <= 0) continue
            val caps = s.incident[a].associateWith { s.cap(it, level) }
            val room = caps.values.sum()
            if (room < need) continue // not this rule's business; a contradiction elsewhere
            val raise = s.incident[a].mapNotNull { p ->
                val k = need - (room - caps.getValue(p))
                // A relaxed level must still be legal once everything is seen.
                if (k > 0 && s.lo[p] + k <= s.hi(p, WITH_ISOLATION)) p to s.lo[p] + k else null
            }.toMap()
            if (raise.isEmpty()) continue
            val usable = caps.count { it.value > 0 }
            val rule = when {
                usable == 1 -> ONE_NEIGHBOUR
                need == room -> ALL_FORCED
                else -> AT_LEAST_ONE
            }
            found += Forced(a, rule, raise, need, room)
        }
        return found
    }

    private fun explainAtom(s: Sight, f: Forced, level: Int): Step {
        val a = f.atom
        val x = s.atoms[a].bonds
        val n = s.n
        val lines = s.incident[a]
        val technique = when (level) {
            BASIC -> f.rule
            WITH_CROSSING -> CROSSING
            else -> ISOLATION
        }
        // Why each line out of this atom has the room it has, when that room is less than a
        // stranger to the board would assume.
        val crossedTo = mutableListOf<Int>()
        val shutOut = mutableListOf<Int>()
        val shutDouble = mutableListOf<Int>()
        var pairOnly = true
        val citedExtra = mutableSetOf<Int>()
        for (p in lines) {
            val b = s.other(p, a)
            val basic = s.cap(p, BASIC)
            val crossed = s.cap(p, WITH_CROSSING)
            val isolated = s.cap(p, WITH_ISOLATION)
            when {
                level >= WITH_CROSSING && crossed < basic -> {
                    crossedTo += b
                    s.crossedBy(p)?.let { citedExtra += pairCell(n, it) }
                }
                level >= WITH_ISOLATION && isolated < crossed -> {
                    val group = s.component(s.pairs[p].a, extra = p)
                    citedExtra += group
                    if (group.size != 2) pairOnly = false
                    if (isolated == 0) shutOut += b else shutDouble += b
                }
            }
        }
        val shut = when {
            shutOut.size + shutDouble.size > 1 -> message("teacher.atoms.explain_atom.1")
            pairOnly -> message("teacher.atoms.explain_atom.2")
            else -> message("teacher.atoms.explain_atom.3")
        }
        // Neighbours by number and place ("to the 2 above"), or, when that runs past the panel, by
        // direction alone ("above").
        fun reasonsFor(short: Boolean): List<Message> {
            fun who(b: Int) = message("teacher.atoms.who.1", "s_atoms_b_bonds" to (s.atoms[b].bonds), "value2" to (where(s.atoms[a], s.atoms[b])))
            fun listedMessage(bs: List<Int>, and: Message = message("teacher.atoms.listed.1")) =
                if (short) listedMessage(bs.map { way(s.atoms[a], s.atoms[it]) }, and) else message("teacher.atoms.listed.2", "value1" to (listedMessage(bs.map(::who), and)))
            return buildList {
                if (crossedTo.isNotEmpty()) {
                    add(if (short) message("teacher.atoms.reasons_for.1", "listed_crossedTo_" to (listedMessage(crossedTo))) else message("teacher.atoms.reasons_for.2", "listed_crossedTo_" to (listedMessage(crossedTo))))
                }
                when (shutOut.size) {
                    0 -> Unit
                    1 -> add(message("teacher.atoms.reasons_for.3", "listed_shutOut_" to (listedMessage(shutOut)), "shut" to (shut)))
                    else -> add(message("teacher.atoms.reasons_for.4", "listed_shutOut_or_" to (listedMessage(shutOut, message("teacher.atoms.reasons_for.extra.1"))), "shut" to (shut)))
                }
                when (shutDouble.size) {
                    0 -> Unit
                    1 -> add(message("teacher.atoms.reasons_for.5", "listed_shutDouble_" to (listedMessage(shutDouble)), "shut" to (shut)))
                    else -> add(message("teacher.atoms.reasons_for.6", "listed_shutDouble_or_" to (listedMessage(shutDouble, message("teacher.atoms.reasons_for.extra.2"))), "shut" to (shut)))
                }
            }
        }
        val reasons = reasonsFor(short = false)
        fun prefixOf(r: List<Message>) = if (r.isEmpty()) literalMessage("") else r.joinedMessages("; ").cap() + ". "
        var prefix = prefixOf(reasons)

        val needWords = if (s.degree(a) == 0) message("teacher.atoms.explain_atom.4", "bondsWord_f_need_" to (bondsWord(f.need))) else message("teacher.atoms.explain_atom.5", "bondsWord_f_need_" to (bondsWord(f.need)))
        fun bodyFor(prefix: Message) = when (f.rule) {
            ONE_NEIGHBOUR -> {
                val p = f.raise.keys.first()
                val b = s.other(p, a)
                val others = lines.filter { it != p }
                val otherWhy = if (others.isEmpty()) "" else {
                    val full = others.filter { s.cap(it, BASIC) == 0 }.map { s.other(it, a) }
                    if (full.isNotEmpty() && reasons.isEmpty()) message("teacher.atoms.body_for.1") else ""
                }
                val goes = when {
                    f.need == 1 && s.degree(a) > 0 -> message("teacher.atoms.body_for.2")
                    f.need == 1 -> message("teacher.atoms.body_for.3")
                    s.degree(a) > 0 -> message("teacher.atoms.body_for.4", "value1" to (numberWordMessage(f.need, upTo = 8)))
                    else -> message("teacher.atoms.body_for.5", "value1" to (if (f.need == 2) message("teacher.atoms.body_for.extra.1") else message("teacher.atoms.body_for.extra.2")))
                }
                if (reasons.isEmpty()) {
                    message("teacher.atoms.body_for.6", "x" to (x), "s_atoms_b_bonds" to (s.atoms[b].bonds), "value3" to (where(s.atoms[a], s.atoms[b])), "otherWhy" to (otherWhy), "goes" to (goes))
                } else {
                    message("teacher.atoms.body_for.7", "s_atoms_b_bonds" to (s.atoms[b].bonds), "value2" to (where(s.atoms[a], s.atoms[b])), "goes" to (goes))
                }
            }
            ALL_FORCED -> {
                val parts = f.raise.entries.sortedBy { it.key }.map { (p, to) ->
                    message("teacher.atoms.body_for.extra.3", "value1" to (numberWordMessage(to - s.lo[p], upTo = 8)), "value2" to (way(s.atoms[a], s.atoms[s.other(p, a)])))
                }
                val lead = message("teacher.atoms.body_for.8", "x" to (x), "needWords" to (needWords))
                val full = message("teacher.atoms.body_for.9", "lead" to (lead), "listed_parts_" to (listedMessage(parts)))
                // The reason above can already have used most of the panel; "so all are used" goes first.
                if (prefix.length + full.length <= MAX_EXPLANATION) full else message("teacher.atoms.body_for.extra.4", "lead" to (lead), "listedMessage_parts_" to (listedMessage(parts)))
            }
            else -> {
                val spare = f.room - f.need
                val targets = f.raise.entries.sortedBy { it.key }
                val each = targets.size == lines.count { s.cap(it, level) > 0 } && targets.all { it.value - s.lo[it.key] == 1 }
                val tail = if (each && targets.size > 1) {
                    message("teacher.atoms.body_for.10")
                } else {
                    // Lines alike in room and in what they are owed read as one sentence.
                    targets.groupBy { (p, to) -> s.cap(p, level) to (to - s.lo[p]) }.entries.map { (key, group) ->
                        val (room, owed) = key
                        val names = group.map { (p, _) ->
                            val b = s.other(p, a)
                            message("teacher.atoms.body_for.11", "s_atoms_b_bonds" to (s.atoms[b].bonds), "value2" to (where(s.atoms[a], s.atoms[b])))
                        }
                        if (names.size == 1) {
                            message("teacher.atoms.body_for.12", "names_0_cap_" to (names[0].cap()), "numberWord_room_upTo_8_" to (numberWordMessage(room, upTo = 8)), "numberWord_owed_upTo_8_" to (numberWordMessage(owed, upTo = 8)))
                        } else {
                            message("teacher.atoms.body_for.13", "listed_names_cap_" to (listedMessage(names).cap()), "numberWord_room_upTo_8_" to (numberWordMessage(room, upTo = 8)), "numberWord_owed_upTo_8_" to (numberWordMessage(owed, upTo = 8)))
                        }
                    }.joinedMessages(" ")
                }
                val usable = lines.filter { s.cap(it, level) > 0 }
                if (reasons.isNotEmpty() && usable.size == 2 && targets.size == 1) {
                    // Two neighbours, one just capped by the reason above: the other must make up the rest.
                    val (p, to) = targets[0]
                    val b = s.other(p, a)
                    message("teacher.atoms.body_for.14", "x" to (x), "needWords" to (needWords), "s_atoms_b_bonds" to (s.atoms[b].bonds), "value4" to (where(s.atoms[a], s.atoms[b])), "value5" to (numberWordMessage(to - s.lo[p], upTo = 8)))
                } else {
                    // Likewise "so one is spare": the arithmetic still stands without it.
                    val lead = message("teacher.atoms.body_for.15", "x" to (x), "needWords" to (needWords), "value3" to (numberWordMessage(f.room, upTo = 8)))
                    val withSpare = message("teacher.atoms.body_for.16", "lead" to (lead), "value2" to (if (spare == 1) message("teacher.atoms.body_for.extra.5") else message("teacher.atoms.body_for.extra.6", "value1" to (numberWordMessage(spare, upTo = 8)))), "tail" to (tail))
                    if (prefix.length + withSpare.length <= MAX_EXPLANATION) withSpare else message("teacher.atoms.body_for.extra.7", "lead" to (lead), "tail" to (tail))
                }
            }
        }
        var body = bodyFor(prefix)
        if (prefix.length + body.length > MAX_EXPLANATION) {
            prefix = prefixOf(reasonsFor(short = true))
            body = bodyFor(prefix)
        }
        val targets = f.raise.keys.map { pairCell(n, it) }.toSet()
        return Step(
            technique = technique,
            raise = f.raise,
            focus = setOf(a),
            cited = setOf(a) + lines.map { s.other(it, a) } + lines.map { pairCell(n, it) } + citedExtra,
            targets = targets,
            nudge = message("teacher.atoms.explain_atom.6", "x" to (x)),
            explanation = prefix + body,
        )
    }

    /** A group of joined atoms, not yet the whole molecule, with only one line left to the rest. */
    private fun onlyWayOut(s: Sight): Step? {
        if (s.components().size == 1) return null
        for (group in s.components()) {
            if (group.size < 2) continue // a lone atom is [ONE_NEIGHBOUR]'s case
            val exits = s.exits(group)
            if (exits.size != 1) continue
            val p = exits[0]
            val pr = s.pairs[p]
            val from = if (pr.a in group) pr.a else pr.b
            val to = s.other(p, from)
            return Step(
                technique = ONLY_WAY_OUT,
                raise = mapOf(p to s.lo[p] + 1),
                focus = group.toSet(),
                cited = group.toSet() + to,
                targets = setOf(pairCell(s.n, p)),
                nudge = message("teacher.atoms.only_way_out.1"),
                explanation = message("teacher.atoms.only_way_out.2", "s_atoms_from_bonds" to (s.atoms[from].bonds), "s_atoms_to_bonds" to (s.atoms[to].bonds), "value3" to (where(s.atoms[from], s.atoms[to]))),
            )
        }
        return null
    }

    // ---- what if ---------------------------------------------------------------------------------

    private sealed interface Dead
    private class Short(val atom: Int) : Dead
    private class Closed(val group: List<Int>) : Dead

    /** A hypothesis followed through at most [MAX_CHAIN] forced atoms to a dead end. */
    private class Chain(val forced: List<Forced>, val dead: Dead) {
        fun cited(s: Sight): Set<Int> {
            val out = mutableSetOf<Int>()
            forced.forEach { f -> out += f.atom; f.raise.keys.forEach { out += pairCell(s.n, it) } }
            when (dead) {
                is Short -> out += dead.atom
                is Closed -> out += dead.group
            }
            return out
        }

        /** "the 3 must bond below, and then the 2 couldn't reach its number". */
        fun phrase(s: Sight, lead: Boolean): Message {
            val steps = forced.map { f ->
                val a = s.atoms[f.atom]
                val where = f.raise.keys.sorted().map { way(a, s.atoms[s.other(it, f.atom)]) }
                message("teacher.atoms.phrase.1", "a_bonds" to (a.bonds), "listed_where_" to (listedMessage(where)))
            }
            val end = when (dead) {
                is Short -> message("teacher.atoms.phrase.2", "s_atoms_dead_atom_bonds" to (s.atoms[dead.atom].bonds))
                is Closed -> if (dead.group.size == 2) {
                    message("teacher.atoms.phrase.3")
                } else {
                    message("teacher.atoms.phrase.4")
                }
            }
            val all = steps + end
            val text = listedMessage(all, message("teacher.atoms.phrase.5"), oxford = true)
            return if (lead) text.cap() else text
        }
    }

    private fun contradiction(s: Sight): Dead? {
        for (a in 0 until s.n) {
            val need = s.need(a)
            if (need < 0) return Short(a)
            if (s.incident[a].sumOf { s.cap(it, WITH_ISOLATION) } < need) return Short(a)
        }
        val groups = s.components()
        if (groups.size > 1) {
            for (g in groups) if (s.exits(g).isEmpty()) return Closed(g)
        }
        return null
    }

    /** Forced atoms, one at a time, until a dead end or [MAX_CHAIN] of them. Mutates [start]. */
    private fun propagate(start: Sight): Chain? {
        val forced = mutableListOf<Forced>()
        while (true) {
            contradiction(start)?.let { return Chain(forced.toList(), it) }
            if (forced.size == MAX_CHAIN) return null
            val f = atomRule(start, WITH_ISOLATION) ?: return null
            f.raise.forEach { (p, to) -> start.lo[p] = to }
            forced += f
        }
    }

    private fun whatIf(s: Sight): Step? {
        var best: Pair<Int, Chain>? = null
        for (p in s.pairs.indices) {
            if (s.cap(p, WITH_ISOLATION) == 0) continue
            val chain = propagate(s.copy(ceilingAt = p, ceilingTo = s.lo[p])) ?: continue
            if (best == null || chain.forced.size < best.second.forced.size) best = p to chain
        }
        val (p, chain) = best ?: return null
        val pr = s.pairs[p]
        val a = s.atoms[pr.a]
        val b = s.atoms[pr.b]
        val line = pairCell(s.n, p)
        val suppose = if (s.lo[p] == 0) message("teacher.atoms.what_if.1") else message("teacher.atoms.what_if.2")
        val need = if (s.lo[p] == 0) message("teacher.atoms.what_if.3") else message("teacher.atoms.what_if.4")
        return Step(
            technique = WHAT_IF,
            raise = mapOf(p to s.lo[p] + 1),
            focus = setOf(pr.a, pr.b, line),
            cited = chain.cited(s) + pr.a + pr.b,
            targets = setOf(line),
            nudge = message("teacher.atoms.what_if.5", "value1" to (if (s.lo[p] == 0) message("teacher.atoms.what_if.extra.1") else message("teacher.atoms.what_if.extra.2"))),
            explanation = message("teacher.atoms.what_if.6", "a_bonds" to (a.bonds), "b_bonds" to (b.bonds), "suppose" to (suppose), "value4" to (chain.phrase(s, lead = false)), "need" to (need)),
        )
    }

    // ---- words -----------------------------------------------------------------------------------

    /** Where [to] sits, seen from [from]: "to the right", "above". */
    private fun where(from: Atom, to: Atom): Message = way(from, to).let { if (it.english == "right" || it.english == "left") message("teacher.atoms.where.1", "it" to (it)) else it }

    /** The same, as one word for lists: "left", "above". */
    private fun way(from: Atom, to: Atom): Message = when {
        to.row == from.row && to.col > from.col -> message("teacher.atoms.way.1")
        to.row == from.row -> message("teacher.atoms.way.2")
        to.row < from.row -> message("teacher.atoms.way.3")
        else -> message("teacher.atoms.way.4")
    }

    private fun bondsWord(k: Int) = message(
        "teacher.atoms.count.bond", "count" to k, "number" to numberWordMessage(k, upTo = 8),
    )
}
