package com.finance.splitup

import android.app.Application
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel

class SplitUpApplication : Application() {

    companion object {
        @Volatile
        private var instance: SplitUpApplication? = null

        /** Application context for non-Android singletons (e.g. CurrencyApiService). */
        val appContext: android.content.Context?
            get() = instance?.applicationContext
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Restore the last persisted exchange rates so balance computations
        // immediately after restart use the freshest known rates instead of
        // the built-in static fallbacks, before the network refresh returns.
        try {
            com.finance.splitup.data.CurrencyApiService.restorePersistedRates()
        } catch (_: Exception) {
        }

        // Verbose logging only for debug builds; keep release logs quiet.
        OneSignal.Debug.logLevel = if (BuildConfig.DEBUG) LogLevel.VERBOSE else LogLevel.WARN

        // Initialize OneSignal with App ID
        OneSignal.initWithContext(this, "607e86ec-f74c-4419-ab95-2e420c912bde")
    }
}
