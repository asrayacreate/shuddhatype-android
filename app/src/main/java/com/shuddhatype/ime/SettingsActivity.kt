package com.shuddhatype.ime

import android.app.Activity
import com.shuddhatype.R
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Opened from the system keyboard list.
 *
 * It states what the keyboard does with your typing — the question people
 * actually open keyboard settings to answer — and now carries the one setting
 * worth having: the theme. Anything that changes daily belongs on a key;
 * anything changed once belongs here, where it costs no keyboard width.
 */
class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theme.reload(this)
        setContentView(buildLayout())
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

        addView(heading("गोपनीयता"))
        addView(body(
            "तपाईंले टाइप गरेको कुनै पनि कुरा फोनबाहिर जाँदैन। " +
            "यो एपसँग इन्टरनेट अनुमति नै छैन।\n\n" +
            "पासवर्ड र OTP लेख्ने ठाउँमा किबोर्डले केही पढ्दैन, सुझाव पनि दिँदैन।"
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
