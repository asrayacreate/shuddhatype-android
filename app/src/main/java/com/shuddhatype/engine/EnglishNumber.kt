package com.shuddhatype.engine

/**
 * Amounts in English words.
 *
 * The companion to [NepaliNumber], and needed for the same reason: a bill
 * carries the figure twice, and the second one is written by hand. Half the
 * paperwork in a Nepali office is in English — invoices to companies, cheques,
 * contracts, anything crossing a border — and "Rupees One Hundred Twenty Three
 * Only" is typed out as slowly as its Nepali twin.
 *
 * The grouping is Nepali, not Western: लाख and करोड, not million and billion.
 * A Nepali invoice written in English still says "Five Lakh Forty Five
 * Thousand", because that is the number everyone in the room is holding in
 * their head. Rendering it as "Five Hundred Forty Five Thousand" would be
 * correct English and the wrong document.
 */
object EnglishNumber {

    /** Same ceiling as the Nepali side: beyond this it is an account number. */
    const val MAX_DIGITS = NepaliNumber.MAX_DIGITS

    /**
     * [digits] is plain Latin digits. Null when the input is not an amount
     * worth spelling — empty, too long, or zero-padded like an account number.
     */
    fun toWords(digits: String): String? {
        if (digits.isEmpty() || digits.length > MAX_DIGITS) return null
        if (digits.length > 1 && digits[0] == '0') return null
        val n = digits.toLongOrNull() ?: return null
        if (n == 0L) return "Zero"

        val sb = StringBuilder()
        var rest = n
        for ((value, name) in SCALES) {
            val count = (rest / value).toInt()
            if (count > 0) {
                sb.append(underThousand(count)).append(' ').append(name).append(' ')
                rest %= value
            }
        }
        if (rest > 0) sb.append(underThousand(rest.toInt()))
        return sb.toString().trim()
    }

    /** 1-999, the largest block that sits under any scale word. */
    private fun underThousand(n: Int): String {
        val sb = StringBuilder()
        val hundreds = n / 100
        val rest = n % 100
        if (hundreds > 0) {
            sb.append(UNITS[hundreds]).append(" Hundred")
            if (rest > 0) sb.append(' ')
        }
        if (rest > 0) {
            if (rest < 20) {
                sb.append(UNITS[rest])
            } else {
                sb.append(TENS[rest / 10])
                if (rest % 10 > 0) sb.append(' ').append(UNITS[rest % 10])
            }
        }
        return sb.toString()
    }

    /** Largest first, so the loop peels the number apart from the top. */
    private val SCALES = listOf(
        10_000_000L to "Crore",
        100_000L to "Lakh",
        1_000L to "Thousand"
    )

    /** 0-19 are irregular in English and have to be listed, as in Nepali. */
    private val UNITS = arrayOf(
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight",
        "Nine", "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen",
        "Sixteen", "Seventeen", "Eighteen", "Nineteen"
    )

    private val TENS = arrayOf(
        "", "", "Twenty", "Thirty", "Forty", "Fifty",
        "Sixty", "Seventy", "Eighty", "Ninety"
    )
}
