package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.asMessage
import com.joebywan.daybook.core.i18n.Message
import com.joebywan.daybook.core.i18n.literalMessage
import com.joebywan.daybook.core.i18n.message
import com.joebywan.daybook.core.joinedMessages
import com.joebywan.daybook.core.listedMessage
import com.joebywan.daybook.core.cap
import com.joebywan.daybook.puzzles.CipherLogic.EMPTY

/**
 * The working behind a Cipher hint, written out so a player can follow it: which carries are settled and why, then
 * the column that gives the letter, as a sum, with every other digit it could have ruled out by name. "Only I = 0
 * fits both" told a player the answer and nothing they could use on the next board.
 *
 * It may lean only on what the player can see: the digits placed. The step's own technique proves which carries are
 * settled ([carry], masks as [CipherTeacher.Sight.carry]); a carry is used only once a sentence here says why, from
 * one neighbouring column and the placed digits (chained outward a few columns at most). Anything it cannot say that
 * way gives null, and the teacher keeps its shorter summary.
 */
internal class CipherWorking(private val sum: CipherSum, private val digits: List<Int>, private val carry: IntArray) {

    private fun leads(l: Int) = sum.leading and (1 shl l) != 0

    /** Who holds digit [d] already, as "T has 1"; null when nobody does. */
    private fun holder(d: Int): Int? = digits.indexOf(d).takeIf { it >= 0 }

    /** Every way column [c] adds up with carry [a] in and [b] out, from [v]. Strict: free digits, all different, no leading 0. */
    private fun fills(c: Int, a: Int, b: Int, v: List<Int>, strict: Boolean): List<List<Int>> {
        val open = sum.columnLetters[c].filter { v[it] == EMPTY }
        val out = mutableListOf<List<Int>>()
        val w = v.toMutableList()
        fun go(k: Int) {
            if (k == open.size) {
                if (sum.columnAddends[c].sumOf { w[it] } + a == w[sum.columnTotal[c]] + 10 * b) out += w.toList()
                return
            }
            val l = open[k]
            for (d in 0..9) {
                if (d == 0 && leads(l)) continue
                if (strict && w.contains(d)) continue
                w[l] = d
                go(k + 1)
                w[l] = EMPTY
            }
        }
        go(0)
        return out
    }

    private class Carry(val values: List<Int>, val why: List<Message> = emptyList(), val columns: Set<Int> = emptySet())

    /** The working as said, and the columns it reads. */
    class Working(text: Message, val columns: Set<Int>) {
        val textMessage: Message = text
        val text: String get() = textMessage.english
    }

    /** Carry [k]: plain, explained (with the sentences that explain it), or either value. [away] is the direction to look for a reason. */
    private fun carryAt(k: Int, away: Int, depth: Int): Carry {
        sum.knownCarry(digits, k)?.let { return Carry(listOf(it)) }
        val m = carry[k]
        if (depth > 0 && m != 0 && m and (m - 1) == 0) {
            val v = m.countTrailingZeroBits()
            listOfNotNull(explainCarry(k, v, away, depth), supposeCarry(k, v))
                .minByOrNull { it.why.sumOf { w -> w.length } }?.let { return it }
        }
        return Carry(listOf(0, 1))
    }

    /** Carry [k] is [v] because the other value, followed column by column, gives some letter a digit it cannot have. */
    private fun supposeCarry(k: Int, v: Int): Carry? {
        val cy = IntArray(sum.width + 1) { -1 }.also { it[0] = 0; it[sum.width] = 0; it[k] = 1 - v }
        val (trace, columns) = follow(digits.toMutableList(), cy) ?: return null
        val who = message("teacher.cipher_working.suppose_carry.1", "sum_place_k_1_" to (CipherTeacher.placeMessage(k - 1)))
        val conclusion = if (v == 1) message("teacher.cipher_working.suppose_carry.2") else message("teacher.cipher_working.suppose_carry.3")
        val hypothesis = if (v == 1) message("teacher.cipher_working.suppose_carry.4", "who" to (who)) else message("teacher.cipher_working.suppose_carry.5", "who" to (who))
        return Carry(listOf(v), listOf(message("teacher.cipher_working.suppose_carry.extra.1", "hypothesis" to (hypothesis), "trace" to (trace), "conclusion" to (conclusion))), columns + (k - 1) + minOf(k, sum.width - 1))
    }

    /**
     * Follows [v] and the carries [cy] (-1 unknown) column by column, as a player would with pencil: a column with one
     * letter left to find gives it, a column with every letter known gives its carries. Returns how it breaks ("T would
     * be 4 and H 9, which A has") and the columns it read, or null if nothing breaks within [LIMIT] letters.
     */
    private fun follow(v: MutableList<Int>, cy: IntArray): Pair<Message, Set<Int>>? {
        val found = mutableListOf<Message>()
        val read = mutableSetOf<Int>()
        fun told(clash: Message): Pair<Message, Set<Int>> {
            val head = when (found.size) {
                0 -> literalMessage("")
                else -> found.joinedMessages(", ") + message("teacher.cipher_working.told.1")
            }
            return (head + clash) to read
        }
        fun place(l: Int, d: Int, c: Int): Pair<Message, Set<Int>>? {
            read += c
            if (d !in 0..9) return told(message("teacher.cipher_working.place.1", "sum_name_l_" to (sum.name(l)), "d" to (d)))
            if (d == 0 && leads(l)) return told(message("teacher.cipher_working.place.2", "sum_name_l_" to (sum.name(l))))
            val who = v.indexOf(d)
            if (who >= 0) {
                val by = if (digits[who] != EMPTY) message("teacher.cipher_working.place.3", "sum_name_who_" to (sum.name(who))) else message("teacher.cipher_working.place.4", "sum_name_who_" to (sum.name(who)))
                return told(message("teacher.cipher_working.place.5", "sum_name_l_" to (sum.name(l)), "d" to (d), "by" to (by)))
            }
            v[l] = d
            found += message("teacher.cipher_working.place.6", "sum_name_l_" to (sum.name(l)), "d" to (d))
            return null
        }
        var changed = true
        while (changed && found.size < LIMIT) {
            changed = false
            for (c in 0 until sum.width) {
                val adds = sum.columnAddends[c]
                val t = sum.columnTotal[c]
                val open = sum.columnLetters[c].filter { v[it] == EMPTY }
                val cin = cy[c]
                val cout = cy[c + 1]
                if (adds.isEmpty()) {
                    // The total's first letter is the carry into it.
                    if (cin >= 0 && v[t] == EMPTY) { place(t, cin, c)?.let { return it }; changed = true }
                    else if (cin < 0 && v[t] != EMPTY) { cy[c] = v[t]; changed = true }
                    else if (cin >= 0 && v[t] != cin) { read += c; return told(message("teacher.cipher_working.follow.1", "sum_name_t_" to (sum.name(t)), "cin" to (cin))) }
                    continue
                }
                val known = adds.filter { v[it] != EMPTY }.sumOf { v[it] }
                if (open.isEmpty()) {
                    val total = v[t]
                    if (cin >= 0) {
                        val s = known + cin
                        if (s % 10 != total || (cout >= 0 && s / 10 != cout)) {
                            read += c
                            return told(if (s % 10 != total) message("teacher.cipher_working.follow.2", "sum_place_c_" to (CipherTeacher.placeMessage(c)), "s" to (s), "total" to (total)) else message("teacher.cipher_working.follow.3", "sum_place_c_" to (CipherTeacher.placeMessage(c)), "s_10" to (s / 10)))
                        }
                        if (cout < 0) { cy[c + 1] = s / 10; changed = true }
                    } else if (cout >= 0) {
                        val need = total + 10 * cout - known
                        if (need !in 0..1) { read += c; return told(message("teacher.cipher_working.follow.4", "sum_place_c_" to (CipherTeacher.placeMessage(c)))) }
                        cy[c] = need
                        changed = true
                    }
                    continue
                }
                if (open.size != 1 || cin < 0) continue
                val l = open[0]
                val times = adds.count { it == l }
                when {
                    l == t && times == 0 -> {
                        val s = known + cin
                        if (cout >= 0 && s / 10 != cout) { read += c; return told(message("teacher.cipher_working.follow.5", "sum_place_c_" to (CipherTeacher.placeMessage(c)), "s_10" to (s / 10))) }
                        place(l, s % 10, c)?.let { return it }
                        cy[c + 1] = s / 10
                    }
                    times == 1 && l != t -> {
                        val d = if (cout >= 0) v[t] + 10 * cout - known - cin else ((v[t] - known - cin) % 10 + 10) % 10
                        place(l, d, c)?.let { return it }
                        if (cout < 0) cy[c + 1] = (known + d + cin) / 10
                    }
                    else -> continue
                }
                changed = true
            }
        }
        return null
    }

    /** Why carry [k] is [v], from the column on the [away] side of it (-1: the column it leaves, +1: the column it enters). */
    private fun explainCarry(k: Int, v: Int, away: Int, depth: Int): Carry? {
        val col = if (away < 0) k - 1 else k
        if (col < 0 || col >= sum.width) return null
        val otherK = if (away < 0) k - 1 else k + 1
        // The column's other carry left open when that is enough, else explained first.
        return listOf(Carry(listOf(0, 1)), carryAt(otherK, away, depth - 1)).distinctBy { it.values }
            .mapNotNull { explainCarry(k, v, away, col, it) }.minByOrNull { it.why.sumOf { w -> w.length } }
    }

    private fun explainCarry(k: Int, v: Int, away: Int, col: Int, other: Carry): Carry? {
        val (ins, outs) = if (away < 0) other.values to listOf(1 - v) else listOf(1 - v) to other.values
        if (ins.any { a -> outs.any { b -> fills(col, a, b, digits, strict = true).isNotEmpty() } }) return null
        val conclusion = if (v == 1) message("teacher.cipher_working.explain_carry.1", "sum_place_k_1_" to (CipherTeacher.placeMessage(k - 1))) else message("teacher.cipher_working.explain_carry.2", "sum_place_k_1_" to (CipherTeacher.placeMessage(k - 1)))
        val t = sum.columnTotal[col]
        // The total's extra first letter is the carry itself.
        if (sum.columnAddends[col].isEmpty()) {
            if (digits[t] == EMPTY) return null
            return Carry(listOf(v), other.why + message("teacher.cipher_working.explain_carry.3", "sum_name_t_" to (sum.name(t)), "digits_t_" to (digits[t]), "conclusion" to (conclusion)), other.columns + col)
        }
        val hypothesis = if (away < 0) {
            if (v == 1) message("teacher.cipher_working.explain_carry.4") else message("teacher.cipher_working.explain_carry.5")
        } else {
            if (v == 1) message("teacher.cipher_working.explain_carry.6") else message("teacher.cipher_working.explain_carry.7")
        }
        val o = other.values.singleOrNull()
        val lhs = if (away < 0) equation(col, o, null) else equation(col, null, o)
        // Everything in the column is placed: the sum itself shows the carry.
        if (sum.columnLetters[col].all { digits[it] != EMPTY }) {
            return Carry(listOf(v), other.why + message("teacher.cipher_working.explain_carry.8", "sum_place_col_" to (CipherTeacher.placeMessage(col)), "lhs" to (lhs), "conclusion" to (conclusion)), other.columns + col)
        }
        val reason = failure(col, ins, outs) ?: return null
        return Carry(listOf(v), other.why + message("teacher.cipher_working.explain_carry.9", "sum_place_col_" to (CipherTeacher.placeMessage(col)), "lhs" to (lhs), "hypothesis" to (hypothesis), "reason" to (reason), "conclusion" to (conclusion)), other.columns + col)
    }

    /** Column [c] as the player reads it; a null carry is written "carry" ([a], in) or left as "ends in" ([b], out). */
    private fun equation(c: Int, a: Int?, b: Int?, v: List<Int> = digits): Message {
        fun term(l: Int) = if (v[l] != EMPTY) v[l].toString() else sum.name(l)
        val t = sum.columnTotal[c]
        val left = left(c, a, v)
        val total = term(t)
        val right = when {
            b == null -> message("teacher.cipher_working.equation.1", "total" to (total))
            b == 0 -> message("teacher.cipher_working.equation.2", "total" to (total))
            v[t] != EMPTY -> message("teacher.cipher_working.equation.3", "value1" to (10 * b + v[t]))
            else -> message("teacher.cipher_working.equation.4", "value1" to (10 * b), "total" to (total))
        }
        return message("teacher.cipher_working.equation.extra.1", "left" to (left), "right" to (right))
    }

    /** Why column [c] cannot add up with any carry in [ins] and out [outs] and the digits placed, as a clause; null if it is not short. */
    private fun failure(c: Int, ins: List<Int>, outs: List<Int>): Message? {
        val open = sum.columnLetters[c].filter { digits[it] == EMPTY }
        val adds = sum.columnAddends[c]
        val t = sum.columnTotal[c]
        val lhs = left(c, ins.singleOrNull())
        if (open.isEmpty()) return message("teacher.cipher_working.failure.1", "lhs" to (lhs), "value2" to (listedMessage(ins.map { adds.sumOf { l -> digits[l] } + it }, "or")))
        val raw = ins.flatMap { a -> outs.flatMap { b -> fills(c, a, b, digits, strict = false) } }
        if (raw.isEmpty()) {
            val max = adds.sumOf { if (digits[it] != EMPTY) digits[it] else 9 } + ins.max()
            val min = adds.sumOf { if (digits[it] != EMPTY) digits[it] else if (leads(it)) 1 else 0 } + ins.min()
            return when {
                outs == listOf(1) && max < 10 -> message("teacher.cipher_working.failure.2", "lhs" to (lhs), "max" to (max))
                outs == listOf(0) && min > 9 -> message("teacher.cipher_working.failure.3", "lhs" to (lhs), "min" to (min))
                else -> null
            }
        }
        if (open.size == 1) {
            val y = open[0]
            val values = raw.map { it[y] }.distinct().sorted()
            val held = values.map { holder(it) }
            if (held.all { it != null }) {
                val who = held.map { sum.name(it!!) }
                return message("teacher.cipher_working.failure.4", "sum_name_y_" to (sum.name(y)), "listed_values_or_" to (listedMessage(values, "or")), "listed_who_and_" to (listedMessage(who, "and")), "value4" to (if (who.size == 1) message("teacher.cipher_working.failure.extra.1") else message("teacher.cipher_working.failure.extra.2")))
            }
            if (values == listOf(0) && leads(y)) return message("teacher.cipher_working.failure.5", "sum_name_y_" to (sum.name(y)))
            return null
        }
        // Two open letters forced equal: "I + 0 is T" with nothing carried makes T the same as I.
        if (open.size == 2 && raw.all { it[open[0]] == it[open[1]] }) {
            val (p, q) = if (open[1] == t) open[1] to open[0] else open[0] to open[1]
            return message("teacher.cipher_working.failure.6", "sum_name_p_" to (sum.name(p)), "sum_name_q_" to (sum.name(q)))
        }
        // One way only, and it needs a digit already placed: "A would be 9 and P 0, which E has".
        if (raw.size == 1) {
            val f = raw[0]
            val held = open.filter { holder(f[it]) != null }
            if (held.isNotEmpty()) {
                val first = message("teacher.cipher_working.failure.7", "sum_name_open_0_" to (sum.name(open[0])), "f_open_0_" to (f[open[0]]))
                val rest = open.drop(1).map { message("teacher.cipher_working.failure.extra.3", "sum_name_it_" to (sum.name(it)), "f_it_" to (f[it])) }
                return message("teacher.cipher_working.failure.8", "first" to (first), "value2" to (if (rest.isNotEmpty()) message("teacher.cipher_working.failure.extra.4", "value1" to (rest.joinedMessages(message("teacher.cipher_working.join_and")))) else ""), "value3" to (listedMessage(held.map { message("teacher.cipher_working.failure.extra.5", "sum_name_holder_f_it_" to (sum.name(holder(f[it])!!)), "f_it_" to (f[it])) }, "and")))
            }
        }
        // Every way to make it uses digits already placed: "E and L would have to come from 0 and 1, and I and F have those".
        val used = raw.flatMap { f -> open.map { f[it] } }.distinct().sorted()
        if (used.size <= 3 && raw.all { f -> open.any { holder(f[it]) != null } }) {
            val held = used.mapNotNull { holder(it) }.distinct()
            if (held.size == used.size) {
                return message("teacher.cipher_working.failure.9", "value1" to (listedMessage(open.map { sum.name(it) }, "and")), "listed_used_and_" to (listedMessage(used, "and")), "value3" to (listedMessage(held.map { sum.name(it) }, "and")))
            }
        }
        return null
    }

    /** The left of column [c]: its addends and carry [a] ("carry" when null). */
    private fun left(c: Int, a: Int?, v: List<Int> = digits): Message {
        fun term(l: Int) = if (v[l] != EMPTY) v[l].toString() else sum.name(l)
        val parts = sum.columnAddends[c].map(::term) + when (a) {
            null -> listOf(message("teacher.cipher_working.left.1"))
            0 -> emptyList()
            else -> listOf(message("teacher.cipher_working.left.2", "a" to (a)))
        }
        return parts.map { it.asMessage() }.joinedMessages(" + ")
    }

    /** Column [c] settles [x] at [d] once its carries are known: the sentence that shows it, or null. */
    private fun settle(c: Int, x: Int, d: Int, cin: List<Int>, cout: List<Int>): Message? {
        val vals = cin.flatMap { a -> cout.flatMap { b -> fills(c, a, b, digits, strict = true).map { it[x] } } }.distinct()
        if (vals != listOf(d)) return null
        val a = cin.singleOrNull()
        val b = cout.singleOrNull()
        val open = sum.columnLetters[c].filter { digits[it] == EMPTY && it != x }
        val adds = sum.columnAddends[c]
        val t = sum.columnTotal[c]
        val head = message("teacher.cipher_working.settle.1", "sum_place_c_" to (CipherTeacher.placeMessage(c)), "equation_c_a_b_" to (equation(c, a, b)))
        val xn = sum.name(x)
        if (adds.isEmpty()) return null
        // Plain arithmetic: everything else in the column is known.
        if (open.isEmpty() && a != null) return message("teacher.cipher_working.settle.2", "head" to (head), "xn" to (xn), "d" to (d))
        // The total, bounded: 1 + R + carry makes 10 or more and at most 11, so I is 0 or 1.
        if (x == t && x !in adds && b != null) {
            val lo = adds.sumOf { if (digits[it] != EMPTY) digits[it] else if (leads(it)) 1 else 0 } + cin.min()
            val hiTerms = adds.map { if (digits[it] != EMPTY) digits[it] else 9 } + listOf(cin.max())
            val hi = hiTerms.sum()
            val from = maxOf(0, lo - 10 * b)
            val to = minOf(9, hi - 10 * b)
            if (to - from in 0..2) {
                val range = (from..to).toList()
                val taken = range.filter { it != d }
                if (taken.all { holder(it) != null || (it == 0 && leads(x)) }) {
                    val most = hiTerms.filter { it != 0 }.joinToString(" + ")
                    val bound = if (b == 1) message("teacher.cipher_working.settle.3", "most" to (most), "hi" to (hi)) else message("teacher.cipher_working.settle.4", "most" to (most), "hi" to (hi))
                    val ruled = taken.map { e -> holder(e)?.let { message("teacher.cipher_working.settle.6", "sum_name_it_" to (sum.name(it)), "e" to (e)) } ?: message("teacher.cipher_working.settle.7", "xn" to (xn)) }.joinedMessages(message("teacher.cipher_working.settle.5"))
                    return if (taken.isEmpty()) message("teacher.cipher_working.settle.8", "head" to (head), "bound" to (bound), "xn" to (xn), "d" to (d)) else message("teacher.cipher_working.settle.9", "head" to (head), "bound" to (bound), "xn" to (xn), "listed_range_or_" to (listedMessage(range, "or")), "ruled" to (ruled), "d" to (d))
                }
            }
        }
        // One other open letter: try each digit for one of the two and see what the other would have to be.
        if (open.size == 1 && a != null) {
            val y = open[0]
            return listOf(x to y, y to x).mapNotNull { (p, q) -> tryEach(c, a, cout, x, d, p, q, head) }.minByOrNull { it.length }
        }
        // Nothing else open: whatever is carried, only a few digits make the column work, and all but one are taken.
        if (open.isEmpty()) {
            val v = digits.toMutableList().also { it[x] = EMPTY }
            val values = cin.flatMap { ai -> cout.flatMap { bo -> fills(c, ai, bo, v, strict = false).map { it[x] } } }.distinct().sorted()
            val bad = values.filter { it != d }
            if (bad.isEmpty() || values.size > 4) return null
            val ruled = bad.map { e -> holder(e)?.let { message("teacher.cipher_working.settle.10", "sum_name_it_" to (sum.name(it)), "e" to (e)) } ?: if (e == 0 && leads(x)) message("teacher.cipher_working.settle.11", "xn" to (xn)) else return null }
            val whatever = if (a == null) message("teacher.cipher_working.settle.12") else ""
            return message("teacher.cipher_working.settle.13", "head" to (head), "xn" to (xn), "listed_values_or_" to (listedMessage(values, "or")), "whatever" to (whatever), "listed_ruled_and_" to (listedMessage(ruled, "and")), "d" to (d))
        }
        return null
    }

    /**
     * Column [c] with carry [a] in: each free digit for [p] makes [q] one digit; every choice but those with [x] = [d]
     * gives [q] a digit it cannot have. "N = 5, 6, 7 or 9 would make U 0, 2, 4 or 8. A has 0, M has 2, W has 8, so N is 7."
     */
    private fun tryEach(c: Int, a: Int, cout: List<Int>, x: Int, d: Int, p: Int, q: Int, head: Message): Message? {
        val pairs = (0..9).filter { it !in digits && !(it == 0 && leads(p)) }.mapNotNull { pd ->
            val v = digits.toMutableList().also { it[p] = pd }
            val qs = cout.flatMap { b -> fills(c, a, b, v, strict = false).map { it[q] } }.distinct()
            when (qs.size) {
                0 -> null
                1 -> pd to qs[0]
                else -> return null
            }
        }
        if (pairs.size !in 2..6) return null
        val reasons = mutableListOf<Message>()
        for ((pd, qd) in pairs) {
            val xd = if (p == x) pd else qd
            val reason = when {
                qd == pd -> message("teacher.cipher_working.try_each.1", "sum_name_p_" to (sum.name(p)), "pd" to (pd), "sum_name_q_" to (sum.name(q)))
                holder(qd) != null -> message("teacher.cipher_working.try_each.2", "sum_name_holder_qd_" to (sum.name(holder(qd)!!)), "qd" to (qd))
                qd == 0 && leads(q) -> message("teacher.cipher_working.try_each.3", "sum_name_q_" to (sum.name(q)))
                else -> null
            }
            if (reason == null && xd != d) return null
            if (reason != null) reasons += reason
        }
        if (reasons.isEmpty()) return null
        return message("teacher.cipher_working.try_each.4", "head" to (head), "sum_name_p_" to (sum.name(p)), "value3" to (listedMessage(pairs.map { it.first }, "or")), "sum_name_q_" to (sum.name(q)), "value5" to (listedMessage(pairs.map { it.second }, "or")), "value6" to (reasons.distinctBy { it.english }.joinedMessages(", ").cap()), "sum_name_x_" to (sum.name(x)), "d" to (d))
    }

    /** The whole working for [x] = [d], from whichever of its columns can show it most briefly; null if none can. */
    fun explain(x: Int, d: Int): Working? =
        (0 until sum.width).filter { x in sum.columnLetters[it] }.flatMap { c ->
            // Each carry either explained or left open: the column may settle the letter whatever is carried, more briefly.
            val open = Carry(listOf(0, 1))
            val ins = listOf(carryAt(c, -1, DEPTH), open).distinctBy { it.values }
            val outs = listOf(carryAt(c + 1, +1, DEPTH), open).distinctBy { it.values }
            ins.flatMap { cin ->
                outs.mapNotNull { cout ->
                    settle(c, x, d, cin.values, cout.values)?.let {
                        Working((cin.why + cout.why + it).joinedMessages(" "), cin.columns + cout.columns + c)
                    }
                }
            }
        }.minByOrNull { it.text.length } ?: suppose(x, d)

    /** Every other digit for [x], followed through the columns until it breaks: "If N were 3, T would be 4, which A has." */
    private fun suppose(x: Int, d: Int): Working? {
        val columns = mutableSetOf<Int>()
        val cases = (0..9).filter { it != d && it !in digits && !(it == 0 && leads(x)) }.map { xd ->
            val cy = IntArray(sum.width + 1) { sum.knownCarry(digits, it) ?: -1 }
            val (trace, read) = follow(digits.toMutableList().also { it[x] = xd }, cy) ?: return null
            columns += read
            message("teacher.cipher_working.suppose.1", "sum_name_x_" to (sum.name(x)), "xd" to (xd), "trace" to (trace))
        }
        if (cases.size > 4) return null
        return Working((cases + message("teacher.cipher_working.suppose.2", "sum_name_x_" to (sum.name(x)), "d" to (d))).joinedMessages(" "), columns)
    }

    private companion object {
        const val DEPTH = 3
        const val LIMIT = 3
    }
}
