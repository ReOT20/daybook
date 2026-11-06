package com.joebywan.daybook

import com.joebywan.daybook.core.PuzzleRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PuzzleLanguageMenuTest {
    private val words = listOf("words", "wordwheel", "mini", "codeword", "hexwords", "dwindle", "cipher")
    private val english = PuzzleRegistry.all.map { it.id }.filterNot { it in words.map { word -> "$word-ru" } }
    private val russian = english.map { if (it in words) "$it-ru" else it }

    @Test fun `menus show one matching edition in each original family position`() {
        for ((tags, expected) in listOf(
            listOf("en", "en-GB", "unknown", "") to english,
            listOf("ru", "ru-RU", "RU_by") to russian,
        )) for (tag in tags) {
            val actual = PuzzleRegistry.forLanguage(tag).map { it.id }
            assertEquals(tag, expected, actual)
            assertEquals(tag, 33, actual.size)
            assertEquals(tag, actual.size, actual.distinct().size)
        }
    }

    @Test fun `all seven word pairs localize but neutral and unknown ids stay unchanged`() {
        for (word in words) for (id in listOf(word, "$word-ru")) {
            assertEquals(word, PuzzleRegistry.localizedId(id, "en-GB"))
            assertEquals("$word-ru", PuzzleRegistry.localizedId(id, "ru-RU"))
            assertEquals(word, PuzzleRegistry.localizedId(id, "unsupported"))
        }
        for (id in english - words.toSet() + "unknown-ru") for (tag in listOf("en", "ru")) {
            assertEquals(id, PuzzleRegistry.localizedId(id, tag))
        }
    }

    @Test fun `switching menus retains lookup for every hidden saved edition`() {
        val all = PuzzleRegistry.all.toList()
        for (tag in listOf("ru", "en", "ru-RU", "unsupported")) {
            PuzzleRegistry.forLanguage(tag)
            assertEquals(40, PuzzleRegistry.all.size)
            assertEquals(all, PuzzleRegistry.all)
            all.forEach { assertSame(it.id, it, PuzzleRegistry.byId(it.id)) }
        }
    }
}
