package com.shuddhatype

import android.app.Application
import com.shuddhatype.crash.CrashHandler

/**
 * Installed here, not in an Activity's onCreate, so it also covers a crash
 * inside the IME service — which can start before any Activity does.
 */
class ShuddhaTypeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
    }
}
