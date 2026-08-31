package com.shuddhatype.ime

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Three ranked candidates above the keys.
 *
 * The bar always occupies its height, even when empty. Letting it collapse and
 * reappear shifts every key down and up while the user is mid-word, which is
 * far more annoying than a strip of empty space.
 */
class SuggestionBar(context: Context) : HorizontalScrollView(context) {

    var onPick: ((String) -> Unit)? = null

    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    init {
        isFillViewport = true
        isHorizontalScrollBarEnabled = false
        setBackgroundColor(BG)
        addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        )
        minimumHeight = dp(HEIGHT_DP)
    }

    fun show(words: List<String>) {
        row.removeAllViews()
        words.forEachIndexed { i, w ->
            if (i > 0) row.addView(divider())
            row.addView(chip(w, isTop = i == 0))
        }
    }

    fun clear() = row.removeAllViews()

    private fun chip(word: String, isTop: Boolean) = TextView(context).apply {
        text = word
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
        setTextColor(if (isTop) TEXT_TOP else TEXT)
        // The top candidate is what pressing space will commit, so it is marked.
        // Everything else stays visually quiet.
        typeface = if (isTop) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        setPadding(dp(18), dp(10), dp(18), dp(10))
        isClickable = true
        setOnClickListener { onPick?.invoke(word) }
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
    }

    private fun divider() = View(context).apply {
        setBackgroundColor(DIVIDER)
        layoutParams = LinearLayout.LayoutParams(dp(1), LinearLayout.LayoutParams.MATCH_PARENT)
            .apply { topMargin = dp(10); bottomMargin = dp(10) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val HEIGHT_DP = 46
        private val BG = Color.parseColor("#16181C")
        private val TEXT = Color.parseColor("#B9BCC2")
        private val TEXT_TOP = Color.parseColor("#FFFFFF")
        private val DIVIDER = Color.parseColor("#2A2E35")
    }
}
