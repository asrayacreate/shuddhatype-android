package com.shuddhatype.ime

import android.app.Activity
import com.shuddhatype.R
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Opened from the system keyboard list.
 *
 * It states what the keyboard does with your typing — the question people
 * actually open keyboard settings to answer — and carries the settings worth
 * having: the theme, and the user's own shortcuts. Anything that changes daily
 * belongs on a key; anything changed once belongs here, where it costs no
 * keyboard width.
 *
 * The tree is rebuilt with [recreate] after every change instead of being
 * patched in place. The screen is small and opened rarely, so the cost is
 * nothing, and a list that is always drawn from storage cannot drift out of
 * step with it.
 */
class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theme.reload(this)
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Theme.palette.screenBg)
            addView(buildLayout())
        })
    }

    private fun buildLayout(): View = LinearLayout(this).apply {
        val p = Theme.palette
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(p.screenBg)
        setPadding(dp(24), dp(40), dp(24), dp(40))

        addView(title(getString(R.string.app_name)))
        addView(body("संस्करण ${versionName()}"))

        addView(heading("रूप"))
        addView(body("किबोर्डको रङ छान्नुहोस्। किबोर्ड अर्को पटक खुल्दा लागू हुन्छ।"))
        addView(themeRow())

        addView(heading("शर्टकट"))
        addView(body(
            "छोटो अक्षर लेखेर लामो कुरा निकाल्नुहोस् — जस्तै pp लेखेर स्पेस थिच्दा " +
            "कम्पनीको नाम।\n\n" +
            "स्पेस थिच्नेबित्तिकै आफैँ फेरिन्छ। फेरिनुअघि माथिको सुझाव पट्टीमा " +
            "के आउँदैछ देखिन्छ।"
        ))
        addView(shortcutList())
        addView(addRow())

        addView(heading("गोपनीयता"))
        addView(body(
            "तपाईंले टाइप गरेको कुनै पनि कुरा फोनबाहिर जाँदैन। " +
            "यो एपसँग इन्टरनेट अनुमति नै छैन।\n\n" +
            "पासवर्ड र OTP लेख्ने ठाउँमा किबोर्डले केही पढ्दैन, सुझाव पनि दिँदैन।\n\n" +
            "शर्टकट पनि यही फोनमै बस्छन्।"
        ))

        addView(Button(this@SettingsActivity).apply {
            text = "सुरुको सेटअप फेरि हेर्ने"
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(Color.WHITE)
            setBackgroundColor(p.accent)
            setOnClickListener {
                startActivity(Intent(this@SettingsActivity, SetupActivity::class.java))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(28) }
        })
    }

    // ---- shortcuts ----

    private fun shortcutList(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(12), 0, 0)
        val saved = Shortcuts.all(this@SettingsActivity)
        if (saved.isEmpty()) {
            addView(body("अहिलेसम्म कुनै शर्टकट छैन।"))
            return@apply
        }
        for ((key, value) in saved) addView(shortcutRow(key, value))
    }

    private fun shortcutRow(key: String, value: String): View {
        val p = Theme.palette
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(p.keyMod)
            setPadding(dp(12), dp(10), dp(6), dp(10))

            addView(TextView(this@SettingsActivity).apply {
                text = key
                setTextColor(p.accent)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                typeface = Typeface.MONOSPACE
                layoutParams = LinearLayout.LayoutParams(dp(64), LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            addView(TextView(this@SettingsActivity).apply {
                text = value
                setTextColor(p.screenText)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
            })
            // Deletes without asking: one row is one line of text the user
            // typed themselves, and a confirmation dialog costs more than
            // typing it again would.
            addView(TextView(this@SettingsActivity).apply {
                text = "मेट्ने"
                gravity = Gravity.CENTER
                setTextColor(p.screenMuted)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(dp(10), dp(6), dp(10), dp(6))
                isClickable = true
                setOnClickListener {
                    Shortcuts.remove(this@SettingsActivity, key)
                    recreate()
                }
            })
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
        }
    }

    private fun addRow(): View {
        val p = Theme.palette
        val keyField = EditText(this).apply {
            hint = "pp"
            setHintTextColor(p.screenMuted)
            setTextColor(p.screenText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            // No autocorrect or capitals: this is a key, not prose, and a
            // helpful capital P would quietly create a shortcut that never
            // matches what the keyboard composes.
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine()
        }
        val valueField = EditText(this).apply {
            hint = "प्रेरक कन्स्ट्रक्सन एन्ड प्रिफ्याब होम्स प्रा. लि."
            setHintTextColor(p.screenMuted)
            setTextColor(p.screenText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine()
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, 0)

            addView(LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(keyField, LinearLayout.LayoutParams(
                    dp(80), LinearLayout.LayoutParams.WRAP_CONTENT
                ))
                addView(valueField, LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                ))
            })

            addView(Button(this@SettingsActivity).apply {
                text = "थप्ने"
                isAllCaps = false
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(Color.WHITE)
                setBackgroundColor(p.accent)
                setOnClickListener {
                    val k = keyField.text.toString().trim()
                    val v = valueField.text.toString().trim()
                    val bad = Shortcuts.rejectReason(k)
                    when {
                        bad != null -> toast(bad)
                        v.isEmpty() -> toast("के लेख्ने भन्ने खाली छ।")
                        v.length > Shortcuts.MAX_VALUE ->
                            toast("धेरै लामो भयो।")
                        else -> {
                            Shortcuts.put(this@SettingsActivity, k, v)
                            recreate()
                        }
                    }
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) }
            })
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // ---- theme ----

    private fun themeRow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(10), 0, 0)
        addView(themeButton("प्रणाली", Theme.Mode.SYSTEM))
        addView(themeButton("गाढा", Theme.Mode.DARK))
        addView(themeButton("उज्यालो", Theme.Mode.LIGHT))
    }

    private fun themeButton(label: String, target: Theme.Mode): View {
        val p = Theme.palette
        val chosen = Theme.mode(this) == target
        return TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(if (chosen) Color.WHITE else p.screenMuted)
            typeface = if (chosen) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            setBackgroundColor(if (chosen) p.accent else p.keyMod)
            setPadding(dp(8), dp(12), dp(8), dp(12))
            isClickable = true
            setOnClickListener {
                Theme.setMode(this@SettingsActivity, target)
                // Cheaper than rebuilding the tree by hand, and it repaints the
                // screen in the palette the user just picked.
                recreate()
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginEnd = dp(6) }
        }
    }

    private fun versionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
    } catch (e: Exception) { "1.0" }

    private fun title(t: String) = TextView(this).apply {
        text = t
        setTextColor(Theme.palette.screenText)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
        typeface = Typeface.DEFAULT_BOLD
    }

    private fun heading(t: String) = TextView(this).apply {
        text = t
        setTextColor(Theme.palette.accent)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(26), 0, dp(6))
    }

    private fun body(t: String) = TextView(this).apply {
        text = t
        setTextColor(Theme.palette.screenMuted)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
