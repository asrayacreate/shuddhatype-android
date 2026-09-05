package com.shuddhatype.ime

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

/**
 * Every colour the keyboard draws, in one place.
 *
 * The palette was hard-coded in each view before this. That was fine while
 * there was one look; it stops being fine the moment a second one exists,
 * because a colour missed in one file leaves a black strip floating in a light
 * keyboard and nobody notices until it ships.
 *
 * [reload] is called when the keyboard becomes visible rather than when the
 * setting is written: an IME cannot repaint itself from a settings screen it
 * does not own, but it is always about to be shown again.
 */
object Theme {

    enum class Mode { SYSTEM, DARK, LIGHT }

    class Palette(
        // keyboard
        val bg: Int,
        val key: Int,
        val keyMod: Int,
        val keyPressed: Int,
        val keyPreview: Int,
        val accent: Int,
        val label: Int,
        val labelMod: Int,
        val labelHint: Int,
        // suggestion bar
        val barBg: Int,
        val barText: Int,
        val barTextTop: Int,
        val divider: Int,
        // settings screen
        val screenBg: Int,
        val screenText: Int,
        val screenMuted: Int
    )

    val DARK = Palette(
        bg = Color.parseColor("#0F1115"),
        key = Color.parseColor("#272B33"),
        keyMod = Color.parseColor("#1A1D23"),
        keyPressed = Color.parseColor("#3A3F49"),
        keyPreview = Color.parseColor("#2E6FE8"),
        accent = Color.parseColor("#E8333A"),
        label = Color.parseColor("#FFFFFF"),
        labelMod = Color.parseColor("#C3C7CE"),
        labelHint = Color.parseColor("#767B85"),
        barBg = Color.parseColor("#16181C"),
        barText = Color.parseColor("#B9BCC2"),
        barTextTop = Color.parseColor("#FFFFFF"),
        divider = Color.parseColor("#2A2E35"),
        screenBg = Color.parseColor("#0D0D0D"),
        screenText = Color.parseColor("#FFFFFF"),
        screenMuted = Color.parseColor("#B9BCC2")
    )

    // Keys are white on a grey board, not grey on white: the gap between keys
    // has to stay visible or the whole thing reads as one flat sheet.
    val LIGHT = Palette(
        bg = Color.parseColor("#DDE1E8"),
        key = Color.parseColor("#FFFFFF"),
        keyMod = Color.parseColor("#C4CAD4"),
        keyPressed = Color.parseColor("#AEB5C1"),
        // The same blue in both themes. The preview has to read as "this is
        // what you just hit" against a white key and a near-black one alike,
        // and the brand red would read as an error.
        keyPreview = Color.parseColor("#2E6FE8"),
        accent = Color.parseColor("#E8333A"),
        label = Color.parseColor("#14161A"),
        labelMod = Color.parseColor("#2E333B"),
        labelHint = Color.parseColor("#7A818C"),
        barBg = Color.parseColor("#EDEFF3"),
        barText = Color.parseColor("#4A4F57"),
        barTextTop = Color.parseColor("#101216"),
        divider = Color.parseColor("#C8CDD6"),
        screenBg = Color.parseColor("#FFFFFF"),
        screenText = Color.parseColor("#101216"),
        screenMuted = Color.parseColor("#565C66")
    )

    @Volatile
    var palette: Palette = DARK
        private set

    fun mode(context: Context): Mode = try {
        Mode.valueOf(prefs(context).getString(KEY_MODE, Mode.SYSTEM.name) ?: Mode.SYSTEM.name)
    } catch (e: IllegalArgumentException) {
        Mode.SYSTEM
    }

    fun setMode(context: Context, m: Mode) {
        prefs(context).edit().putString(KEY_MODE, m.name).apply()
        reload(context)
    }

    fun reload(context: Context) {
        palette = when (mode(context)) {
            Mode.DARK -> DARK
            Mode.LIGHT -> LIGHT
            Mode.SYSTEM -> if (systemIsDark(context)) DARK else LIGHT
        }
    }

    private fun systemIsDark(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val PREFS = "shuddhatype"
    private const val KEY_MODE = "theme_mode"
}
