package com.joebywan.daybook.core.i18n

import kotlinx.datetime.LocalDate

private val englishDays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
private val englishMonths = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)
private val russianDays = listOf("понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье")
private val russianShortDays = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")
private val russianMonths = listOf(
    "января", "февраля", "марта", "апреля", "мая", "июня",
    "июля", "августа", "сентября", "октября", "ноября", "декабря",
)
private val russianStandaloneMonths = listOf(
    "январь", "февраль", "март", "апрель", "май", "июнь",
    "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь",
)
private val russianShortMonths = listOf("янв.", "февр.", "мар.", "апр.", "мая", "июн.", "июл.", "авг.", "сент.", "окт.", "нояб.", "дек.")

/** The web's supported date patterns; kept pure so calendar text is checked on the JVM too. */
internal fun calendarDate(date: LocalDate, pattern: String, languageTag: String): String {
    val russian = isRussianLanguage(languageTag)
    val month = date.month.ordinal
    val weekday = date.dayOfWeek.ordinal
    return pattern.split(' ').joinToString(" ") { field ->
        when (field) {
            "EEEE" -> if (russian) russianDays[weekday] else englishDays[weekday]
            "EEE" -> if (russian) russianShortDays[weekday] else englishDays[weekday].take(3)
            "d" -> date.day.toString()
            "MMMM" -> if (russian) russianMonths[month] else englishMonths[month]
            "LLLL" -> if (russian) russianStandaloneMonths[month] else englishMonths[month]
            "MMM" -> if (russian) russianShortMonths[month] else englishMonths[month].take(3)
            "yyyy" -> date.year.toString()
            else -> error("formatDate: unsupported field '$field' in '$pattern'")
        }
    }
}
