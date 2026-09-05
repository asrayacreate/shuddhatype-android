package com.shuddhatype.engine

/**
 * Roman -> Devanagari, then spelling correction against a real Nepali lexicon.
 *
 * Pipeline (the order is the whole design):
 *   1. toUnits()   split Roman into consonant+matra units
 *   2. variants()  expand every ambiguous unit into the spellings Nepalis confuse
 *   3. filter      keep only strings the lexicon says are real words
 *   4. rank        corpus frequency picks the winner; distance from what was
 *                  typed breaks near-ties
 *   5. fuzzy       only if 3 kept nothing — the word was mistyped, not merely
 *                  spelled ambiguously, so [Fuzzy] searches the wordlist by
 *                  edit distance instead
 *
 * A "unit" is one consonant plus its matra, an independent vowel, or a raw
 * passthrough character. Modelling it this way (instead of on the final string)
 * is what makes the ambiguity expansion in variants() tractable.
 */
object Transliterator {

    data class Unit(
        val cons: String? = null,
        val matra: String = "",
        val vowel: String? = null,
        val raw: String? = null
    )

    private fun matchAt(s: String, i: Int, table: List<Pair<String, String>>): Pair<String, String>? {
        for (entry in table) if (s.startsWith(entry.first, i)) return entry
        return null
    }

    fun toUnits(word: String): List<Unit> {
        val s = word.lowercase()
        val units = ArrayList<Unit>()
        var i = 0
        while (i < s.length) {
            val c = matchAt(s, i, Tables.CONS)
            if (c != null) {
                i += c.first.length
                val m = matchAt(s, i, Tables.MATRA)
                if (m != null) {
                    i += m.first.length
                    units.add(Unit(cons = c.second, matra = m.second))
                } else {
                    // No vowel follows: halant before another consonant,
                    // inherent "a" at the end of the word.
                    val more = i < s.length && matchAt(s, i, Tables.CONS) != null
                    units.add(Unit(cons = c.second, matra = if (more) "्" else ""))
                }
                continue
            }
            val v = matchAt(s, i, Tables.INDEP)
            if (v != null) {
                units.add(Unit(vowel = v.second)); i += v.first.length; continue
            }
            units.add(Unit(raw = Tables.DIGITS[s[i]] ?: s[i].toString())); i++
        }
        return units
    }

    fun render(units: List<Unit>): String {
        val sb = StringBuilder()
        for (u in units) sb.append(u.raw ?: u.vowel ?: (u.cons + u.matra))
        return sb.toString()
    }

    private fun unitAlts(u: Unit, isLast: Boolean): List<String> {
        u.raw?.let { return listOf(it) }
        u.vowel?.let { return listOf(it, it + "ँ", it + "ं") }

        val c = u.cons!!
        val consOpts = ArrayList<String>(4).apply {
            add(c); Tables.CONS_ALT[c]?.let { addAll(it) }
        }
        val matraOpts = ArrayList<String>(4)
        if (u.matra == "्") {
            matraOpts.addAll(listOf("्", "", "ा"))
        } else {
            matraOpts.add(u.matra)
            Tables.MATRA_ALT[u.matra]?.let { matraOpts.addAll(it) }
            // Roman drops the word-final halant: khelchan -> खेल्छन्.
            // Only at the end — mid-word this turns बढायो into बढ्यो.
            if (isLast && u.matra == "") matraOpts.add("्")
        }

        val out = ArrayList<String>(consOpts.size * matraOpts.size + 2)
        for (co in consOpts) for (mo in matraOpts) out.add(co + mo)
        // न्/म् before a consonant is usually written as anusvara,
        // and a trailing n/m after a vowel is nasalisation: garaun -> गरौँ.
        if (c == "न" || c == "म") {
            if (u.matra == "्") out.add("ं")
            if (u.matra == "") { out.add("ँ"); out.add("ं") }
        }
        // Roman "ri" is र+ि in हरि but vocalic ऋ/ृ in कृष्ण, ऋषि, वृद्धि. Offer both
        // and let the wordlist decide — the letters alone cannot tell you which.
        // कृष्ण resolves because {क,्} can already drop its halant, so क + ृ = कृ.
        if (c == "र" && u.matra == "ि") { out.add("ृ"); out.add("ऋ") }
        return out
    }

    private const val VARIANT_CAP = 6000

    fun variants(units: List<Unit>): List<String> {
        var out = mutableListOf("")
        for ((idx, u) in units.withIndex()) {
            val alts = unitAlts(u, idx == units.size - 1)
            val next = ArrayList<String>(minOf(out.size * alts.size, VARIANT_CAP + 1))
            for (prefix in out) {
                for (a in alts) {
                    next.add(prefix + a)
                    if (next.size > VARIANT_CAP) return next
                }
            }
            out = next
        }
        return out
    }

    /** Positional character difference — cheap, and enough to break near-ties. */
    private fun distance(a: String, b: String): Int {
        var d = 0
        val n = maxOf(a.length, b.length)
        for (i in 0 until n) {
            if (i >= a.length || i >= b.length || a[i] != b[i]) d++
        }
        return d
    }

    enum class Source { SHORT, DICT, ENGLISH, SHUDDHI, RULE_WORD, RULE, FUZZY, JOINED, ROMAN }

    data class Candidate(val word: String, val source: Source)

    /**
     * A rare-but-valid spelling must not beat what the user plainly typed, so
     * each character of deviation costs [DISTANCE_PENALTY] rank places.
     * Tuned to 800 over 27 held-out sentences — re-tune if the corpus changes.
     */
    private const val DISTANCE_PENALTY = 800

    /** Below this, a word is too short to guess English from. See [English]. */
    private const val MIN_ROMAN_FALLBACK = 4

    /**
     * Typo corrections are guesses, so the bar shows few of them. Three is
     * enough to catch the intended word without burying the Roman fallback
     * that a brand or a name needs.
     */
    private const val FUZZY_LIMIT = 3

    fun candidates(roman: String, lex: Lexicon, limit: Int = 5): List<Candidate> {
        val key = roman.lowercase()
        val seen = LinkedHashMap<String, Source>()
        fun push(w: String, s: Source) { if (w.isNotEmpty() && !seen.containsKey(w)) seen[w] = s }

        // 1. shorthand — convention, must beat phonetic guessing
        Tables.SHORT[key]?.let { push(it, Source.SHORT) }

        // 2. hand-tuned romanisations
        Tables.DICT[key]?.split("|")?.forEach { push(it, Source.DICT) }

        // 3. English typed without leaving the नेपाली page. "download" has no
        //    Devanagari spelling worth guessing, and दोव्न्लोअड helps nobody, so
        //    the word goes back exactly as typed. The list is deliberately
        //    conservative about words that are also Nepali — see [English].
        if (English.contains(key)) push(roman, Source.ENGLISH)

        // 4. every lexicon-valid spelling, best-ranked first
        val units = toUnits(key)
        val base = render(units)
        variants(units)
            .asSequence()
            .filter { lex.contains(it) }
            .distinct()
            .sortedBy { lex.rankOf(it) + distance(it, base) * DISTANCE_PENALTY }
            .forEach { push(it, if (it == base) Source.RULE_WORD else Source.SHUDDHI) }

        // 5. stem + postposition ("sikshako" -> "siksha" + "ko")
        if (seen.isEmpty()) {
            for (suf in Tables.POSTPOS.keys.sortedByDescending { it.length }) {
                if (key.length > suf.length + 1 && key.endsWith(suf)) {
                    val stem = candidates(key.dropLast(suf.length), lex, 2)
                    val good = stem.firstOrNull { it.source != Source.RULE }
                    if (good != null) { push(good.word + Tables.POSTPOS[suf], Source.JOINED); break }
                }
            }
        }

        val beforeFallback = seen.size

        // 6. raw rule output, always available as a fallback
        push(base, Source.RULE)

        // 6b. No legal spelling of what was typed is a word, so this is a typo
        //     rather than an ambiguity — a letter missing, doubled, swapped, or
        //     simply wrong. variants() has nothing left to resolve; only the
        //     wordlist can help, searched by edit distance.
        //
        //     Deliberately after step 6: the rule output stays first, so space
        //     still commits exactly what the user typed and best() is unchanged.
        //     A guess that silently overwrites a correct but unlisted word is
        //     far worse than one sitting a tap away.
        if (beforeFallback == 0) {
            lex.nearMisses(base, FUZZY_LIMIT).forEach { push(it, Source.FUZZY) }
        }

        // 7. Nothing above recognised the word — it is English we do not list,
        //    a brand, or a name. The rule output stays first so a genuine Nepali
        //    word missing from the lexicon still writes correctly, but the Roman
        //    sits right behind it, one tap away.
        if (beforeFallback == 0 && key.length >= MIN_ROMAN_FALLBACK) {
            push(roman, Source.ROMAN)
        }

        return seen.entries.take(limit).map { Candidate(it.key, it.value) }
    }

    fun best(roman: String, lex: Lexicon): String =
        candidates(roman, lex, 1).firstOrNull()?.word ?: roman

    private val PUNCT = Regex("[।?!,.:;\"'()]")
    private val NUMERIC = Regex("^[०-९0-9]+$")

    /**
     * Render a whole line. Roman typists separate विभक्ति (मा/ले/को/लाई) and the
     * plural हरू; Nepali attaches them to the previous word, so we reattach.
     * सँगै is deliberately absent from POSTPOS — it is an adverb.
     */
    fun renderLine(text: String, lex: Lexicon): List<String> {
        val tokens = PUNCT.replace(text, " ").trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val out = ArrayList<String>(tokens.size)
        tokens.forEachIndexed { i, t ->
            val k = t.lowercase()
            val post = Tables.POSTPOS[k]
            if (i > 0 && post != null) {
                val prev = out.lastOrNull()
                if (prev != null && !NUMERIC.matches(prev)) out[out.size - 1] = prev + post
                else out.add(post)
                return@forEachIndexed
            }
            out.add(best(k, lex))
        }
        return out
    }
}
