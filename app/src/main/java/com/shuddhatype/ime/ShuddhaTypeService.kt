package com.shuddhatype.ime

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import com.shuddhatype.engine.Lexicon
import com.shuddhatype.engine.Transliterator
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

    /** Roman letters typed since the last word boundary. */
    private val composing = StringBuilder()

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
        return keyboardView
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        composing.setLength(0)
        // Never transliterate or learn from a password or OTP field.
        keyboardView.setSensitive(isSensitiveField(info))
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
        composing.append(ch)
        updateComposingText()
        refreshSuggestions()
    }

    override fun onBackspace() {
        if (composing.isNotEmpty()) {
            composing.setLength(composing.length - 1)
            updateComposingText()
            refreshSuggestions()
        } else {
            currentInputConnection?.deleteSurroundingText(1, 0)
        }
    }

    override fun onSpace() {
        finishWord(separator = " ")
    }

    override fun onEnter() {
        finishWord(separator = "")
        currentInputConnection?.performEditorAction(EditorInfo.IME_ACTION_UNSPECIFIED)
    }

    override fun onPunctuation(text: String) {
        finishWord(separator = text)
    }

    /** Commit the current best guess, then the separator. */
    private fun finishWord(separator: String) {
        val ic = currentInputConnection ?: return
        if (composing.isNotEmpty()) {
            ic.commitText(Transliterator.best(composing.toString(), lexicon), 1)
            composing.setLength(0)
        }
        if (separator.isNotEmpty()) ic.commitText(separator, 1)
        suggestionBar.clear()
    }

    /** The user tapped a suggestion instead of accepting the top one. */
    private fun commitChoice(word: String) {
        currentInputConnection?.commitText("$word ", 1)
        composing.setLength(0)
        suggestionBar.clear()
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

    private fun refreshSuggestions() {
        if (composing.isEmpty()) { suggestionBar.clear(); return }
        val cands = Transliterator.candidates(composing.toString(), lexicon, limit = 3)
        suggestionBar.show(cands.map { it.word })
    }
}

/** Key events the view layer reports back to the service. */
interface KeyboardActions {
    fun onLetter(ch: Char)
    fun onBackspace()
    fun onSpace()
    fun onEnter()
    fun onPunctuation(text: String)
}
