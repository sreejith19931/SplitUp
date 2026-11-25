package com.finance.splitup

import android.app.Application
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel

class SplitUpApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Verbose logging only for debug builds; keep release logs quiet.
        OneSignal.Debug.logLevel = if (BuildConfig.DEBUG) LogLevel.VERBOSE else LogLevel.WARN

        // Initialize OneSignal with App ID
        OneSignal.initWithContext(this, "607e86ec-f74c-4419-ab95-2e420c912bde")
    }
}
