package com.shuddhatype.crash

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Shows the trace CrashHandler wrote to disk, with a Share button.
 *
 * Deliberately built with framework views only, and every color set
 * inline — no @style/Theme.ShuddhaType, no colors.xml, no layout XML, no
 * engine import. If the real crash turns out to be a resource problem, this
 * screen still has to render, so it can't share the resource it's diagnosing.
 */
class CrashActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val trace = try {
            CrashHandler.readLastTrace(this)
        } catch (_: Throwable) {
            null
        } ?: "Crash भयो तर trace file भेटिएन। Downloads folder मा " +
            "shuddhatype_last_crash.txt खोज्नुहोस्।"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(dp(24), dp(48), dp(24), dp(24))
        }

        root.addView(TextView(this).apply {
            text = "ShuddhaType बन्द भयो"
            setTextColor(Color.WHITE)
            textSize = 20f
            setPadding(0, 0, 0, dp(8))
        })

        root.addView(TextView(this).apply {
            text = "यो जानकारी Share गरेर developer लाई पठाउनुहोस्।"
            setTextColor(Color.LTGRAY)
            textSize = 13f
            setPadding(0, 0, 0, dp(16))
        })

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
            setBackgroundColor(Color.parseColor("#1A1A1A"))
        }
        scroll.addView(TextView(this).apply {
            text = trace
            setTextColor(Color.parseColor("#DDDDDD"))
            textSize = 11f
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setTextIsSelectable(true)
        })
        root.addView(scroll)

        root.addView(Button(this).apply {
            text = "Share"
            setPadding(0, dp(16), 0, 0)
            setOnClickListener {
                try {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, trace)
                    }
                    startActivity(Intent.createChooser(send, "Share crash log"))
                } catch (_: Throwable) {
                    // Nothing more this screen can do if even Share fails.
                }
            }
        })

        root.addView(Button(this).apply {
            text = "बन्द गर्नुहोस्"
            setPadding(0, dp(8), 0, 0)
            setOnClickListener { finish() }
        })

        setContentView(root)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
