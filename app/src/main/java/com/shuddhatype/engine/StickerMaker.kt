package com.shuddhatype.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.random.Random

/**
 * Turns words into a picture worth sending.
 *
 * The idea: nobody needs another pack of drawn stickers. What a Nepali writer
 * cannot get anywhere is *their own words* set well — and, just as often, the
 * dozen greetings everyone sends anyway, which no keyboard offers in Nepali at
 * all. Both come from the same place here.
 *
 * Looking designed without an illustrator comes down to four things a canvas
 * can do on its own: a gradient rather than a flat fill, confetti, an inset
 * border, and a shadow under the letters. The fifth is the emoji, drawn large
 * at the top — the system font renders it in full colour, so one glyph does
 * the work of an illustration.
 */
object StickerMaker {

    /** Square, and large enough to stay sharp when a chat app scales it up. */
    const val SIZE = 512

    /**
     * A greeting nobody should have to type. Each carries the emoji drawn on
     * it: the picture and the words are one thing, not text with a decoration
     * bolted on afterwards.
     */
    class Phrase(val text: String, val emoji: String)

    /**
     * What the ✍️ tab shows before anything is typed, so it is never an empty
     * screen asking the user to do the work first.
     *
     * The Nepali festivals lead, because that is when these actually get sent
     * and because that is the gap: every keyboard has a birthday sticker, none
     * has तिहार.
     */
    val PHRASES = listOf(
        Phrase("जन्मदिनको शुभकामना", "🎂"),
        Phrase("बधाई छ", "🎉"),
        Phrase("धन्यवाद", "🙏"),
        Phrase("दशैंको शुभकामना", "🌸"),
        Phrase("तिहारको शुभकामना", "🪔"),
        Phrase("नयाँ वर्षको शुभकामना", "✨"),
        Phrase("शुभ विवाह", "💍"),
        Phrase("शुभ रात्री", "🌙"),
        Phrase("शुभ प्रभात", "☀️"),
        Phrase("स्वागतम्", "🙏"),
        Phrase("माफ गर्नुहोस्", "🥺"),
        Phrase("सफलताको शुभकामना", "⭐"),
        Phrase("Happy Birthday", "🎂"),
        Phrase("Happy Anniversary", "❤️"),
        Phrase("Congratulations", "🎊"),
        Phrase("Thank You", "🙏")
    )

    /** How many designs the pad offers. A getter — see [STYLES]. */
    val styleCount: Int get() = STYLES.size

    /**
     * [emoji] may be empty, in which case the words get the whole square.
     * Null comes back for blank text, which is the caller's cue to say so
     * rather than send an empty box.
     */
    fun render(text: String, style: Int, emoji: String = ""): Bitmap? {
        val words = text.trim()
        if (words.isEmpty()) return null

        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val s = STYLES[style.coerceIn(0, STYLES.lastIndex)]

        drawBackground(canvas, s, words)
        if (emoji.isNotEmpty()) drawEmoji(canvas, emoji)
        drawText(canvas, wrap(words), s, emoji.isNotEmpty())
        return bmp
    }

    // ---- background ----

    private fun drawBackground(canvas: Canvas, s: Style, seedFrom: String) {
        val pad = SIZE * 0.05f
        val box = RectF(pad, pad, SIZE - pad, SIZE - pad)
        val radius = SIZE * 0.12f

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                box.left, box.top, box.left, box.bottom,
                s.bgTop, s.bgBottom, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(box, radius, radius, fill)

        // Seeded from the text, so the same words always give the same sticker.
        // Confetti that reshuffles on every redraw makes the preview and the
        // thing that gets sent look like two different files.
        val rng = Random(seedFrom.hashCode())
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = s.speck }
        canvas.save()
        canvas.clipRect(box)
        repeat(30) {
            val x = box.left + rng.nextFloat() * box.width()
            val y = box.top + rng.nextFloat() * box.height()
            canvas.drawCircle(x, y, SIZE * (0.004f + rng.nextFloat() * 0.012f), dot)
        }
        canvas.restore()

        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = SIZE * 0.010f
            color = s.border
        }
        val inset = SIZE * 0.026f
        canvas.drawRoundRect(
            RectF(box.left + inset, box.top + inset, box.right - inset, box.bottom - inset),
            radius * 0.8f, radius * 0.8f, edge
        )
    }

    /**
     * Drawn with the system font, which carries the colour emoji — one glyph
     * gives the sticker an illustration nobody had to draw.
     */
    private fun drawEmoji(canvas: Canvas, emoji: String) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = SIZE * 0.20f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(emoji, SIZE / 2f, SIZE * 0.255f, paint)
    }

    // ---- text ----

    /**
     * Two lines wherever there are enough words for it. Devanagari set across
     * the full width of a square reads small; broken in half it reads large,
     * and large is the whole point of a sticker.
     */
    private fun wrap(text: String): List<String> {
        val w = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            w.size <= 2 -> w
            w.size == 3 -> listOf(w[0], w[1] + " " + w[2])
            else -> {
                val half = (w.size + 1) / 2
                listOf(w.take(half).joinToString(" "), w.drop(half).joinToString(" "))
            }
        }
    }

    private fun drawText(canvas: Canvas, lines: List<String>, s: Style, hasEmoji: Boolean) {
        if (lines.isEmpty()) return
        val pad = SIZE * 0.05f
        val top = if (hasEmoji) SIZE * 0.33f else SIZE * 0.16f
        val room = (SIZE - pad) - top - SIZE * 0.06f
        val width = SIZE * 0.76f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        // Step down rather than solve for it: Devanagari's tall matras and
        // descenders change the measured height in ways a formula would miss.
        var size = SIZE * 0.24f
        while (size > SIZE * 0.035f) {
            paint.textSize = size
            val widest = lines.maxOf { paint.measureText(it) }
            if (widest <= width && lines.size * size * LINE_SPACING <= room) break
            size -= SIZE * 0.006f
        }

        val lineHeight = size * LINE_SPACING
        var y = top + (room - lines.size * lineHeight) / 2f + lineHeight * 0.78f

        for (line in lines) {
            paint.color = s.shadow
            canvas.drawText(line, SIZE / 2f + size * 0.045f, y + size * 0.045f, paint)
            paint.color = s.fg
            canvas.drawText(line, SIZE / 2f, y, paint)
            y += lineHeight
        }
    }

    private class Style(
        val bgTop: Int,
        val bgBottom: Int,
        val fg: Int,
        val border: Int,
        val speck: Int,
        val shadow: Int
    )

    private const val LINE_SPACING = 1.22f

    private fun c(hex: String) = Color.parseColor(hex)

    /**
     * Six designs that read as six different things in a row of previews, not
     * one thing in six colours. The first is the brand's own red — the one
     * people will recognise as having come from this keyboard.
     */
    private val STYLES = listOf(
        Style(c("#E8333A"), c("#9A1446"), Color.WHITE, c("#6EFFFFFF"), c("#37FFFFFF"), c("#50000000")),
        Style(c("#1A1A1A"), c("#000000"), c("#FFD54A"), c("#55FFD54A"), c("#2AFFD54A"), c("#66000000")),
        Style(c("#FFFFFF"), c("#EDEDED"), c("#141414"), c("#33000000"), c("#22000000"), c("#22000000")),
        Style(c("#FF9A3D"), c("#E8333A"), Color.WHITE, c("#66FFFFFF"), c("#33FFFFFF"), c("#55000000")),
        Style(c("#22B37F"), c("#0B6E52"), Color.WHITE, c("#66FFFFFF"), c("#33FFFFFF"), c("#55000000")),
        Style(c("#4B5BE0"), c("#7B2FF7"), Color.WHITE, c("#66FFFFFF"), c("#33FFFFFF"), c("#55000000"))
    )
}
