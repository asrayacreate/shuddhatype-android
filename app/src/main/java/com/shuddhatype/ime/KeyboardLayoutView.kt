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

    init {
        orientation = VERTICAL
        addView(suggestions, LayoutParams(LayoutParams.MATCH_PARENT, dp(SuggestionBar.HEIGHT_DP)))
        addView(keys, LayoutParams(LayoutParams.MATCH_PARENT, dp(KEYBOARD_HEIGHT_DP)))
        addView(emojiPad, LayoutParams(LayoutParams.MATCH_PARENT, dp(KEYBOARD_HEIGHT_DP)))
        emojiPad.visibility = GONE
        keys.onEmojiRequest = { showEmoji(true) }
        emojiPad.onBack = { showEmoji(false) }
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
    }

    fun setSensitive(sensitive: Boolean) = keys.setSensitive(sensitive)

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
    val hint: String = ""
) {
    enum class Kind { LETTER, DIGIT, SHIFT, BACKSPACE, SPACE, ENTER, MODE, LAYER, EMOJI, PUNCT }
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

    // Direct Devanagari for people who already type it. Ordered by frequency
    // of use, not by the traditional alphabet order — the common consonants
    // belong under the fingers.
    private val devaRows = listOf(
        digits("१२३४५६७८९०", "1234567890"),
        letters("ािीुूेैोौ"),
        letters("कखगघचछजझटठ"),
        listOf(Key("⇧", "", Key.Kind.SHIFT, 1.5f)) +
            letters("यरलवसशहँं्") +
            listOf(Key("⌫", "", Key.Kind.BACKSPACE, 1.5f)),
        bottomRow()
    )

    // Second Devanagari page reached with ⇧ — the letters that did not fit.
    private val devaShiftRow = letters("डतथदधनपबभम")

    private val symbolRows = listOf(
        digits("1234567890", "१२३४५६७८९०"),
        "@#\$_&-+()/".map { Key(it.toString(), it.toString(), Key.Kind.PUNCT) },
        listOf(Key("=\\<", "", Key.Kind.SHIFT, 1.5f)) +
            "*\"':;!?".map { Key(it.toString(), it.toString(), Key.Kind.PUNCT) } +
            listOf(Key("⌫", "", Key.Kind.BACKSPACE, 1.5f)),
        listOf(
            Key("ABC", "", Key.Kind.LAYER, 1.5f),
            Key("☺", "", Key.Kind.EMOJI, 1.1f),
            Key(",", ",", Key.Kind.PUNCT, 0.9f),
            Key("space", " ", Key.Kind.SPACE, 4.6f),
            Key(".", ".", Key.Kind.PUNCT, 0.9f),
            Key("↵", "", Key.Kind.ENTER, 1.4f)
        )
    )

    // Space now takes about 42% of the row, up from 38%. The comma and the
    // danda sit either side of it — the two marks a Nepali sentence actually
    // needs — and each holds a second mark: , -> ? and । -> .
    private fun bottomRow() = listOf(
        Key(FLAG, "", Key.Kind.MODE, 1.2f),
        Key("123", "", Key.Kind.LAYER, 1.2f),
        Key("☺", "", Key.Kind.EMOJI, 1.0f),
        Key(",", ",", Key.Kind.PUNCT, 0.9f, "?"),
        Key("space", " ", Key.Kind.SPACE, 4.8f),
        Key("।", "।", Key.Kind.PUNCT, 0.9f, "."),
        Key("↵", "", Key.Kind.ENTER, 1.4f)
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
    private fun visibleRow(index: Int, row: List<Key>): List<Key> =
        if (mode == 2 && !symbols && shifted && index == 2) devaShiftRow else row

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
        }
    }

    private fun startLongPress(k: Key) {
        stopLongPress()
        longPressRunnable = Runnable {
            longPressFired = true
            // Routed through onPunctuation so a half-typed word is committed
            // first — a bracket must not be swallowed into the composing buffer.
            actions.onPunctuation(k.hint)
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
        /** Long enough not to trip on a slow tap, short enough not to feel stuck. */
        private const val LONGPRESS_MS = 320L
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

    private val bar: LinearLayout
    private val barLabels = ArrayList<TextView>(3)

    init {
        orientation = VERTICAL
        val scroll = ScrollView(context)
        val grid = LinearLayout(context).apply { orientation = VERTICAL }
        EMOJI.chunked(COLUMNS).forEach { rowChars ->
            grid.addView(LinearLayout(context).apply {
                orientation = HORIZONTAL
                rowChars.forEach { e -> addView(cell(e)) }
            })
        }
        scroll.addView(grid)
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        bar = bottomBar()
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, dp(46)))
        applyTheme()
    }

    fun applyTheme() {
        val p = Theme.palette
        setBackgroundColor(p.bg)
        bar.setBackgroundColor(p.keyMod)
        barLabels.forEach { it.setTextColor(p.labelMod) }
    }

    private fun cell(e: String) = TextView(context).apply {
        text = e
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
        setPadding(0, dp(8), 0, dp(8))
        isClickable = true
        setOnClickListener { actions.onDirectText(e) }
        layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
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

    companion object {
        private const val COLUMNS = 8
        private val EMOJI = listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣",
            "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰",
            "😘", "😗", "😙", "😚", "😋", "😛", "😝", "😜",
            "🤪", "🤨", "🧐", "🤓", "😎", "🥳", "😏", "😒",
            "😞", "😔", "😟", "😕", "🙁", "😣", "😖", "😫",
            "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬",
            "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥",
            "🤗", "🤔", "🤭", "🤫", "🤥", "😶", "😐", "😑",
            "😬", "🙄", "😯", "😴", "🤤", "😪", "😵", "🤐",
            "🥴", "🤢", "🤮", "🤧", "😷", "🤒", "🤕", "🤑",
            "👍", "👎", "👌", "🤝", "🙏", "👏", "🙌", "💪",
            "✌️", "🤞", "👋", "🤲", "☝️", "👉", "👈", "👆",
            "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "💔",
            "💯", "🔥", "✨", "⭐", "🌟", "💫", "🎉", "🎊",
            "🌸", "🌼", "🌹", "🌻", "🍀", "🌿", "🌳", "🌈",
            "☀️", "🌙", "⛅", "🌧️", "❄️", "⚡", "💧", "🌊",
            "🍎", "🍌", "🍇", "🍉", "🥭", "🍊", "🍓", "🥥",
            "🍚", "🍛", "🍲", "🫓", "🥘", "🍜", "☕", "🍵",
            "🏠", "🏫", "🏥", "🏔️", "🛕", "🚩", "🇳🇵", "🎂",
            "🚗", "🏍️", "🚌", "✈️", "🚲", "⚽", "🏏", "🎵",
            "📱", "💻", "📷", "📚", "✏️", "📝", "💰", "🎁",
            "✅", "❌", "❓", "❗", "💤", "🔔", "🕉️", "🙋"
        )
    }
}
