package com.shuddhatype.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface

/**
 * Turns whatever the user just wrote into a picture.
 *
 * The idea this exists to serve: nobody needs another pack of drawn stickers.
 * What a Nepali writer cannot get anywhere is *their own words* set well —
 * "बधाई छ", "धन्यवाद", a shop's name — in Devanagari that looks deliberate
 * rather than like a screenshot of a text box. That needs a font and a canvas,
 * not an illustrator, and it never runs out.
 *
 * Everything is drawn at [SIZE]², the square messaging apps expect. Nothing
 * here touches the network or the disk; the caller decides where the bitmap
 * goes.
 */
object StickerMaker {

    /** Square, and large enough to stay sharp when a chat app scales it up. */
    const val SIZE = 512

    /**
     * How many designs the pad offers.
     *
     * A getter, not a stored value: an object initialises its properties in
     * the order they are written, so reading STYLES from up here would read it
     * before it exists.
     */
    val styleCount: Int get() = STYLES.size

    /**
     * [text] is drawn as given — the keyboard has already made it correct
     * Devanagari, and second-guessing it here would only introduce errors.
     * Returns null for blank text, which is the caller's cue to say so rather
     * than send an empty square.
     */
    fun render(text: String, style: Int): Bitmap? {
        val words = text.trim()
        if (words.isEmpty()) return null

        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val s = STYLES[style.coerceIn(0, STYLES.lastIndex)]

        drawBackground(canvas, s)
        drawText(canvas, wrap(words), s)
        return bmp
    }

    // ---- background ----

    private fun drawBackground(canvas: Canvas, s: Style) {
        val pad = SIZE * 0.06f
        val box = RectF(pad, pad, SIZE - pad, SIZE - pad)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        if (s.bgEnd != null) {
            // Top-left to bottom-right, so the lighter corner sits behind the
            // start of the first line where the eye lands.
            paint.shader = LinearGradient(
                box.left, box.top, box.right, box.bottom,
                s.bgStart, s.bgEnd, Shader.TileMode.CLAMP
            )
        } else {
            paint.color = s.bgStart
        }
        canvas.drawRoundRect(box, SIZE * 0.11f, SIZE * 0.11f, paint)

        if (s.border != 0) {
            val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = SIZE * 0.018f
                color = s.border
            }
            val inset = SIZE * 0.025f
            canvas.drawRoundRect(
                RectF(box.left + inset, box.top + inset, box.right - inset, box.bottom - inset),
                SIZE * 0.085f, SIZE * 0.085f, edge
            )
        }
    }

    // ---- text ----

    /**
     * Break on spaces into at most [MAX_LINES]. Devanagari has no hyphenation
     * to fall back on, so a word that will not fit is left long and the size
     * search below shrinks the whole block until it does.
     */
    private fun wrap(text: String): List<String> {
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.size <= 1) return words
        val perLine = Math.ceil(words.size / MAX_LINES.toDouble()).toInt()
        return words.chunked(perLine.coerceAtLeast(1)).map { it.joinToString(" ") }
            .take(MAX_LINES)
    }

    private fun drawText(canvas: Canvas, lines: List<String>, s: Style) {
        val avail = SIZE * 0.78f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        // Largest size that fits both ways. Stepping down rather than solving
        // for it keeps Devanagari's tall matras and descenders honest — they
        // change the measured height in ways a formula would miss.
        var size = SIZE * 0.30f
        while (size > SIZE * 0.05f) {
            paint.textSize = size
            val widest = lines.maxOf { paint.measureText(it) }
            val tall = lines.size * size * LINE_SPACING
            if (widest <= avail && tall <= avail) break
            size -= SIZE * 0.01f
        }

        val lineHeight = size * LINE_SPACING
        val block = lines.size * lineHeight
        var y = SIZE / 2f - block / 2f + lineHeight * 0.78f

        for (line in lines) {
            if (s.shadow != 0) {
                paint.color = s.shadow
                canvas.drawText(line, SIZE / 2f + size * 0.035f, y + size * 0.035f, paint)
            }
            if (s.outline != 0) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = size * 0.11f
                paint.color = s.outline
                canvas.drawText(line, SIZE / 2f, y, paint)
                paint.style = Paint.Style.FILL
            }
            paint.color = s.fg
            canvas.drawText(line, SIZE / 2f, y, paint)
            y += lineHeight
        }
    }

    private class Style(
        val bgStart: Int,
        val bgEnd: Int? = null,
        val fg: Int,
        val border: Int = 0,
        val outline: Int = 0,
        val shadow: Int = 0
    )

    private const val MAX_LINES = 3
    private const val LINE_SPACING = 1.18f

    /**
     * Six designs, chosen so the row of previews looks like six different
     * things rather than one thing in six colours: light and dark, flat and
     * gradient, bordered and outlined.
     *
     * The first is the brand's own red — the one people will recognise as
     * having come from this keyboard.
     */
    private val STYLES = listOf(
        Style(bgStart = Color.parseColor("#E8333A"), fg = Color.WHITE,
              border = Color.parseColor("#33FFFFFF")),
        Style(bgStart = Color.parseColor("#0D0D0D"), fg = Color.parseColor("#FFD54A"),
              border = Color.parseColor("#33FFD54A")),
        Style(bgStart = Color.parseColor("#FFFFFF"), fg = Color.parseColor("#111111"),
              border = Color.parseColor("#22000000"), shadow = Color.parseColor("#22000000")),
        Style(bgStart = Color.parseColor("#FF8A3D"), bgEnd = Color.parseColor("#E8333A"),
              fg = Color.WHITE, shadow = Color.parseColor("#44000000")),
        Style(bgStart = Color.parseColor("#1FA97C"), bgEnd = Color.parseColor("#0B6E52"),
              fg = Color.WHITE, outline = Color.parseColor("#0B3D2E")),
        Style(bgStart = Color.parseColor("#3B4CCA"), bgEnd = Color.parseColor("#7B2FF7"),
              fg = Color.WHITE, outline = Color.parseColor("#1B2270"))
    )
}
