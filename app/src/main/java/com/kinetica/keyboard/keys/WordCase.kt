package com.kinetica.keyboard.keys

import java.util.Locale

/**
 * The case of a written word, as a thing that can be asked for.
 *
 * `ShiftState` decides what the next letter looks like from taps; re-casing a word already on
 * screen needs the case as a value of its own, and invertible, because the popup
 * pre-selects the case the word is in. Pure, and not on `ShiftState`, which should not read state
 * off text.
 */
enum class WordCase {
    LOWER,
    TITLE,
    UPPER,
    ;

    fun applyTo(word: String): String = applyTo(word, Locale.ROOT)

    fun applyTo(word: String, locale: Locale): String = when (this) {
        LOWER -> word.lowercase(locale)
        TITLE -> word.lowercase(locale).replaceFirstChar { it.uppercase(locale) }
        UPPER -> word.uppercase(locale)
    }

    /**
     * [applyTo] for any run of text, a selection: TITLE capitalizes every word in it, not
     * only the first letter of the run.
     */
    fun applyToText(text: String): String = applyToText(text, Locale.ROOT)

    fun applyToText(text: String, locale: Locale): String = when (this) {
        LOWER, UPPER -> applyTo(text, locale)
        TITLE -> buildString(text.length) {
            // A word starts at a letter after anything but a letter or an apostrophe, so
            // `(quoted)` is a word and `don't` stays one.
            var start = true
            for (c in text.lowercase(locale)) {
                append(if (start && c.isLetter()) c.toString().uppercase(locale) else c)
                start = !c.isLetter() && c != '\''
            }
        }
    }

    /**
     * [applyTo] for a word split at the cursor, one half at a time, so the cursor can go back
     * between them. Joined, the halves equal [applyTo] on the whole word wherever a case
     * change keeps each character's length, which is every letter but a few like `ß`.
     */
    fun applyAround(head: String, tail: String): Pair<String, String> =
        applyAround(head, tail, Locale.ROOT)

    fun applyAround(head: String, tail: String, locale: Locale): Pair<String, String> = when (this) {
        LOWER -> head.lowercase(locale) to tail.lowercase(locale)
        TITLE -> TITLE.applyTo(head, locale) to tail.lowercase(locale)
        UPPER -> head.uppercase(locale) to tail.uppercase(locale)
    }

    companion object {
        /**
         * The case [word] is written in.
         *
         * A one-letter word reads as TITLE when it is uppercase, matching the `length > 1` guard
         * `reloadWordUnderCursor` uses to tell `I` from a shouted word: a single capital is far
         * more often a sentence start than an abbreviation, and the popup would otherwise open on
         * UPPER for every `I`.
         */
        fun of(word: String): WordCase = of(word, Locale.ROOT)

        fun of(word: String, locale: Locale): WordCase {
            if (word.isEmpty()) return LOWER
            val upper = word.uppercase(locale)
            return when {
                word.length > 1 && word.indices.all { i ->
                    !word[i].isLetter() || word[i].toString() == upper[i].toString()
                } && word.any { it.isLetter() } -> UPPER
                word.firstOrNull()?.let { it.toString() != it.toString().lowercase(locale) } == true -> TITLE
                else -> LOWER
            }
        }
    }
}
