package com.stahij.jarvis

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Bounded public-web transport for the on-device research agent.
 * Search results and page text are untrusted reference data, never tool instructions.
 */
object InternetResearchTool {
    data class Source(
        val title: String,
        val url: String,
        val retrievedAt: Long,
        val text: String
    )

    private const val MAX_RESPONSE_BYTES = 1_500_000
    private const val MAX_EXTRACTED_CHARS = 24_000
    private const val CONNECT_TIMEOUT_MS = 12_000
    private const val READ_TIMEOUT_MS = 18_000

    fun search(query: String, limit: Int = 8): List<Source> {
        require(query.isNotBlank()) { "Поисковый запрос пуст." }
        val address = "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query, "UTF-8")
        val html = request(address)
        val results = mutableListOf<Source>()
        val resultPattern = Regex(
            """(?is)<a[^>]+class=["'][^"']*result__a[^"']*["'][^>]+href=["']([^"']+)["'][^>]*>(.*?)</a>"""
        )
        val snippetPattern = Regex(
            """(?is)<a[^>]+class=["'][^"']*result__snippet[^"']*["'][^>]*>(.*?)</a>|<div[^>]+class=["'][^"']*result__snippet[^"']*["'][^>]*>(.*?)</div>"""
        )
        val matches = resultPattern.findAll(html).take(limit.coerceIn(1, 10))
        for (match in matches) {
            val link = decodeEntities(stripTags(match.groupValues[1]))
            val target = unwrapSearchUrl(link) ?: continue
            if (!isPublicHttpUrl(target)) continue
            val title = stripTags(match.groupValues[2]).take(240)
            val snippetMatch = snippetPattern.find(html.substring(match.range.last + 1))
            val snippet = snippetMatch?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }
                ?.let { stripTags(it) }.orEmpty()
            results.add(Source(title, target, System.currentTimeMillis(), snippet.take(1200)))
        }
        return results
    }

    fun fetch(address: String): Source {
        require(isPublicHttpUrl(address)) { "Разрешены только публичные HTTP(S)-адреса." }
        val html = request(address)
        val title = Regex("(?is)<title[^>]*>(.*?)</title>").find(html)
            ?.groupValues?.get(1)?.let { decodeEntities(stripTags(it)) }
            ?.take(240).orEmpty().ifBlank { URL(address).host }
        val canonical = Regex(
            """(?is)<link[^>]+rel=["']canonical["'][^>]+href=["']([^"']+)["']"""
        ).find(html)?.groupValues?.get(1)?.let { resolve(address, decodeEntities(it)) } ?: address
        val cleaned = html
            .replace(Regex("(?is)<(script|style|noscript|svg|nav|footer|header)[^>]*>.*?</\\1>"), " ")
            .replace(Regex("(?is)<(br|/p|/div|/li|/h[1-6]|/tr)[^>]*>"), "\\n")
        val text = decodeEntities(stripTags(cleaned))
            .replace(Regex("[\\t\\x0B\\f\\r ]+"), " ")
            .replace(Regex(" *\\n *"), "\\n")
            .replace(Regex("\\n{3,}"), "\\n\\n")
            .trim().take(MAX_EXTRACTED_CHARS)
        if (text.isBlank()) throw IllegalStateException("На странице не удалось извлечь читаемый текст.")
        return Source(title, canonical, System.currentTimeMillis(), text)
    }

    private fun request(address: String): String {
        require(isPublicHttpUrl(address)) { "Недопустимый адрес источника." }
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "JarvisAndroid/1.1 (public web research)")
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,text/plain,application/json")
            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                    ?: throw IllegalStateException("Источник вернул redirect без адреса.")
                val next = resolve(address, location)
                require(isPublicHttpUrl(next)) { "Переадресация на непубличный адрес заблокирована." }
                return request(next)
            }
            if (code !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException("HTTP $code: ${detail.take(180)}")
            }
            val length = connection.contentLengthLong
            require(length <= MAX_RESPONSE_BYTES) { "Страница слишком большая для обработки на телефоне." }
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_RESPONSE_BYTES) { "Превышен лимит размера страницы." }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            return String(bytes, Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }

    private fun isPublicHttpUrl(address: String): Boolean {
        return try {
            val url = URL(address)
            if (url.protocol.lowercase(Locale.ROOT) !in setOf("http", "https")) return false
            if (url.userInfo != null) return false
            val host = url.host.lowercase(Locale.ROOT).removePrefix("[").removeSuffix("]")
            if (host.isBlank() || host == "localhost" || host.endsWith(".localhost") ||
                host.endsWith(".local")) return false
            val resolved = InetAddress.getAllByName(host)
            resolved.isNotEmpty() && resolved.all { ip ->
                !ip.isAnyLocalAddress && !ip.isLoopbackAddress && !ip.isLinkLocalAddress &&
                    !ip.isSiteLocalAddress && !ip.isMulticastAddress
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun unwrapSearchUrl(value: String): String? {
        val url = try { URL(value) } catch (_: Exception) { return null }
        if (url.host.endsWith("duckduckgo.com")) {
            val query = url.query.orEmpty().split("&").firstOrNull { it.startsWith("uddg=") }
            if (query != null) return java.net.URLDecoder.decode(query.substringAfter("="), "UTF-8")
        }
        return value
    }

    private fun resolve(base: String, path: String): String =
        URL(URL(base), path).toString()

    private fun stripTags(value: String): String =
        value.replace(Regex("(?is)<[^>]+>"), " ")
            .replace("&nbsp;", " ").replace("&#39;", "'").replace("&amp;", "&")
            .replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">")
            .replace(Regex("\\s+"), " ").trim()

    private fun decodeEntities(value: String): String =
        value.replace("&nbsp;", " ").replace("&#39;", "'").replace("&amp;", "&")
            .replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">")
}
