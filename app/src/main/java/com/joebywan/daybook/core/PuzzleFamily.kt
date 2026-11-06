package com.joebywan.daybook.core

/** Shared mechanics may reuse technique and tier prose; saves, seeds and records always keep the full id. */
fun puzzleFamilyId(id: String): String = when (id) {
    "words-ru", "wordwheel-ru", "mini-ru", "codeword-ru", "hexwords-ru", "dwindle-ru", "cipher-ru" -> id.removeSuffix("-ru")
    else -> id
}
