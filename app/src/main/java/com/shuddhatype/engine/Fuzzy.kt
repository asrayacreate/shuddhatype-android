package com.shuddhatype.engine

/**
 * Edit-distance correction for words the rules could not resolve.
 *
 * Division of labour with [Transliterator.variants]: variants() handles
 * *ambiguity* — the Roman is right but the Devanagari spelling has more than
 * one legal shape (स/श/ष, ि/ी, ब/व). It expands every unit and asks the
 * lexicon which combination is a real word. That covers everything the typist
 * got right.
 *
 * This class handles the other half: the typist got it *wrong*. A letter is
 * missing, doubled, swapped, or plain incorrect, so no legal spelling of what
 * was typed is a word at all. variants() returns nothing and there is no
 * ambiguity left to resolve — only a wordlist to search.
 *
 * So Fuzzy runs only when variants() came back empty. On any word the rules
 * already resolved it never runs, which is most keystrokes.
 *
 * Cost model: a real edit costs [FULL], a swap inside a [NEAR] pair costs
 * [HALF]. That is the whole point — ह्रस्व/दीर्घ and स/श/ष are half-mistakes
 * in Nepali, and a correction that fixes one of them should outrank a
 * correction that invents a whole new letter.
 *
 * Search space is cut by bucketing on (first character, length). A typo rarely
 * lands on the first letter, and when it does it is usually a NEAR swap, so the
 * neighbours of the first character are searched too, plus the bucket for the
 * first character having been dropped entirely. Measured on the real 20,017
 * word list: 0.35 ms per query on a desktop JVM, against 15 ms for scanning
 * every word of a plausible length.
 */
class Fuzzy {

    /** One (head, length) bucket. Ranks are parallel to words — no second map. */
    private class Bucket(val words: Array<String>, val ranks: IntArray)

    private var buckets: HashMap<Int, Bucket> = HashMap(0)

    /**
     * [words] must arrive in corpus-frequency order, because the position in
     * the list *is* the rank — the same contract words.txt has with [Lexicon].
     *
     * Only attested words are indexed. Generated verb forms are deliberately
     * left out: correcting a typo *to* a form nobody has been observed writing
     * is how a keyboard starts inventing words.
     */
    fun build(words: List<String>) {
        val tmp = HashMap<Int, ArrayList<Int>>(1024)
        words.forEachIndexed { i, w ->
            if (w.isNotEmpty() && w.length <= MAX_LEN) {
                tmp.getOrPut(keyOf(w[0], w.length)) { ArrayList() }.add(i)
            }
        }
        val built = HashMap<Int, Bucket>(tmp.size * 2)
        for ((k, idx) in tmp) {
            built[k] = Bucket(
                Array(idx.size) { words[idx[it]] },
                IntArray(idx.size) { idx[it] }
            )
        }
        buckets = built
    }

    /**
     * Near misses for [word], best first. Empty if the word is too short to
     * guess from — below three characters almost anything is one edit away.
     */
    fun search(word: String, limit: Int): List<String> {
        val n = word.length
        if (n < MIN_LEN || n > MAX_LEN || buckets.isEmpty()) return emptyList()

        // One edit on a short word, two on a long one. Allowing two on a short
        // word turns घर into a coin toss between चार, घार, हर and घडी.
        val max = if (n <= SHORT_LEN) FULL else 2 * FULL
        val span = max / FULL

        val heads = ArrayList<Char>(6)
        heads.add(word[0])
        NEIGHBOURS[word[0]]?.forEach { heads.add(it) }
        // First character dropped: नेपाली typed as एपाली.
        if (n > 1 && !heads.contains(word[1])) heads.add(word[1])

        // Allocated once per search, not per comparison — dist() is called
        // thousands of times inside this loop.
        val rows = Array(3) { IntArray(MAX_LEN + 1) }

        val scores = ArrayList<Long>(32)
        val found = ArrayList<String>(32)
        for (head in heads) {
            for (len in (n - span)..(n + span)) {
                if (len < 1) continue
                val b = buckets[keyOf(head, len)] ?: continue
                for (i in b.words.indices) {
                    val d = dist(word, b.words[i], max, rows)
                    if (d > max) continue
                    // Distance dominates; frequency only orders words that are
                    // equally far from what was typed.
                    scores.add(d.toLong() * RANK_SPAN + b.ranks[i])
                    found.add(b.words[i])
                }
            }
        }
        if (found.isEmpty()) return emptyList()

        val order = found.indices.sortedBy { scores[it] }
        val out = ArrayList<String>(limit)
        for (i in order) {
            val w = found[i]
            if (w != word && !out.contains(w)) out.add(w)
            if (out.size == limit) break
        }
        return out
    }

    /**
     * Damerau-Levenshtein with [NEAR] pairs discounted, abandoning the moment
     * every cell in a row is already past [max]. The abort is what makes a
     * full-bucket scan affordable: most words fail on the second or third row.
     */
    private fun dist(a: String, b: String, max: Int, rows: Array<IntArray>): Int {
        val la = a.length
        val lb = b.length
        if (lb > MAX_LEN) return max + 1
        if (kotlin.math.abs(la - lb) * FULL > max) return max + 1

        var prev2 = rows[0]
        var prev = rows[1]
        var cur = rows[2]
        for (j in 0..lb) prev[j] = j * FULL

        for (i in 1..la) {
            cur[0] = i * FULL
            var best = cur[0]
            val ai = a[i - 1]
            for (j in 1..lb) {
                val bj = b[j - 1]
                var v = prev[j - 1] + subCost(ai, bj)
                val del = prev[j] + FULL
                if (del < v) v = del
                val ins = cur[j - 1] + FULL
                if (ins < v) v = ins
                // Transposition: two adjacent letters swapped is one mistake,
                // and on a phone keyboard it is the commonest one there is.
                if (i > 1 && j > 1 && ai == b[j - 2] && a[i - 2] == bj) {
                    val sw = prev2[j - 2] + FULL
                    if (sw < v) v = sw
                }
                cur[j] = v
                if (v < best) best = v
            }
            if (best > max) return max + 1
            val spare = prev2
            prev2 = prev
            prev = cur
            cur = spare
        }
        return prev[lb]
    }

    private fun subCost(a: Char, b: Char): Int =
        if (a == b) 0
        else if (NEAR_SET.contains((a.code shl 16) or b.code)) HALF
        else FULL

    private fun keyOf(head: Char, len: Int): Int =
        (head.code shl 6) or if (len > 63) 63 else len

    companion object {
        /** A real edit. Costs are integers so the DP stays on IntArray. */
        const val FULL = 2

        /** A swap inside a [NEAR] pair — half a mistake, not a whole one. */
        const val HALF = 1

        /** Wider than any rank in words.txt, so distance always dominates. */
        private const val RANK_SPAN = 30_000L

        private const val MIN_LEN = 3
        private const val SHORT_LEN = 4
        private const val MAX_LEN = 40

        /**
         * Letters Nepalis genuinely interchange. Everything here is a spelling
         * mistake people actually make, not a phonetic neighbour: ह्रस्व/दीर्घ,
         * the three sibilants, ब/व, न/ण, and the aspirated pairs.
         *
         * Each entry is exactly two characters. Keep it that way — the loader
         * below reads them positionally.
         */
        private val NEAR = arrayOf(
            "िी", "ुू", "इई", "उऊ",
            "सश", "सष", "शष",
            "बव", "नण", "ंँ", "ंङ",
            "तथ", "दध", "डढ", "टठ",
            "कख", "गघ", "जझ", "पफ", "बभ",
            "ृि", "ेै", "ोौ"
        )

        private val NEAR_SET = HashSet<Int>(NEAR.size * 4).apply {
            for (p in NEAR) {
                add((p[0].code shl 16) or p[1].code)
                add((p[1].code shl 16) or p[0].code)
            }
        }

        private val NEIGHBOURS: Map<Char, CharArray> = HashMap<Char, MutableList<Char>>().apply {
            for (p in NEAR) {
                getOrPut(p[0]) { ArrayList() }.add(p[1])
                getOrPut(p[1]) { ArrayList() }.add(p[0])
            }
        }.mapValues { it.value.toCharArray() }
    }
}
