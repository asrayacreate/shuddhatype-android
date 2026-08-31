package com.shuddhatype.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

/**
 * The key grid.
 *
 * Drawn on a single Canvas rather than built from child Views. A keyboard
 * redraws on every touch, and 30-odd nested Views make that measurably slower
 * on the low-end phones this app is aimed at.
 *
 * Two layouts: QWERTY (the primary path — type Roman, get Nepali) and a direct
 * Devanagari layout for people who prefer it. v1 ships both and nothing else.
 */
@SuppressLint("ViewConstructor")
class KeyboardLayoutView(
    context: Context,
    private val actions: KeyboardActions,
    suggestions: SuggestionBar
) : LinearLayout(context) {

    private val keys = KeyGrid(context, actions)

    init {
        orientation = VERTICAL
        setBackgroundColor(BG)
        addView(suggestions, LayoutParams(LayoutParams.MATCH_PARENT, dp(SuggestionBar.HEIGHT_DP)))
        addView(keys, LayoutParams(LayoutParams.MATCH_PARENT, dp(KEYBOARD_HEIGHT_DP)))
    }

    fun setSensitive(sensitive: Boolean) = keys.setSensitive(sensitive)
    fun toggleScript() = keys.toggleScript()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val KEYBOARD_HEIGHT_DP = 210
        private val BG = Color.parseColor("#0F1115")
    }
}

private class Key(
    val label: String,
    val output: String,
    val kind: Kind = Kind.LETTER,
    val weight: Float = 1f
) {
    enum class Kind { LETTER, SHIFT, BACKSPACE, SPACE, ENTER, SCRIPT, PUNCT }
    var bounds = RectF()
}

@SuppressLint("ViewConstructor")
private class KeyGrid(context: Context, private val actions: KeyboardActions) : View(context) {

    private var devanagari = false
    private var shifted = false
    private var sensitive = false
    private var pressed: Key? = null

    private val rows: List<List<Key>> get() = if (devanagari) devaRows else romanRows

    private val romanRows = listOf(
        "qwertyuiop".map { Key(it.toString(), it.toString()) },
        "asdfghjkl".map { Key(it.toString(), it.toString()) },
        listOf(Key("⇧", "", Key.Kind.SHIFT, 1.5f)) +
            "zxcvbnm".map { Key(it.toString(), it.toString()) } +
            listOf(Key("⌫", "", Key.Kind.BACKSPACE, 1.5f)),
        listOf(
            Key("क", "", Key.Kind.SCRIPT, 1.5f),
            Key(",", ",", Key.Kind.PUNCT),
            Key("space", " ", Key.Kind.SPACE, 5f),
            Key("।", "।", Key.Kind.PUNCT),
            Key("↵", "", Key.Kind.ENTER, 1.5f)
        )
    )

    // Direct Devanagari for people who already type it. Ordered by frequency
    // of use, not by the traditional alphabet order — the common consonants
    // belong under the fingers.
    private val devaRows = listOf(
        "ािीुूेैोौ".map { Key(it.toString(), it.toString()) },
        "कखगघचछजझटठ".map { Key(it.toString(), it.toString()) },
        "डतथदधनपबभम".map { Key(it.toString(), it.toString()) },
        listOf(Key("⇧", "", Key.Kind.SHIFT, 1.5f)) +
            "यरलवसशहँं्".map { Key(it.toString(), it.toString()) } +
            listOf(Key("⌫", "", Key.Kind.BACKSPACE, 1.5f)),
        listOf(
            Key("A", "", Key.Kind.SCRIPT, 1.5f),
            Key(",", ",", Key.Kind.PUNCT),
            Key("space", " ", Key.Kind.SPACE, 5f),
            Key("।", "।", Key.Kind.PUNCT),
            Key("↵", "", Key.Kind.ENTER, 1.5f)
        )
    )

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
    }

    private val repeatHandler = Handler(Looper.getMainLooper())
    private var repeatRunnable: Runnable? = null

    fun setSensitive(value: Boolean) { sensitive = value; invalidate() }

    fun toggleScript() { devanagari = !devanagari; requestLayout(); invalidate() }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val pad = dp(3f)
        val rowH = (height - pad * 2) / rows.size.toFloat()
        rows.forEachIndexed { ri, row ->
            val totalWeight = row.sumOf { it.weight.toDouble() }.toFloat()
            var x = pad.toFloat()
            val usable = width - pad * 2
            row.forEach { k ->
                val w = usable * (k.weight / totalWeight)
                k.bounds = RectF(x, pad + ri * rowH, x + w, pad + (ri + 1) * rowH)
                x += w
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        val gap = dp(2.5f)
        val radius = dp(6f)
        rows.forEach { row ->
            row.forEach { k ->
                keyPaint.color = when {
                    k === pressed -> KEY_PRESSED
                    k.kind == Key.Kind.LETTER -> KEY
                    k.kind == Key.Kind.ENTER -> KEY_ACCENT
                    else -> KEY_MOD
                }
                val r = RectF(
                    k.bounds.left + gap, k.bounds.top + gap,
                    k.bounds.right - gap, k.bounds.bottom - gap
                )
                canvas.drawRoundRect(r, radius, radius, keyPaint)

                textPaint.textSize = if (k.kind == Key.Kind.LETTER) dp(19f) else dp(14f)
                textPaint.color = if (k.kind == Key.Kind.LETTER) Color.WHITE else LABEL_MOD
                val label = if (shifted && k.kind == Key.Kind.LETTER) k.label.uppercase() else k.label
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
        rows.flatten().firstOrNull { it.bounds.contains(x, y) }

    private fun fire(k: Key) {
        when (k.kind) {
            Key.Kind.LETTER -> {
                val ch = if (shifted) k.output.uppercase() else k.output
                actions.onLetter(ch[0])
                if (shifted) { shifted = false; invalidate() }
            }
            Key.Kind.SHIFT -> { shifted = !shifted; invalidate() }
            Key.Kind.BACKSPACE -> actions.onBackspace()
            Key.Kind.SPACE -> actions.onSpace()
            Key.Kind.ENTER -> actions.onEnter()
            Key.Kind.PUNCT -> actions.onPunctuation(k.output)
            Key.Kind.SCRIPT -> toggleScript()
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
        private val KEY = Color.parseColor("#272B33")
        private val KEY_MOD = Color.parseColor("#1A1D23")
        private val KEY_PRESSED = Color.parseColor("#3A3F49")
        private val KEY_ACCENT = Color.parseColor("#E8333A")
        private val LABEL_MOD = Color.parseColor("#C3C7CE")
    }
}
