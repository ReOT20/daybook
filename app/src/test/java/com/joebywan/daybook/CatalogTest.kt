package com.joebywan.daybook

import com.joebywan.daybook.core.i18n.SUPPORTED_LANGUAGES
import com.joebywan.daybook.core.i18n.catalogPlural
import com.joebywan.daybook.core.i18n.message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CatalogTest {
    @Test fun `regional tags and unsupported languages resolve consistently`() {
        val greeting = message("tutorial.done")
        assertEquals("That's it.", greeting.english)
        assertEquals("Верно.", greeting.resolve("RU-ru"))
        assertEquals("Верно.", greeting.resolve("ru_BY"))
        assertEquals("That's it.", greeting.resolve("fr-FR"))
        assertEquals(listOf("en", "ru"), SUPPORTED_LANGUAGES.map { it.tag })
    }

    @Test fun `CLDR selects complete Russian noun phrases`() {
        for (n in listOf(1, 21, 101, -21))
            assertEquals("день подряд", message("home.streak.caption", "count" to n).resolve("ru"))
        for (n in listOf(2, 3, 4, 22, 104))
            assertEquals("дня подряд", message("home.streak.caption", "count" to n).resolve("ru"))
        for (n in listOf(0, 5, 11, 12, 14, 25, 111))
            assertEquals("дней подряд", message("home.streak.caption", "count" to n).resolve("ru"))
        assertEquals("many", catalogPlural("ru", Long.MIN_VALUE))
        assertEquals("other", catalogPlural("en", Long.MIN_VALUE))
    }

    @Test fun `unresolved messages keep canonical English for reasoning`() {
        val result = message("tutorial.retry")
        assertEquals("Try the glowing square.", result.toString())
        assertEquals(result.english.length, result.length)
        assertEquals("Попробуйте подсвеченную клетку.", result.resolve("ru"))
    }

    @Test fun `unknown message keys fail instead of hiding missing extraction`() {
        assertThrows(IllegalStateException::class.java) { message("missing.key").resolve("en") }
        assertThrows(IllegalArgumentException::class.java) { message("home.streak.caption").resolve("ru") }
        assertThrows(IllegalArgumentException::class.java) { message("home.streak.caption", "count" to 1.5).resolve("ru") }
    }
}
