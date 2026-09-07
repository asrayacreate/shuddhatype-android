package com.shuddhatype.engine

/**
 * Preeti to Unicode.
 *
 * Preeti is not an encoding — it is a *font*. A Preeti document is plain ASCII
 * whose glyphs happen to look Nepali, so `sf7df08"` is stored on disk exactly
 * like that and reads as काठमाण्डू only while the font is installed. Every
 * government office, school and press in Nepal has twenty years of paperwork
 * in it, and none of that text can be searched, spell-checked, or pasted into
 * anything modern until it is converted.
 *
 * Two passes, in this order:
 *
 *   1. **Map** each ASCII character to the Devanagari it was drawing.
 *   2. **Repair** the result. Preeti stores things in visual order, not
 *      logical: ि is typed before the consonant it hangs off, the reph र् is
 *      typed after the syllable it belongs to, and matras land the wrong side
 *      of a conjunct. Unicode wants logical order, so [RULES] moves them.
 *
 * The rules run in sequence and each one can feed the next, so their order is
 * load-bearing. Do not sort them.
 *
 * Table and rules are from the MIT-licensed `preeti2unicode` package, which
 * traces back to the FOSS Nepal community's converters. MIT matters here: the
 * better-known implementations are CC BY-NC-SA, and the NC clause would forbid
 * shipping this in anything sold.
 */
object Preeti {

    /**
     * Anything with no mapping — spaces, digits already in Latin, English
     * words, punctuation Preeti never claimed — passes through untouched, so
     * mixed documents survive.
     */
    fun toUnicode(text: String): String {
        if (text.isEmpty()) return text
        val sb = StringBuilder(text.length * 2)
        for (c in text) {
            val mapped = MAP[c]
            if (mapped != null) sb.append(mapped) else sb.append(c)
        }
        var out = sb.toString()
        for ((pattern, replacement) in RULES) out = pattern.replace(out, replacement)
        return out
    }

    /** True if converting would change anything — used to keep a button honest. */
    fun looksLikePreeti(text: String): Boolean =
        text.any { MAP.containsKey(it) }

    /** One Preeti keystroke to the Devanagari its glyph drew. */
    private val MAP: Map<Char, String> = mapOf(
        '!' to "१", '"' to "ू", '#' to "३", '$' to "४",
        '%' to "५", '&' to "७", '\'' to "ु", '(' to "९",
        ')' to "०", '*' to "८", '+' to "ं", ',' to ",",
        '-' to "-", '.' to "।", '/' to "र", '0' to "ण्",
        '1' to "ज्ञ", '2' to "द्द", '3' to "घ", '4' to "द्ध",
        '5' to "छ", '6' to "ट", '7' to "ठ", '8' to "ड",
        '9' to "ढ", ':' to "स्", ';' to "स", '<' to "?",
        '=' to ".", '>' to "श्र", '?' to "रु", '@' to "२",
        'A' to "ब्", 'B' to "द्य", 'C' to "ऋ", 'D' to "म्",
        'E' to "भ्", 'F' to "ँ", 'G' to "न्", 'H' to "ज्",
        'I' to "क्ष्", 'J' to "व्", 'K' to "प्", 'L' to "ी",
        'M' to "ः", 'N' to "ल्", 'O' to "इ", 'P' to "ए",
        'Q' to "त्त", 'R' to "च्", 'S' to "क्", 'T' to "त्",
        'U' to "ग्", 'V' to "ख्", 'W' to "ध्", 'X' to "ह्",
        'Y' to "थ्", 'Z' to "श्", '[' to "ृ", '\\' to "्",
        ']' to "े", '^' to "६", '_' to "-", '`' to "ञ",
        'a' to "ब", 'b' to "द", 'c' to "अ", 'd' to "म",
        'e' to "भ", 'f' to "ा", 'g' to "न", 'h' to "ज",
        'i' to "ष्", 'j' to "व", 'k' to "प", 'l' to "ि",
        'n' to "ल", 'o' to "य", 'p' to "उ", 'q' to "त्र",
        'r' to "च", 's' to "क", 't' to "त", 'u' to "ग",
        'v' to "ख", 'w' to "ध", 'x' to "ह", 'y' to "थ",
        'z' to "श", '|' to "्र", '}' to "ै", '~' to "ञ्",
        '¡' to "ज्ञ्", '¢' to "द्घ", '£' to "घ्", '¤' to "झ्",
        '¥' to "र्‍", '§' to "ट्ट", '©' to "र", 'ª' to "ङ",
        '«' to "्र", '°' to "ङ्ढ", '±' to "+", '´' to "झ",
        '¶' to "ठ्ठ", '¿' to "रू", 'Å' to "हृ", 'Æ' to "”",
        'Ë' to "ङ्ग", 'Ì' to "न्न", 'Í' to "ङ्क", 'Î' to "ङ्ख",
        'Ò' to "¨", 'Ö' to "=", '×' to "×", 'Ø' to "्य",
        'Ù' to ";", 'Ú' to "’", 'Û' to "!", 'Ü' to "%",
        'Ý' to "ट्ठ", 'ß' to "द्म", 'å' to "द्व", 'æ' to "“",
        'ç' to "ॐ", '÷' to "/", 'ˆ' to "फ्", '˜' to "ऽ",
        '‘' to "ॅ", '„' to "ध्र", '•' to "ड्ड", '…' to "‘",
        '‰' to "झ्", '‹' to "ङ्घ", '›' to "द्र"
    )

    /**
     * Visual order to logical order. Sequence matters: several rules exist only
     * to clean up what an earlier one produced.
     */
    private val RULES: List<Pair<Regex, String>> = listOf(
        // Not from the source package — it ships this rule commented out, which
        // is a bug: without it lgdf{0f converts to िनर्माण instead of निर्माण.
        // Preeti types ि before the consonant it hangs off, because that is
        // where it is drawn; Unicode wants it after. It runs first so every
        // later rule sees matras already in logical order.
        "ि((.्)*[^्])" to "\$1ि",
        "्ा" to "",
        "(त्र|त्त)([^उभप]+?)m" to "\$1m\$2",
        "त्रm" to "क्र",
        "त्तm" to "क्त",
        "उm" to "ऊ",
        "भm" to "झ",
        "पm" to "फ",
        "इ{" to "ई",
        "(.[ािीुूृेैोौंःँ]*?){" to "{\$1",
        "((.्)*){" to "{\$1",
        "{" to "र्",
        "([ाीुूृेैोौंःँ]+?)(्(.्)*[^्])" to "\$2\$1",
        "्([ाीुूृेैोौंःँ]+?)((.्)*[^्])" to "्\$2\$1",
        "([ंँ])([ािीुूृेैोौः]*)" to "\$2\$1",
        "ँँ" to "ँ",
        "ंं" to "ं",
        "ेे" to "े",
        "ैै" to "ै",
        "ुु" to "ु",
        "ूू" to "ू",
        "^ः" to ":",
        "टृ" to "ट्ट",
        "ेा" to "ाे",
        "ैा" to "ाै",
        "अाे" to "ओ",
        "अाै" to "औ",
        "अा" to "आ",
        "एे" to "ऐ",
        "ाे" to "ो",
        "ाै" to "ौ"
    ).map { Regex(it.first) to it.second }
}
