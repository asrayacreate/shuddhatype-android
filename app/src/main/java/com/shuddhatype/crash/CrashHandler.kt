package com.shuddhatype.crash

import android.content.Context
import android.content.Intent
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A crashing process can't reliably show its own dialog — the window is
 * already being torn down. So this does the two things that actually
 * survive a crash:
 *
 * 1. Write the trace to disk, in more than one place, before anything else
 *    can go wrong.
 * 2. Launch CrashActivity with FLAG_ACTIVITY_NEW_TASK, then kill this
 *    process. Android starts CrashActivity in a *new* process — the crash
 *    doesn't follow it there.
 *
 * CrashActivity itself must not depend on anything this handler could be
 * catching a crash from — no custom theme, no engine, no resources beyond
 * what the platform guarantees. See CrashActivity for that constraint.
 */
object CrashHandler {
    private const val FILE_NAME = "shuddhatype_last_crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            try {
                writeTrace(appContext, throwable)
            } catch (_: Throwable) {
                // A failure here must not stop the process from restarting.
            }
            try {
                val intent = Intent(appContext, CrashActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                appContext.startActivity(intent)
            } catch (_: Throwable) {
                // If this also fails, the file on disk is the fallback.
            }
            android.os.Process.killProcess(android.os.Process.myPid())
            Runtime.getRuntime().exit(10)
        }
    }

    private fun writeTrace(context: Context, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val text = buildString {
            append("ShuddhaType crash — ")
            append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
            append("\n\n")
            append(sw.toString())
        }

        // Three attempts, in order of reachability without a computer:
        // 1) app-internal storage (always available, but needs the app itself
        //    or root to browse — this is what CrashActivity reads back).
        // 2) app-external storage (visible to File Manager apps under
        //    Android/data/com.shuddhatype/files, no permission needed on
        //    modern Android).
        // 3) /sdcard/Download, plainly visible, if the OS allows the write.
        val candidates = mutableListOf<File>()
        candidates += File(context.filesDir, FILE_NAME)
        context.getExternalFilesDir(null)?.let { candidates += File(it, FILE_NAME) }
        candidates += File("/sdcard/Download", FILE_NAME)

        for (f in candidates) {
            try {
                f.parentFile?.mkdirs()
                f.writeText(text)
            } catch (_: Throwable) {
                // Try the next location.
            }
        }
    }

    /** Used by CrashActivity, and by SetupActivity to offer a leftover trace on next launch. */
    fun readLastTrace(context: Context): String? {
        val f = File(context.filesDir, FILE_NAME)
        return if (f.exists()) f.readText() else null
    }

    fun clearLastTrace(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
    }
}
