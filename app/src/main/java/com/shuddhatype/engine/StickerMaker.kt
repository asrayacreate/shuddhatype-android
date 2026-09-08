package com.shuddhatype.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
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
 * Looking designed without an illustrator comes down to what a canvas can do on
 * its own: a diagonal gradient rather than a flat fill, the sticker's own emoji
 * scattered faintly as a pattern, confetti, a double frame, a glow behind the
 * emoji and a shadow under the letters. The emoji does the work of an
 * illustration, because the system font draws it in full colour.
 */
object StickerMaker {

    /** Square, and large enough to stay sharp when a chat app scales it up. */
    const val SIZE = 512

    /**
     * A greeting nobody should have to type. The emoji is not stored with the
     * words — it is looked up from them, so a ready phrase and the same phrase
     * typed by hand come out identical.
     */
    class Phrase(val text: String, val emoji: String)

    /**
     * What a phrase should look like. [quiet] drops the confetti and the
     * pattern: a condolence covered in party dots is worse than no sticker.
     */
    private class Look(val emoji: String, val style: Int, val quiet: Boolean = false)

    /**
     * Words to a look, most specific first — जन्मदिनको शुभकामना has to reach
     * जन्मदिन before it reaches शुभकामना. Devanagari and Roman keys both,
     * because the phrase on a sticker is rarely spelled the same way twice.
     *
     * Declared above [PHRASES] on purpose: the ready phrases read their emoji
     * out of this table while the object is being built.
     */
    private val LOOKS: List<Pair<List<String>, Look>> = listOf(
        listOf("जन्मदिन", "जन्म दिन", "janmadin", "janmadhin", "birthday")
            to Look("🎂", 5),
        listOf("दशैं", "दशैँ", "दसैं", "दसैँ", "विजयादशमी", "dashain", "dasain")
            to Look("🌺", 0),
        listOf("तिहार", "दीपावली", "दिपावली", "भाइटीका", "tihar", "deepawali", "bhaitika")
            to Look("🪔", 1),
        listOf("नयाँ वर्ष", "नयाँ बर्ष", "नववर्ष", "naya varsha", "naya barsha", "new year")
            to Look("✨", 5),
        listOf("विवाह", "बिवाह", "बिहे", "vivah", "bibah", "wedding", "marriage")
            to Look("💍", 0),
        listOf("श्रद्धाञ्जली", "श्रद्धान्जली", "शोक", "दुःखद", "निधन", "shraddhanjali")
            to Look("🕯️", 2, quiet = true),
        listOf("बधाई", "badhai", "congrat")
            to Look("🎊", 3),
        listOf("सफलता", "शुभेच्छा", "safalta", "success", "good luck", "best of luck")
            to Look("⭐", 1),
        listOf("धन्यवाद", "आभार", "dhanyabad", "dhanyabaad", "thank")
            to Look("🙏", 4),
        listOf("नमस्ते", "नमस्कार", "namaste", "namaskar")
            to Look("🙏", 4),
        listOf("स्वागत", "swagat", "welcome")
            to Look("🌸", 4),
        listOf("प्रभात", "बिहानी", "good morning", "shubha prabhat")
            to Look("☀️", 3),
        listOf("रात्री", "राति", "good night", "shubha ratri")
            to Look("🌙", 1),
        listOf("माया", "प्रेम", "वार्षिकोत्सव", "love", "anniversary")
            to Look("❤️", 0),
        listOf("माफ", "माफी", "क्षमा", "sorry")
            to Look("🥺", 2),
        listOf("शुभकामना", "शुभ कामना", "subhakamana", "shubhakamana", "greetings")
            to Look("🎉", 5),
        listOf("शुभ", "shubha")
            to Look("🌟", 5)
    )

    /**
     * Past this length the text is a message, not a greeting, and the words
     * need the whole square more than they need a picture on top of them.
     */
    private const val EMOJI_MAX_CHARS = 40

    private fun lookFor(text: String): Look? {
        val t = text.lowercase()
        for ((keys, look) in LOOKS) if (keys.any { t.contains(it) }) return look
        return null
    }

    /**
     * The emoji these words earn. Anything unrecognised still gets one — a
     * user's own words used to come out as colour alone, sitting next to ready
     * phrases that each had a picture, and looked like the poor relation.
     */
    fun emojiFor(text: String): String {
        val t = text.trim()
        if (t.isEmpty()) return ""
        lookFor(t)?.let { return it.emoji }
        return if (t.length <= EMOJI_MAX_CHARS) "✨" else ""
    }

    /**
     * The designs, with the one that suits these words first. The row is a row
     * of choices either way; putting the right answer at the head of it just
     * means the first thing seen is usually the thing sent.
     */
    fun styleOrder(text: String): List<Int> {
        val first = (lookFor(text)?.style ?: 0).coerceIn(0, STYLES.lastIndex)
        return listOf(first) + STYLES.indices.filter { it != first }
    }

    /**
     * What the ✍️ tab shows before anything is typed, so it is never an empty
     * screen asking the user to do the work first.
     *
     * The Nepali festivals lead, because that is when these actually get sent
     * and because that is the gap: every keyboard has a birthday sticker, none
     * has तिहार.
     */
    val PHRASES: List<Phrase> = listOf(
        "जन्मदिनको शुभकामना",
        "बधाई छ",
        "धन्यवाद",
        "दशैंको शुभकामना",
        "तिहारको शुभकामना",
        "नयाँ वर्षको शुभकामना",
        "शुभ विवाह",
        "शुभ रात्री",
        "शुभ प्रभात",
        "स्वागतम्",
        "माफ गर्नुहोस्",
        "सफलताको शुभकामना",
        "Happy Birthday",
        "Happy Anniversary",
        "Congratulations",
        "Thank You"
    ).map { Phrase(it, emojiFor(it)) }

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
        val quiet = lookFor(words)?.quiet == true

        drawBackground(canvas, s, words, emoji, quiet)
        if (emoji.isNotEmpty()) drawEmoji(canvas, emoji, s)
        drawText(canvas, wrap(words), s, emoji.isNotEmpty(), quiet)
        return bmp
    }

    // ---- background ----

    private fun drawBackground(
        canvas: Canvas,
        s: Style,
        seedFrom: String,
        emoji: String,
        quiet: Boolean
    ) {
        val pad = SIZE * 0.05f
        val box = RectF(pad, pad, SIZE - pad, SIZE - pad)
        val radius = SIZE * 0.12f

        // Corner to corner rather than top to bottom: a diagonal reads as light
        // falling across the card, where a vertical one reads as two colours.
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                box.left, box.top, box.right, box.bottom,
                s.bgTop, s.bgBottom, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(box, radius, radius, fill)

        val clip = Path().apply { addRoundRect(box, radius, radius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)

        if (!quiet) {
            drawPattern(canvas, box, emoji, seedFrom)
            drawConfetti(canvas, box, s, seedFrom)
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

        // A second, finer rule just inside it. Two lines read as a frame where
        // one line reads as an outline.
        edge.strokeWidth = SIZE * 0.0035f
        val inner = SIZE * 0.050f
        canvas.drawRoundRect(
            RectF(box.left + inner, box.top + inner, box.right - inner, box.bottom - inner),
            radius * 0.66f, radius * 0.66f, edge
        )
    }

    /**
     * The sticker's own emoji, small and faint, around the edges. It costs
     * nothing, it is different on every sticker without anyone choosing it,
     * and it keeps clear of the middle where the words go.
     */
    private fun drawPattern(canvas: Canvas, box: RectF, emoji: String, seedFrom: String) {
        if (emoji.isEmpty()) return
        val rng = Random(seedFrom.hashCode() * 31 + 7)
        val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = SIZE * 0.125f
            textAlign = Paint.Align.CENTER
        }
        // Corners and the two side margins — outside the 0.12..0.88 band the
        // text is set in.
        val spots = listOf(
            0.13f to 0.13f, 0.87f to 0.15f,
            0.05f to 0.52f, 0.95f to 0.50f,
            0.12f to 0.88f, 0.88f to 0.87f
        )
        canvas.saveLayerAlpha(box.left, box.top, box.right, box.bottom, 40)
        for ((fx, fy) in spots) {
            val x = SIZE * fx + (rng.nextFloat() - 0.5f) * SIZE * 0.03f
            val y = SIZE * fy + (rng.nextFloat() - 0.5f) * SIZE * 0.03f
            canvas.save()
            canvas.rotate(-26f + rng.nextFloat() * 52f, x, y)
            canvas.drawText(emoji, x, y, mark)
            canvas.restore()
        }
        canvas.restore()
    }

    /**
     * Seeded from the text, so the same words always give the same sticker.
     * Confetti that reshuffles on every redraw makes the preview and the thing
     * that gets sent look like two different files.
     */
    private fun drawConfetti(canvas: Canvas, box: RectF, s: Style, seedFrom: String) {
        val rng = Random(seedFrom.hashCode())
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = s.speck }
        repeat(30) {
            val x = box.left + rng.nextFloat() * box.width()
            val y = box.top + rng.nextFloat() * box.height()
            canvas.drawCircle(x, y, SIZE * (0.004f + rng.nextFloat() * 0.012f), dot)
        }
    }

    /**
     * Drawn with the system font, which carries the colour emoji — one glyph
     * gives the sticker an illustration nobody had to draw. The glow behind it
     * is what stops it looking pasted on.
     */
    private fun drawEmoji(canvas: Canvas, emoji: String, s: Style) {
        val cx = SIZE / 2f
        val cy = SIZE * 0.215f
        val r = SIZE * 0.145f

        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, r,
                s.glow, s.glow and 0x00FFFFFF, Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, r, halo)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = SIZE * 0.21f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(emoji, cx, cy + SIZE * 0.075f, paint)
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

    private fun drawText(
        canvas: Canvas,
        lines: List<String>,
        s: Style,
        hasEmoji: Boolean,
        quiet: Boolean
    ) {
        if (lines.isEmpty()) return
        val pad = SIZE * 0.05f
        val top = if (hasEmoji) SIZE * 0.36f else SIZE * 0.16f
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

        // A short rule under the words, drawn only when there is clearly room
        // for it. A flourish that collides with a descender is not a flourish.
        if (quiet) return
        val ruleY = y - lineHeight + size * 0.46f
        if (ruleY < SIZE - pad - SIZE * 0.085f) {
            val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = s.border
                strokeWidth = SIZE * 0.013f
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawLine(
                SIZE / 2f - SIZE * 0.085f, ruleY,
                SIZE / 2f + SIZE * 0.085f, ruleY, rule
            )
        }
    }

    private class Style(
        val bgTop: Int,
        val bgBottom: Int,
        val fg: Int,
        val border: Int,
        val speck: Int,
        val shadow: Int,
        val glow: Int
    )

    private const val LINE_SPACING = 1.22f

    private fun c(hex: String) = Color.parseColor(hex)

    /**
     * Six designs that read as six different things in a row of previews, not
     * one thing in six colours. The first is the brand's own red — the one
     * people will recognise as having come from this keyboard.
     */
    private val STYLES = listOf(
        Style(c("#E8333A"), c("#9A1446"), Color.WHITE, c("#6EFFFFFF"), c("#37FFFFFF"), c("#50000000"), c("#4DFFFFFF")),
        Style(c("#1A1A1A"), c("#000000"), c("#FFD54A"), c("#55FFD54A"), c("#2AFFD54A"), c("#66000000"), c("#4DFFD54A")),
        Style(c("#FFFFFF"), c("#EDEDED"), c("#141414"), c("#33000000"), c("#22000000"), c("#22000000"), c("#1F000000")),
        Style(c("#FF9A3D"), c("#E8333A"), Color.WHITE, c("#66FFFFFF"), c("#33FFFFFF"), c("#55000000"), c("#4DFFFFFF")),
        Style(c("#22B37F"), c("#0B6E52"), Color.WHITE, c("#66FFFFFF"), c("#33FFFFFF"), c("#55000000"), c("#4DFFFFFF")),
        Style(c("#4B5BE0"), c("#7B2FF7"), Color.WHITE, c("#66FFFFFF"), c("#33FFFFFF"), c("#55000000"), c("#4DFFFFFF"))
    )
}
