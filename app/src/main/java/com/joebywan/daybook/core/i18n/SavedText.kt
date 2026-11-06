package com.joebywan.daybook.core.i18n

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.core.techniqueNamesOf
import com.joebywan.daybook.platform.formatDate
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A celebration keeps what it earned across recreation, while its words follow the current language. */
@Serializable
data class SavedText(
    val key: String,
    val arguments: Map<String, SavedArgument> = emptyMap(),
    val capitalize: Boolean = false,
) {
    fun resolve(): String {
        val result = text(key, *arguments.map { (key, value) -> key to value.resolve() }.toTypedArray())
        return if (capitalize) result.replaceFirstChar { it.uppercase() } else result
    }
}

@Serializable
sealed interface SavedArgument {
    fun resolve(): Any

    @Serializable @SerialName("literal")
    data class Literal(val value: String) : SavedArgument { override fun resolve() = value }

    @Serializable @SerialName("count")
    data class Count(val value: Int) : SavedArgument { override fun resolve() = value }

    @Serializable @SerialName("message")
    data class Nested(val value: SavedText) : SavedArgument { override fun resolve() = value.resolve() }

    @Serializable @SerialName("puzzles")
    data class Puzzles(val ids: List<String>, val separator: String? = null, val sort: Boolean = true) : SavedArgument {
        override fun resolve(): String {
            val names = ids.map { PuzzleRegistry.byId(it)?.displayName ?: it }
            return (if (sort) names.sorted() else names).joinToString(separator ?: text("list.and"))
        }
    }

    @Serializable @SerialName("tiers")
    data class Tiers(val tiers: List<Difficulty>) : SavedArgument {
        override fun resolve() = tiers.joinToString(text("list.and")) { it.label }
    }

    @Serializable @SerialName("messages")
    data class Messages(val messages: List<SavedText>) : SavedArgument {
        override fun resolve() = messages.joinToString(text("list.and")) { it.resolve() }
    }

    @Serializable @SerialName("date")
    data class Date(val date: LocalDate, val pattern: String) : SavedArgument {
        override fun resolve() = formatDate(date, pattern)
    }

    @Serializable @SerialName("technique")
    data class Technique(val puzzleId: String, val id: String) : SavedArgument {
        override fun resolve() = techniqueNamesOf(puzzleId)[id] ?: id
    }
}

fun savedText(key: String, vararg arguments: Pair<String, Any>): SavedText = SavedText(key, arguments.associate { (name, value) ->
    name to when (value) {
        is String -> SavedArgument.Literal(value)
        is Int -> SavedArgument.Count(value)
        is SavedText -> SavedArgument.Nested(value)
        is SavedArgument -> value
        else -> error("Unsupported saved text argument: $name")
    }
})
