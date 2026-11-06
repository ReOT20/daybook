package com.joebywan.daybook.core

import com.joebywan.daybook.core.i18n.Message
import com.joebywan.daybook.core.i18n.literalMessage

// Small pieces of English the teachers share. Free of java.*: the web compiles this file.

/** The first letter in capitals. */
fun String.cap() = replaceFirstChar { it.uppercaseChar() }

/** "1", "1 and 2", "1, 2 and 4" ([and] can be "or"; [oxford] gives "1, 2, and 4"). Empty is "". */
fun <T> listed(items: List<T>, and: String = "and", oxford: Boolean = false): String = when (items.size) {
    0 -> ""
    1 -> "${items[0]}"
    else -> items.dropLast(1).joinToString(", ") + (if (oxford) ", " else " ") + and + " " + items.last()
}

private val NUMBER_WORDS =
    listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve")

/** [k] in words up to [upTo] (at most twelve), digits above it; [zero] is "zero" or "no". */
fun numberWord(k: Int, zero: String = "zero", upTo: Int = 12): String = when {
    k == 0 -> zero
    k in 1..upTo -> NUMBER_WORDS[k]
    else -> k.toString()
}

/** "1 tent", "3 tents": digits, for the counts a player reads at a glance. */
fun plural(n: Int, one: String, many: String = one + "s") = "$n ${if (n == 1) one else many}"

/** Line [line] of an [n] by [n] board: rows first, then columns. */
fun lineName(n: Int, line: Int) = if (line < n) "row ${line + 1}" else "column ${line - n + 1}"

/** Square [i] of a board [width] squares wide, as "row 2, column 3". */
fun squareName(width: Int, i: Int) = "row ${i / width + 1}, column ${i % width + 1}"

/** The first of [options] no longer than [max] characters, else the last (the shortest a teacher could write). */
fun fit(options: List<String>, max: Int): String = options.firstOrNull { it.length <= max } ?: options.last()

/** Preserve a catalog descriptor when bridging legacy caller-supplied strings. */
fun CharSequence.asMessage(): Message = this as? Message ?: literalMessage(toString())

/** Apply typography to both the canonical reasoning text and its translated display. */
fun Message.cap(): Message = Message { language -> resolve(language).replaceFirstChar { it.uppercaseChar() } }

/** Presentation equivalent of [listed], retaining any nested message descriptors. */
fun <T> listedMessage(items: List<T>, and: CharSequence = com.joebywan.daybook.core.i18n.message("teaching.list.and"), oxford: Boolean = false): Message = Message { language ->
    val conjunction = if (and is Message) and.resolve(language) else com.joebywan.daybook.core.i18n.message("teaching.list.$and").resolve(language)
    listed(items.map { if (it is Message) it.resolve(language) else it }, conjunction, oxford)
}

fun lineNameMessage(n: Int, line: Int): Message = com.joebywan.daybook.core.i18n.message(
    if (line < n) "teaching.row" else "teaching.column", "count" to (if (line < n) line + 1 else line - n + 1),
)

fun squareNameMessage(width: Int, i: Int): Message = com.joebywan.daybook.core.i18n.message(
    "teaching.square", "row" to (i / width + 1), "column" to (i % width + 1),
)

/** Select by canonical English length, so a locale cannot change the teacher's chosen move. */
fun fitMessage(options: List<Message>, max: Int): Message = options.firstOrNull { it.length <= max } ?: options.last()

/** Join message fragments without flattening them into canonical English. */
fun Iterable<Message>.joinedMessages(separator: CharSequence = ", "): Message = Message { language ->
    joinToString(if (separator is Message) separator.resolve(language) else separator.toString()) { it.resolve(language) }
}

fun Message.substringBefore(delimiter: Char): Message = Message { language -> resolve(language).substringBefore(delimiter) }

fun boxPositionMessage(index: Int): Message = com.joebywan.daybook.core.i18n.message("teaching.box.$index")

fun numberWordMessage(k: Int, zero: CharSequence = "zero", upTo: Int = 12): Message = when {
    k == 0 -> if (zero is Message) zero else if (zero == "no") com.joebywan.daybook.core.i18n.message("teaching.number.no")
        else com.joebywan.daybook.core.i18n.message("teaching.number.0")
    k in 1..upTo -> com.joebywan.daybook.core.i18n.message("teaching.number.$k")
    else -> literalMessage(k.toString())
}
