package com.kinetica.keyboard.engine

import java.util.Locale

/**
 * Experimental suffix candidates for a user-explicitly-marked personal name.
 *
 * This helper is intentionally not connected to dictionary loading: call sites must
 * opt in for a name the user marked, not run it over the general vocabulary. It keeps
 * the written root unchanged (Turkish proper-name spelling retains p/ç/t/k in text,
 * even where pronunciation softens) and emits two front/back harmony candidates per
 * case family because the spelling of foreign names may not reflect pronunciation.
 *
 * This is a candidate enumerator, not a Turkish morphological analyzer. Context and
 * user selections would need to rank its low-frequency forms before runtime adoption.
 */
internal object TurkishPersonalNameInflector {
    private val locale: Locale = Locale.forLanguageTag("tr-TR")
    private val vowels = setOf('a', 'e', 'ı', 'i', 'o', 'ö', 'u', 'ü')
    private val backVowels = setOf('a', 'ı', 'o', 'u')
    private val roundedVowels = setOf('o', 'ö', 'u', 'ü')
    private val voiceless = setOf('ç', 'f', 'h', 'k', 'p', 's', 'ş', 't')

    /** Return at most two spelling-harmony variants for each of six common case families. */
    fun candidates(name: String): List<String> {
        require(name.isNotBlank() && name == name.trim()) { "name must be a single non-blank token" }
        require(name.all { it.isLetter() || it == '-' || it == '\'' }) {
            "name must contain letters, hyphens or an internal apostrophe only"
        }
        val lower = name.lowercase(locale)
        val lastVowel = lower.lastOrNull { it in vowels }
            ?: throw IllegalArgumentException("name needs at least one Turkish vowel for suffix candidates")
        val back = lastVowel in backVowels
        val rounded = lastVowel in roundedVowels
        val high = when {
            back && rounded -> listOf("u", "ü")
            !back && rounded -> listOf("ü", "u")
            back -> listOf("ı", "i")
            else -> listOf("i", "ı")
        }
        val low = if (back) listOf("a", "e") else listOf("e", "a")
        val lastWritten = lower.last()
        // Foreign spellings ending in y (e.g. Mary) may be pronounced with a final
        // vowel; model the buffer-consonant alternative without changing the root.
        val vowelSoundEnding = lastWritten in vowels || lastWritten == 'y'
        val locativeInitial = if (lastWritten in voiceless) "t" else "d"
        val forms = LinkedHashSet<String>()

        // Accusative -(y)I, dative -(y)A, genitive -(n)In.
        for (v in high) forms.add("$name'${if (vowelSoundEnding) "y" else ""}$v")
        for (v in low) forms.add("$name'${if (vowelSoundEnding) "y" else ""}$v")
        for (v in high) forms.add("$name'${if (vowelSoundEnding) "n" else ""}${v}n")

        // Locative -DA and ablative -DAn: the name's written final consonant is not
        // rewritten. It controls d/t and the forms retain the standard apostrophe.
        for (v in low) forms.add("$name'$locativeInitial$v")
        for (v in low) forms.add("$name'$locativeInitial${v}n")

        // Instrumental -(y)lA.
        for (v in low) forms.add("$name'${if (vowelSoundEnding) "y" else ""}l$v")
        return forms.toList()
    }
}
