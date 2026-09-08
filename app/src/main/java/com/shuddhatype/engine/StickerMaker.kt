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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Turns words into a picture worth sending.
 *
 * The idea: nobody needs another pack of drawn stickers. What a Nepali writer
 * cannot get anywhere is *their own words* set well — and, just as often, the
 * dozen greetings everyone sends anyway, which no keyboard offers in Nepali at
 * all. Both come from the same place here.
 *
 * **Everything is drawn, nothing is a file.** The first version leaned on the
 * system emoji font for its picture, which cost nothing but gave no control:
 * 💍 came out looking like handcuffs, and there is no emoji at all for a कलश,
 * a जमरा or a लगन गाँठो. So the symbols are drawn with paths here — no assets,
 * no APK growth, and a दशैं sticker that looks like दशैं.
 *
 * The background is drawn too: rays, a सयपत्री garland, पताका bunting, corner
 * marigolds, a star field with a Himalaya along the bottom. Flat colour behind
 * flat text was the one thing that made the old stickers look unfinished.
 */
object StickerMaker {

    /** Square, and large enough to stay sharp when a chat app scales it up. */
    const val SIZE = 512

    /**
     * A greeting nobody should have to type. [emoji] is only the label on the
     * chip in the pad — what gets *drawn* is the motif, chosen from the words.
     */
    class Phrase(val text: String, val emoji: String)

    // ---- motifs (drawn, not emoji) ----

    private const val M_NONE = 0
    private const val M_DIYO = 1        // तिहार
    private const val M_KALASH = 2      // दशैं — कलश with जमरा
    private const val M_GANTHO = 3      // विवाह — लगन गाँठो
    private const val M_MOON = 4        // शुभ रात्री — full moon
    private const val M_SUN = 5         // शुभ प्रभात
    private const val M_CAKE = 6        // जन्मदिन
    private const val M_CANDLE = 7      // शोक
    private const val M_LOTUS = 8       // नमस्ते, धन्यवाद
    private const val M_BURST = 9       // बधाई, पटाका
    private const val M_STAR = 10       // सफलता
    private const val M_HEART = 11      // माया
    private const val M_FLOWER = 12     // स्वागत — सयपत्री
    private const val M_SPARK = 13      // anything else

    // ---- background decoration ----

    private const val D_NONE = 0
    private const val D_RAYS = 1
    private const val D_GARLAND = 2     // सयपत्रीको माला
    private const val D_BUNTING = 3     // पताका
    private const val D_CORNERS = 4
    private const val D_NIGHT = 5       // stars + हिमाल

    /**
     * What a phrase should look like. [quiet] drops every piece of decoration:
     * a condolence covered in confetti would be worse than no sticker at all.
     */
    private class Look(
        val emoji: String,
        val style: Int,
        val motif: Int,
        val deco: Int,
        val quiet: Boolean = false
    )

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
            to Look("🎂", 5, M_CAKE, D_BUNTING),
        listOf("दशैं", "दशैँ", "दसैं", "दसैँ", "विजयादशमी", "dashain", "dasain")
            to Look("🌺", 7, M_KALASH, D_GARLAND),
        listOf("तिहार", "दीपावली", "दिपावली", "भाइटीका", "tihar", "deepawali", "bhaitika")
            to Look("🪔", 1, M_DIYO, D_RAYS),
        listOf("नयाँ वर्ष", "नयाँ बर्ष", "नववर्ष", "naya varsha", "naya barsha", "new year")
            to Look("✨", 5, M_BURST, D_BUNTING),
        listOf("विवाह", "बिवाह", "बिहे", "vivah", "bibah", "wedding", "marriage")
            to Look("💍", 7, M_GANTHO, D_GARLAND),
        listOf("श्रद्धाञ्जली", "श्रद्धान्जली", "शोक", "दुःखद", "निधन", "shraddhanjali")
            to Look("🕯️", 2, M_CANDLE, D_NONE, quiet = true),
        listOf("बधाई", "badhai", "congrat")
            to Look("🎊", 0, M_BURST, D_BUNTING),
        listOf("सफलता", "शुभेच्छा", "safalta", "success", "good luck", "best of luck")
            to Look("⭐", 1, M_STAR, D_RAYS),
        listOf("धन्यवाद", "आभार", "dhanyabad", "dhanyabaad", "thank")
            to Look("🙏", 4, M_LOTUS, D_CORNERS),
        listOf("नमस्ते", "नमस्कार", "namaste", "namaskar")
            to Look("🙏", 4, M_LOTUS, D_CORNERS),
        listOf("स्वागत", "swagat", "welcome")
            to Look("🌸", 3, M_FLOWER, D_CORNERS),
        listOf("प्रभात", "बिहानी", "good morning", "shubha prabhat")
            to Look("☀️", 3, M_SUN, D_RAYS),
        listOf("रात्री", "राति", "good night", "shubha ratri")
            to Look("🌙", 6, M_MOON, D_NIGHT),
        listOf("माया", "प्रेम", "वार्षिकोत्सव", "love", "anniversary")
            to Look("❤️", 0, M_HEART, D_CORNERS),
        listOf("माफ", "माफी", "क्षमा", "sorry")
            to Look("🥺", 2, M_LOTUS, D_CORNERS),
        listOf("शुभकामना", "शुभ कामना", "subhakamana", "shubhakamana", "greetings")
            to Look("🎉", 5, M_BURST, D_BUNTING),
        listOf("शुभ", "shubha")
            to Look("🌟", 5, M_STAR, D_RAYS)
    )

    /** Past this length the words are a message, and they need the whole square. */
    private const val MOTIF_MAX_CHARS = 40

    private fun lookFor(text: String): Look? {
        val t = text.lowercase()
        for ((keys, look) in LOOKS) if (keys.any { t.contains(it) }) return look
        return null
    }

    /** The emoji for this phrase's chip in the pad. Not what gets drawn. */
    fun emojiFor(text: String): String {
        val t = text.trim()
        if (t.isEmpty()) return ""
        lookFor(t)?.let { return it.emoji }
        return if (t.length <= MOTIF_MAX_CHARS) "✨" else ""
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
     * Null comes back for blank text, which is the caller's cue to say so
     * rather than send an empty box. Everything else — symbol, background,
     * whether there is any decoration at all — is decided by the words.
     */
    fun render(text: String, style: Int): Bitmap? {
        val words = text.trim()
        if (words.isEmpty()) return null

        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val s = STYLES[style.coerceIn(0, STYLES.lastIndex)]
        val look = lookFor(words)
        val quiet = look?.quiet == true
        val motif = look?.motif ?: if (words.length <= MOTIF_MAX_CHARS) M_SPARK else M_NONE
        val deco = if (quiet) D_NONE else (look?.deco ?: D_CORNERS)

        val pad = 0.05f * SIZE
        val box = RectF(pad, pad, SIZE - pad, SIZE - pad)
        val radius = 0.12f * SIZE

        card(c, box, radius, s)

        val clip = Path().apply { addRoundRect(box, radius, radius, Path.Direction.CW) }
        c.save()
        c.clipPath(clip)
        if (!quiet) {
            decorate(c, deco, box, s, words)
            speckle(c, box, s, words)
        }
        c.restore()

        var top = 0.16f
        if (motif != M_NONE) {
            val cx = SIZE / 2f
            val cy = 0.250f * SIZE
            val r = 0.150f * SIZE
            glow(c, cx, cy, r * 1.9f, s.glow)
            motif(c, motif, cx, cy, r, s)
            top = 0.44f
        }

        drawText(c, wrap(words), s, top, !quiet)
        frame(c, box, radius, s)
        return bmp
    }

    // ---- paint helpers ----

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        this.style = Paint.Style.STROKE
        this.strokeWidth = width
        this.strokeCap = Paint.Cap.ROUND
    }

    private fun oval(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, color: Int) =
        c.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), fill(color))

    private fun poly(c: Canvas, color: Int, vararg pts: Float) {
        val p = Path()
        p.moveTo(pts[0], pts[1])
        var i = 2
        while (i < pts.size) {
            p.lineTo(pts[i], pts[i + 1]); i += 2
        }
        p.close()
        c.drawPath(p, fill(color))
    }

    /**
     * A soft circle of light. The first version faked this by stacking dozens
     * of translucent circles, which stacked into a dark disc instead; a real
     * radial shader is both correct and one draw call.
     */
    private fun glow(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        if (r <= 0f) return
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(
            cx, cy, r, color, color and 0x00FFFFFF, Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, r, p)
    }

    private fun star(c: Canvas, cx: Float, cy: Float, outer: Float, inner: Float,
                     points: Int, color: Int) {
        val p = Path()
        for (i in 0 until points * 2) {
            val r = if (i % 2 == 0) outer else inner
            val a = -Math.PI / 2 + i * Math.PI / points
            val x = cx + r * cos(a).toFloat()
            val y = cy + r * sin(a).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        c.drawPath(p, fill(color))
    }

    // ---- card, frame, speckle ----

    private fun card(c: Canvas, box: RectF, radius: Float, s: Style) {
        // Corner to corner rather than top to bottom: a diagonal reads as light
        // falling across the card, where a vertical one reads as two colours.
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(
            box.left, box.top, box.right, box.bottom,
            s.bgTop, s.bgBottom, Shader.TileMode.CLAMP
        )
        c.drawRoundRect(box, radius, radius, p)
    }

    private fun frame(c: Canvas, box: RectF, radius: Float, s: Style) {
        val edge = stroke(s.border, 0.010f * SIZE)
        edge.strokeCap = Paint.Cap.BUTT
        val a = 0.026f * SIZE
        c.drawRoundRect(
            RectF(box.left + a, box.top + a, box.right - a, box.bottom - a),
            radius * 0.8f, radius * 0.8f, edge
        )
        // A second, finer rule just inside it. Two lines read as a frame where
        // one line reads as an outline.
        edge.strokeWidth = 0.0035f * SIZE
        val b = 0.050f * SIZE
        c.drawRoundRect(
            RectF(box.left + b, box.top + b, box.right - b, box.bottom - b),
            radius * 0.66f, radius * 0.66f, edge
        )
    }

    /**
     * Seeded from the text, so the same words always give the same sticker.
     * Confetti that reshuffles on every redraw makes the preview and the thing
     * that gets sent look like two different files.
     */
    private fun speckle(c: Canvas, box: RectF, s: Style, seed: String) {
        val rng = Random(seed.hashCode())
        val dot = fill(s.speck)
        repeat(26) {
            val x = box.left + rng.nextFloat() * box.width()
            val y = box.top + rng.nextFloat() * box.height()
            c.drawCircle(x, y, SIZE * (0.004f + rng.nextFloat() * 0.010f), dot)
        }
    }

    // ---- background decoration ----

    private fun decorate(c: Canvas, deco: Int, box: RectF, s: Style, seed: String) {
        when (deco) {
            D_RAYS -> rays(c, s)
            D_GARLAND -> garland(c, box)
            D_BUNTING -> bunting(c, box)
            D_CORNERS -> cornerFlowers(c)
            D_NIGHT -> night(c, box, seed)
        }
    }

    /** Light behind the symbol, the way a festival poster is printed. */
    private fun rays(c: Canvas, s: Style) {
        val cx = SIZE / 2f
        val cy = 0.30f * SIZE
        val col = (s.ink and 0x00FFFFFF) or 0x34000000
        for (i in 0 until 24) {
            val a = i * 2.0 * Math.PI / 24
            poly(
                c, col,
                cx, cy,
                cx + (cos(a - 0.055) * SIZE).toFloat(), cy + (sin(a - 0.055) * SIZE).toFloat(),
                cx + (cos(a + 0.055) * SIZE).toFloat(), cy + (sin(a + 0.055) * SIZE).toFloat()
            )
        }
    }

    /** सयपत्रीको माला across the top. */
    private fun garland(c: Canvas, box: RectF) {
        for (i in 0..18) {
            val t = i / 18f
            val x = box.left + (box.width()) * t
            val y = box.top + 0.055f * SIZE + (sin(t * Math.PI) * 0.075 * SIZE).toFloat()
            val r = 0.030f * SIZE
            c.drawCircle(x, y, r, fill(0xEBF7961E.toInt()))
            c.drawCircle(x, y, r * 0.5f, fill(0xEBFFC83C.toInt()))
            if (i % 3 == 0) oval(c, x, y + r * 2.2f, r * 0.8f, r * 0.8f, 0xDC3CA046.toInt())
        }
    }

    /** पताका. */
    private fun bunting(c: Canvas, box: RectF) {
        val cols = intArrayOf(
            0xEBDC2828.toInt(), 0xEBFFAA1E.toInt(), 0xEBFAF0DC.toInt(),
            0xEB3C9650.toInt(), 0xEB3C6EC8.toInt()
        )
        val n = 9
        for (i in 0 until n) {
            val t0 = i / n.toFloat()
            val t1 = (i + 1) / n.toFloat()
            val xa = box.left + box.width() * t0
            val xb = box.left + box.width() * t1
            val ya = box.top + 0.045f * SIZE + (sin(t0 * Math.PI) * 0.045 * SIZE).toFloat()
            val yb = box.top + 0.045f * SIZE + (sin(t1 * Math.PI) * 0.045 * SIZE).toFloat()
            poly(
                c, cols[i % cols.size],
                xa, ya, xb, yb, (xa + xb) / 2f, (ya + yb) / 2f + 0.085f * SIZE
            )
        }
        val line = stroke(0x96FFFFFF.toInt(), 0.008f * SIZE)
        val p = Path()
        p.moveTo(box.left, box.top + 0.045f * SIZE)
        p.quadTo(SIZE / 2f, box.top + 0.125f * SIZE, box.right, box.top + 0.045f * SIZE)
        c.drawPath(p, line)
    }

    private fun cornerFlowers(c: Canvas) {
        val spots = arrayOf(
            0.135f to 0.135f, 0.865f to 0.135f, 0.130f to 0.870f, 0.870f to 0.865f
        )
        for ((fx, fy) in spots) {
            val cx = fx * SIZE
            val cy = fy * SIZE
            for (i in 0 until 7) {
                val a = i * 2.0 * Math.PI / 7
                c.drawCircle(
                    cx + (cos(a) * 0.030 * SIZE).toFloat(),
                    cy + (sin(a) * 0.030 * SIZE).toFloat(),
                    0.026f * SIZE, fill(0xD2F3821A.toInt())
                )
            }
            c.drawCircle(cx, cy, 0.014f * SIZE, fill(0xEBFFBE32.toInt()))
        }
    }

    /** Star field, and a Himalaya low enough that it never fights the words. */
    private fun night(c: Canvas, box: RectF, seed: String) {
        val rng = Random(seed.hashCode() + 7)
        repeat(26) {
            val x = box.left + rng.nextFloat() * box.width()
            val y = box.top + rng.nextFloat() * box.height() * 0.80f
            val r = SIZE * (0.004f + rng.nextFloat() * 0.008f)
            star(c, x, y, r * 2.4f, r * 0.9f, 4, 0xBEFFFFFF.toInt())
        }
        val peaks = floatArrayOf(
            0.05f, 0.955f, 0.15f, 0.895f, 0.26f, 0.935f, 0.38f, 0.862f,
            0.50f, 0.918f, 0.62f, 0.856f, 0.74f, 0.912f, 0.86f, 0.884f, 0.95f, 0.945f
        )
        val p = Path()
        p.moveTo(0.05f * SIZE, box.bottom)
        var i = 0
        while (i < peaks.size) {
            p.lineTo(peaks[i] * SIZE, peaks[i + 1] * SIZE); i += 2
        }
        p.lineTo(0.95f * SIZE, box.bottom)
        p.close()
        c.drawPath(p, fill(0xFF0E162E.toInt()))
        for ((px, py) in listOf(0.38f to 0.862f, 0.62f to 0.856f)) {
            poly(
                c, 0xC8CDDCFF.toInt(),
                px * SIZE, py * SIZE,
                (px - 0.030f) * SIZE, (py + 0.030f) * SIZE,
                (px + 0.030f) * SIZE, (py + 0.030f) * SIZE
            )
        }
    }

    // ---- the symbols ----

    private fun motif(c: Canvas, which: Int, cx: Float, cy: Float, r: Float, s: Style) {
        when (which) {
            M_DIYO -> diyo(c, cx, cy, r, s)
            M_KALASH -> kalash(c, cx, cy, r, s)
            M_GANTHO -> gantho(c, cx, cy, r, s)
            M_MOON -> moon(c, cx, cy, r)
            M_SUN -> sun(c, cx, cy, r, s)
            M_CAKE -> cake(c, cx, cy, r, s)
            M_CANDLE -> candle(c, cx, cy, r, s)
            M_LOTUS -> lotus(c, cx, cy, r)
            M_BURST -> burst(c, cx, cy, r, s)
            M_STAR -> star(c, cx, cy, r * 1.15f, r * 0.48f, 5, s.ink)
            M_HEART -> heart(c, cx, cy, r, s)
            M_FLOWER -> flower(c, cx, cy, r)
            M_SPARK -> spark(c, cx, cy, r, s)
        }
    }

    /** तिहारको दियो. */
    private fun diyo(c: Canvas, cx: Float, cy: Float, r: Float, s: Style) {
        glow(c, cx, cy - r * 0.80f, r * 1.35f, 0xBEFFB432.toInt())
        poly(
            c, 0xFFFFA828.toInt(),
            cx, cy - r * 1.62f, cx + r * 0.34f, cy - r * 0.78f,
            cx + r * 0.16f, cy - r * 0.36f, cx - r * 0.16f, cy - r * 0.36f,
            cx - r * 0.34f, cy - r * 0.78f
        )
        poly(
            c, 0xFFFFE26E.toInt(),
            cx, cy - r * 1.34f, cx + r * 0.20f, cy - r * 0.72f,
            cx, cy - r * 0.40f, cx - r * 0.20f, cy - r * 0.72f
        )
        poly(
            c, 0xFFFFFFF0.toInt(),
            cx, cy - r * 1.02f, cx + r * 0.09f, cy - r * 0.66f,
            cx, cy - r * 0.44f, cx - r * 0.09f, cy - r * 0.66f
        )
        c.drawLine(cx, cy - r * 0.40f, cx, cy - r * 0.12f, stroke(0xFF6E3C19.toInt(), r * 0.10f))
        c.drawArc(RectF(cx - r, cy - r * 0.46f, cx + r, cy + r * 0.90f), 0f, 180f, true, fill(s.ink))
        oval(c, cx, cy - r * 0.46f, r, r * 0.20f, s.ink2)
        oval(c, cx, cy - r * 0.46f, r * 0.76f, r * 0.12f, 0xFF7E3E1A.toInt())
        c.drawRoundRect(
            RectF(cx - r * 0.46f, cy + r * 0.76f, cx + r * 0.46f, cy + r * 0.96f),
            r * 0.09f, r * 0.09f, fill(s.ink)
        )
    }

    /** दशैं — कलश with जमरा and a red टीका band. */
    private fun kalash(c: Canvas, cx: Float, cy: Float, r: Float, s: Style) {
        for (i in 0 until 9) {
            val k = (i - 4) * 0.24
            val x2 = cx + (sin(k) * r * 1.45).toFloat()
            val y2 = cy - r * 0.55f - (cos(k) * r * 0.95).toFloat()
            c.drawLine(
                cx, cy - r * 0.30f, x2, y2,
                stroke(if (i % 2 == 0) 0xFF96C83C.toInt() else 0xFFC4DE4E.toInt(), r * 0.13f)
            )
            c.drawCircle(x2, y2, r * 0.08f, fill(0xFFE2F082.toInt()))
        }
        poly(
            c, s.ink,
            cx - r * 0.34f, cy - r * 0.46f, cx + r * 0.34f, cy - r * 0.46f,
            cx + r * 0.30f, cy - r * 0.16f, cx - r * 0.30f, cy - r * 0.16f
        )
        oval(c, cx, cy - r * 0.46f, r * 0.50f, r * 0.12f, s.ink2)
        c.drawArc(RectF(cx - r * 0.86f, cy - r * 0.62f, cx + r * 0.86f, cy + r * 1.10f),
            0f, 180f, true, fill(s.ink))
        c.drawRoundRect(
            RectF(cx - r * 0.84f, cy + r * 0.02f, cx + r * 0.84f, cy + r * 0.26f),
            r * 0.10f, r * 0.10f, fill(0xFFC5202A.toInt())
        )
        c.drawRoundRect(
            RectF(cx - r * 0.46f, cy + r * 0.92f, cx + r * 0.46f, cy + r * 1.12f),
            r * 0.08f, r * 0.08f, fill(s.ink2)
        )
    }

    /** विवाह — लगन गाँठो. A ring emoji read as handcuffs; a knot does not. */
    private fun gantho(c: Canvas, cx: Float, cy: Float, r: Float, s: Style) {
        poly(
            c, s.ink2,
            cx - r * 0.16f, cy + r * 0.10f, cx + r * 0.10f, cy + r * 0.16f,
            cx + r * 0.34f, cy + r * 1.42f, cx - r * 0.02f, cy + r * 1.20f
        )
        poly(
            c, s.ink,
            cx + r * 0.16f, cy + r * 0.10f, cx - r * 0.10f, cy + r * 0.16f,
            cx - r * 0.40f, cy + r * 1.34f, cx - r * 0.04f, cy + r * 1.16f
        )
        val shade = Color.rgb(
            Color.red(s.ink) / 2 + 40, Color.green(s.ink) / 2 + 20, Color.blue(s.ink) / 2
        )
        for (sgn in intArrayOf(-1, 1)) {
            poly(
                c, s.ink,
                cx + sgn * r * 0.10f, cy,
                cx + sgn * r * 0.72f, cy - r * 0.86f,
                cx + sgn * r * 1.24f, cy - r * 0.18f,
                cx + sgn * r * 0.66f, cy + r * 0.42f
            )
            poly(
                c, shade,
                cx + sgn * r * 0.30f, cy - r * 0.06f,
                cx + sgn * r * 0.74f, cy - r * 0.62f,
                cx + sgn * r * 1.00f, cy - r * 0.22f,
                cx + sgn * r * 0.62f, cy + r * 0.20f
            )
        }
        c.drawRoundRect(
            RectF(cx - r * 0.30f, cy - r * 0.34f, cx + r * 0.30f, cy + r * 0.34f),
            r * 0.14f, r * 0.14f, fill(s.ink2)
        )
    }

    /** शुभ रात्री — a full moon. A half moon looked like a mistake. */
    private fun moon(c: Canvas, cx: Float, cy: Float, r: Float) {
        glow(c, cx, cy, r * 2.3f, 0x78FFF0BE.toInt())
        c.drawCircle(cx, cy, r, fill(0xFFFFF4D0.toInt()))
        val craters = arrayOf(
            Triple(-0.34f, -0.22f, 0.20f), Triple(0.30f, 0.16f, 0.15f),
            Triple(-0.06f, 0.42f, 0.11f), Triple(0.42f, -0.40f, 0.10f)
        )
        for ((fx, fy, fr) in craters) {
            c.drawCircle(cx + r * fx, cy + r * fy, r * fr, fill(0xFFEEE0B8.toInt()))
        }
    }

    private fun sun(c: Canvas, cx: Float, cy: Float, r: Float, s: Style) {
        glow(c, cx, cy, r * 1.9f, 0x82FFDC78.toInt())
        val ray = stroke(s.ink, r * 0.13f)
        for (i in 0 until 12) {
            val a = i * Math.PI / 6
            c.drawLine(
                cx + (cos(a) * r * 1.05).toFloat(), cy + (sin(a) * r * 1.05).toFloat(),
                cx + (cos(a) * r * 1.45).toFloat(), cy + (sin(a) * r * 1.45).toFloat(), ray
            )
        }
        c.drawCircle(cx, cy, r * 0.82f, fill(s.ink))
    }

    private fun cake(c: Canvas, cx: Float, cy: Float, r: Float, s: Style) {
        for (i in -1..1) {
            val x = cx + i * r * 0.42f
            c.drawRect(
                RectF(x - r * 0.07f, cy - r * 0.85f, x + r * 0.07f, cy - r * 0.35f),
                fill(0xF0FFFFFF.toInt())
            )
            glow(c, x, cy - r * 0.98f, r * 0.34f, 0xAAFFC846.toInt())
            poly(
                c, 0xFFFFDC5A.toInt(),
                x, cy - r * 1.18f, x + r * 0.11f, cy - r * 0.92f,
                x, cy - r * 0.80f, x - r * 0.11f, cy - r * 0.92f
            )
        }
        c.drawRoundRect(
            RectF(cx - r * 0.92f, cy - r * 0.35f, cx + r * 0.92f, cy + r * 0.18f),
            r * 0.10f, r * 0.10f, fill(s.ink)
        )
        c.drawRoundRect(
            RectF(cx - r * 1.10f, cy + r * 0.14f, cx + r * 1.10f, cy + r * 0.82f),
            r * 0.10f, r * 0.10f, fill(s.ink2)
        )
        for (i in 0 until 7) {
            val x = cx - r * 1.02f + i * r * 0.34f
            oval(c, x, cy + r * 0.18f, r * 0.11f, r * 0.12f, s.ink)
        }
    }

    /** शोक — one candle, and nothing else on the card. */
    private fun candle(c: Canvas, cx: Float, cy: Float, r: Float, s: Style) {
        glow(c, cx, cy - r * 0.72f, r * 0.62f, 0x78FFC85A.toInt())
        poly(
            c, 0xFFFFCD50.toInt(),
            cx, cy - r * 1.10f, cx + r * 0.15f, cy - r * 0.72f,
            cx, cy - r * 0.52f, cx - r * 0.15f, cy - r * 0.72f
        )
        c.drawLine(cx, cy - r * 0.52f, cx, cy - r * 0.40f, stroke(0xFF503C28.toInt(), r * 0.07f))
        c.drawRoundRect(
            RectF(cx - r * 0.26f, cy - r * 0.40f, cx + r * 0.26f, cy + r * 0.95f),
            r * 0.10f, r * 0.10f, fill(0xFFF5F0E6.toInt())
        )
        c.drawRoundRect(
            RectF(cx - r * 0.46f, cy + r * 0.88f, cx + r * 0.46f, cy + r * 1.06f),
            r * 0.06f, r * 0.06f, fill(s.ink2)
        )
    }

    /** नमस्ते, धन्यवाद, माफी — कमल, in lotus colours rather than the style tint. */
    private fun lotus(c: Canvas, cx: Float, cy: Float, r: Float) {
        glow(c, cx, cy, r * 1.5f, 0x78FFAAC8.toInt())
        val outer = 0xFFF48FB1.toInt()
        val inner = 0xFFFFD6E7.toInt()
        val back = arrayOf(-1.05f to 0.70f, 1.05f to 0.70f, -0.55f to 0.88f,
            0.55f to 0.88f, 0.0f to 1.0f)
        for ((a, sc) in back) {
            val tx = cx + (sin(a.toDouble()) * r * 1.15 * sc).toFloat()
            val ty = cy - (cos(a.toDouble()) * r * 1.15 * sc).toFloat() + r * 0.30f
            poly(
                c, outer,
                cx, cy + r * 0.58f, tx - r * 0.34f * sc, cy + r * 0.02f,
                tx, ty, tx + r * 0.34f * sc, cy + r * 0.02f
            )
        }
        val front = arrayOf(-0.50f to 0.62f, 0.50f to 0.62f, 0.0f to 0.78f)
        for ((a, sc) in front) {
            val tx = cx + (sin(a.toDouble()) * r * 1.15 * sc).toFloat()
            val ty = cy - (cos(a.toDouble()) * r * 1.15 * sc).toFloat() + r * 0.34f
            poly(
                c, inner,
                cx, cy + r * 0.58f, tx - r * 0.30f * sc, cy + r * 0.10f,
                tx, ty, tx + r * 0.30f * sc, cy + r * 0.10f
            )
        }
        oval(c, cx, cy + r * 0.61f, r * 1.05f, r * 0.19f, 0xFF78BE82.toInt())
    }

    /** बधाई, नयाँ वर्ष — पटाका. */
    private fun burst(c: Canvas, cx: Float, cy: Float, r: Float, s: Style) {
        for (i in 0 until 14) {
            val a = i * 2.0 * Math.PI / 14
            val col = if (i % 2 == 0) s.ink2 else s.ink
            val r1 = r * 0.30f
            val r2 = r * if (i % 2 == 0) 1.05f else 1.30f
            val x2 = cx + (cos(a) * r2).toFloat()
            val y2 = cy + (sin(a) * r2).toFloat()
            c.drawLine(
                cx + (cos(a) * r1).toFloat(), cy + (sin(a) * r1).toFloat(),
                x2, y2, stroke(col, r * 0.11f)
            )
            c.drawCircle(x2, y2, r * 0.10f, fill(col))
        }
        glow(c, cx, cy, r * 0.60f, 0xC8FFE696.toInt())
    }

    private fun heart(c: Canvas, cx: Float, cy: Float, r: Float, s: Style) {
        glow(c, cx, cy, r * 1.4f, 0x78FF5A78.toInt())
        oval(c, cx - r * 0.47f, cy - r * 0.33f, r * 0.45f, r * 0.45f, s.ink)
        oval(c, cx + r * 0.47f, cy - r * 0.33f, r * 0.45f, r * 0.45f, s.ink)
        poly(
            c, s.ink,
            cx - r * 0.90f, cy - r * 0.22f, cx + r * 0.90f, cy - r * 0.22f,
            cx, cy + r * 1.05f
        )
    }

    /** स्वागत — सयपत्री, in marigold colours. */
    private fun flower(c: Canvas, cx: Float, cy: Float, r: Float) {
        glow(c, cx, cy, r * 1.5f, 0x6EFFAA3C.toInt())
        for (i in 0 until 10) {
            val a = i * 2.0 * Math.PI / 10
            c.drawCircle(
                cx + (cos(a) * r * 0.74).toFloat(), cy + (sin(a) * r * 0.74).toFloat(),
                r * 0.36f, fill(0xFFF37A14.toInt())
            )
        }
        for (i in 0 until 8) {
            val a = i * 2.0 * Math.PI / 8 + 0.4
            c.drawCircle(
                cx + (cos(a) * r * 0.42).toFloat(), cy + (sin(a) * r * 0.42).toFloat(),
                r * 0.30f, fill(0xFFFFB020.toInt())
            )
        }
        c.drawCircle(cx, cy, r * 0.26f, fill(0xFFC8600C.toInt()))
        for (sgn in intArrayOf(-1, 1)) {
            oval(c, cx + sgn * r * 0.95f, cy + r * 0.75f, r * 0.30f, r * 0.20f, 0xFF40964C.toInt())
        }
    }

    private fun spark(c: Canvas, cx: Float, cy: Float, r: Float, s: Style) {
        val at = arrayOf(Triple(0f, 0f, 1.0f), Triple(-0.85f, -0.55f, 0.42f),
            Triple(0.80f, 0.50f, 0.34f))
        for ((fx, fy, sc) in at) {
            val x = cx + r * fx
            val y = cy + r * fy
            val rr = r * sc
            poly(
                c, s.ink,
                x, y - rr, x + rr * 0.26f, y - rr * 0.26f,
                x + rr, y, x + rr * 0.26f, y + rr * 0.26f,
                x, y + rr, x - rr * 0.26f, y + rr * 0.26f,
                x - rr, y, x - rr * 0.26f, y - rr * 0.26f
            )
        }
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

    /**
     * Set in relief: a stack of darkened copies stepping down and right, a pale
     * copy a hair above for the lit top edge, then the face. Flat letters on a
     * flat card were the other half of why these looked unfinished.
     */
    private fun drawText(c: Canvas, lines: List<String>, s: Style, topF: Float, relief: Boolean) {
        if (lines.isEmpty()) return
        val pad = 0.05f * SIZE
        val top = topF * SIZE
        val room = (SIZE - pad) - top - 0.06f * SIZE
        val width = 0.76f * SIZE

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        // Step down rather than solve for it: Devanagari's tall matras and
        // descenders change the measured height in ways a formula would miss.
        var size = 0.24f * SIZE
        while (size > 0.035f * SIZE) {
            paint.textSize = size
            val widest = lines.maxOf { paint.measureText(it) }
            if (widest <= width && lines.size * size * LINE_SPACING <= room) break
            size -= 0.006f * SIZE
        }

        val lineHeight = size * LINE_SPACING
        var y = top + (room - lines.size * lineHeight) / 2f + lineHeight * 0.78f
        val deep = Color.rgb(
            (Color.red(s.fg) * 0.35f).toInt(),
            (Color.green(s.fg) * 0.35f).toInt(),
            (Color.blue(s.fg) * 0.35f).toInt()
        )

        for (line in lines) {
            if (relief) {
                paint.color = deep
                var k = size * 0.10f
                while (k > 0f) {
                    c.drawText(line, SIZE / 2f + k * 0.8f, y + k * 0.9f, paint)
                    k -= 1f
                }
                paint.color = 0x5AFFFFFF
                c.drawText(line, SIZE / 2f, y - size * 0.03f, paint)
            } else {
                paint.color = s.shadow
                c.drawText(line, SIZE / 2f + size * 0.045f, y + size * 0.045f, paint)
            }
            paint.color = s.fg
            c.drawText(line, SIZE / 2f, y, paint)
            y += lineHeight
        }
    }

    private class Style(
        val bgTop: Int,
        val bgBottom: Int,
        val fg: Int,
        val border: Int,
        val speck: Int,
        val shadow: Int,
        val glow: Int,
        val ink: Int,
        val ink2: Int
    )

    private const val LINE_SPACING = 1.26f

    private fun c(hex: String) = Color.parseColor(hex)

    /**
     * Eight designs that read as eight different things in a row of previews,
     * not one thing in eight colours. The first is the brand's own red — the
     * one people will recognise as having come from this keyboard. The last two
     * were added for the festivals: maroon and gold is what a Nepali card is
     * actually printed in, and a night sky needs to be a night sky.
     */
    private val STYLES = listOf(
        Style(c("#E8333A"), c("#9A1446"), Color.WHITE, c("#6EFFFFFF"), c("#37FFFFFF"), c("#50000000"), c("#4DFFFFFF"), c("#FFD54A"), Color.WHITE),
        Style(c("#1A1A1A"), c("#000000"), c("#FFD54A"), c("#55FFD54A"), c("#2AFFD54A"), c("#66000000"), c("#4DFFD54A"), c("#FFC93C"), c("#FF7A18")),
        Style(c("#FFFFFF"), c("#EDEDED"), c("#141414"), c("#33000000"), c("#22000000"), c("#22000000"), c("#1F000000"), c("#C0392B"), c("#7F8C8D")),
        Style(c("#FF9A3D"), c("#E8333A"), Color.WHITE, c("#66FFFFFF"), c("#33FFFFFF"), c("#55000000"), c("#4DFFFFFF"), c("#FFF3C4"), Color.WHITE),
        Style(c("#22B37F"), c("#0B6E52"), Color.WHITE, c("#66FFFFFF"), c("#33FFFFFF"), c("#55000000"), c("#4DFFFFFF"), c("#FFE082"), Color.WHITE),
        Style(c("#4B5BE0"), c("#7B2FF7"), Color.WHITE, c("#66FFFFFF"), c("#33FFFFFF"), c("#55000000"), c("#4DFFFFFF"), c("#FFD54A"), Color.WHITE),
        Style(c("#101A3A"), c("#04060F"), c("#EAF0FF"), c("#55A9C0FF"), c("#2AA9C0FF"), c("#66000000"), c("#40A9C0FF"), c("#FFF1B8"), c("#A9C0FF")),
        Style(c("#7E1030"), c("#3A0616"), c("#FFE9A8"), c("#66FFD54A"), c("#2EFFD54A"), c("#66000000"), c("#4DFFD54A"), c("#FFC93C"), c("#FF8A3D"))
    )
}
