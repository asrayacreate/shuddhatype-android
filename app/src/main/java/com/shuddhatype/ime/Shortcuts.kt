package com.shuddhatype.ime

import android.content.Context

/**
 * User-defined text shortcuts: type `pp`, press space, get the company name.
 *
 * Why these expand on space instead of waiting for a tap, unlike every other
 * suggestion this keyboard makes: the rest of the engine is *guessing* what
 * you meant, so it offers and lets you decide. A shortcut is not a guess. You
 * wrote the rule yourself, in settings, deliberately. Making you confirm your
 * own instruction every time would defeat the point of having it.
 *
 * That is also why a shortcut beats the lexicon. If someone maps `sat` to
 * their address, `sat` stops meaning सात for them — that is what they asked
 * for, and a shortcut that silently loses to a dictionary word would be a bug
 * with no visible cause. A bad choice shows itself on the first use and is one
 * tap to delete.
 *
 * Stored as one preference string rather than a file or a database: a few
 * dozen short lines do not need either, and the keyboard reads them on every
 * field focus.
 */
object Shortcuts {

    /** Shortest key allowed. One letter would fire constantly by accident. */
    const val MIN_KEY = 2

    /**
     * Long enough for a whole WhatsApp introduction — the services list, the
     * numbers, the sign-off. That is the message a contractor sends twenty
     * times a day, and it is the reason to have shortcuts at all. 200 was
     * enough for a company name and nothing else.
     */
    const val MAX_VALUE = 1000

    /** Insertion-ordered, so settings lists them the way they were added. */
    @Volatile
    private var cache: Map<String, String> = emptyMap()

    /**
     * Read from disk into [cache]. Called when a text field is focused, not
     * when the keyboard is built — settings can change while the IME is alive,
     * and SharedPreferences is memory-cached after the first read anyway.
     */
    fun reload(context: Context) {
        cache = parse(prefs(context).getString(STORE, "") ?: "")
    }

    /** The expansion for what is being typed, or null. Case is ignored. */
    fun expansionFor(typed: String): String? {
        if (typed.length < MIN_KEY) return null
        return cache[typed.lowercase()]
    }

    fun all(context: Context): Map<String, String> {
        reload(context)
        return cache
    }

    /**
     * Reasons a key is rejected, so settings can say which one rather than
     * failing silently. Null means the key is fine.
     */
    fun rejectReason(key: String): String? {
        val k = key.trim().lowercase()
        if (k.length < MIN_KEY) return "शर्टकट कम्तीमा दुई अक्षरको हुनुपर्छ।"
        // The नेपाली page only ever composes Roman letters — a digit or symbol
        // ends the word before it can match, so such a key could never fire.
        if (!k.all { it in 'a'..'z' }) return "शर्टकटमा अंग्रेजी अक्षर (a-z) मात्र चल्छ।"
        return null
    }

    /** True if it was stored. [rejectReason] explains a false. */
    fun put(context: Context, key: String, value: String): Boolean {
        if (rejectReason(key) != null) return false
        val v = value.trim()
        if (v.isEmpty() || v.length > MAX_VALUE) return false

        val map = LinkedHashMap(all(context))
        map[key.trim().lowercase()] = v
        write(context, map)
        return true
    }

    fun remove(context: Context, key: String) {
        val map = LinkedHashMap(all(context))
        map.remove(key.lowercase())
        write(context, map)
    }

    private fun write(context: Context, map: Map<String, String>) {
        val sb = StringBuilder()
        for ((k, v) in map) {
            if (sb.isNotEmpty()) sb.append(ROW)
            sb.append(k).append(SEP).append(escape(v))
        }
        prefs(context).edit().putString(STORE, sb.toString()).apply()
        cache = LinkedHashMap(map)
    }

    private fun parse(raw: String): Map<String, String> {
        if (raw.isEmpty()) return emptyMap()
        val map = LinkedHashMap<String, String>()
        for (line in raw.split(ROW)) {
            val i = line.indexOf(SEP)
            if (i <= 0 || i == line.length - 1) continue
            map[line.substring(0, i)] = unescape(line.substring(i + 1))
        }
        return map
    }

    /**
     * The store is one string with newline between records and tab between key
     * and value, so a value containing either would split the file. They used
     * to be replaced with spaces, which quietly flattened a formatted message
     * into one paragraph — the layout of a services list is most of its point.
     * Escaping keeps the text intact instead.
     */
    private fun escape(s: String) = s
        .replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace("\t", "\\t")

    /**
     * One pass, not three replaces. Unescaping in stages would turn a literal
     * backslash-n that the user actually typed into a line break.
     */
    private fun unescape(s: String): String {
        if (!s.contains('\\')) return s
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    'n' -> { out.append('\n'); i += 2 }
                    't' -> { out.append('\t'); i += 2 }
                    '\\' -> { out.append('\\'); i += 2 }
                    else -> { out.append(c); i++ }
                }
            } else {
                out.append(c); i++
            }
        }
        return out.toString()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Same preference file the theme uses. */
    private const val PREFS = "shuddhatype"
    private const val STORE = "shortcuts"
    private const val ROW = "\n"
    private const val SEP = "\t"
}
