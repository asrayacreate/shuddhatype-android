package com.shuddhatype.engine

/**
 * Today's date in Bikram Sambat.
 *
 * Every letter, bill and quotation written in Nepal is dated in BS, and there
 * is no formula for it: the calendar is lunisolar, so month lengths are
 * recomputed from astronomical data each year and Baisakh can be 30, 31 or 32
 * days depending on which year it is. The only way to convert is a table.
 *
 * The table below covers 1975–2099 BS. It was cross-checked against published
 * dates before being written here — Baisakh 1 of 2079–2083 against the known
 * Nepali New Year dates, and four separate 2083 anchors (Baisakh 1, Asar 1,
 * Magh 1, Chaitra 30) against a published patro. A wrong date on a quotation is
 * worse than no date key at all, so none of this was written from memory.
 *
 * Only 13 distinct year shapes occur across those 125 years, so the years are
 * stored as an index into [SHAPES] rather than as 125 separate arrays.
 */
object NepaliDate {

    const val FIRST_YEAR = 1975
    const val LAST_YEAR = 2099

    data class Bs(val year: Int, val month: Int, val day: Int, val weekday: Int)

    /** [month] is 1-12 and [day] 1-31, as a human writes them. */
    fun fromGregorian(year: Int, month: Int, day: Int): Bs? {
        var left = civilDays(year, month, day) - EPOCH_DAYS
        if (left < 0) return null
        // Sunday = 0. 13 April 1918 was a Saturday, so the epoch starts at 6.
        val weekday = ((left + 6) % 7).toInt()

        var y = FIRST_YEAR
        while (y <= LAST_YEAR) {
            val months = shapeOf(y)
            val total = months.sum()
            if (left >= total) { left -= total; y++; continue }
            for (m in 0..11) {
                if (left >= months[m]) { left -= months[m]; continue }
                return Bs(y, m + 1, left.toInt() + 1, weekday)
            }
        }
        return null
    }

    fun monthName(month: Int): String = MONTHS[month - 1]

    fun weekdayName(weekday: Int): String = WEEKDAYS[weekday]

    /** Latin digits to Devanagari, zero-padded to [width]. */
    fun deva(n: Int, width: Int = 0): String {
        val s = n.toString().padStart(width, '0')
        return s.map { DIGITS[it - '0'] }.joinToString("")
    }

    private fun shapeOf(year: Int): IntArray {
        val c = MAP[year - FIRST_YEAR]
        return SHAPES[if (c in '0'..'9') c - '0' else c - 'a' + 10]
    }

    /**
     * Days since 1970-01-01 for a proleptic Gregorian date. Pure arithmetic
     * rather than Calendar or java.time: no time zone to get wrong, no minimum
     * API level to worry about, and it is trivially testable.
     */
    private fun civilDays(y: Int, m: Int, d: Int): Long {
        val yy = if (m <= 2) y - 1 else y
        val era = (if (yy >= 0) yy else yy - 399) / 400
        val yoe = (yy - era * 400).toLong()                       // 0..399
        val mp = (m + 9) % 12                                     // Mar = 0
        val doy = (153L * mp + 2) / 5 + d - 1                     // 0..365
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy           // 0..146096
        return era.toLong() * 146097 + doe - 719468
    }

    /** 13 April 1918, which is Baisakh 1 of 1975 BS. */
    private val EPOCH_DAYS = civilDays(1918, 4, 13)

    private val DIGITS = charArrayOf('०', '१', '२', '३', '४', '५', '६', '७', '८', '९')

    private val MONTHS = arrayOf(
        "बैशाख", "जेठ", "असार", "साउन", "भदौ", "असोज",
        "कात्तिक", "मंसिर", "पुस", "माघ", "फागुन", "चैत"
    )

    private val WEEKDAYS = arrayOf(
        "आइतबार", "सोमबार", "मंगलबार", "बुधबार", "बिहीबार", "शुक्रबार", "शनिबार"
    )

    // The 13 shapes a BS year can take, as month lengths for Baisakh..Chaitra.
    private val SHAPES = arrayOf(
        intArrayOf(31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30),
        intArrayOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31),
        intArrayOf(30, 32, 31, 32, 31, 31, 29, 30, 30, 29, 29, 31),
        intArrayOf(31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30),
        intArrayOf(31, 31, 31, 32, 31, 31, 29, 30, 30, 29, 29, 31),
        intArrayOf(31, 31, 31, 32, 31, 31, 29, 30, 30, 29, 30, 30),
        intArrayOf(31, 32, 31, 32, 31, 30, 30, 29, 30, 29, 30, 30),
        intArrayOf(31, 31, 31, 32, 31, 31, 30, 29, 30, 29, 30, 30),
        intArrayOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 30),
        intArrayOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31),
        intArrayOf(31, 31, 32, 31, 32, 30, 30, 29, 30, 29, 30, 30),
        intArrayOf(30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31),
        intArrayOf(30, 32, 31, 32, 31, 31, 29, 30, 29, 30, 29, 31)
    )

    // One base-36 digit per year from 1975 BS, indexing into SHAPES.
    private const val MAP =
        "01230143015361738973893a1b301b3014301530153697389731b3a1b3" +
        "01230153015361738973893a1b301c3014301536173897389331b301b3" +
        "014301530"
}
