package com.shuddhatype.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * The key grid.
 *
 * Drawn on a single Canvas rather than built from child Views. A keyboard
 * redraws on every touch, and 30-odd nested Views make that measurably slower
 * on the low-end phones this app is aimed at.
 *
 * Three modes, cycled by the mode key:
 *   🇳🇵  Roman in, शुद्ध Devanagari out (the main path)
 *   EN  Roman in, Roman out — for English words, no transliteration
 *   दे  direct Devanagari, for people who already type it
 *
 * Plus a symbols layer (123) and an emoji pad, so a whole message can be
 * written without ever leaving the keyboard.
 */
@SuppressLint("ViewConstructor")
class KeyboardLayoutView(
    context: Context,
    private val actions: KeyboardActions,
    private val suggestions: SuggestionBar
) : LinearLayout(context) {

    private val keys = KeyGrid(context, actions)
    private val emojiPad = EmojiPad(context, actions)

    /** Set by the service; the sticker tab is inert until they are. */
    var stickerSource: (() -> String)? = null
    var onStickerPicked: ((android.graphics.Bitmap, String) -> String?)? = null

    init {
        orientation = VERTICAL
        addView(suggestions, LayoutParams(LayoutParams.MATCH_PARENT, dp(SuggestionBar.HEIGHT_DP)))
        addView(keys, LayoutParams(LayoutParams.MATCH_PARENT, dp(KEYBOARD_HEIGHT_DP)))
        addView(emojiPad, LayoutParams(LayoutParams.MATCH_PARENT, dp(KEYBOARD_HEIGHT_DP)))
        emojiPad.visibility = GONE
        keys.onEmojiRequest = { showEmoji(true) }
        emojiPad.onBack = { showEmoji(false) }
        emojiPad.stickerSource = { stickerSource?.invoke() ?: "" }
        emojiPad.onStickerPicked = { b, t -> onStickerPicked?.invoke(b, t) }
        applyTheme()
    }

    /**
     * The settings screen and the keyboard live in different processes' minds:
     * an IME is not on screen while its settings are being changed. Re-reading
     * the palette every time the keyboard is shown is both the simplest place
     * to catch a change and the only one that is always correct.
     */
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) applyTheme()
    }

    private fun applyTheme() {
        Theme.reload(context)
        setBackgroundColor(Theme.palette.bg)
        suggestions.applyTheme()
        keys.invalidate()
        emojiPad.applyTheme()
    }

    private fun showEmoji(show: Boolean) {
        keys.visibility = if (show) GONE else VISIBLE
        emojiPad.visibility = if (show) VISIBLE else GONE
        if (show) emojiPad.onShown()
    }

    fun setSensitive(sensitive: Boolean) = keys.setSensitive(sensitive)

    fun startLatin() = keys.startLatin()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        // Raised from 252. At 252 the board sat at roughly a quarter of the
        // screen — noticeably shorter than the stock keyboard, which made the
        // keys feel cramped and easy to miss.
        const val KEYBOARD_HEIGHT_DP = 300
    }
}

/**
 * [hint] is what a long press produces — the small grey character printed
 * above the label. It is what keeps the 123 layer off the common path: a
 * bracket, a colon or a Devanagari digit is one hold away instead of a layer
 * switch, a tap and a switch back.
 */
private class Key(
    val label: String,
    val output: String,
    val kind: Kind = Kind.LETTER,
    val weight: Float = 1f,
    val hint: String = "",
    /** Set when the hint opens something instead of typing something. */
    val hintKind: Kind? = null
) {
    enum class Kind { LETTER, DIGIT, SHIFT, BACKSPACE, SPACE, ENTER, MODE, LAYER, EMOJI, PUNCT, DATE, TEMPLATE }
    var bounds = RectF()
}

@SuppressLint("ViewConstructor")
private class KeyGrid(context: Context, private val actions: KeyboardActions) : View(context) {

    /** 0 = नेपाली (transliterate), 1 = English (raw), 2 = direct Devanagari. */
    private var mode = 0
    private var symbols = false
    private var shifted = false
    private var sensitive = false
    private var pressed: Key? = null
    private var longPressFired = false
    var onEmojiRequest: (() -> Unit)? = null

    private val rows: List<List<Key>>
        get() = when {
            symbols -> symbolRows
            mode == 2 -> devaRows
            else -> romanRows
        }

    /** [hints] is read position by position; a shorter string leaves the rest bare. */
    private fun digits(s: String, hints: String = "") = s.mapIndexed { i, c ->
        Key(c.toString(), c.toString(), Key.Kind.DIGIT, 1f, hints.getOrNull(i)?.toString() ?: "")
    }

    private fun letters(s: String, hints: String = "") = s.mapIndexed { i, c ->
        Key(c.toString(), c.toString(), Key.Kind.LETTER, 1f, hints.getOrNull(i)?.toString() ?: "")
    }

    /**
     * Like [letters], but each entry is a whole string, so a key can carry a
     * conjunct the alphabet writes as one letter but Unicode stores as three
     * (क्ष is क + ् + ष). Write "क|ख" to hang ख on a hold of क.
     *
     * Safe on the दे page because mode 2 sends the key's whole output through
     * onDirectText; only the नेपाली page reduces a key to its first character.
     */
    private fun glyphs(vararg spec: String) = spec.map {
        val parts = it.split("|")
        Key(parts[0], parts[0], Key.Kind.LETTER, 1f, parts.getOrNull(1) ?: "")
    }

    // The digit row types Latin numerals, because that is what phone numbers,
    // prices and forms expect. Holding a key gives the Devanagari numeral for
    // the times a document wants २०८२ instead of 2082.
    private val romanRows = listOf(
        digits("1234567890", "१२३४५६७८९०"),
        letters("qwertyuiop", "@#\$_&-+()/"),
        letters("asdfghjkl", "*\"':;!?~="),
        listOf(Key("⇧", "", Key.Kind.SHIFT, 1.5f)) +
            letters("zxcvbnm", "%\\|<>[]") +
            listOf(Key("⌫", "", Key.Kind.BACKSPACE, 1.5f)),
        bottomRow()
    )

    // Direct Devanagari across two pages.
    //
    // Layout rule, learned the hard way: **every key that existed before keeps
    // the position it had.** An earlier version of this page added ृ and ् into
    // the matra row, which pushed all nine matras one place along. The row was
    // more complete and completely unusable — a thumb that knew where ो was hit
    // ौ instead. Muscle memory is the whole value of a direct page, so the
    // twenty-five characters that were missing had to go somewhere else.
    //
    // Where they went: ⇧ turns over all three letter rows instead of one, which
    // buys a second page; and five rare marks hang on long presses, each on a
    // key of its own kind — ञ under ज, ः under ं, ऽ under ्, ॐ under ँ, ऋ under
    // ृ. That is exactly enough room for all 63 characters with none of the old
    // ones moved.
    private val devaRows = listOf(
        digits("१२३४५६७८९०", "1234567890"),
        letters("ािीुूेैोौ"),
        glyphs("क", "ख", "ग", "घ", "च", "छ", "ज|ञ", "झ", "ट", "ठ"),
        listOf(Key("⇧", "", Key.Kind.SHIFT, 1.5f)) +
            glyphs("य", "र", "ल", "व", "स", "श", "ह", "ँ|ॐ", "ं|ः", "्|ऽ") +
            listOf(Key("⌫", "", Key.Kind.BACKSPACE, 1.5f)),
        bottomRow()
    )

    // Page two, reached with ⇧.
    //
    // Row two is the old shift row, character for character and in the same
    // place — ⇧ then that row is a movement a user already has. The other two
    // rows are new: the vowels, the six consonants that were missing, and the
    // three conjuncts the alphabet writes as single letters.
    private val devaShiftRows = listOf(
        glyphs("अ", "आ", "इ", "ई", "उ", "ऊ", "ए", "ऐ", "ओ"),
        letters("डतथदधनपबभम"),
        listOf(Key("⇧", "", Key.Kind.SHIFT, 1.5f)) +
            glyphs("औ", "ृ|ऋ", "ङ", "ढ", "ण", "फ", "ष", "क्ष", "त्र", "ज्ञ") +
            listOf(Key("⌫", "", Key.Kind.BACKSPACE, 1.5f))
    )

    private val symbolRows = listOf(
        digits("1234567890", "१२३४५६७८९०"),
        "@#\$_&-+()/".map { Key(it.toString(), it.toString(), Key.Kind.PUNCT) },
        listOf(Key("=\\<", "", Key.Kind.SHIFT, 1.5f)) +
            "*\"':;!?".map { Key(it.toString(), it.toString(), Key.Kind.PUNCT) } +
            listOf(Key("⌫", "", Key.Kind.BACKSPACE, 1.5f)),
        // मिति earns a key of its own here rather than a hold: a hold is only
        // discoverable once you already know it exists, and nobody guesses that
        // a keyboard can write today's Bikram Sambat date. Emoji moves onto a
        // hold of ABC, matching what 123 does on the letters page.
        listOf(
            Key("ABC", "", Key.Kind.LAYER, 1.2f, "☺", Key.Kind.EMOJI),
            Key("मिति", "", Key.Kind.DATE, 1.3f),
            // ढाँचा gets a key for the same reason मिति did. It was first put
            // in settings, which meant seven steps to write a निवेदन — open the
            // app, scroll, pick, copy, come back, paste. From here the letter
            // lands in the field you are already writing in, blanks and all,
            // which is also where it is easiest to fill them.
            Key("ढाँचा", "", Key.Kind.TEMPLATE, 1.5f),
            Key("space", " ", Key.Kind.SPACE, 4.6f),
            Key(".", ".", Key.Kind.PUNCT, 0.85f, ","),
            Key("↵", "", Key.Kind.ENTER, 1.1f)
        )
    )

    // Five keys. Emoji moved onto a hold of the 123 key: it is opened a few
    // times a day, while space is hit on every word, and a key that is not
    // there is the only kind that costs nothing. Space lands at 61% — the
    // widest it goes before the mode and layer keys drop under 40dp, which is
    // the point where a thumb starts missing them.
    private fun bottomRow() = listOf(
        Key(FLAG, "", Key.Kind.MODE, 1.15f),
        Key("123", "", Key.Kind.LAYER, 1.15f, "☺", Key.Kind.EMOJI),
        Key("space", " ", Key.Kind.SPACE, 6.8f),
        Key("।", "।", Key.Kind.PUNCT, 0.9f, ","),
        Key("↵", "", Key.Kind.ENTER, 1.2f)
    )

    // The flag marks the mode that actually makes this keyboard Nepali. दे is
    // also Devanagari, but the flag belongs on the शुद्ध transliteration page.
    private fun modeLabel() = when (mode) {
        0 -> FLAG
        1 -> "EN"
        else -> "दे"
    }

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private val repeatHandler = Handler(Looper.getMainLooper())
    private var repeatRunnable: Runnable? = null
    private var longPressRunnable: Runnable? = null

    fun setSensitive(value: Boolean) {
        sensitive = value
        // A password field must never be transliterated or learned from, so it
        // gets the plain Roman keyboard whatever the user last chose.
        if (value) { mode = 1; symbols = false }
        actions.onModeChanged(isNepali())
        requestLayout(); invalidate()
    }

    /**
     * Open on the English page for a field that only accepts Latin.
     *
     * Without this, a shortcut key typed on the नेपाली page arrives as
     * Devanagari — pp becomes प्प — and settings rejects it for containing no
     * a-z. The user sees a refusal with no visible cause, having watched
     * themselves type exactly what was asked for.
     *
     * Unlike [setSensitive] this is only a starting page, not a lock: the मोड
     * key still works, because the field below it takes Nepali and the user
     * moves between the two.
     */
    fun startLatin() {
        if (sensitive) return
        mode = 1
        symbols = false
        shifted = false
        actions.onModeChanged(isNepali())
        requestLayout(); invalidate()
    }

    private fun isNepali() = mode == 0 && !symbols && !sensitive

    private fun cycleMode() {
        if (sensitive) return
        mode = (mode + 1) % 3
        shifted = false
        actions.onModeChanged(isNepali())
        requestLayout(); invalidate()
    }

    private fun toggleLayer() {
        symbols = !symbols
        shifted = false
        actions.onModeChanged(isNepali())
        requestLayout(); invalidate()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val pad = dp(2f)
        val rowH = (height - pad * 2) / rows.size.toFloat()
        rows.forEachIndexed { ri, row ->
            val cells = visibleRow(ri, row)
            val totalWeight = cells.sumOf { it.weight.toDouble() }.toFloat()
            var x = pad
            val usable = width - pad * 2
            cells.forEach { k ->
                val w = usable * (k.weight / totalWeight)
                k.bounds = RectF(x, pad + ri * rowH, x + w, pad + (ri + 1) * rowH)
                x += w
            }
        }
    }

    /** The Devanagari page swaps its consonant row when shift is held. */
    /**
     * ⇧ on the दे page turns over all three letter rows, not one. Ten spare
     * slots were never going to hold the eleven vowels, the six missing
     * consonants and the conjuncts; a whole second page holds them with room
     * left. The digit and bottom rows stay put so ⇧, space and मोड never move
     * under the thumb.
     */
    private fun visibleRow(index: Int, row: List<Key>): List<Key> =
        if (mode == 2 && !symbols && shifted && index in 1..3) devaShiftRows[index - 1] else row

    override fun onDraw(canvas: Canvas) {
        val p = Theme.palette
        // Narrower gap than before: the space between keys was eating room the
        // key face could use, which is what made the buttons look small.
        val gap = dp(1.5f)
        val radius = dp(7f)
        rows.forEachIndexed { ri, row ->
            visibleRow(ri, row).forEach { k ->
                keyPaint.color = when {
                    k === pressed -> p.keyPressed
                    k.kind == Key.Kind.LETTER || k.kind == Key.Kind.DIGIT -> p.key
                    k.kind == Key.Kind.ENTER -> p.accent
                    else -> p.keyMod
                }
                val r = RectF(
                    k.bounds.left + gap, k.bounds.top + gap,
                    k.bounds.right - gap, k.bounds.bottom - gap
                )
                canvas.drawRoundRect(r, radius, radius, keyPaint)

                val isChar = k.kind == Key.Kind.LETTER || k.kind == Key.Kind.DIGIT
                val label = when {
                    k.kind == Key.Kind.MODE -> modeLabel()
                    shifted && k.kind == Key.Kind.LETTER && mode != 2 -> k.label.uppercase()
                    else -> k.label
                }
                textPaint.textSize = when {
                    isChar -> dp(23f)
                    label == FLAG -> dp(20f)
                    // "मिति" is four letters on a narrow key; 16dp overflows it.
                    k.kind == Key.Kind.DATE || k.kind == Key.Kind.TEMPLATE -> dp(14f)
                    else -> dp(16f)
                }
                // Enter sits on the red key in both themes, so its label is the
                // one thing that cannot follow the palette.
                textPaint.color = when {
                    k.kind == Key.Kind.ENTER -> Color.WHITE
                    isChar -> p.label
                    else -> p.labelMod
                }

                if (k.hint.isNotEmpty()) {
                    hintPaint.color = p.labelHint
                    hintPaint.textSize = dp(11f)
                    canvas.drawText(k.hint, r.centerX(), r.top + dp(15f), hintPaint)
                }
                // Nudge the label down so the hint above it does not crowd it.
                val drop = if (k.hint.isNotEmpty()) dp(5f) else 0f
                val cy = r.centerY() + drop - (textPaint.descent() + textPaint.ascent()) / 2
                canvas.drawText(label, r.centerX(), cy, textPaint)
            }
        }
        // Last, so it sits over its neighbours rather than under them.
        pressed?.let { drawPreview(canvas, it, p) }
    }

    /**
     * The raised bubble over the key being held.
     *
     * A finger covers the key it is pressing, so recolouring the key underneath
     * tells the user nothing — they cannot see it. The bubble puts the
     * character somewhere the hand is not.
     *
     * Only characters get one. Nobody needs confirmation that they hit space.
     */
    private fun drawPreview(canvas: Canvas, k: Key, p: Theme.Palette) {
        if (k.kind != Key.Kind.LETTER && k.kind != Key.Kind.DIGIT) return

        val w = k.bounds.width() * 1.45f
        val h = k.bounds.height() * 1.05f
        // Keep the bubble on screen for the outermost keys, where centring it
        // on the key would push half of it past the edge.
        val half = w / 2
        val cx = k.bounds.centerX().coerceIn(half + dp(2f), width - half - dp(2f))

        var top = k.bounds.top - h - dp(3f)
        // The number row has nothing above it, so its bubble drops below.
        if (top < 0f) top = k.bounds.bottom + dp(3f)

        val box = RectF(cx - half, top, cx + half, top + h)
        keyPaint.color = p.keyPreview
        canvas.drawRoundRect(box, dp(9f), dp(9f), keyPaint)

        val label = if (shifted && k.kind == Key.Kind.LETTER && mode != 2)
            k.label.uppercase() else k.label
        textPaint.color = Color.WHITE
        textPaint.textSize = dp(30f)
        val cy = box.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(label, box.centerX(), cy, textPaint)
    }

    /**
     * Backspace fires on press and repeats; everything else fires on release.
     *
     * Committing letters on press felt fractionally quicker, but it leaves no
     * room for a long press — the character is already in the field by the time
     * the finger has been held. Release is what every other keyboard does, so
     * nothing about this reads as slow in the hand.
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val key = keyAt(event.x, event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = key
                longPressFired = false
                invalidate()
                key?.let {
                    if (it.kind == Key.Kind.BACKSPACE) { fire(it); startRepeat(it) }
                    else if (it.hint.isNotEmpty()) startLongPress(it)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                // Sliding off a key cancels it. Fingers drift on small keys, and
                // committing the key they drifted onto is worse than doing nothing.
                if (key !== pressed) {
                    stopRepeat(); stopLongPress(); pressed = null; invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                val k = pressed
                stopRepeat(); stopLongPress()
                if (k != null && !longPressFired && k.kind != Key.Kind.BACKSPACE) fire(k)
                pressed = null; invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                stopRepeat(); stopLongPress(); pressed = null; invalidate()
            }
        }
        return true
    }

    private fun keyAt(x: Float, y: Float): Key? =
        rows.mapIndexed { i, r -> visibleRow(i, r) }.flatten()
            .firstOrNull { it.bounds.contains(x, y) }

    private fun fire(k: Key) {
        when (k.kind) {
            Key.Kind.LETTER -> {
                val ch = if (shifted && mode != 2) k.output.uppercase() else k.output
                // Only the नेपाली page feeds the transliterator. Everything else
                // is exactly what the user asked for and goes straight through.
                if (isNepali()) actions.onLetter(ch[0]) else actions.onDirectText(ch)
                if (shifted && mode != 2) { shifted = false; invalidate() }
            }
            Key.Kind.DIGIT -> actions.onDirectText(k.output)
            Key.Kind.SHIFT -> { shifted = !shifted; requestLayout(); invalidate() }
            Key.Kind.BACKSPACE -> actions.onBackspace()
            Key.Kind.SPACE -> actions.onSpace()
            Key.Kind.ENTER -> actions.onEnter()
            Key.Kind.PUNCT -> actions.onPunctuation(k.output)
            Key.Kind.MODE -> cycleMode()
            Key.Kind.LAYER -> toggleLayer()
            Key.Kind.EMOJI -> onEmojiRequest?.invoke()
            Key.Kind.DATE -> actions.onDate()
            Key.Kind.TEMPLATE -> actions.onTemplate()
        }
    }

    private fun startLongPress(k: Key) {
        stopLongPress()
        longPressRunnable = Runnable {
            longPressFired = true
            if (k.hintKind != null) {
                fire(Key(k.hint, "", k.hintKind))
            } else {
                // Routed through onPunctuation so a half-typed word is committed
                // first — a bracket must not be swallowed into the composing buffer.
                actions.onPunctuation(k.hint)
            }
            pressed = null
            invalidate()
        }.also { repeatHandler.postDelayed(it, LONGPRESS_MS) }
    }

    private fun stopLongPress() {
        longPressRunnable?.let { repeatHandler.removeCallbacks(it) }
        longPressRunnable = null
    }

    private fun startRepeat(k: Key) {
        stopRepeat()
        repeatRunnable = object : Runnable {
            override fun run() {
                fire(k)
                repeatHandler.postDelayed(this, REPEAT_MS)
            }
        }.also { repeatHandler.postDelayed(it, REPEAT_DELAY_MS) }
    }

    private fun stopRepeat() {
        repeatRunnable?.let { repeatHandler.removeCallbacks(it) }
        repeatRunnable = null
    }

    override fun onDetachedFromWindow() {
        stopRepeat(); stopLongPress(); super.onDetachedFromWindow()
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density

    companion object {
        private const val REPEAT_DELAY_MS = 400L
        private const val REPEAT_MS = 55L
        /**
         * Raised from 320ms after stray quotes and brackets started appearing
         * mid-sentence: a thumb resting on a key for a third of a second is
         * still just typing. 420 is past that and short enough that a
         * deliberate hold does not feel stuck.
         */
        private const val LONGPRESS_MS = 420L
        private const val FLAG = "🇳🇵"
    }
}

/**
 * Emoji picker.
 *
 * Plain TextViews in a ScrollView rather than a Canvas grid: this page is not
 * on the typing hot path, and Views give free scrolling and touch feedback.
 */
@SuppressLint("ViewConstructor")
private class EmojiPad(context: Context, private val actions: KeyboardActions) :
    LinearLayout(context) {

    var onBack: (() -> Unit)? = null
    var stickerSource: (() -> String)? = null
    var onStickerPicked: ((android.graphics.Bitmap, String) -> String?)? = null

    private val stickerPad: StickerPad
    private val toast: TextView
    private val tabRow: LinearLayout
    private val scroller: ScrollView
    // Keyed by tab index, not by position in the row: the sticker tab is shown
    // first but keeps the last index, and a list would tie the two together.
    private val tabs = HashMap<Int, TextView>(CATEGORIES.size + 2)
    private val grid: LinearLayout
    private val bar: LinearLayout
    private val barLabels = ArrayList<TextView>(3)

    /** Most recent first. Persisted, so it survives the keyboard being closed. */
    private val recent = ArrayList<String>(RECENT_MAX)
    private var current = 0

    init {
        orientation = VERTICAL
        loadRecent()

        tabRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        // Word stickers lead. They are the one thing here that no other
        // keyboard has, and a new thing put at the end is a new thing nobody
        // finds; emoji are what everyone already knows to look for, so they
        // survive being second. The pad still *opens* on emoji — see show()
        // below — so nothing is taken from the person who came for a smiley.
        tabRow.addView(tab(STICKER_ICON, STICKER_TAB))
        // Recents next, and always present even when empty — a tab that
        // appears only once you have used the pad is a tab nobody finds.
        tabRow.addView(tab(RECENT_ICON, 0))
        CATEGORIES.forEachIndexed { i, c -> tabRow.addView(tab(c.icon, i + 1)) }
        addView(tabRow, LayoutParams(LayoutParams.MATCH_PARENT, dp(40)))

        scroller = ScrollView(context)
        grid = LinearLayout(context).apply { orientation = VERTICAL }
        scroller.addView(grid)
        addView(scroller, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        stickerPad = StickerPad(
            context,
            source = { stickerSource?.invoke() ?: "" },
            onPick = { bmp, label ->
                val problem = onStickerPicked?.invoke(bmp, label)
                if (problem != null) flash(problem)
            }
        )
        stickerPad.visibility = GONE
        addView(stickerPad, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        toast = TextView(context).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            visibility = GONE
        }
        addView(toast, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        bar = bottomBar()
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, dp(46)))

        show(if (recent.isEmpty()) 1 else 0)
        applyTheme()
    }

    /**
     * Called each time the pad is opened. Recents are written by whichever
     * keyboard instance is on screen, so they can change while this one is
     * hidden; re-reading here is what keeps the tab honest.
     */
    fun onShown() {
        loadRecent()
        if (current == 0 || current == STICKER_TAB) show(current)
    }

    fun applyTheme() {
        val p = Theme.palette
        setBackgroundColor(p.bg)
        bar.setBackgroundColor(p.keyMod)
        barLabels.forEach { it.setTextColor(p.labelMod) }
        tabRow.setBackgroundColor(p.keyMod)
        toast.setBackgroundColor(p.keyMod)
        toast.setTextColor(p.labelMod)
        stickerPad.applyTheme()
        paintTabs()
    }

    /**
     * A keyboard has no Toast of its own worth using — a system toast from an
     * IME lands behind the keyboard on some phones. A strip inside the pad is
     * where the user is already looking.
     */
    private fun flash(message: String) {
        toast.text = message
        toast.visibility = VISIBLE
        toast.removeCallbacks(hideToast)
        toast.postDelayed(hideToast, 2600)
    }

    private val hideToast = Runnable { toast.visibility = GONE }

    // ---- categories ----

    private fun tab(icon: String, index: Int) = TextView(context).apply {
        text = icon
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        isClickable = true
        setOnClickListener { show(index) }
        layoutParams = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
        tabs[index] = this
    }

    private fun paintTabs() {
        val p = Theme.palette
        for ((i, t) in tabs) {
            // The chosen tab gets the keyboard's own background, so it reads as
            // continuous with the grid below it rather than as another button.
            t.setBackgroundColor(if (i == current) p.bg else p.keyMod)
        }
    }

    private fun show(index: Int) {
        current = index
        val sticker = index == STICKER_TAB
        scroller.visibility = if (sticker) GONE else VISIBLE
        stickerPad.visibility = if (sticker) VISIBLE else GONE
        if (sticker) {
            stickerPad.applyTheme()
            stickerPad.refresh()
            paintTabs()
            return
        }
        grid.removeAllViews()
        val items = if (index == 0) recent else CATEGORIES[index - 1].emoji
        if (items.isEmpty()) {
            grid.addView(TextView(context).apply {
                text = "पछिल्लो प्रयोग गरेका इमोजी यहाँ देखिनेछन्।"
                setTextColor(Theme.palette.labelMod)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(28), dp(16), dp(16))
            })
        } else {
            items.chunked(COLUMNS).forEach { row ->
                grid.addView(LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    row.forEach { e -> addView(cell(e)) }
                    // Pad the last row so its cells keep the others' width.
                    repeat(COLUMNS - row.size) { addView(spacer()) }
                })
            }
        }
        paintTabs()
    }

    // ---- recents ----

    private fun loadRecent() {
        val raw = prefs().getString(RECENT_KEY, "") ?: ""
        recent.clear()
        if (raw.isNotEmpty()) recent.addAll(raw.split(SEP).filter { it.isNotEmpty() })
    }

    /**
     * Newest first, no duplicates, capped at two rows. Two rows because the
     * point of this tab is the handful of emoji someone actually reuses; a
     * longer list is just the full pad again, in a worse order.
     */
    private fun remember(e: String) {
        recent.remove(e)
        recent.add(0, e)
        while (recent.size > RECENT_MAX) recent.removeAt(recent.size - 1)
        prefs().edit().putString(RECENT_KEY, recent.joinToString(SEP)).apply()
        if (current == 0) show(0)
    }

    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---- cells ----

    private fun cell(e: String) = TextView(context).apply {
        text = e
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
        setPadding(0, dp(8), 0, dp(8))
        isClickable = true
        setOnClickListener {
            actions.onDirectText(e)
            remember(e)
        }
        layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
    }

    private fun spacer() = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
    }

    private fun bottomBar() = LinearLayout(context).apply {
        orientation = HORIZONTAL
        addView(barKey("ABC", 2f) { onBack?.invoke() })
        addView(barKey("space", 4f) { actions.onDirectText(" ") })
        addView(barKey("⌫", 2f) { actions.onBackspace() })
    }

    private fun barKey(label: String, weight: Float, click: () -> Unit) =
        TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            isClickable = true
            setOnClickListener { click() }
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, weight)
            barLabels.add(this)
        }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private class Category(val icon: String, val emoji: List<String>)

    companion object {
        private const val COLUMNS = 8
        private const val RECENT_MAX = 16
        private const val RECENT_ICON = "🕐"
        private const val STICKER_ICON = "✍️"

        /** After the six emoji groups and the recents tab. */
        private const val STICKER_TAB = 7
        private const val PREFS = "shuddhatype"
        private const val RECENT_KEY = "recent_emoji"
        private const val SEP = "\u0001"

        /**
         * Six groups, each labelled by one of its own emoji rather than a word.
         * A picture tab needs no translation and costs no width — and the pad
         * is used by people writing Nepali, English and Roman Nepali alike.
         *
         * The faces group is first and is where the pad opens on a fresh
         * install, because it is what most people came for.
         */
        private val CATEGORIES = listOf(
            Category("😀", listOf(
                "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣",
                "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰",
                "😘", "😗", "😙", "😚", "😋", "😛", "😝", "😜",
                "🤪", "🤨", "🧐", "🤓", "😎", "🥳", "😏", "😒",
                "😞", "😔", "😟", "😕", "🙁", "😣", "😖", "😫",
                "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬",
                "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥",
                "🤗", "🤔", "🤭", "🤫", "🤥", "😶", "😐", "😑",
                "😬", "🙄", "😯", "😴", "🤤", "😪", "😵", "🤐",
                "🥴", "🤢", "🤮", "🤧", "😷", "🤒", "🤕", "🤑"
            )),
            Category("🙏", listOf(
                "👍", "👎", "👌", "🤝", "🙏", "👏", "🙌", "💪",
                "✌️", "🤞", "👋", "🤲", "☝️", "👉", "👈", "👆",
                "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "💔",
                "💯", "🔥", "✨", "⭐", "🌟", "💫", "🎉", "🎊",
                "🙋"
            )),
            Category("🌸", listOf(
                "🌸", "🌼", "🌹", "🌻", "🍀", "🌿", "🌳", "🌈",
                "☀️", "🌙", "⛅", "🌧️", "❄️", "⚡", "💧", "🌊"
            )),
            Category("🍎", listOf(
                "🍎", "🍌", "🍇", "🍉", "🥭", "🍊", "🍓", "🥥",
                "🍚", "🍛", "🍲", "🫓", "🥘", "🍜", "☕", "🍵",
                "🎂"
            )),
            Category("🏠", listOf(
                "🏠", "🏫", "🏥", "🏔️", "🛕", "🚩", "🇳🇵",
                "🚗", "🏍️", "🚌", "✈️", "🚲", "⚽", "🏏", "🎵"
            )),
            Category("✅", listOf(
                "✅", "❌", "❓", "❗", "💤", "🔔", "🕉️",
                "📱", "💻", "📷", "📚", "✏️", "📝", "💰", "🎁"
            ))
        )
    }
}
