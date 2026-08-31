package com.shuddhatype.engine

import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * Golden tests. These cases were each a real reported bug — if one of them
 * regresses, the engine has lost something a user already complained about.
 * Run them before every release.
 */
class EngineTest {

    companion object {
        private lateinit var lex: Lexicon

        @BeforeClass @JvmStatic fun setUp() {
            val dir = File("src/main/assets")
            lex = Lexicon().apply {
                load(open = { name -> File(dir, name).inputStream() })
            }
        }
    }

    private fun line(roman: String) = Transliterator.renderLine(roman, lex).joinToString(" ")

    @Test fun shorthandBeatsPhonetics() {
        assertEquals("के छ साथी", line("k xa sathi"))          // "k" must not become खा
        assertEquals("के गरौँ", line("k garau"))
        assertEquals("मलाई भोक लाग्यो", line("mlai vok lagyo"))
    }

    @Test fun frequencyPicksTheRightSpelling() {
        assertEquals("मेरो नाम राम हो", line("mero nam ram ho"))  // राम, not रम
        assertEquals("शरीर", line("sarir"))
        assertEquals("विकास", line("bikas"))
    }

    @Test fun verbConjugation() {
        assertEquals("गर्ने गरिन्छ गर्छेउ", line("garne garinxa garcheu"))
        assertEquals("बच्चाहरू स्कूलमा खेल्छन्", line("bachcha haru school ma khelchan"))
        assertEquals("उनीहरू बिहान उठ्छन्", line("uniharu bihana uthchan"))
        assertEquals("आमाले भात पकाउनुभयो", line("ama le bhat pakaunubhayo"))
    }

    @Test fun postpositionsAttachButAdverbsDoNot() {
        assertEquals("समाजमा शिक्षाको महत्त्व छ", line("samaj ma siksha ko mahattwa cha"))
        assertEquals("नेपालको संविधान २०७२ मा जारी भयो", line("nepal ko sambidhan 2072 ma jari bhayo"))
    }

    /**
     * A Bengali U+09C7 once slipped into MATRA_ALT in place of the Devanagari
     * U+0947. It compiled, and a 25-sentence check passed, because no test case
     * happened to exercise that alternative. Assert on the codepoints directly.
     */
    @Test fun tablesContainOnlyDevanagari() {
        val strings = Tables.CONS.map { it.second } + Tables.MATRA.map { it.second } +
            Tables.INDEP.map { it.second } + Tables.SHORT.values +
            Tables.POSTPOS.values + Tables.DICT.values +
            Tables.CONS_ALT.flatMap { listOf(it.key) + it.value } +
            Tables.MATRA_ALT.flatMap { listOf(it.key) + it.value }
        for (s in strings) for (c in s) {
            val cp = c.code
            assert(cp !in 0x0980..0x0DFF) {
                "non-Devanagari Indic char U+%04X in \"%s\"".format(cp, s)
            }
        }
    }

    @Test fun vocalicR() {
        assertEquals("कृष्ण", line("krishna"))
        assertEquals("ऋषि", line("rishi"))
        assertEquals("वृद्धि", line("briddhi"))
        assertEquals("संस्कृति", line("sanskriti"))
        assertEquals("हरि", line("hari"))      // plain र+ि must survive
        assertEquals("शरीर", line("sarir"))
    }

    @Test fun jnaMapsBothWays() {
        assertEquals("ज्ञान", line("jnan"))
        assertEquals("ज्ञान", line("gyan"))
        assertEquals("विज्ञान", line("bijnan"))
        assertEquals("खोज्नु", line("khojnu"))  // "jn" here is ज्+न, not ज्ञ
    }

    @Test fun latencyStaysUnderKeyboardBudget() {
        val words = listOf("garcheu", "pakaunubhayo", "bachcha", "khelchan", "sarir")
        repeat(2000) { for (w in words) Transliterator.best(w, lex) }
        val t = System.nanoTime()
        repeat(5000) { for (w in words) Transliterator.best(w, lex) }
        val perWord = (System.nanoTime() - t).toDouble() / (5000 * words.size) / 1_000_000
        assert(perWord < 5.0) { "per-word $perWord ms — too slow for a keyboard" }
    }
}
