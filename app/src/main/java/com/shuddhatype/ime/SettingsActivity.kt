package com.shuddhatype.ime

import android.app.Activity
import com.shuddhatype.R
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Opened from the system keyboard list. v1 has no options to change — the
 * engine has no knobs worth exposing yet — but the class must exist, because
 * method.xml names it and Android will crash on a missing settingsActivity.
 *
 * Rather than showing an empty screen, it states what the keyboard does with
 * your typing. That is the question people actually open keyboard settings to
 * answer.
 */
class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildLayout())
    }

    private fun buildLayout(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(BG)
        setPadding(dp(24), dp(40), dp(24), dp(40))

        addView(title(getString(R.string.app_name)))
        addView(body("संस्करण ${versionName()}"))

        addView(heading("गोपनीयता"))
        addView(body(
            "तपाईंले टाइप गरेको कुनै पनि कुरा फोनबाहिर जाँदैन। " +
            "यो एपसँग इन्टरनेट अनुमति नै छैन।\n\n" +
            "पासवर्ड र OTP लेख्ने ठाउँमा किबोर्डले केही पढ्दैन, सुझाव पनि दिँदैन।"
        ))

        addView(heading("मिलाउने कुरा"))
        addView(body("यो संस्करणमा मिलाउनुपर्ने केही छैन — किबोर्ड जस्ताको तस्तै चल्छ।"))

        addView(Button(this@SettingsActivity).apply {
            text = "सुरुको सेटअप फेरि हेर्ने"
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(Color.WHITE)
            setBackgroundColor(ACCENT)
            setOnClickListener {
                startActivity(Intent(this@SettingsActivity, SetupActivity::class.java))
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(28) }
        })
    }

    private fun versionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
    } catch (e: Exception) { "1.0" }

    private fun title(t: String) = TextView(this).apply {
        text = t
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
        typeface = Typeface.DEFAULT_BOLD
    }

    private fun heading(t: String) = TextView(this).apply {
        text = t
        setTextColor(ACCENT)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(26), 0, dp(6))
    }

    private fun body(t: String) = TextView(this).apply {
        text = t
        setTextColor(MUTED)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private val BG = Color.parseColor("#0D0D0D")
        private val ACCENT = Color.parseColor("#E8333A")
        private val MUTED = Color.parseColor("#B9BCC2")
    }
}
