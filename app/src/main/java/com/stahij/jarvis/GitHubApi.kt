package com.stahij.jarvis

import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Small authenticated GitHub REST client. Never logs or returns authorization headers. */
class GitHubApi(private val token: String) {
    data class RemoteFile(val path: String, val content: String, val sha: String)

    fun verify(): String {
        val user = request("GET", "https://api.github.com/user")
        return user.optString("login").ifBlank { "GitHub user" }
    }

    fun repository(repo: String): JSONObject =
        request("GET", "https://api.github.com/repos/${safeRepo(repo)}")

    fun tree(repo: String, branch: String): List<String> {
        val encoded = URLEncoder.encode(branch, "UTF-8")
        val json = request("GET", "https://api.github.com/repos/${safeRepo(repo)}/git/trees/$encoded?recursive=1")
        if (json.optBoolean("truncated")) throw IllegalStateException("Дерево репозитория обрезано GitHub.")
        val array = json.optJSONArray("tree") ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            if (item.optString("type") == "blob") item.optString("path").takeIf { it.isNotBlank() } else null
        }
    }

    fun readFile(repo: String, branch: String, path: String): RemoteFile {
        val encodedPath = path.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        val ref = URLEncoder.encode(branch, "UTF-8")
        val json = request("GET", "https://api.github.com/repos/${safeRepo(repo)}/contents/$encodedPath?ref=$ref")
        val raw = json.optString("content").replace("\n", "")
        val decoded = if (raw.isBlank()) "" else String(Base64.decode(raw, Base64.DEFAULT), Charsets.UTF_8)
        return RemoteFile(path, decoded, json.optString("sha"))
    }

    fun writeFile(repo: String, branch: String, path: String, content: String, sha: String?, message: String): String {
        val encodedPath = path.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        val body = JSONObject()
            .put("message", message)
            .put("content", Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
            .put("branch", branch)
        if (!sha.isNullOrBlank()) body.put("sha", sha)
        val result = request("PUT", "https://api.github.com/repos/${safeRepo(repo)}/contents/$encodedPath", body)
        return result.optJSONObject("commit")?.optString("sha").orEmpty()
    }

    private fun safeRepo(repo: String): String {
        require(Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$").matches(repo)) { "Неверный формат owner/repository" }
        return repo
    }

    private fun request(method: String, address: String, payload: JSONObject? = null): JSONObject {
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15000
            connection.readTimeout = 25000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", "Jarvis-Android")
            if (payload != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val detail = try { JSONObject(text).optString("message") } catch (_: Exception) { text }
                throw IllegalStateException("GitHub API HTTP $code: ${detail.take(240)}")
            }
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }
}
