package com.joebywan.daybook.core.i18n

import com.joebywan.daybook.platform.platformLanguage
import kotlin.math.abs

/** Presentation only: language must never enter a puzzle's seed, saved state or identifiers. */
fun tr(english: String, russian: String): String = localized(english, russian, platformLanguage())

internal fun localized(english: String, russian: String, languageTag: String): String =
    if (isRussianLanguage(languageTag)) russian else english

fun isRussianLanguage(languageTag: String): Boolean =
    languageTag.substringBefore('-').substringBefore('_').equals("ru", ignoreCase = true)

/** The noun after a count: 1 день, 2 дня, 5 дней, 11 дней, 21 день. */
fun russianPlural(count: Int, one: String, few: String, many: String): String {
    val lastHundred = abs(count % 100)
    return when {
        lastHundred in 11..14 -> many
        lastHundred % 10 == 1 -> one
        lastHundred % 10 in 2..4 -> few
        else -> many
    }
}
