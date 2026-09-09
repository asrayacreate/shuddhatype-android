package com.shuddhatype.ime

import android.content.Context

/**
 * Letters the user writes themselves, alongside the five that ship in
 * [com.shuddhatype.engine.Templates].
 *
 * The built-in five cover the shapes an office expects, but every trade has
 * its own letter — a site handover, a payment reminder, a materials request —
 * and no list written here will ever have the one somebody actually needs.
 * Shortcuts could already hold a long message, but a शर्टकट is a *key* you
 * remember; a ढाँचा is a *name* you pick from a list. Different jobs.
 *
 * Storage is [Shortcuts]' own shape, deliberately: one preference string, tab
 * between name and body, newline between records, both escaped so a letter's
 * own line breaks survive. That format has already been through the bug where
 * a formatted message came back as one paragraph, and there is no reason to
 * repeat it here.
 */
object UserTemplates {

    /** A name has to be readable in the suggestion bar; one letter is not. */
    const val MIN_NAME = 2

    /** Long enough for a full निवेदन with room to spare. */
    const val MAX_BODY = 4000

    /** Insertion-ordered, so settings and the ढाँचा key list them as added. */
    @Volatile
    private var cache: Map<String, String> = emptyMap()

    fun reload(context: Context) {
        cache = parse(prefs(context).getString(STORE, "") ?: "")
    }

    fun all(context: Context): Map<String, String> {
        reload(context)
        return cache
    }

    /** Null when the name is fine; otherwise why it was refused. */
    fun rejectReason(name: String): String? {
        val n = name.trim()
        return when {
            n.length < MIN_NAME -> "नाम कम्तीमा दुई अक्षरको हुनुपर्छ।"
            n.contains('\t') || n.contains('\n') -> "नाममा नयाँ हरफ राख्न मिल्दैन।"
            else -> null
        }
    }

    fun put(context: Context, name: String, body: String): Boolean {
        val n = name.trim()
        if (rejectReason(n) != null) return false
        if (body.isEmpty() || body.length > MAX_BODY) return false
        val map = LinkedHashMap(all(context))
        map[n] = body
        write(context, map)
        return true
    }

    fun remove(context: Context, name: String) {
        val map = LinkedHashMap(all(context))
        map.remove(name)
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

    private fun escape(s: String) = s
        .replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace("\t", "\\t")

    /** One pass, so a backslash-n the user typed stays a backslash-n. */
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

    private const val PREFS = "shuddhatype"
    private const val STORE = "templates"
    private const val ROW = "\n"
    private const val SEP = "\t"
}
