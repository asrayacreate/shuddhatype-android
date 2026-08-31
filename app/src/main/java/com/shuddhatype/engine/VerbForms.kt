package com.shuddhatype.engine

/**
 * Nepali verb conjugation, generated rather than stored.
 *
 * Storing every form costs ~800 KB and still misses whatever the user types.
 * Storing 1,002 roots plus these ending tables costs 15 KB and produces
 * ~120,000 forms at startup. गर् alone yields गर्छु, गर्छेउ, गर्नुभयो,
 * गरिन्थ्यो, गरेपछि and ninety more.
 *
 * The Int beside each ending is roughly how common that form is. It only
 * separates generated forms from each other — anything attested in the ranked
 * wordlist always wins, because generated forms start at RANK_BASE.
 */
object VerbForms {

    /** Consonant-final stems: गर् -> stem "गर" */
    val CONS: List<Pair<String, Int>> = listOf(
    "्छ" to 1,
    "्छु" to 3,
    "्छन्" to 5,
    "्छौं" to 9,
    "्छौ" to 11,
    "्छे" to 14,
    "्छस्" to 22,
    "्छेउ" to 26,
    "्नुहुन्छ" to 12,
    "्नुहुन्न" to 30,
    "्दैन" to 8,
    "्दैनन्" to 18,
    "्दिनँ" to 24,
    "्दिन" to 25,
    "्दैनौं" to 32,
    "्दैनौ" to 33,
    "्दैनस्" to 40,
    "े" to 4,
    "ें" to 7,
    "ेँ" to 15,
    "्यो" to 6,
    "्यौ" to 20,
    "्यौं" to 19,
    "िस्" to 34,
    "िन्" to 21,
    "ी" to 28,
    "्नुभयो" to 13,
    "्नुभएन" to 36,
    "ेन" to 17,
    "ेनन्" to 29,
    "िनँ" to 35,
    "ेनौ" to 38,
    "ेनौं" to 39,
    "्थ्यो" to 16,
    "्थें" to 23,
    "्थे" to 27,
    "्थिन्" to 37,
    "्थ्यौं" to 41,
    "्थ्यौ" to 42,
    "्नुहुन्थ्यो" to 31,
    "्ने" to 2,
    "ेको" to 3,
    "ेका" to 10,
    "ेकी" to 20,
    "्दै" to 8,
    "्दा" to 12,
    "ेर" to 7,
    "ेपछि" to 18,
    "्नु" to 4,
    "्न" to 5,
    "्नाले" to 44,
    "ेदेखि" to 45,
    "्नेछ" to 43,
    "्नुपर्छ" to 9,
    "्नुपर्ने" to 17,
    "्नुपर्यो" to 29,
    "्नुपर्दैन" to 34,
    "्नुपऱ्यो" to 43,
    "" to 6,
    "्नुहोस्" to 13,
    "ौं" to 14,
    "ौँ" to 15,
    "ूँ" to 34,
    "ून्" to 36,
    "िन्छ" to 10,
    "िन्छन्" to 24,
    "िन्थ्यो" to 33,
    "ियो" to 12,
    "िएको" to 16,
    "िएका" to 30,
    "िने" to 19,
    "िनु" to 26,
    "ुँला" to 28,
    "ौंला" to 35,
    "ेला" to 27,
    "ेलान्" to 42,
    "ेछ" to 31,
    "ेछन्" to 41,
    "िसक्नु" to 37,
    "िसकेको" to 40    )

    /** Vowel-final stems: खा, जा, दि, हु */
    val VOWEL: List<Pair<String, Int>> = listOf(
    "न्छ" to 1,
    "न्छु" to 3,
    "न्छन्" to 5,
    "न्छौं" to 9,
    "न्छौ" to 11,
    "न्छे" to 14,
    "न्छस्" to 22,
    "न्छेउ" to 26,
    "नुहुन्छ" to 12,
    "ँदैन" to 8,
    "ँदैनन्" to 18,
    "ँदिनँ" to 24,
    "यो" to 6,
    "ए" to 4,
    "एँ" to 7,
    "एँं" to 15,
    "एन" to 17,
    "इन्" to 21,
    "नुभयो" to 13,
    "न्थ्यो" to 16,
    "न्थें" to 23,
    "ने" to 2,
    "एको" to 3,
    "एका" to 10,
    "एकी" to 20,
    "ँदै" to 8,
    "ँदा" to 12,
    "एर" to 7,
    "एपछि" to 18,
    "नु" to 4,
    "न" to 5,
    "नुहोस्" to 13,
    "नुपर्छ" to 9,
    "नुपर्ने" to 17,
    "नुपर्यो" to 29,
    "नुपर्दैन" to 34,
    "उनुभयो" to 13,
    "उने" to 16,
    "उँछ" to 10,
    "ऊँ" to 34,
    "औं" to 14,
    "औँ" to 15,
    "इन्छ" to 10,
    "इयो" to 12,
    "इएको" to 16,
    "इने" to 19,
    "उँला" to 28    )

    /** Causatives from a consonant stem: पक् -> पकाउनु, बन् -> बनाउनु */
    val CAUS: List<Pair<String, Int>> = listOf(
    "उँछ" to 2,
    "उँछु" to 5,
    "उँछन्" to 8,
    "उँछौ" to 14,
    "उँछौं" to 13,
    "उँछे" to 22,
    "उने" to 3,
    "उनु" to 4,
    "उनुहोस्" to 12,
    "उनुभयो" to 9,
    "उनुपर्छ" to 11,
    "उनुहुन्छ" to 15,
    "ए" to 6,
    "एँ" to 16,
    "एको" to 7,
    "एका" to 17,
    "एकी" to 24,
    "यो" to 6,
    "ई" to 26,
    "उँदै" to 10,
    "उँदा" to 13,
    "एर" to 8,
    "एपछि" to 19,
    "उँदैन" to 18,
    "उन" to 20,
    "इन्छ" to 15,
    "इयो" to 18,
    "इएको" to 21,
    "इने" to 23,
    "ऊँ" to 28,
    "औं" to 20,
    "औँ" to 21    )

    val VOWEL_ROOTS: List<String> = listOf(
        "खा", "जा", "आ", "पा", "ला", "गा", "धु", "छु", "रु", "दि", "ली", "पि", "बि", "हु", "ध", "सि"
    )

    const val RANK_BASE = 25_000

    /**
     * Probe forms that a real corpus would actually contain, so a root nobody
     * uses cannot outrank a common one. (पक् has पकाउने in the corpus; पख् has
     * nothing, and without this पखाउनुभयो beats पकाउनुभयो.)
     */
    private fun rootRank(root: String, stem: String, rankOf: (String) -> Int?): Int {
        var best = Int.MAX_VALUE
        for (probe in listOf(
            root, stem, stem + "्छ", stem + "्नु", stem + "्ने", stem + "े", stem + "ेको",
            stem + "्यो", stem + "्दै", stem + "न्छ", stem + "नु", stem + "ने",
            stem + "ाउनु", stem + "ाउने", stem + "ाउँछ", stem + "ाएको", stem + "ाइयो"
        )) {
            rankOf(probe)?.let { if (it < best) best = it }
        }
        // No corpus evidence at all means the root is rare. Penalise, don't trust.
        return if (best == Int.MAX_VALUE) 60_000 else best
    }

    private val DEVANAGARI = Regex("^[\\u0900-\\u097F]+$")

    /**
     * Expand [roots] into every conjugated form, writing word -> rank into [sink].
     * [sink] should skip words the attested wordlist already ranks.
     */
    fun build(roots: List<String>, rankOf: (String) -> Int?, sink: (String, Int) -> Unit) {
        for (root in roots) {
            val isCons = root.endsWith("्")
            val stem = if (isCons) root.dropLast(1) else root
            val nudge = minOf(rootRank(root, stem, rankOf), 60_000) / 300
            val table = if (isCons) CONS else VOWEL + CAUS

            for ((suf, w) in table) {
                val f = stem + suf
                if (DEVANAGARI.matches(f)) sink(f, RANK_BASE + nudge + w)
            }

            if (isCons) {
                // Nepali shortens the stem vowel in causatives: लाग् -> लगाउनु,
                // पाक् -> पकाउनु. Generate both and let rank decide.
                val shortened = stem.replace("ा", "")
                val stems = if (shortened.isNotEmpty() && shortened != stem)
                    listOf(shortened, stem) else listOf(stem)
                stems.forEachIndexed { i, cs ->
                    for ((suf, w) in CAUS) {
                        val f = cs + "ा" + suf
                        if (DEVANAGARI.matches(f)) sink(f, RANK_BASE + nudge + 40 + w + i * 3)
                    }
                }
            }

            // negative imperative: गर -> नगर, नगर्नुहोस्
            val neg = "न" + stem
            if (DEVANAGARI.matches(neg)) sink(neg, RANK_BASE + nudge + 30)
            val negPolite = neg + (if (isCons) "्नुहोस्" else "नुहोस्")
            if (DEVANAGARI.matches(negPolite)) sink(negPolite, RANK_BASE + nudge + 38)
        }
    }
}
