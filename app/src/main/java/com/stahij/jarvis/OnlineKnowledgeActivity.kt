package com.stahij.jarvis

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/** Manual public-source lookup. Search text only; chat history and files are never sent. */
class OnlineKnowledgeActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val bg = Color.rgb(5, 10, 22)
    private val cyan = Color.rgb(64, 231, 255)
    private lateinit var query: EditText
    private lateinit var status: TextView
    private lateinit var results: LinearLayout
    private val prefs by lazy { getSharedPreferences("jarvis_knowledge", MODE_PRIVATE) }
    private val saved = JSONArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(bg)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "ИНТЕРНЕТ И ЗНАНИЯ"
            textSize = 18f
            setTextColor(cyan)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(Button(this).apply {
            text = "Назад"
            setOnClickListener { finish() }
        })
        root.addView(header)
        query = EditText(this).apply {
            hint = "Что нужно найти?"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            singleLine = true
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setBackgroundColor(Color.rgb(15, 27, 43))
        }
        root.addView(query)
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(Button(this).apply {
            text = "Найти"
            isAllCaps = false
            setOnClickListener { search() }
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        buttons.addView(Button(this).apply {
            text = "Сохранённое"
            isAllCaps = false
            setOnClickListener { showSaved() }
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(buttons)
        status = TextView(this).apply {
            text = "Поиск запускается только по твоему запросу. Источник: Wikipedia."
            textSize = 11f
            setTextColor(Color.rgb(104, 160, 181))
            setPadding(0, dp(8), 0, dp(8))
        }
        root.addView(status)
        val scroll = ScrollView(this)
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(results)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun search() {
        val term = query.text.toString().trim()
        if (term.isBlank()) return
        status.text = "ИЩУ: " + term
        results.removeAllViews()
        executor.execute {
            val response = try {
                val address = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=" +
                    enc(term) + "&format=json&utf8=1&srlimit=8"
                val connection = URL(address).openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 15000
                connection.setRequestProperty("User-Agent", "JarvisAndroid/0.6 (local assistant)")
                try {
                    if (connection.responseCode !in 200..299) throw IllegalStateException("HTTP " + connection.responseCode)
                    JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                } finally { connection.disconnect() }
            } catch (e: Exception) {
                JSONObject().put("error", e.message ?: "Ошибка поиска")
            }
            runOnUiThread { renderSearch(response, term) }
        }
    }

    private fun renderSearch(response: JSONObject, term: String) {
        results.removeAllViews()
        val error = response.optString("error")
        if (error.isNotBlank()) {
            status.text = "Поиск не выполнен: " + error
            return
        }
        val items = response.optJSONObject("query")?.optJSONArray("search") ?: JSONArray()
        status.text = "Найдено: " + items.length() + " • " + term
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val title = item.optString("title")
            val snippet = android.text.Html.fromHtml(item.optString("snippet"), android.text.Html.FROM_HTML_MODE_LEGACY).toString()
            val pageUrl = "https://en.wikipedia.org/wiki/" + title.replace(" ", "_")
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(9), dp(10), dp(9))
                setBackgroundColor(Color.rgb(13, 27, 43))
            }
            card.addView(TextView(this).apply {
                text = title
                textSize = 15f
                setTextColor(cyan)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            })
            card.addView(TextView(this).apply {
                text = snippet
                textSize = 13f
                setTextColor(Color.rgb(220, 232, 240))
                setPadding(0, dp(5), 0, dp(5))
            })
            val saveButton = Button(this).apply {
                text = "Сохранить в знания"
                isAllCaps = false
                setOnClickListener {
                    saveEntry(title, snippet, pageUrl, term)
                    text = "Сохранено"
                    isEnabled = false
                }
            }
            card.addView(saveButton)
            results.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
    }

    private fun saveEntry(title: String, note: String, url: String, term: String) {
        val existing = JSONArray(prefs.getString("entries", "[]"))
        for (i in 0 until existing.length()) {
            if (existing.optJSONObject(i)?.optString("url") == url) {
                status.text = "Этот источник уже сохранён"
                return
            }
        }
        existing.put(JSONObject()
            .put("title", title)
            .put("note", note)
            .put("url", url)
            .put("query", term)
            .put("savedAt", System.currentTimeMillis()))
        prefs.edit().putString("entries", existing.toString()).apply()
        status.text = "Источник сохранён локально"
    }

    private fun showSaved() {
        results.removeAllViews()
        val entries = JSONArray(prefs.getString("entries", "[]"))
        status.text = "Локальная база знаний • " + entries.length() + " записей"
        for (i in entries.length() - 1 downTo 0) {
            val item = entries.optJSONObject(i) ?: continue
            val title = item.optString("title")
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(9), dp(10), dp(9))
                setBackgroundColor(Color.rgb(13, 27, 43))
            }
            card.addView(TextView(this).apply {
                text = title
                textSize = 15f
                setTextColor(cyan)
            })
            card.addView(TextView(this).apply {
                text = item.optString("note") + "\n\n" + item.optString("url")
                textSize = 12f
                setTextColor(Color.LTGRAY)
            })
            card.addView(Button(this).apply {
                text = "Удалить"
                isAllCaps = false
                setOnClickListener {
                    val updated = JSONArray()
                    for (j in 0 until entries.length()) if (j != i) updated.put(entries.getJSONObject(j))
                    prefs.edit().putString("entries", updated.toString()).apply()
                    showSaved()
                }
            })
            results.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
    }

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
