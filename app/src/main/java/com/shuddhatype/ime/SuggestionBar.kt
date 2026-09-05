package com.shuddhatype.ime

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The ranked candidates above the keys.
 *
 * The bar always occupies its height, even when empty. Letting it collapse and
 * reappear shifts every key down and up while the user is mid-word, which is
 * far more annoying than a strip of empty space.
 */
class SuggestionBar(context: Context) : HorizontalScrollView(context) {

    var onPick: ((String) -> Unit)? = null

    /** Kept so a theme change can rebuild the chips in the new colours. */
    private var current: List<String> = emptyList()

    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    init {
        isFillViewport = false
        isHorizontalScrollBarEnabled = false
        // A hard edge gives no sign that more candidates are off-screen; a fade
        // does, without spending a key's worth of width on an arrow.
        isHorizontalFadingEdgeEnabled = true
        setFadingEdgeLength(dp(28))
        addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        )
        minimumHeight = dp(HEIGHT_DP)
        applyTheme()
    }

    fun applyTheme() {
        setBackgroundColor(Theme.palette.barBg)
        show(current)
    }

    fun show(words: List<String>) {
        current = words
        row.removeAllViews()
        words.forEachIndexed { i, w ->
            if (i > 0) row.addView(divider())
            row.addView(chip(w, isTop = i == 0))
        }
        scrollTo(0, 0)
    }

    fun clear() {
        current = emptyList()
        row.removeAllViews()
    }

    private fun chip(word: String, isTop: Boolean) = TextView(context).apply {
        val p = Theme.palette
        text = word
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
        setTextColor(if (isTop) p.barTextTop else p.barText)
        // The top candidate is what pressing space will commit, so it is marked.
        // Everything else stays visually quiet.
        typeface = if (isTop) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        setPadding(dp(16), dp(10), dp(16), dp(10))
        isClickable = true
        setOnClickListener { onPick?.invoke(word) }
        // Wrap, not weight: six candidates squeezed into one screen width are
        // unreadable and un-tappable. Let them size to the word and scroll.
        minWidth = dp(64)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
    }

    private fun divider() = View(context).apply {
        setBackgroundColor(Theme.palette.divider)
        layoutParams = LinearLayout.LayoutParams(dp(1), LinearLayout.LayoutParams.MATCH_PARENT)
            .apply { topMargin = dp(10); bottomMargin = dp(10) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val HEIGHT_DP = 46
    }
}
