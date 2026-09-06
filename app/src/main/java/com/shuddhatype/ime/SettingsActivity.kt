package com.shuddhatype.ime

import android.app.Activity
import com.shuddhatype.R
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Opened from the system keyboard list, and from step 4 of the setup screen —
 * Android only offers it as a gear three screens deep, which is not where
 * anyone looks.
 *
 * It states what the keyboard does with your typing, and carries the settings
 * worth having: the theme, and the user's own shortcuts.
 *
 * The tree is rebuilt with [recreate] after every change rather than patched in
 * place, so the list is always drawn from storage and cannot drift out of step
 * with it. The cost is that a half-typed shortcut would be thrown away by a
 * theme tap; [draftKey] and [draftValue] carry it across.
 */
class SettingsActivity : Activity() {

    private lateinit var keyField: EditText
    private lateinit var valueField: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theme.reload(this)
        val root = ScrollView(this).apply {
            setBackgroundColor(Theme.palette.screenBg)
            addView(buildLayout())
        }
        fitInsets(root)
        setContentView(root)
    }

    /**
     * Leave room for the status bar and the keyboard.
     *
     * targetSdk 35 makes Android 15 lay every window out edge to edge, which
     * turns `adjustResize` into a no-op — the keyboard is painted *over* the
     * layout rather than shrinking it, hiding whatever field is being typed
     * into. Below API 30 there is no `ime()` inset type and `adjustResize`
     * still works on its own.
     */
    private fun fitInsets(root: View) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        root.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            val ime = insets.getInsets(WindowInsets.Type.ime())
            // Keyboard and navigation bar share the bottom edge; the larger of
            // the two is the whole of what has to be cleared.
            v.setPadding(0, bars.top, 0, maxOf(bars.bottom, ime.bottom))
            insets
        }
    }

    /** Rebuild in the new palette without losing what is half-typed. */
    private fun rebuild() {
        draftKey = keyField.text.toString()
        draftValue = valueField.text.toString()
        recreate()
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
            // Deletes without asking: one row is one line the user typed
            // themselves, and a confirmation costs more than retyping it would.
            addView(TextView(this@SettingsActivity).apply {
                text = "मेट्ने"
                gravity = Gravity.CENTER
                setTextColor(p.screenMuted)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(dp(10), dp(6), dp(10), dp(6))
                isClickable = true
                setOnClickListener {
                    Shortcuts.remove(this@SettingsActivity, key)
                    draftKey = ""
                    draftValue = ""
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

        keyField = EditText(this).apply {
            hint = "pp"
            setHintTextColor(faded(p.screenMuted))
            setTextColor(p.screenText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.MONOSPACE
            // No autocorrect or capitals: this is a key, not prose, and a
            // helpful capital P would create a shortcut that never matches.
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            // Ask our own keyboard to open on the English page. Typed on the
            // नेपाली page, pp arrives as प्प and is refused for containing no
            // a-z — a rejection the user has no way to make sense of, having
            // watched themselves type exactly what the hint asked for.
            privateImeOptions = ShuddhaTypeService.LATIN_FIELD
            setSingleLine()
            setText(draftKey)
        }

        valueField = EditText(this).apply {
            hint = "प्रेरक कन्स्ट्रक्सन एन्ड प्रिफ्याब होम्स प्रा. लि."
            setHintTextColor(faded(p.screenMuted))
            setTextColor(p.screenText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine()
            setText(draftValue)
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(16), 0, 0)

            addView(LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                // Labels, because an example inside an empty field reads as
                // something already typed. This is exactly what went wrong: the
                // grey pp looked filled in, so "थप्ने" was pressed on an empty
                // field and refused it for being too short.
                addView(fieldLabel("छोटो अक्षर", dp(96)))
                addView(fieldLabel("के लेख्ने", 0, 1f))
            })

            addView(LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(keyField, LinearLayout.LayoutParams(
                    dp(96), LinearLayout.LayoutParams.WRAP_CONTENT
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
                        v.length > Shortcuts.MAX_VALUE -> toast("धेरै लामो भयो।")
                        else -> {
                            Shortcuts.put(this@SettingsActivity, k, v)
                            draftKey = ""
                            draftValue = ""
                            recreate()
                        }
                    }
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(10) }
            })
        }
    }

    private fun fieldLabel(text: String, width: Int, weight: Float = 0f) =
        TextView(this).apply {
            this.text = text
            setTextColor(Theme.palette.screenMuted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(2), 0, 0, dp(2))
            layoutParams = LinearLayout.LayoutParams(
                width, LinearLayout.LayoutParams.WRAP_CONTENT, weight
            )
        }

    /** Muted enough that an example can never pass for typed text. */
    private fun faded(color: Int) =
        Color.argb(80, Color.red(color), Color.green(color), Color.blue(color))

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
                rebuild()
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

    private companion object {
        /**
         * A half-typed shortcut, held across the [recreate] that a theme tap
         * causes. Static rather than saved instance state because it only has
         * to survive that one rebuild, inside one process.
         */
        var draftKey: String = ""
        var draftValue: String = ""
    }
}
