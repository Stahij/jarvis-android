package com.stahij.jarvis

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Checks the latest stable GitHub release. It never installs an APK silently:
 * Android's package installer and user confirmation remain mandatory.
 */
class ReleaseUpdateChecker(
    private val currentVersion: String,
    private val onUpdateAvailable: (version: String, notes: String, releaseUrl: String) -> Unit
) {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun check() {
        executor.execute {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL("https://api.github.com/repos/Stahij/jarvis-android/releases/latest")
                    .openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 7000
                    readTimeout = 7000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                }
                if (connection.responseCode !in 200..299) return@execute
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val release = JSONObject(body)
                if (release.optBoolean("draft", true) || release.optBoolean("prerelease", true)) return@execute
                val tag = release.optString("tag_name").removePrefix("v")
                if (isNewer(tag, currentVersion)) {
                    val notes = release.optString("body").take(1200)
                    val url = release.optString("html_url",
                        "https://github.com/Stahij/jarvis-android/releases/latest")
                    main.post { onUpdateAvailable(tag, notes, url) }
                }
            } catch (_: Exception) {
                // Offline or GitHub unavailable: launcher remains fully usable.
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun isNewer(remote: String, local: String): Boolean {
        fun parts(value: String) = value.split('.', '-', '+').map { it.toIntOrNull() ?: 0 }
        val a = parts(remote)
        val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(i) { 0 }
            val right = b.getOrElse(i) { 0 }
            if (left != right) return left > right
        }
        return false
    }
}
