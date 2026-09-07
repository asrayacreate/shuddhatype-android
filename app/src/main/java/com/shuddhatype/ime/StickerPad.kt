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
 * It reads what the user has just written rather than asking them to type it
 * again, because they have already written it — the phrase is sitting in the
 * message box above the keyboard. Tapping a design sends the picture; the
 * typed text stays where it is, so nothing is lost if they change their mind.
 *
 * Previews are rendered small and on demand. Six 512² bitmaps held for a pad
 * that may never be opened is a lot of memory to spend on a maybe.
 */
class StickerPad(
    context: Context,
    private val source: () -> String,
    private val onPick: (Bitmap, String) -> Unit
) : LinearLayout(context) {

    private val prompt: TextView
    private val row: LinearLayout
    private var text: String = ""

    init {
        orientation = VERTICAL
        prompt = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(6))
        }
        addView(prompt, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        row = LinearLayout(context).apply { orientation = HORIZONTAL }
        addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun applyTheme() {
        setBackgroundColor(Theme.palette.bg)
        prompt.setTextColor(Theme.palette.labelMod)
    }

    /** Re-read the field and redraw. Called every time the tab is opened. */
    fun refresh() {
        text = source().trim()
        row.removeAllViews()

        if (text.isEmpty()) {
            prompt.text = "पहिले केही लेख्नुहोस् — त्यही स्टिकर बन्छ।"
            return
        }
        prompt.text = "\"$text\" — डिजाइन छान्नुहोस्"

        for (i in 0 until StickerMaker.styleCount) {
            val bmp = StickerMaker.render(text, i) ?: continue
            row.addView(ImageView(context).apply {
                // Scaled for the strip; the full-size bitmap is made again on
                // tap, so what gets sent is never the thumbnail.
                setImageBitmap(Bitmap.createScaledBitmap(bmp, dp(96), dp(96), true))
                bmp.recycle()
                setPadding(dp(6), dp(4), dp(6), dp(10))
                isClickable = true
                setOnClickListener {
                    StickerMaker.render(text, i)?.let { full -> onPick(full, text) }
                }
                layoutParams = LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
                )
            })
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
