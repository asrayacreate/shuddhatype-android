package com.shuddhatype.ime

import android.inputmethodservice.InputMethodService
import android.view.View
import android.content.ClipDescription
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import java.io.File
import java.io.FileOutputStream
import com.shuddhatype.engine.Lexicon
import com.shuddhatype.engine.NepaliDate
import com.shuddhatype.engine.EnglishNumber
import com.shuddhatype.engine.NepaliNumber
import com.shuddhatype.engine.Respect
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

    /**
     * The Latin letters typed since the last separator, on the pages where the
     * engine composes nothing — EN, and दे when English keys are used.
     *
     * A shortcut on the नेपाली page matches [composing], which is still in
     * flight and can simply be replaced. Here the letters have already gone
     * into the field one by one, so expanding a shortcut means taking them
     * back out again before writing the expansion in their place. This is the
     * record of how many to take back.
     */
    private val latinRun = StringBuilder()

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
        // The pad has no way to reach the field on its own; these are its only
        // route in and out.
        keyboardView.stickerSource = ::stickerSource
        keyboardView.onStickerPicked = ::sendSticker
        return keyboardView
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        composing.setLength(0)
        digits.setLength(0)
        latinRun.setLength(0)
        // Settings can change while the IME is alive, and this is the moment
        // just before the user could use one. SharedPreferences is cached in
        // memory after the first read, so re-reading here costs nothing.
        Shortcuts.reload(this)
        sensitiveField = isSensitiveField(info)
        // onStartInput can run before onCreateInputView(); apply it then instead.
        if (::keyboardView.isInitialized) {
            keyboardView.setSensitive(sensitiveField)
            if (!sensitiveField && wantsLatin(info)) keyboardView.startLatin()
        }
    }

    /**
     * A field that can only hold Latin letters. Our own settings screen marks
     * the shortcut key this way; email and URI fields ask for it by convention,
     * and typing an address on the नेपाली page was never going to end well
     * either.
     */
    private fun wantsLatin(info: EditorInfo?): Boolean {
        if (info?.privateImeOptions?.contains(LATIN_FIELD) == true) return true
        val variation = (info?.inputType ?: 0) and android.text.InputType.TYPE_MASK_VARIATION
        return variation == android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
            variation == android.text.InputType.TYPE_TEXT_VARIATION_URI
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
        // The नेपाली page composes instead of committing, so any run the
        // direct pages were building is over.
        latinRun.setLength(0)
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

        // A single Roman letter extends the run a shortcut could match; a digit,
        // a symbol or a Devanagari key from the दे page ends it.
        val letter = text.length == 1 && (text[0] in 'a'..'z' || text[0] in 'A'..'Z')
        if (letter) latinRun.append(text[0].lowercaseChar()) else latinRun.setLength(0)

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
        latinRun.setLength(0)
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
        if (latinRun.isNotEmpty()) latinRun.setLength(latinRun.length - 1)
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

    /**
     * Commit the current best guess, then the separator.
     *
     * A shortcut wins over the engine here, and does so without asking. Every
     * other suggestion is the keyboard guessing what you meant; a shortcut is
     * a rule you wrote yourself, so confirming it every time would defeat it.
     * See [Shortcuts].
     *
     * It fires on the English page too, which takes more work: there the
     * letters were committed as they were typed, so they have to be deleted
     * back out before the expansion can take their place. Password fields are
     * excluded outright — a keyboard that rewrites what you type into a
     * password box would be indefensible, however useful the rule.
     */
    private fun finishWord(separator: String) {
        val ic = currentInputConnection ?: return
        if (composing.isNotEmpty()) {
            val typed = composing.toString()
            val text = Shortcuts.expansionFor(typed)
                ?: Transliterator.best(typed, lexicon)
            ic.commitText(text, 1)
            composing.setLength(0)
        } else if (separator.isNotEmpty() && !sensitiveField && latinRun.isNotEmpty()) {
            val run = latinRun.toString()
            Shortcuts.expansionFor(run)?.let {
                ic.deleteSurroundingText(run.length, 0)
                ic.commitText(it, 1)
            }
        }
        // Only a real separator ends the run. onDirectText calls this with an
        // empty one on every keystroke, and clearing there would leave the run
        // one letter long forever.
        if (separator.isNotEmpty()) latinRun.setLength(0)
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
     * The amount in words, offered five ways: Nepali plain and as the full
     * अक्षरेपी phrase, the figure regrouped in Devanagari, then the same two in
     * English. Nothing is shown for a single digit, which needs no help.
     *
     * English is there because half the paperwork in a Nepali office is —
     * invoices to companies, cheques, contracts. It keeps लाख and करोड rather
     * than converting to millions: an invoice reading "Five Lakh Forty Five
     * Thousand" is the number everyone in the room is already holding.
     *
     * Nepali stays first because this is a Nepali keyboard; the bar scrolls, so
     * the English pair costs nothing but a swipe to whoever does not want it.
     */
    private fun showAmount() {
        if (!::suggestionBar.isInitialized) return
        val raw = digits.toString()
        val words = if (raw.length >= 2) NepaliNumber.toWords(raw) else null
        if (words == null) { suggestionBar.clear(); return }

        val out = ArrayList<String>(5)
        out.add(words)
        out.add("$words रुपैयाँ मात्र")
        NepaliNumber.format(raw)?.let { out.add(it) }
        EnglishNumber.toWords(raw)?.let {
            out.add(it)
            out.add("Rupees $it Only")
        }
        suggestionBar.show(out)
    }

    private fun refreshSuggestions() {
        if (!::suggestionBar.isInitialized) return
        if (composing.isEmpty()) { suggestionBar.clear(); return }
        val roman = composing.toString()
        val words = ArrayList<String>(SUGGESTION_LIMIT + 2)
        // Shown first because it is what space will commit. The composing text
        // still shows the Devanagari — swapping a long address in under the
        // cursor mid-word would move the text about while you are still typing.
        // The bar is where you look to see what is coming.
        Shortcuts.expansionFor(roman)?.let { words.add(it) }
        val guesses = Transliterator.candidates(roman, lexicon, limit = SUGGESTION_LIMIT)
        guesses.forEach { if (!words.contains(it.word)) words.add(it.word) }
        // The same verb at the other levels of respect, behind the ordinary
        // guesses. `garyau` can only ever come out गर्‍यौ — the writer has
        // already typed the level into the Roman — so the only place this can
        // be offered is after the word is recognised. Behind, not in front:
        // what was typed is still what space commits.
        guesses.firstOrNull()?.let { top ->
            Respect.variants(top.word) { lexicon.contains(it) }
                .forEach { if (!words.contains(it)) words.add(it) }
        }
        // The Roman spelling itself is always offered. Nepalis write English
        // words mid-sentence constantly ("मेरो keyboard"), and forcing a mode
        // switch for one word is the fastest way to lose the user.
        if (!words.contains(roman)) words.add(roman)
        suggestionBar.show(words)
    }

    // ---- word stickers ----

    /**
     * What the sticker pad turns into a picture: the text already sitting in
     * the field, so nobody has to type their phrase twice.
     *
     * Only the current line, and never from a password box. A keyboard that
     * reads a whole document to make a picture is not a keyboard anyone should
     * install.
     */
    fun stickerSource(): String {
        if (sensitiveField) return ""
        val ic = currentInputConnection ?: return ""
        val before = ic.getTextBeforeCursor(STICKER_MAX_CHARS, 0)?.toString() ?: ""
        val after = ic.getTextAfterCursor(STICKER_MAX_CHARS, 0)?.toString() ?: ""
        return (before + after).substringAfterLast('\n').trim()
    }

    /**
     * Write the bitmap where [StickerProvider] can serve it, then hand the URI
     * over. Returns a message when it cannot, because silence on a tap reads
     * as a broken button.
     *
     * `commitContent` needs API 25, and the receiving app has to have said it
     * takes PNGs. Plenty do not — a notes app or an SMS field will refuse, and
     * saying so plainly is better than appearing to do nothing.
     */
    fun sendSticker(bitmap: Bitmap, label: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return "यो फोनमा स्टिकर पठाउन मिल्दैन।"
        }
        val ic = currentInputConnection ?: return "अहिले पठाउन मिलेन।"
        val info = currentInputEditorInfo ?: return "अहिले पठाउन मिलेन।"
        if (!acceptsPng(info)) return "यो एपले तस्बिर लिँदैन। WhatsApp मा चलाउनुहोस्।"

        val uri = try { writeSticker(bitmap) } catch (e: Exception) { null }
            ?: return "स्टिकर बनाउन सकिएन।"

        val content = InputContentInfo(
            uri, ClipDescription(label, arrayOf(MIME_PNG)), null
        )
        val flags = InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
        return if (ic.commitContent(content, flags, null)) null
        else "यो एपले तस्बिर लिँदैन। WhatsApp मा चलाउनुहोस्।"
    }

    private fun acceptsPng(info: EditorInfo): Boolean {
        val types = info.contentMimeTypes ?: return false
        return types.any { ClipDescription.compareMimeTypes(it, MIME_PNG) }
    }

    /**
     * One file, overwritten each time. The picture is gone the moment the chat
     * app has copied it, and a folder of every sticker anyone ever sent is a
     * privacy problem waiting to be found.
     */
    private fun writeSticker(bitmap: Bitmap): Uri {
        val dir = StickerProvider.dir(this)
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, STICKER_FILE)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return StickerProvider.uriFor(STICKER_FILE)
    }

    companion object {
        /** The bar scrolls, so more than three is free screen space, not clutter. */
        const val SUGGESTION_LIMIT = 6

        /**
         * privateImeOptions marker asking the keyboard to open on the English
         * page. Public because SettingsActivity sets it on the shortcut key
         * field, and the two must agree on the string.
         */
        const val LATIN_FIELD = "com.shuddhatype.latin"

        private const val MIME_PNG = "image/png"
        private const val STICKER_FILE = "sticker.png"

        /** A sticker is a phrase, not an essay. */
        private const val STICKER_MAX_CHARS = 120
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
