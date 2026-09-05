package com.shuddhatype.ime

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import com.shuddhatype.engine.Lexicon
import com.shuddhatype.engine.NepaliDate
import com.shuddhatype.engine.NepaliNumber
import com.shuddhatype.engine.Transliterator
import java.util.Calendar
import kotlin.concurrent.thread

/**
 * The keyboard.
 *
 * Design rule that shapes everything here: the lexicon takes ~260 ms to build,
 * which is far too long to block the first keypress. So the IME starts usable
 * immediately on plain transliteration rules and swaps in शुद्धि correction the
 * moment the lexicon is ready. A keyboard that stutters on launch gets
 * uninstalled no matter how good its spelling is.
 */
class ShuddhaTypeService : InputMethodService(), KeyboardActions {

    private val lexicon = Lexicon()
    private lateinit var keyboardView: KeyboardLayoutView
    private lateinit var suggestionBar: SuggestionBar

    /** Remembered until the view exists; onStartInput can fire before onCreateInputView(). */
    private var sensitiveField = false

    /** False on the English and direct-Devanagari pages, where keys pass through. */
    private var nepaliMode = true

    /** Roman letters typed since the last word boundary. */
    private val composing = StringBuilder()

    /**
     * Digits typed in an unbroken run, in Latin form whatever page they came
     * from. They are already committed to the field — this is only kept so the
     * bar can offer the same amount written out. Anything that is not another
     * digit ends the run.
     */
    private val digits = StringBuilder()

    override fun onCreate() {
        super.onCreate()
        thread(name = "shuddha-lexicon") {
            lexicon.load(open = { name -> assets.open(name) })
            // The load thread can finish before onCreateInputView() has run, so
            // the view may not exist yet. Check before touching it.
            if (::keyboardView.isInitialized && composing.isNotEmpty()) {
                keyboardView.post { refreshSuggestions() }
            }
        }
    }

    override fun onCreateInputView(): View {
        suggestionBar = SuggestionBar(this).apply { onPick = ::commitChoice }
        keyboardView = KeyboardLayoutView(this, actions = this, suggestions = suggestionBar)
        keyboardView.setSensitive(sensitiveField)
        return keyboardView
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        composing.setLength(0)
        digits.setLength(0)
        sensitiveField = isSensitiveField(info)
        // onStartInput can run before onCreateInputView(); apply it then instead.
        if (::keyboardView.isInitialized) keyboardView.setSensitive(sensitiveField)
    }

    private fun isSensitiveField(info: EditorInfo?): Boolean {
        val variation = (info?.inputType ?: 0) and android.text.InputType.TYPE_MASK_VARIATION
        return variation == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            (info?.inputType ?: 0) and android.text.InputType.TYPE_MASK_CLASS ==
            android.text.InputType.TYPE_CLASS_NUMBER
    }

    // ---- KeyboardActions ----

    override fun onLetter(ch: Char) {
        digits.setLength(0)
        composing.append(ch)
        updateComposingText()
        refreshSuggestions()
    }

    /**
     * Digits, emoji, English letters, symbols: things the user typed literally.
     * Any half-finished Nepali word is committed first so the two never
     * interleave in the text field.
     */
    override fun onDirectText(text: String) {
        finishWord(separator = "")
        currentInputConnection?.commitText(text, 1)

        val digit = if (text.length == 1) latinDigit(text[0]) else null
        if (digit != null) {
            digits.append(digit)
            showAmount()
        } else {
            digits.setLength(0)
        }
    }

    override fun onModeChanged(nepali: Boolean) {
        if (nepaliMode != nepali) finishWord(separator = "")
        nepaliMode = nepali
        digits.setLength(0)
        if (::suggestionBar.isInitialized) suggestionBar.clear()
    }

    override fun onBackspace() {
        if (composing.isNotEmpty()) {
            composing.setLength(composing.length - 1)
            updateComposingText()
            refreshSuggestions()
            return
        }
        currentInputConnection?.deleteSurroundingText(1, 0)
        if (digits.isNotEmpty()) {
            digits.setLength(digits.length - 1)
            showAmount()
        }
    }

    override fun onSpace() {
        // Space does not end the number: people type "रु. 5 45 000" as often as
        // they type it unbroken, and losing the run on a space would mean the
        // amount never appears for them.
        if (digits.isNotEmpty()) {
            currentInputConnection?.commitText(" ", 1)
            return
        }
        finishWord(separator = " ")
    }

    override fun onEnter() {
        digits.setLength(0)
        finishWord(separator = "")
        currentInputConnection?.performEditorAction(EditorInfo.IME_ACTION_UNSPECIFIED)
    }

    override fun onPunctuation(text: String) {
        digits.setLength(0)
        finishWord(separator = text)
    }

    /**
     * The मिति key does not type anything — it offers today's date in the four
     * shapes Nepali documents actually use, and the user picks one. Committing
     * a single format would be guessing: a letter heads with २०८३ भदौ २०, a
     * ledger wants २०८३/०५/२०, and neither is a reasonable default for the
     * other.
     */
    override fun onDate() {
        if (!::suggestionBar.isInitialized) return
        digits.setLength(0)
        finishWord(separator = "")

        val now = Calendar.getInstance()
        val bs = NepaliDate.fromGregorian(
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH) + 1,
            now.get(Calendar.DAY_OF_MONTH)
        ) ?: return

        val y = NepaliDate.deva(bs.year)
        val mm = NepaliDate.deva(bs.month, 2)
        val dd = NepaliDate.deva(bs.day, 2)
        val d = NepaliDate.deva(bs.day)
        val month = NepaliDate.monthName(bs.month)
        val weekday = NepaliDate.weekdayName(bs.weekday)

        suggestionBar.show(
            listOf(
                "$y/$mm/$dd",
                "$y $month $d",
                "$y साल $month $d गते",
                "$month $d, $y $weekday"
            )
        )
    }

    /** Commit the current best guess, then the separator. */
    private fun finishWord(separator: String) {
        val ic = currentInputConnection ?: return
        if (composing.isNotEmpty()) {
            ic.commitText(Transliterator.best(composing.toString(), lexicon), 1)
            composing.setLength(0)
        }
        if (separator.isNotEmpty()) ic.commitText(separator, 1)
        if (::suggestionBar.isInitialized) suggestionBar.clear()
    }

    /**
     * The user tapped a suggestion instead of accepting the top one.
     *
     * For a word this replaces what was being composed. For an amount the
     * digits are already committed and staying — a quotation wants the figure
     * and the words side by side — so the choice is appended instead.
     */
    private fun commitChoice(word: String) {
        val ic = currentInputConnection ?: return
        if (digits.isNotEmpty()) {
            ic.commitText(" $word ", 1)
            digits.setLength(0)
        } else {
            ic.commitText("$word ", 1)
        }
        composing.setLength(0)
        if (::suggestionBar.isInitialized) suggestionBar.clear()
    }

    /**
     * Show the Devanagari underlined in place while the word is unfinished, so
     * the user sees what they are getting before committing to it.
     */
    private fun updateComposingText() {
        val ic = currentInputConnection ?: return
        if (composing.isEmpty()) { ic.setComposingText("", 1); return }
        ic.setComposingText(Transliterator.best(composing.toString(), lexicon), 1)
    }

    /** Devanagari and Latin digit keys both feed the same run. */
    private fun latinDigit(c: Char): Char? = when (c) {
        in '0'..'9' -> c
        in '०'..'९' -> '0' + (c - '०')
        else -> null
    }

    /**
     * The amount, offered three ways: the words alone for prose, the full
     * अक्षरेपी phrase for a quotation, and the figure regrouped in Devanagari.
     * Nothing is shown for a single digit, which needs no help.
     */
    private fun showAmount() {
        if (!::suggestionBar.isInitialized) return
        val raw = digits.toString()
        val words = if (raw.length >= 2) NepaliNumber.toWords(raw) else null
        if (words == null) { suggestionBar.clear(); return }

        val out = ArrayList<String>(3)
        out.add(words)
        out.add("$words रुपैयाँ मात्र")
        NepaliNumber.format(raw)?.let { out.add(it) }
        suggestionBar.show(out)
    }

    private fun refreshSuggestions() {
        if (!::suggestionBar.isInitialized) return
        if (composing.isEmpty()) { suggestionBar.clear(); return }
        val roman = composing.toString()
        val words = ArrayList<String>(SUGGESTION_LIMIT + 1)
        Transliterator.candidates(roman, lexicon, limit = SUGGESTION_LIMIT)
            .forEach { words.add(it.word) }
        // The Roman spelling itself is always offered. Nepalis write English
        // words mid-sentence constantly ("मेरो keyboard"), and forcing a mode
        // switch for one word is the fastest way to lose the user.
        if (!words.contains(roman)) words.add(roman)
        suggestionBar.show(words)
    }

    private companion object {
        /** The bar scrolls, so more than three is free screen space, not clutter. */
        const val SUGGESTION_LIMIT = 6
    }
}

/** Key events the view layer reports back to the service. */
interface KeyboardActions {
    fun onLetter(ch: Char)
    fun onDirectText(text: String)
    fun onModeChanged(nepali: Boolean)
    fun onBackspace()
    fun onSpace()
    fun onEnter()
    fun onPunctuation(text: String)
    fun onDate()
}
