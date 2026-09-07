package com.shuddhatype.ime

import android.content.Context
import android.graphics.Bitmap
import android.view.Gravity
import android.util.TypedValue
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.shuddhatype.engine.StickerMaker

/**
 * The word-sticker tab.
 *
 * Two ways in, and it always offers one of them:
 *
 *   - **What you just wrote.** The phrase is already sitting in the message
 *     box; asking the user to type it a second time would be absurd.
 *   - **Ready greetings.** दशैं, तिहार, जन्मदिन — the messages everyone sends
 *     anyway. Without these the tab opens empty on the day someone first finds
 *     it, and an empty screen that asks you to do work first is a screen you
 *     do not come back to.
 *
 * Previews are rendered small and on demand. Holding six 512² bitmaps for a
 * pad that may never be opened is a lot of memory to spend on a maybe.
 */
class StickerPad(
    context: Context,
    private val source: () -> String,
    private val onPick: (Bitmap, String) -> Unit
) : LinearLayout(context) {

    private val prompt: TextView
    private val chips: LinearLayout
    private val row: LinearLayout

    private var text: String = ""
    private var emoji: String = ""

    init {
        orientation = VERTICAL

        prompt = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(4))
        }
        addView(prompt, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        // The greetings, as a scrolling strip of words above the designs.
        chips = LinearLayout(context).apply { orientation = HORIZONTAL }
        addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        row = LinearLayout(context).apply { orientation = HORIZONTAL }
        addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun applyTheme() {
        setBackgroundColor(Theme.palette.bg)
        prompt.setTextColor(Theme.palette.labelMod)
        buildChips()
    }

    /** Re-read the field and redraw. Called every time the tab is opened. */
    fun refresh() {
        val typed = source().trim()
        // What the user wrote wins: they wrote it just now, on purpose.
        if (typed.isNotEmpty()) select(typed, "") else select(StickerMaker.PHRASES[0])
        buildChips()
    }

    private fun select(phrase: StickerMaker.Phrase) = select(phrase.text, phrase.emoji)

    private fun select(newText: String, newEmoji: String) {
        text = newText
        emoji = newEmoji
        prompt.text = "\"$text\" — डिजाइन छान्नुहोस्"
        buildDesigns()
    }

    private fun buildChips() {
        chips.removeAllViews()
        val p = Theme.palette
        val typed = source().trim()
        if (typed.isNotEmpty()) chips.addView(chip("✍️ $typed", typed == text) { select(typed, "") })
        for (ph in StickerMaker.PHRASES) {
            chips.addView(chip("${ph.emoji} ${ph.text}", ph.text == text) { select(ph) })
        }
        chips.setBackgroundColor(p.bg)
    }

    private fun chip(label: String, chosen: Boolean, click: () -> Unit) =
        TextView(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            val p = Theme.palette
            setTextColor(if (chosen) p.accent else p.labelMod)
            setBackgroundColor(if (chosen) p.key else p.keyMod)
            setPadding(dp(12), dp(7), dp(12), dp(7))
            isClickable = true
            setOnClickListener { click() }
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(6) }
        }

    private fun buildDesigns() {
        row.removeAllViews()
        if (text.isEmpty()) return
        for (i in 0 until StickerMaker.styleCount) {
            val bmp = StickerMaker.render(text, i, emoji) ?: continue
            row.addView(ImageView(context).apply {
                // Scaled for the strip; the full-size bitmap is rendered again
                // on tap, so what gets sent is never the thumbnail.
                setImageBitmap(Bitmap.createScaledBitmap(bmp, dp(104), dp(104), true))
                bmp.recycle()
                setPadding(dp(6), dp(4), dp(6), dp(8))
                isClickable = true
                setOnClickListener {
                    StickerMaker.render(text, i, emoji)?.let { full -> onPick(full, text) }
                }
                layoutParams = LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
                )
            })
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
