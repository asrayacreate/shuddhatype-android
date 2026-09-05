package com.shuddhatype.engine

/**
 * Amounts in Nepali words, and Nepali digit grouping.
 *
 * Every quotation, bill and cheque in the country carries the figure twice —
 * "रु. ५,४५,०००" and then "अक्षरेपी पाँच लाख पैँतालिस हजार रुपैयाँ मात्र" — and
 * the second one is written out by hand, slowly, every single time. That is the
 * job this file removes.
 *
 * Two things make it more than a lookup table:
 *
 *   1. Nepali has a distinct word for every number from 1 to 99. There is no
 *      rule that builds पैँतालिस out of पाँच and चालिस; it has to be listed.
 *   2. The grouping is 2-2-3 (लाख, हजार, सय), not the Western 3-3-3, so both
 *      the words and the commas break in places a generic formatter gets wrong.
 */
object NepaliNumber {

    /** Anything longer is a phone number or an account number, not an amount. */
    const val MAX_DIGITS = 9

    /**
     * [digits] is plain Latin digits. Returns null when the input is not an
     * amount worth spelling out — empty, too long, or leading-zero padded like
     * an account number.
     */
    fun toWords(digits: String): String? {
        if (digits.isEmpty() || digits.length > MAX_DIGITS) return null
        if (digits.length > 1 && digits[0] == '0') return null
        val n = digits.toLongOrNull() ?: return null
        if (n == 0L) return UNITS[0]

        val sb = StringBuilder()
        var rest = n
        for ((value, name) in SCALES) {
            val count = (rest / value).toInt()
            if (count > 0) {
                if (count > 99) return null
                sb.append(UNITS[count]).append(' ').append(name).append(' ')
                rest %= value
            }
        }
        if (rest > 0) sb.append(UNITS[rest.toInt()])
        return sb.toString().trim()
    }

    /**
     * Devanagari digits with Nepali grouping: 545000 -> ५,४५,०००.
     * The first comma falls after three digits, every one after that after two.
     */
    fun format(digits: String): String? {
        if (digits.isEmpty() || digits.length > MAX_DIGITS) return null
        if (digits.length > 1 && digits[0] == '0') return null

        val grouped = StringBuilder()
        val last3 = digits.takeLast(3)
        var head = digits.dropLast(3)
        grouped.append(last3)
        while (head.isNotEmpty()) {
            val pair = head.takeLast(2)
            head = head.dropLast(2)
            grouped.insert(0, "$pair,")
        }
        return grouped.map { if (it in '0'..'9') DEVA[it - '0'] else it }.joinToString("")
    }

    private val DEVA = charArrayOf('०', '१', '२', '३', '४', '५', '६', '७', '८', '९')

    private val SCALES = listOf(
        100_000_000_000L to "खर्ब",
        1_000_000_000L to "अर्ब",
        10_000_000L to "करोड",
        100_000L to "लाख",
        1_000L to "हजार",
        100L to "सय"
    )

    // 0-99 in full. Nepali builds none of these from parts — पैँतालिस is not
    // पाँच plus चालिस in any regular way — so the table is the algorithm.
    private val UNITS = arrayOf(
        "शून्य", "एक", "दुई", "तीन", "चार", "पाँच", "छ", "सात", "आठ", "नौ",
        "दश", "एघार", "बाह्र", "तेह्र", "चौध", "पन्ध्र", "सोह्र", "सत्र", "अठार", "उन्नाइस",
        "बीस", "एक्काइस", "बाइस", "तेइस", "चौबिस", "पच्चिस", "छब्बिस", "सत्ताइस", "अठ्ठाइस", "उनन्तिस",
        "तीस", "एकतिस", "बत्तिस", "तेत्तिस", "चौँतिस", "पैँतिस", "छत्तिस", "सैँतिस", "अठतिस", "उनन्चालिस",
        "चालिस", "एकचालिस", "बयालिस", "त्रिचालिस", "चवालिस", "पैँतालिस", "छयालिस", "सतचालिस", "अठचालिस", "उनन्चास",
        "पचास", "एकाउन्न", "बाउन्न", "त्रिपन्न", "चवन्न", "पचपन्न", "छपन्न", "सन्ताउन्न", "अन्ठाउन्न", "उनन्साठी",
        "साठी", "एकसट्ठी", "बयसट्ठी", "त्रिसट्ठी", "चौंसट्ठी", "पैंसट्ठी", "छयसट्ठी", "सतसट्ठी", "अठसट्ठी", "उनन्सत्तरी",
        "सत्तरी", "एकहत्तर", "बहत्तर", "त्रिहत्तर", "चौहत्तर", "पचहत्तर", "छयहत्तर", "सतहत्तर", "अठहत्तर", "उनासी",
        "असी", "एकासी", "बयासी", "त्रियासी", "चौरासी", "पचासी", "छयासी", "सतासी", "अठासी", "उनान्नब्बे",
        "नब्बे", "एकानब्बे", "बयानब्बे", "त्रियानब्बे", "चौरानब्बे", "पन्चानब्बे", "छयानब्बे", "सन्तानब्बे", "अन्ठानब्बे", "उनान्सय"
    )
}
