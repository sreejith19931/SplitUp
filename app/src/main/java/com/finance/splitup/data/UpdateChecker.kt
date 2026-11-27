package com.finance.splitup.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.finance.splitup.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import com.finance.splitup.data.UiMessage

object UpdateChecker {
    private const val PLAY_STORE_PACKAGE = "com.android.vending"

    /**
     * True when the app was installed or is currently updated by Google Play.
     * GitHub-release self-updates must never run for Play-distributed installs
     * (Google Play policy: apps may not alter or replace themselves outside Play).
     */
    fun isInstalledFromPlayStore(context: Context): Boolean {
        return try {
            val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
            installer == PLAY_STORE_PACKAGE
        } catch (e: Exception) {
            false
        }
    }

    suspend fun checkLatestRelease(
        context: Context,
        onUpdateAvailable: (version: String, url: String) -> Unit,
        onUpToDate: () -> Unit = {}
    ) {
        // Never self-update a Play Store install; point users at the Play listing instead.
        if (isInstalledFromPlayStore(context)) {
            withContext(Dispatchers.Main) {
                onUpdateAvailable("play", "https://play.google.com/store/apps/details?id=${context.packageName}")
            }
            return
        }
        withContext(Dispatchers.IO) {
            try {
                val url = URL("https://api.github.com/repos/sreejith19931/SplitUp/releases/latest")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                if (conn.responseCode == 200) {
                    val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(jsonText)
                    val tagName = json.optString("tag_name", "").removePrefix("v")
                    val htmlUrl = json.optString("html_url", "https://github.com/sreejith19931/SplitUp/releases/latest")
                    val currentVersion = BuildConfig.VERSION_NAME

                    if (tagName.isNotBlank() && isNewerVersion(tagName, currentVersion)) {
                        withContext(Dispatchers.Main) {
                            onUpdateAvailable(tagName, htmlUrl)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            onUpToDate()
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore network errors silently
            }
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val partsLatest = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val partsCurrent = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(partsLatest.size, partsCurrent.size)) {
            val l = partsLatest.getOrElse(i) { 0 }
            val c = partsCurrent.getOrElse(i) { 0 }
            if (l != c) return l > c
        }
        return false
    }

    fun openReleaseUrl(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (e: Exception) {
            UiMessage.show("Could not open browser")
        }
    }
}
