package com.shuddhatype.ime

import android.app.Activity
import com.shuddhatype.R
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
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
        setContentView(buildLayout())
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    private fun buildLayout(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            setPadding(dp(24), dp(40), dp(24), dp(40))
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

        // Step 4 — the settings screen.
        //
        // Android only offers it as a gear buried in the system keyboard list,
        // three screens deep, and people who want to add a shortcut do not find
        // it. The app icon is where they look instead, so this is where it goes.
        // Note the fully qualified name: android.provider.Settings is imported
        // above for the system screens, and the bare name would resolve to that.
        root.addView(stepBlock(
            "४",
            "शर्टकट थप्ने, रङ फेर्ने",
            null,
            actionButton("सेटिङ खोल्ने") {
                startActivity(Intent(this, com.shuddhatype.ime.SettingsActivity::class.java))
            }
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
