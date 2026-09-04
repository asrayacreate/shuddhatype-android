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
    suggestions: SuggestionBar
) : LinearLayout(context) {

    private val keys = KeyGrid(context, actions)
    private val emojiPad = EmojiPad(context, actions)

    init {
        orientation = VERTICAL
        setBackgroundColor(BG)
        addView(suggestions, LayoutParams(LayoutParams.MATCH_PARENT, dp(SuggestionBar.HEIGHT_DP)))
        addView(keys, LayoutParams(LayoutParams.MATCH_PARENT, dp(KEYBOARD_HEIGHT_DP)))
        addView(emojiPad, LayoutParams(LayoutParams.MATCH_PARENT, dp(KEYBOARD_HEIGHT_DP)))
        emojiPad.visibility = GONE
        keys.onEmojiRequest = { showEmoji(true) }
        emojiPad.onBack = { showEmoji(false) }
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
        private val BG = Color.parseColor("#0F1115")
    }
}

private class Key(
    val label: String,
    val output: String,
    val kind: Kind = Kind.LETTER,
    val weight: Float = 1f
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
    var onEmojiRequest: (() -> Unit)? = null

    private val rows: List<List<Key>>
        get() = when {
            symbols -> symbolRows
            mode == 2 -> devaRows
            else -> romanRows
        }

    private fun digits(s: String) = s.map { Key(it.toString(), it.toString(), Key.Kind.DIGIT) }

    private val romanRows = listOf(
        digits("1234567890"),
        "qwertyuiop".map { Key(it.toString(), it.toString()) },
        "asdfghjkl".map { Key(it.toString(), it.toString()) },
        listOf(Key("⇧", "", Key.Kind.SHIFT, 1.5f)) +
            "zxcvbnm".map { Key(it.toString(), it.toString()) } +
            listOf(Key("⌫", "", Key.Kind.BACKSPACE, 1.5f)),
        bottomRow()
    )

    // Direct Devanagari for people who already type it. Ordered by frequency
    // of use, not by the traditional alphabet order — the common consonants
    // belong under the fingers.
    private val devaRows = listOf(
        digits("१२३४५६७८९०"),
        "ािीुूेैोौ".map { Key(it.toString(), it.toString()) },
        "कखगघचछजझटठ".map { Key(it.toString(), it.toString()) },
        listOf(Key("⇧", "", Key.Kind.SHIFT, 1.5f)) +
            "यरलवसशहँं्".map { Key(it.toString(), it.toString()) } +
            listOf(Key("⌫", "", Key.Kind.BACKSPACE, 1.5f)),
        bottomRow()
    )

    // Second Devanagari page reached with ⇧ — the letters that did not fit.
    private val devaShiftRow = "डतथदधनपबभम".map { Key(it.toString(), it.toString()) }

    private val symbolRows = listOf(
        digits("1234567890"),
        "@#\$_&-+()/".map { Key(it.toString(), it.toString(), Key.Kind.PUNCT) },
        listOf(Key("=\\<", "", Key.Kind.SHIFT, 1.5f)) +
            "*\"':;!?".map { Key(it.toString(), it.toString(), Key.Kind.PUNCT) } +
            listOf(Key("⌫", "", Key.Kind.BACKSPACE, 1.5f)),
        listOf(
            Key("ABC", "", Key.Kind.LAYER, 1.5f),
            Key("☺", "", Key.Kind.EMOJI, 1.2f),
            Key(",", ",", Key.Kind.PUNCT),
            Key("space", " ", Key.Kind.SPACE, 4f),
            Key(".", ".", Key.Kind.PUNCT),
            Key("↵", "", Key.Kind.ENTER, 1.5f)
        )
    )

    private fun bottomRow() = listOf(
        Key(FLAG, "", Key.Kind.MODE, 1.4f),
        Key("123", "", Key.Kind.LAYER, 1.4f),
        Key("☺", "", Key.Kind.EMOJI, 1.2f),
        Key("space", " ", Key.Kind.SPACE, 4f),
        Key("।", "।", Key.Kind.PUNCT),
        Key("↵", "", Key.Kind.ENTER, 1.5f)
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
        color = Color.WHITE
    }

    private val repeatHandler = Handler(Looper.getMainLooper())
    private var repeatRunnable: Runnable? = null

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
        // Narrower gap than before: the space between keys was eating room the
        // key face could use, which is what made the buttons look small.
        val gap = dp(1.5f)
        val radius = dp(7f)
        rows.forEachIndexed { ri, row ->
            visibleRow(ri, row).forEach { k ->
                keyPaint.color = when {
                    k === pressed -> KEY_PRESSED
                    k.kind == Key.Kind.LETTER || k.kind == Key.Kind.DIGIT -> KEY
                    k.kind == Key.Kind.ENTER -> KEY_ACCENT
                    else -> KEY_MOD
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
                textPaint.color = if (isChar) Color.WHITE else LABEL_MOD

                val cy = r.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
                canvas.drawText(label, r.centerX(), cy, textPaint)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val key = keyAt(event.x, event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = key; invalidate()
                key?.let { fire(it); if (it.kind == Key.Kind.BACKSPACE) startRepeat(it) }
            }
            MotionEvent.ACTION_MOVE -> {
                // Sliding off a key cancels it. Fingers drift on small keys, and
                // committing the key they drifted onto is worse than doing nothing.
                if (key !== pressed) { stopRepeat(); pressed = null; invalidate() }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                stopRepeat(); pressed = null; invalidate()
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

    override fun onDetachedFromWindow() { stopRepeat(); super.onDetachedFromWindow() }

    private fun dp(v: Float) = v * resources.displayMetrics.density

    companion object {
        private const val REPEAT_DELAY_MS = 400L
        private const val REPEAT_MS = 55L
        private const val FLAG = "🇳🇵"
        private val KEY = Color.parseColor("#272B33")
        private val KEY_MOD = Color.parseColor("#1A1D23")
        private val KEY_PRESSED = Color.parseColor("#3A3F49")
        private val KEY_ACCENT = Color.parseColor("#E8333A")
        private val LABEL_MOD = Color.parseColor("#C3C7CE")
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

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.parseColor("#0F1115"))
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
        addView(bottomBar(), LayoutParams(LayoutParams.MATCH_PARENT, dp(46)))
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
        setBackgroundColor(Color.parseColor("#1A1D23"))
        addView(barKey("ABC", 2f) { onBack?.invoke() })
        addView(barKey("space", 4f) { actions.onDirectText(" ") })
        addView(barKey("⌫", 2f) { actions.onBackspace() })
    }

    private fun barKey(label: String, weight: Float, click: () -> Unit) =
        TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#C3C7CE"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            isClickable = true
            setOnClickListener { click() }
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, weight)
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
