package com.joebywan.daybook.core.i18n

import kotlinx.datetime.LocalDate
import kotlinx.datetime.DayOfWeek

internal data class CalendarNames(
    val weekdays: List<String>,
    val shortWeekdays: List<String>,
    val weekdayCaptions: List<String>,
    val months: List<String>,
    val standaloneMonths: List<String>,
    val shortMonths: List<String>,
)

private fun calendarNames(languageTag: String): CalendarNames = catalogCalendars.getValue(catalogLanguage(languageTag))

/** Monday-first captions share the same generated locale metadata as date formatting. */
internal fun calendarDayCaption(day: DayOfWeek, languageTag: String): String =
    calendarNames(languageTag).weekdayCaptions[day.ordinal]

/** The web's supported date patterns; kept pure so calendar text is checked on the JVM too. */
internal fun calendarDate(date: LocalDate, pattern: String, languageTag: String): String {
    val names = calendarNames(languageTag)
    val month = date.month.ordinal
    val weekday = date.dayOfWeek.ordinal
    return pattern.split(' ').joinToString(" ") { field ->
        when (field) {
            "EEEE" -> names.weekdays[weekday]
            "EEE" -> names.shortWeekdays[weekday]
            "d" -> date.day.toString()
            "MMMM" -> names.months[month]
            "LLLL" -> names.standaloneMonths[month]
            "MMM" -> names.shortMonths[month]
            "yyyy" -> date.year.toString()
            else -> error("formatDate: unsupported field '$field' in '$pattern'")
        }
    }
}
