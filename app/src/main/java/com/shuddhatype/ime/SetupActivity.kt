package com.shuddhatype.ime

import android.app.Activity
import com.shuddhatype.R
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.shuddhatype.crash.CrashHandler

/**
 * First run.
 *
 * Android makes enabling a third-party keyboard genuinely confusing — two
 * separate system screens, neither of which explains itself — and this is where
 * most installs are abandoned. So the screen does three things and nothing else:
 * enable, select, then a box to type in and see it working.
 *
 * Each step shows its own state, checked live in onResume(), because the user
 * leaves this activity to complete every step and comes back.
 */
class SetupActivity : Activity() {

    private lateinit var enableBtn: Button
    private lateinit var selectBtn: Button
    private lateinit var enableMark: TextView
    private lateinit var selectMark: TextView
    private lateinit var preview: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = buildLayout()
        fitInsets(root)
        setContentView(root)
    }

    /**
     * Leave room for the status bar and the keyboard.
     *
     * targetSdk 35 makes Android 15 lay every window out edge to edge, and that
     * turns `adjustResize` into a no-op — the keyboard is painted *over* the
     * layout instead of shrinking it. That buried the shortcut fields and the
     * थप्ने button under the keys, with nothing to scroll to. It is also why
     * the title sat under the clock.
     *
     * So the insets are measured and turned into padding on the scrolling root.
     * Below API 30 there is no `ime()` inset type, and `adjustResize` still
     * does the job by itself.
     */
    private fun fitInsets(root: View) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        root.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            val ime = insets.getInsets(WindowInsets.Type.ime())
            // The keyboard and the navigation bar occupy the same edge, so the
            // larger of the two is the whole of what has to be cleared.
            v.setPadding(0, bars.top, 0, maxOf(bars.bottom, ime.bottom))
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    private fun buildLayout(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            // Deep bottom padding so the last field can scroll clear of the
            // keyboard. adjustResize shrinks the window, but with nothing below
            // the button there is nowhere left to scroll to.
            setPadding(dp(24), dp(40), dp(24), dp(96))
        }

        // Third fallback: if CrashActivity never got to run (e.g. the OS killed
        // the process too fast to launch it), a trace may still be sitting on
        // disk from last time. Wrapped in try-catch so a problem here can never
        // block the setup screen itself from showing.
        try {
            val leftover = CrashHandler.readLastTrace(this)
            if (leftover != null) {
                root.addView(TextView(this).apply {
                    text = "⚠️ अघिल्लो पटक app बन्द भएको थियो। थिच्नुहोस् — Share गर्न।"
                    setTextColor(Color.parseColor("#FDBA74"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    setBackgroundColor(Color.parseColor("#2A1F14"))
                    setOnClickListener {
                        try {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, leftover)
                            }
                            startActivity(Intent.createChooser(send, "Share crash log"))
                            CrashHandler.clearLastTrace(this@SetupActivity)
                        } catch (_: Throwable) { }
                    }
                })
            }
        } catch (_: Throwable) { }

        root.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "नेपालीको शुद्धाशुद्धि बुझ्ने किबोर्ड"
            setTextColor(MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(0, dp(6), 0, dp(28))
        })

        // Step 1
        enableMark = stepMark()
        enableBtn = actionButton(getString(R.string.setup_enable)) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        root.addView(stepBlock("१", "सूचीमा ShuddhaType खोजेर अन गर्नुहोस्", enableMark, enableBtn))

        // Step 2
        selectMark = stepMark()
        selectBtn = actionButton(getString(R.string.setup_select)) {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showInputMethodPicker()
        }
        root.addView(stepBlock("२", "अहिले चलाउने किबोर्ड ShuddhaType बनाउनुहोस्", selectMark, selectBtn))

        // Step 3 — proof it works
        preview = EditText(this).apply {
            hint = "k xa sathi"
            setHintTextColor(HINT)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setBackgroundColor(FIELD)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setSingleLine(false)
            minLines = 2
        }
        root.addView(stepBlock("३", getString(R.string.setup_try), null, preview))

        // Step 4 — the shortcut offer, asked outright.
        //
        // This used to be a button labelled "शर्टकट थप्ने, रङ फेर्ने" leading to
        // settings, and nobody ever pressed it. A label is not an offer: it
        // names a feature to someone who has no idea they want it, on the one
        // screen where they are busy getting the keyboard working at all.
        //
        // So the question is asked here instead, with the fields under it. The
        // first shortcut gets made before the user ever leaves setup, and the
        // feature is learned by using it rather than by reading about it.
        root.addView(stepBlock(
            "४",
            "कम्पनीको नाम, ठेगाना — बारम्बार लेख्नुहुन्छ?",
            null,
            shortcutOffer()
        ))

        root.addView(TextView(this).apply {
            text = "तपाईंले टाइप गरेको कुनै पनि कुरा फोनबाहिर जाँदैन। " +
                "इन्टरनेट चाहिँदैन, खाता चाहिँदैन।"
            setTextColor(MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(28), 0, 0)
        })

        return ScrollView(this).apply {
            setBackgroundColor(BG)
            addView(root, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    /**
     * The two fields, a button, and a line telling the user what they just
     * bought — the shortcut written back at them as the thing they will type.
     * Without that line the reward is invisible and the habit never forms.
     *
     * Settings stays reachable underneath for the theme and for editing later,
     * but it is no longer the only way in.
     */
    private fun shortcutOffer(): View {
        val status = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(MUTED)
            setPadding(0, dp(8), 0, 0)
        }

        val keyField = EditText(this).apply {
            hint = "pp"
            setHintTextColor(HINT)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.MONOSPACE
            setBackgroundColor(FIELD)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            // Our own keyboard opens on the English page here. On the नेपाली
            // page pp arrives as प्प and would be refused for containing no a-z.
            privateImeOptions = ShuddhaTypeService.LATIN_FIELD
            setSingleLine()
        }

        val valueField = EditText(this).apply {
            hint = "प्रेरक कन्स्ट्रक्सन एन्ड प्रिफ्याब होम्स प्रा. लि."
            setHintTextColor(HINT)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setBackgroundColor(FIELD)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine()
        }

        fun showCount() {
            val n = Shortcuts.all(this).size
            status.text = if (n == 0) {
                "छोटो अक्षर लेखेर स्पेस थिच्दा पूरा कुरा आउँछ। पछि पनि थप्न सकिन्छ।"
            } else {
                "$n वटा शर्टकट तयार छ। किबोर्डमा छोटो अक्षर लेखेर स्पेस थिच्नुहोस्।"
            }
        }
        showCount()

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            addView(TextView(this@SetupActivity).apply {
                text = "छोटो अक्षर लेख्नुहोस् (जस्तै pp), अनि त्यसले के निकालोस् भन्ने।"
                setTextColor(MUTED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, 0, 0, dp(10))
            })

            addView(keyField, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            addView(valueField, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) })

            val addButton = actionButton("शर्टकट थप्ने") {
                val k = keyField.text.toString().trim()
                val v = valueField.text.toString().trim()
                val bad = Shortcuts.rejectReason(k)
                when {
                    bad != null -> status.text = bad
                    v.isEmpty() -> status.text = "के लेख्ने भन्ने खाली छ।"
                    v.length > Shortcuts.MAX_VALUE -> status.text = "धेरै लामो भयो।"
                    else -> {
                        Shortcuts.put(this@SetupActivity, k, v)
                        keyField.setText("")
                        valueField.setText("")
                        // Named back at them, so the next step is obvious.
                        status.text = "भयो — अब जहाँ पनि $k लेखेर स्पेस थिच्नुहोस्।"
                    }
                }
            }
            addView(addButton, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) })

            addView(status)

            addView(TextView(this@SetupActivity).apply {
                text = "सेटिङ — रङ फेर्ने, शर्टकट मेट्ने"
                setTextColor(ACCENT)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(0, dp(14), 0, dp(4))
                isClickable = true
                setOnClickListener {
                    // Fully qualified: android.provider.Settings is imported for
                    // the system screens above, and the bare name resolves to it.
                    startActivity(Intent(
                        this@SetupActivity,
                        com.shuddhatype.ime.SettingsActivity::class.java
                    ))
                }
            })
        }
    }

    private fun stepBlock(number: String, caption: String, mark: TextView?, action: View): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(26))

            addView(LinearLayout(this@SetupActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(this@SetupActivity).apply {
                    text = number
                    setTextColor(ACCENT)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(0, 0, dp(10), 0)
                })
                addView(TextView(this@SetupActivity).apply {
                    text = caption
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                mark?.let { addView(it) }
            })

            addView(action, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(10)
            })
        }

    private fun stepMark() = TextView(this).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setPadding(dp(8), 0, 0, 0)
    }

    private fun actionButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTextColor(Color.WHITE)
        setBackgroundColor(ACCENT)
        setOnClickListener { onClick() }
    }

    /** Both steps are checked against the system, never assumed from a tap. */
    private fun refreshState() {
        val enabled = isKeyboardEnabled()
        val selected = isKeyboardSelected()

        enableMark.text = if (enabled) "✓" else ""
        enableMark.setTextColor(OK)
        enableBtn.setBackgroundColor(if (enabled) DONE else ACCENT)
        enableBtn.text = if (enabled) "सक्रिय भइसक्यो" else getString(R.string.setup_enable)

        selectMark.text = if (selected) "✓" else ""
        selectMark.setTextColor(OK)
        selectBtn.isEnabled = enabled
        selectBtn.alpha = if (enabled) 1f else 0.4f
        selectBtn.setBackgroundColor(if (selected) DONE else ACCENT)
        selectBtn.text = if (selected) "रोजिइसक्यो" else getString(R.string.setup_select)

        preview.isEnabled = selected
        preview.alpha = if (selected) 1f else 0.4f
    }

    private fun isKeyboardEnabled(): Boolean {
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    return imm.enabledInputMethodList.any { it.packageName == packageName }
}
   private fun isKeyboardSelected(): Boolean {
    return try {
        val current = Settings.Secure.getString(
            contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD
        ) ?: return false
        current.contains(packageName)
    } catch (e: SecurityException) {
        false
    }
}

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private val BG = Color.parseColor("#0D0D0D")
        private val ACCENT = Color.parseColor("#E8333A")
        private val DONE = Color.parseColor("#2A2E35")
        private val MUTED = Color.parseColor("#8A8580")
        private val HINT = Color.parseColor("#5C5854")
        private val FIELD = Color.parseColor("#16181C")
        private val OK = Color.parseColor("#4ADE80")
    }
}
