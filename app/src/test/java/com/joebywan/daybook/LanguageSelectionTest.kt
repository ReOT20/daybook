package com.joebywan.daybook

import com.joebywan.daybook.core.i18n.SUPPORTED_LANGUAGES
import com.joebywan.daybook.core.i18n.catalogLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LanguageSelectionTest {
    @Test fun `Android system picker exposes exactly the shipped catalog languages`() {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val document = factory.newDocumentBuilder().parse(File("src/main/res/xml/locale_config.xml"))
        val locales = document.getElementsByTagName("locale")
        val tags = (0 until locales.length).map { locales.item(it).attributes.getNamedItemNS(
            "http://schemas.android.com/apk/res/android", "name").nodeValue }
        assertEquals(SUPPORTED_LANGUAGES.map { it.tag }, tags)
        assertTrue(File("src/main/AndroidManifest.xml").readText().contains("android:localeConfig=\"@xml/locale_config\""))
    }

    @Test fun `catalog language resolution handles regions and unrecognized settings`() {
        assertEquals("en", catalogLanguage("en-GB"))
        assertEquals("ru", catalogLanguage("ru-RU"))
        assertEquals("ru", catalogLanguage("RU_by"))
        assertEquals("en", catalogLanguage("unknown"))
        assertEquals("en", catalogLanguage(""))
    }
}
