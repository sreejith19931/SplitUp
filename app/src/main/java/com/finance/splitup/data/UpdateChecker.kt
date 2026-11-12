package com.finance.splitup.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.finance.splitup.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {
    suspend fun checkLatestRelease(onUpdateAvailable: (version: String, url: String) -> Unit, onUpToDate: () -> Unit = {}) {
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
            Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
        }
    }
}
