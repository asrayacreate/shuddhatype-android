package com.shuddhatype.ime

import android.app.Activity
import com.shuddhatype.R
import com.shuddhatype.engine.Preeti
import com.shuddhatype.engine.Templates
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
        // Labelled, not just printed. The name alone sat under the version
        // number and read as part of the header — it never answered the
        // question anyone actually opens settings with, which is who made this.
        addView(body(
            "विकासकर्ता: Prerak Multipurpose Pvt. Ltd.\n" +
            "हेटौंडा-२, मकवानपुर, नेपाल\n" +
            "www.prerakmultipurpose.com"
        ))

        addView(heading("रूप"))
        addView(body("किबोर्डको रङ छान्नुहोस्। किबोर्ड अर्को पटक खुल्दा लागू हुन्छ।"))
        addView(themeRow())

        addView(heading("शर्टकट"))
        addView(body(
            "छोटो अक्षर लेखेर लामो कुरा निकाल्नुहोस् — जस्तै pp लेखेर स्पेस थिच्दा " +
            "कम्पनीको नाम।\n\n" +
            "स्पेस थिच्नेबित्तिकै आफैँ फेरिन्छ। फेरिनुअघि माथिको सुझाव पट्टीमा " +
            "के आउँदैछ देखिन्छ।\n\n" +
            "लामो सन्देश पनि राख्न सकिन्छ — WhatsApp मा पठाउने पूरा परिचय जस्तै। " +
            "लाइन ब्रेक जस्ताको तस्तै रहन्छ।"
        ))
        addView(shortcutList())
        addView(addRow())

        addView(heading("Preeti → युनिकोड"))
        addView(body(
            "पुराना Preeti फन्टका लेख युनिकोडमा बदल्नुहोस् — पुराना कागजात, " +
            "पत्र, सूचना।\n\n" +
            "Preeti फन्ट होइन, अक्षर नै हो भन्ने कुरा यहाँ काम लाग्छ: फन्ट " +
            "नभएको कम्प्युटरमा जे देखिन्छ, त्यही यहाँ टाँस्नुहोस्।"
        ))
        addView(preetiBox())

        addView(heading("औपचारिक ढाँचा"))
        addView(body(
            "निवेदन, सिफारिस, कोटेशन पठाउने पत्र — ढाँचा छान्नुहोस्, तलको " +
            "बाकसमा [ ] भित्रका ठाउँ भर्नुहोस्, अनि कपी गरेर जहाँ लेख्दै " +
            "हुनुहुन्छ त्यहाँ टाँस्नुहोस्।"
        ))
        addView(templateBox())

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

    /**
     * One stored shortcut.
     *
     * A shortcut can now hold a whole WhatsApp introduction, so the row shows
     * only the first line until it is tapped. Printing all of it would make a
     * list of three shortcuts longer than the screen, and the thing you came to
     * check — which key does what — would be the hardest part to find.
     *
     * Editing loads the pair back into the fields below rather than opening
     * anything new: [Shortcuts.put] overwrites by key, so saving from there
     * replaces the row. Fixing one letter of a five-hundred-character message
     * used to mean deleting it and typing the whole thing again.
     */
    private fun shortcutRow(key: String, value: String): View {
        val p = Theme.palette
        val open = expanded.contains(key)
        val lines = value.count { it == '\n' } + 1
        val preview = when {
            open -> value
            value.length <= PREVIEW_CHARS && lines == 1 -> value
            else -> value.take(PREVIEW_CHARS).substringBefore('\n').trimEnd() + " …"
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(p.keyMod)
            setPadding(dp(12), dp(10), dp(10), dp(10))

            addView(LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL

                addView(TextView(this@SettingsActivity).apply {
                    text = key
                    setTextColor(p.accent)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    typeface = Typeface.MONOSPACE
                    layoutParams = LinearLayout.LayoutParams(
                        dp(64), LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                })
                addView(TextView(this@SettingsActivity).apply {
                    text = preview
                    setTextColor(p.screenText)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    isClickable = true
                    setOnClickListener {
                        if (open) expanded.remove(key) else expanded.add(key)
                        rebuild()
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    )
                })
            })

            // Only offered when there is something hidden — a one-line shortcut
            // has nothing to open, and the word would just be noise.
            val hasMore = value.length > PREVIEW_CHARS || lines > 1

            addView(LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(64), dp(6), 0, 0)
                if (hasMore) addView(rowAction(if (open) "लुकाउने" else "पूरा हेर्ने") {
                    if (open) expanded.remove(key) else expanded.add(key)
                    rebuild()
                })
                addView(rowAction("सम्पादन") {
                    keyField.setText(key)
                    valueField.setText(value)
                    valueField.requestFocus()
                    toast("सच्याएर थप्ने थिच्नुहोस्।")
                })
                addView(rowAction("मेट्ने") {
                    Shortcuts.remove(this@SettingsActivity, key)
                    expanded.remove(key)
                    draftKey = ""
                    draftValue = ""
                    recreate()
                })
            })

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
        }
    }

    /**
     * Paste Preeti in, get Unicode out, copy it away.
     *
     * A screen rather than a keyboard key because the text almost never starts
     * on the phone — it arrives from a document, an email, a twenty-year-old
     * notice — so pasting is the natural move, and a key would cost keyboard
     * width for something used once a week.
     *
     * The output is a field, not a label, so it can be corrected before it is
     * copied. That matters: a Preeti file with English in it converts the
     * English too — "Hello" was stored as the same bytes as ज्भििय and nothing
     * in the text says which was meant. Nobody can fix that automatically.
     */
    /**
     * Pick a letter, fill in the brackets, copy it away.
     *
     * An editable box rather than a label on purpose: every one of these has
     * blanks in it, and a template you cannot type into is a template you have
     * to retype somewhere else first.
     */
    private fun templateBox(): View {
        val p = Theme.palette

        val out = EditText(this).apply {
            hint = "माथिबाट ढाँचा छान्नुहोस्"
            setHintTextColor(faded(p.screenMuted))
            setTextColor(p.screenText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setSingleLine(false)
            maxLines = 12
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, 0)
            val wide = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            // Names on their own rows rather than in a scrolling strip: there
            // are five of them and they are read once, not scanned daily.
            for (t in Templates.ALL) {
                addView(Button(this@SettingsActivity).apply {
                    text = t.name
                    isAllCaps = false
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    setTextColor(p.screenText)
                    setBackgroundColor(p.keyMod)
                    setOnClickListener { out.setText(t.body) }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(6) }
                })
            }

            addView(out, wide)

            addView(Button(this@SettingsActivity).apply {
                text = "कपी गर्ने"
                isAllCaps = false
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(Color.WHITE)
                setBackgroundColor(p.accent)
                setOnClickListener {
                    val text = out.text.toString()
                    if (text.isBlank()) {
                        toast("पहिले ढाँचा छान्नुहोस्।"); return@setOnClickListener
                    }
                    val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cb.setPrimaryClip(ClipData.newPlainText("ShuddhaType", text))
                    toast("कपी भयो।")
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) }
            })
        }
    }

    private fun preetiBox(): View {
        val p = Theme.palette

        val input = EditText(this).apply {
            hint = "Preeti लेख यहाँ टाँस्नुहोस्"
            setHintTextColor(faded(p.screenMuted))
            setTextColor(p.screenText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine(false)
            maxLines = 5
        }
        val output = EditText(this).apply {
            hint = "बदलिएको लेख यहाँ आउँछ"
            setHintTextColor(faded(p.screenMuted))
            setTextColor(p.screenText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setSingleLine(false)
            maxLines = 6
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, 0)
            val wide = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addView(input, wide)

            addView(LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(8), 0, 0)

                addView(Button(this@SettingsActivity).apply {
                    text = "बदल्ने"
                    isAllCaps = false
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    setTextColor(Color.WHITE)
                    setBackgroundColor(p.accent)
                    setOnClickListener {
                        val src = input.text.toString()
                        when {
                            src.isBlank() -> toast("पहिले Preeti लेख टाँस्नुहोस्।")
                            !Preeti.looksLikePreeti(src) ->
                                toast("यो Preeti लेख जस्तो देखिएन।")
                            else -> output.setText(Preeti.toUnicode(src))
                        }
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    ).apply { marginEnd = dp(6) }
                })

                addView(Button(this@SettingsActivity).apply {
                    text = "कपी गर्ने"
                    isAllCaps = false
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    setTextColor(Color.WHITE)
                    setBackgroundColor(p.keyMod)
                    setOnClickListener {
                        val out = output.text.toString()
                        if (out.isBlank()) { toast("बदल्ने पहिले थिच्नुहोस्।"); return@setOnClickListener }
                        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("ShuddhaType", out))
                        toast("कपी भयो।")
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    )
                })
            })

            addView(output, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) })
        }
    }

    private fun rowAction(label: String, onClick: () -> Unit) =
        TextView(this).apply {
            text = label
            setTextColor(Theme.palette.screenMuted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(4), dp(18), dp(4))
            isClickable = true
            setOnClickListener { onClick() }
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
            // Multi-line: a shortcut can hold a whole message now, and the
            // Enter key has to make a line break rather than close the field.
            // Capped at six lines so a long one scrolls instead of pushing the
            // थप्ने button off the screen.
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setSingleLine(false)
            maxLines = 6
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
                        v.length > Shortcuts.MAX_VALUE ->
                            toast("धेरै लामो भयो — ${Shortcuts.MAX_VALUE} अक्षरसम्म मात्र।")
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

        /**
         * Which rows are showing their full text. Static for the same reason
         * as the drafts: it only has to survive the [recreate] that a tap
         * causes, inside one process.
         */
        val expanded = mutableSetOf<String>()

        /** How much of a long shortcut the collapsed row shows. */
        const val PREVIEW_CHARS = 42
    }
}
