package com.shuddhatype.engine

/**
 * The same verb at another level of respect.
 *
 * Nepali marks respect in the verb, not just the pronoun, and getting it wrong
 * is the one mistake that actually costs something: writing तिमीले गर्‍यौ to a
 * client reads as rude, and गर्नुभयो to a younger brother reads as sarcastic.
 * No Roman-input keyboard can help with this, because the writer has already
 * typed the level into the Roman — `garyau` can only come out गर्‍यौ. The fix
 * has to be an offer *after* the word is recognised, which is what this is.
 *
 * **Three levels, not four, and no बक्सनु.** The plan said तिमी → तपाईं →
 * हजुर, but हजुर is a *pronoun*; its verbs are the तपाईं ones — हजुरले
 * गर्नुभयो, not गरिबक्सियो. बक्सनु is royal-court Nepali that nobody writes in
 * a message, and offering it would make the feature look like a toy. The three
 * levels people actually write are तँ, तिमी and तपाईं.
 *
 * Every suggestion is checked against the lexicon before it is offered, so a
 * stem the rules mangle simply produces nothing rather than a made-up word.
 * That check is also what keeps a non-verb ending in यौ from being conjugated.
 */
object Respect {

    /** What the three columns below mean, low to high. */
    val LEVELS = listOf("तँ", "तिमी", "तपाईं")

    /**
     * One tense, at all three levels. [cons] is for stems from a consonant
     * root (गर् -> गर), [vowel] for stems ending in a vowel (खा, जा).
     */
    private class Slot(
        val cons: Array<String>,
        val vowel: Array<String>,
        val caus: Array<String>
    ) {
        val all get() = listOf(cons, vowel, caus)
    }

    /**
     * Only tenses where a real second-person contrast exists. The future was
     * dropped on purpose: `VerbForms` does not generate गर्नेछौ or गर्नुहुनेछ,
     * so nothing there would survive the lexicon check anyway, and a slot that
     * can never fire is a slot that only misleads whoever reads this next.
     */
    private val SLOTS = listOf(
        // past — गरिस् · गर्‍यौ · गर्नुभयो
        Slot(arrayOf("िस्", "्यौ", "्नुभयो"), arrayOf("इस्", "यौ", "नुभयो"),
            arrayOf("इस्", "यौ", "उनुभयो")),
        // present — गर्छस् · गर्छौ · गर्नुहुन्छ
        Slot(arrayOf("्छस्", "्छौ", "्नुहुन्छ"), arrayOf("न्छस्", "न्छौ", "नुहुन्छ"),
            arrayOf("उँछस्", "उँछौ", "उनुहुन्छ")),
        // present negative — गर्दैनस् · गर्दैनौ · गर्नुहुन्न
        Slot(arrayOf("्दैनस्", "्दैनौ", "्नुहुन्न"), arrayOf("ँदैनस्", "ँदैनौ", "नुहुन्न"),
            arrayOf("उँदैनस्", "उँदैनौ", "उनुहुन्न")),
        // past negative — गरिनस् · गरेनौ · गर्नुभएन
        Slot(arrayOf("िनस्", "ेनौ", "्नुभएन"), arrayOf("इनस्", "एनौ", "नुभएन"),
            arrayOf("इनस्", "एनौ", "उनुभएन")),
        // past habitual — गर्थिस् · गर्थ्यौ · गर्नुहुन्थ्यो
        Slot(arrayOf("्थिस्", "्थ्यौ", "्नुहुन्थ्यो"), arrayOf("न्थिस्", "न्थ्यौ", "नुहुन्थ्यो"),
            arrayOf("उँथिस्", "उँथ्यौ", "उनुहुन्थ्यो")),
        // imperative — गर् · गर · गर्नुहोस्
        Slot(arrayOf("", "", "्नुहोस्"), arrayOf("", "", "नुहोस्"),
            arrayOf("", "", "उनुहोस्"))
    )

    private class Ending(val text: String, val slot: Int, val level: Int)

    /**
     * Every ending worth matching on, longest first, so गर्‍यौ is read as the
     * consonant `्यौ` rather than the vowel `यौ` with a stem of गर्.
     *
     * Endings shorter than two characters are matchable only as *output* — the
     * imperative at the two lower levels is the bare stem, and matching on an
     * empty ending would turn every noun in the language into a verb.
     */
    private val ENDINGS: List<Ending> = ArrayList<Ending>().apply {
        SLOTS.forEachIndexed { i, s ->
            for (table in s.all) {
                table.forEachIndexed { lv, e ->
                    if (e.length >= 2 && none { it.text == e && it.slot == i && it.level == lv }) {
                        add(Ending(e, i, lv))
                    }
                }
            }
        }
    }.sortedByDescending { it.text.length }

    /**
     * The verbs the rules cannot reach. हुनु and जानु are suppletive — भयौ has
     * no stem in common with हुनुभयो — and they are also the two verbs most
     * likely to be typed, so leaving them to the rules would mean the feature
     * failing on its most common case.
     */
    private val IRREGULAR: List<List<String>> = listOf(
        listOf("भइस्", "भयौ", "हुनुभयो"),
        listOf("थिइस्", "थियौ", "हुनुहुन्थ्यो"),
        listOf("छस्", "छौ", "हुनुहुन्छ"),
        listOf("होस्", "हौ", "हुनुहोस्"),
        listOf("गइस्", "गयौ", "जानुभयो"),
        listOf("जान्छस्", "जान्छौ", "जानुहुन्छ"),
        listOf("जा", "जाऊ", "जानुहोस्"),
        listOf("आइस्", "आयौ", "आउनुभयो"),
        listOf("आउँछस्", "आउँछौ", "आउनुहुन्छ"),
        listOf("आइज", "आऊ", "आउनुहोस्"),
        listOf("दिइस्", "दियौ", "दिनुभयो"),
        listOf("देस्", "देऊ", "दिनुहोस्"),
        listOf("लिइस्", "लियौ", "लिनुभयो"),
        listOf("ले", "लेऊ", "लिनुहोस्"),
        listOf("खाइस्", "खायौ", "खानुभयो"),
        listOf("खा", "खाऊ", "खानुहोस्")
    )

    /**
     * The other respect levels of [word], or an empty list when it is not a
     * second-person verb form. [known] decides whether a produced form is a
     * real word — pass the lexicon.
     */
    fun variants(word: String, known: (String) -> Boolean): List<String> {
        val w = word.trim()
        if (w.length < 2) return emptyList()

        for (row in IRREGULAR) {
            val at = row.indexOf(w)
            if (at >= 0) return row.filterIndexed { i, _ -> i != at }
        }

        for (e in ENDINGS) {
            if (!w.endsWith(e.text)) continue
            val stem = w.dropLast(e.text.length)
            // A one-character stem is almost always a false match: भयौ is not
            // भ + यौ, it is हुनु, which the table above already handles.
            if (stem.length < 2) continue

            // Try all three ending tables and let the lexicon decide. पठायौ
            // and खायौ have the same shape but conjugate differently —
            // पठाउनुभयो against खानुभयो — and the only thing that can tell
            // them apart is which of the two is a real word.
            val out = ArrayList<String>(2)
            for (table in SLOTS[e.slot].all) {
                table.forEachIndexed { lv, suffix ->
                    if (lv == e.level) return@forEachIndexed
                    val form = stem + suffix
                    if (form != w && !out.contains(form) && known(form)) out.add(form)
                }
            }
            if (out.isNotEmpty()) return out
        }
        return emptyList()
    }
}
