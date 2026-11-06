package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.cap
import com.joebywan.daybook.core.i18n.Message
import com.joebywan.daybook.core.i18n.literalMessage
import com.joebywan.daybook.core.i18n.message

/** Display vocabulary only. Canonical words and saved item indices remain in [WhosWhoWords]. */
internal object WhosWhoText {
    private val categories = listOf("house", "colour", "pet", "job", "drink", "music")

    fun name(s: WhosWhoState, person: Int): Message = personName(s.names[person])

    fun personName(name: String): Message {
        val index = WhosWhoWords.NAMES.indexOf(name)
        return if (index < 0) literalMessage(name) else message("whoswho.name.$index")
    }

    fun header(category: Int): Message = message("whoswho.header.${categories[category]}")
    fun noun(category: Int): Message = message("whoswho.noun.${categories[category]}")

    private fun category(s: WhosWhoState, ref: Int): Int =
        if (ref / s.n == 0) WhosWhoWords.NAME else s.groups[ref / s.n - 1]

    private fun term(category: Int, word: String, form: String = "name"): Message =
        if (category == WhosWhoWords.NAME) personName(word)
        else message("whoswho.word.${categories[category]}.${WhosWhoWords.WORDS[category].indexOf(word)}.$form")

    fun word(s: WhosWhoState, ref: Int): Message = term(category(s, ref), WhosWhoLogic.word(s, ref))
    fun word(s: WhosWhoState, column: Int, slot: Int): Message = word(s, (column + 1) * s.n + slot)

    private fun heldWord(category: Int, word: String): Message = term(category, word, when (category) {
        WhosWhoWords.COLOUR, WhosWhoWords.MUSIC -> "in"
        WhosWhoWords.PET, WhosWhoWords.DRINK -> "object"
        WhosWhoWords.JOB -> "with"
        else -> "name"
    })

    private fun phraseKey(function: String, category: Int) =
        "whoswho.$function.${if (category == WhosWhoWords.NAME) "name" else categories[category]}"

    fun who(s: WhosWhoState, ref: Int): Message {
        val category = category(s, ref)
        val word = WhosWhoLogic.word(s, ref)
        if (category == WhosWhoWords.MUSIC) return message("whoswho.player.${WhosWhoWords.WORDS[category].indexOf(word)}")
        val form = when (category) {
            WhosWhoWords.COLOUR, WhosWhoWords.PET -> "of"
            WhosWhoWords.DRINK -> "object"
            else -> "name"
        }
        return message(phraseKey("who", category), "word" to term(category, word, form))
    }

    fun has(s: WhosWhoState, ref: Int): Message = relation("has", s, ref)
    fun hasNot(s: WhosWhoState, ref: Int): Message = relation("has_not", s, ref)
    fun base(s: WhosWhoState, ref: Int): Message = relation("base", s, ref)

    private fun relation(function: String, s: WhosWhoState, ref: Int): Message {
        val category = category(s, ref)
        return message(phraseKey(function, category), "word" to heldWord(category, WhosWhoLogic.word(s, ref)))
    }

    private fun either(s: WhosWhoState, a: Int, b: Int): Message {
        val category = category(s, a)
        return message(phraseKey("either", category),
            "a" to heldWord(category, WhosWhoLogic.word(s, a)), "b" to heldWord(category, WhosWhoLogic.word(s, b)))
    }

    fun thing(s: WhosWhoState, ref: Int): Message {
        val category = category(s, ref)
        val word = word(s, ref)
        return when (category) {
            WhosWhoWords.NAME, WhosWhoWords.DRINK -> word
            WhosWhoWords.HOUSE -> message("whoswho.thing.house", "word" to word)
            WhosWhoWords.COLOUR -> message("whoswho.thing.colour", "word" to word)
            else -> message("whoswho.thing.other", "word" to word)
        }
    }

    private fun near(s: WhosWhoState, ref: Int): Message =
        if (category(s, ref) == WhosWhoWords.COLOUR) thing(s, ref) else who(s, ref)

    fun clueText(s: WhosWhoState, k: Int): Message {
        val clue = s.clues[k]
        val rank = { ref: Int -> WhosWhoWords.rank(category(s, ref)) }
        val colour = { ref: Int -> category(s, ref) == WhosWhoWords.COLOUR }
        val houseNo = { ref: Int -> category(s, ref) == WhosWhoWords.HOUSE }
        val (x, y) = if (rank(clue.b) < rank(clue.a) && clue.kind != WhosWhoLogic.LEFT) clue.b to clue.a else clue.a to clue.b
        return when (clue.kind) {
            WhosWhoLogic.SAME, WhosWhoLogic.NOT -> if (colour(x) && houseNo(y)) {
                message("whoswho.clue.colour_house", "colour" to word(s, x), "house" to word(s, y),
                    "negative" to if (clue.kind == WhosWhoLogic.NOT) message("whoswho.clue.negative") else literalMessage(""))
            } else {
                message("whoswho.clue.statement", "subject" to who(s, x).cap(),
                    "relation" to if (clue.kind == WhosWhoLogic.SAME) has(s, y) else hasNot(s, y))
            }
            WhosWhoLogic.NEXT -> if (colour(x)) {
                message("whoswho.clue.next_colour", "colour" to word(s, x), "near" to near(s, y))
            } else message("whoswho.clue.next", "subject" to who(s, x).cap(), "near" to near(s, y))
            WhosWhoLogic.LEFT -> {
                val flip = rank(clue.b) < rank(clue.a)
                val first = if (flip) clue.b else clue.a
                val second = if (flip) clue.a else clue.b
                val side = message(if (flip) "whoswho.clue.right_side" else "whoswho.clue.left_side")
                if (colour(first)) message("whoswho.clue.left_colour", "colour" to word(s, first), "side" to side, "near" to near(s, second))
                else message("whoswho.clue.left", "subject" to who(s, first).cap(), "side" to side, "near" to near(s, second))
            }
            else -> message("whoswho.clue.statement", "subject" to who(s, clue.a).cap(), "relation" to either(s, clue.b, clue.c))
        }
    }
}
