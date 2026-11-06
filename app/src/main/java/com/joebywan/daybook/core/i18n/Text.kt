package com.joebywan.daybook.core.i18n

import com.joebywan.daybook.platform.platformLanguage

data class Language(val tag: String, val nativeName: String)

/** Generated catalogs supply the supported languages; adding one never changes UI call sites. */
val SUPPORTED_LANGUAGES: List<Language> get() = catalogLanguages

internal data class CatalogEntry(val forms: Map<String, String>)

internal fun catalogLanguage(tag: String): String {
    val normalized = tag.replace('_', '-').lowercase()
    return SUPPORTED_LANGUAGES.firstOrNull { it.tag.lowercase() == normalized }?.tag
        ?: SUPPORTED_LANGUAGES.firstOrNull { it.tag == normalized.substringBefore('-') }?.tag
        ?: "en"
}

/** An untranslated message can travel through reasoning without changing its English length or content. */
class Message internal constructor(private val render: (String) -> String) : CharSequence {
    val english: String get() = render("en")
    fun resolve(languageTag: String = platformLanguage()): String = render(catalogLanguage(languageTag))
    override val length: Int get() = english.length
    override fun get(index: Int): Char = english[index]
    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = english.subSequence(startIndex, endIndex)
    override fun toString(): String = english
    operator fun plus(other: Message): Message = Message { render(it) + other.resolve(it) }
    operator fun plus(other: String): Message = Message { render(it) + other }
}

/** Only for existing caller-supplied text and fixtures; shipped prose belongs in a catalog. */
fun literalMessage(value: String): Message = Message { value }

fun message(key: String, vararg arguments: Pair<String, Any?>): Message = Message { language ->
    val entry = catalogEntries[language]?.get(key) ?: catalogEntries.getValue("en")[key]
        ?: error("Unknown translation key: $key")
    val args = arguments.toMap()
    val count = args["count"] as? Number
    require(entry.forms.size == 1 || count is Byte || count is Short || count is Int || count is Long) {
        "Plural message $key requires an integer count"
    }
    val category = count?.let { catalogPlural(language, it.toLong()) } ?: "other"
    val template = entry.forms[category] ?: entry.forms.getValue("other")
    PLACEHOLDER.replace(template) { match ->
        val name = match.groupValues[1]
        require(name in args) { "Missing translation argument '$name' for $key" }
        when (val value = args[name]) {
            is Message -> value.resolve(language)
            else -> value.toString()
        }
    }
}

fun text(key: String, vararg arguments: Pair<String, Any?>): String = message(key, *arguments).resolve()

private val PLACEHOLDER = Regex("\\{([A-Za-z][A-Za-z0-9_]*)\\}")
