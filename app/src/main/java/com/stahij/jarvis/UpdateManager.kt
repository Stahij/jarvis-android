package com.stahij.jarvis

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageInfo
import android.os.Build
import android.content.pm.PackageInstaller
import android.app.PendingIntent
import android.net.Uri
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.json.JSONObject

data class JarvisUpdate(
    val version: String,
    val downloadUrl: String,
    val sha256: String
)

class UpdateManager(private val context: Context) {
    fun checkForUpdate(): JarvisUpdate? {
        val connection = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 15000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Jarvis-Android")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
        try {
            if (connection.responseCode == 404) return null
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("GitHub вернул HTTP ${connection.responseCode}")
            }
            val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val latestVersion = release.optString("tag_name").removePrefix("v")
            val currentVersion = currentVersion()
            if (latestVersion.isBlank() || compareVersions(latestVersion, currentVersion) <= 0) return null

            val assets = release.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name") == APK_ASSET_NAME) {
                    val digest = asset.optString("digest").removePrefix("sha256:").lowercase()
                    val url = asset.optString("browser_download_url")
                    if (digest.matches(Regex("[a-f0-9]{64}")) && url.startsWith("https://")) {
                        return JarvisUpdate(latestVersion, url, digest)
                    }
                }
            }
            throw IllegalStateException("В релизе нет APK с опубликованной SHA-256 контрольной суммой")
        } finally {
            connection.disconnect()
        }
    }

    fun downloadAndVerify(update: JarvisUpdate): File {
        val directory = File(context.cacheDir, "updates")
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("Не удалось создать папку загрузки")
        }
        val apk = File(directory, "Jarvis-${update.version}.apk")
        val temporary = File(directory, "Jarvis-${update.version}.apk.part")
        val connection = (URL(update.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 60000
            setRequestProperty("User-Agent", "Jarvis-Android")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Не удалось скачать APK: HTTP ${connection.responseCode}")
            }
            connection.inputStream.use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }

        val actualHash = sha256(temporary)
        if (!actualHash.equals(update.sha256, ignoreCase = true)) {
            temporary.delete()
            throw SecurityException("SHA-256 APK не совпадает. Установка отменена.")
        }
        verifySigningCertificate(temporary)
        if (apk.exists()) apk.delete()
        if (!temporary.renameTo(apk)) {
            temporary.copyTo(apk, overwrite = true)
            temporary.delete()
        }
        return apk
    }

    fun install(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("jarvis-update.apk", 0, apk.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val callback = Intent(context, InstallResultReceiver::class.java)
                .setAction(InstallResultReceiver.ACTION_INSTALL_RESULT)
            val pending = PendingIntent.getBroadcast(
                context,
                sessionId,
                callback,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(pending.intentSender)
        }
    }

    private fun verifySigningCertificate(candidate: File) {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
            else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val installed = packageInfo(context.packageName, flags)
        val downloaded = context.packageManager.getPackageArchiveInfo(candidate.absolutePath, flags)
            ?: throw SecurityException("Android не распознал подпись APK")

        val installedSignatures = if (Build.VERSION.SDK_INT >= 28) {
            installed.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION") installed.signatures
        }
        val candidateSignatures = if (Build.VERSION.SDK_INT >= 28) {
            downloaded.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION") downloaded.signatures
        }
        val installedHash = installedSignatures?.firstOrNull()?.toByteArray()?.let { sha256(it) }
        val candidateHash = candidateSignatures?.firstOrNull()?.toByteArray()?.let { sha256(it) }
        if (installedHash.isNullOrBlank() || installedHash != candidateHash) {
            throw SecurityException("Подпись обновления отличается от установленной. Установка отменена.")
        }
        if (downloaded.packageName != context.packageName) {
            throw SecurityException("APK предназначен для другого приложения.")
        }
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(name: String, flags: Int): PackageInfo =
        context.packageManager.getPackageInfo(name, flags)

    private fun currentVersion(): String = packageInfo(context.packageName, 0).versionName ?: "0.0.0"

    private fun compareVersions(left: String, right: String): Int {
        val a = left.split(".", "-", "+").map { it.toIntOrNull() ?: 0 }
        val b = right.split(".", "-", "+").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val diff = (a.getOrNull(i) ?: 0).compareTo(b.getOrNull(i) ?: 0)
            if (diff != 0) return diff
        }
        return 0
    }

    private fun sha256(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count <= 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        private const val LATEST_RELEASE_API =
            "https://api.github.com/repos/Stahij/jarvis-android/releases/latest"
        private const val APK_ASSET_NAME = "Jarvis-release.apk"
    }
}
