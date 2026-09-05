package com.shuddhatype.engine

import java.io.InputStream

/**
 * Word -> frequency rank. Lower rank means more common, and that ordering is
 * the only thing that knows राम beats रम.
 *
 * Two sources:
 *   words.txt.gz       20,017 attested words. LINE NUMBER IS THE RANK.
 *                      Never sort this file — sorting destroys the engine.
 *   verb_roots.txt.gz  1,002 roots, expanded by VerbForms at load.
 *
 * Loading is not instant (roughly 120 ms on a desktop JVM, budget more on a
 * low-end phone), so [load] must run off the main thread. Until it finishes,
 * [isReady] is false and the engine still works — it just falls back to plain
 * rule output with no शुद्धि correction, which is better than a frozen keyboard.
 */
class Lexicon {

    private var ranks: HashMap<String, Int> = HashMap(0)

    /**
     * Built from words.txt only, so it never corrects a typo into a verb form
     * that was generated rather than observed. See [Fuzzy].
     */
    private val fuzzyIndex = Fuzzy()

    @Volatile
    var isReady: Boolean = false
        private set

    val size: Int get() = ranks.size

    fun contains(word: String): Boolean = ranks.containsKey(word)

    /** Rank of [word], or [Int.MAX_VALUE] if unknown. */
    fun rankOf(word: String): Int = ranks[word] ?: Int.MAX_VALUE

    /**
     * Near misses for a word the rules could not resolve, best first. Empty
     * until [isReady] — a keyboard that is still loading offers what it has
     * rather than making the user wait.
     */
    fun nearMisses(word: String, limit: Int): List<String> =
        if (isReady) fuzzyIndex.search(word, limit) else emptyList()

    /**
     * [open] returns the raw gzip stream for an asset name. On Android pass
     * `assets::open`; the desktop harness and unit tests pass a file opener.
     * Keeping I/O out of here is what lets the same class run in both.
     */
    fun load(open: (String) -> InputStream, verbRootLimit: Int = DEFAULT_VERB_ROOTS) {
        val words = readLines(open, "words.txt")
        // verb_roots.txt.gz is ordered by how much corpus evidence each root has,
        // so taking a prefix keeps the common verbs and drops the obscure ones.
        // 400 roots scored the same as all 1,002 on the test set at a third of
        // the heap; raise this if profiling on real devices says there is room.
        val roots = readLines(open, "verb_roots.txt").take(verbRootLimit)

        // Sized up front: rehashing 140k entries mid-load is the slowest thing
        // this class could do.
        val map = HashMap<String, Int>(220_000)
        words.forEachIndexed { i, w -> map.putIfAbsent(w, i) }

        // Before the verb expansion, so the index holds attested words only.
        fuzzyIndex.build(words)

        val allRoots = roots + VerbForms.VOWEL_ROOTS
        VerbForms.build(allRoots, { map[it] }) { form, rank ->
            // Attested spellings keep their real rank; generated forms only fill gaps.
            if (!map.containsKey(form)) map[form] = rank
        }

        ranks = map
        isReady = true
    }

    private fun readLines(open: (String) -> InputStream, name: String): List<String> =
        open(name).bufferedReader(Charsets.UTF_8).use { r ->
            r.lineSequence().filter { it.isNotBlank() }.toList()
        }

    companion object {
        /** Tuned against measured heap, not guessed. See load(). */
        const val DEFAULT_VERB_ROOTS = 400

        /** For unit tests and the desktop harness, where there is no AssetManager. */
        fun fromLines(words: List<String>, roots: List<String>): Lexicon {
            val lex = Lexicon()
            val map = HashMap<String, Int>(220_000)
            words.forEachIndexed { i, w -> map.putIfAbsent(w, i) }
            lex.fuzzyIndex.build(words)
            VerbForms.build(roots + VerbForms.VOWEL_ROOTS, { map[it] }) { form, rank ->
                if (!map.containsKey(form)) map[form] = rank
            }
            lex.ranks = map
            lex.isReady = true
            return lex
        }
    }
}
