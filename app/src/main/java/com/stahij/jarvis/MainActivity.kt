package com.stahij.jarvis

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val bg = Color.rgb(5, 10, 22)
    private val cyan = Color.rgb(64, 231, 255)
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var messages: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var sendButton: Button
    private var isSending = false
    private val maxHistoryMessages = 40
    private val prefs by lazy { getSharedPreferences("jarvis", MODE_PRIVATE) }
    private val history = JSONArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildUi()
        loadHistory()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(12))
            setBackgroundColor(bg)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "J A R V I S"
            textSize = 23f
            setTextColor(cyan)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(Button(this).apply {
            text = "ЯДРО"
            setTextColor(cyan)
            setBackgroundColor(Color.rgb(12, 36, 53))
            setOnClickListener { toggleOrb() }
        })
        root.addView(header)

        status = TextView(this).apply {
            text = "ЛОКАЛЬНЫЙ ИНТЕЛЛЕКТ • QWEN"
            textSize = 11f
            setTextColor(Color.rgb(104, 160, 181))
            setPadding(0, dp(4), 0, dp(10))
        }
        root.addView(status)

        scroll = ScrollView(this)
        messages = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(messages)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        input = EditText(this).apply {
            hint = "Скажи Джарвису..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            textSize = 16f
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEND
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.rgb(15, 27, 43))
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEND) {
                    sendMessage()
                    true
                } else false
            }
        }
        row.addView(input, LinearLayout.LayoutParams(0, dp(52), 1f))
        sendButton = Button(this).apply {
            text = "➤"
            textSize = 20f
            setTextColor(Color.BLACK)
            setBackgroundColor(cyan)
            setOnClickListener { sendMessage() }
        }
        row.addView(sendButton, LinearLayout.LayoutParams(dp(58), dp(52)))
        root.addView(row)
        setContentView(root)
    }

    private fun toggleOrb() {
        if (Settings.canDrawOverlays(this)) {
            prefs.edit().putBoolean("orb_enabled", true).apply()
            startOrbService()
            status.text = "ЭНЕРГЕТИЧЕСКОЕ ЯДРО АКТИВНО • нажми шар, чтобы вернуться"
        } else {
            startActivityForResult(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                501
            )
        }
    }

    private fun startOrbService() {
        val intent = Intent(this, OrbService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 501 && Settings.canDrawOverlays(this)) toggleOrb()
    }

    private fun sendMessage() {
        if (isSending) return
        val prompt = input.text.toString().trim()
        if (prompt.isEmpty()) return

        input.setText("")
        addBubble("Ты", prompt, true)
        history.put(JSONObject().put("role", "user").put("content", prompt))
        trimHistory()
        saveHistory()

        // Snapshot on the UI thread: the request cannot race with later history edits.
        val requestMessages = JSONArray(history.toString())
        val payload = JSONObject()
            .put("messages", requestMessages)
            .put("temperature", 0.7)
            .put("max_tokens", 512)

        isSending = true
        input.isEnabled = false
        sendButton.isEnabled = false
        status.text = "QWEN ОБРАБАТЫВАЕТ ЗАПРОС..."

        executor.execute {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL("http://127.0.0.1:8080/v1/chat/completions")
                    .openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10000
                    readTimeout = 120000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use {
                    it.write(payload.toString())
                }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) throw IllegalStateException("HTTP $code: $body")

                val answer = JSONObject(body).getJSONArray("choices")
                    .getJSONObject(0).getJSONObject("message").getString("content").trim()

                runOnUiThread {
                    history.put(JSONObject().put("role", "assistant").put("content", answer))
                    trimHistory()
                    saveHistory()
                    addBubble("Джарвис", answer, false)
                    finishSending("ЛОКАЛЬНЫЙ ИНТЕЛЛЕКТ • ГОТОВ")
                }
            } catch (ex: Exception) {
                runOnUiThread {
                    addBubble(
                        "Система",
                        "Не удалось подключиться к Qwen на 127.0.0.1:8080. Проверь llama-server в Termux. " +
                            (ex.message ?: "Неизвестная ошибка"),
                        false
                    )
                    finishSending("НЕТ СОЕДИНЕНИЯ С QWEN")
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun finishSending(statusText: String) {
        isSending = false
        input.isEnabled = true
        sendButton.isEnabled = true
        status.text = statusText
        input.requestFocus()
    }

    private fun addBubble(who: String, text: String, outgoing: Boolean) {
        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(9), dp(12), dp(9))
            setBackgroundColor(if (outgoing) Color.rgb(15, 47, 62) else Color.rgb(13, 23, 39))
        }
        bubble.addView(TextView(this).apply {
            this.text = who.uppercase()
            textSize = 10f
            setTextColor(if (outgoing) cyan else Color.rgb(104, 190, 211))
        })
        bubble.addView(TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(Color.rgb(229, 241, 248))
            setPadding(0, dp(3), 0, 0)
        })
        val lp = LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(8)
            leftMargin = if (outgoing) dp(28) else 0
            rightMargin = if (outgoing) 0 else dp(28)
        }
        messages.addView(bubble, lp)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun loadHistory() {
        try {
            val saved = JSONArray(prefs.getString("history", "[]"))
            val start = (saved.length() - maxHistoryMessages).coerceAtLeast(0)
            for (i in start until saved.length()) {
                val item = saved.getJSONObject(i)
                history.put(item)
                val role = item.optString("role")
                if (role == "user" || role == "assistant") {
                    addBubble(if (role == "user") "Ты" else "Джарвис",
                        item.optString("content"), role == "user")
                }
            }
            trimHistory()
        } catch (_: Exception) {
            history.put(JSONObject().put("role", "assistant")
                .put("content", "История диалога повреждена и была сброшена. Можно продолжать общение."))
            saveHistory()
        }
    }

    private fun trimHistory() {
        while (history.length() > maxHistoryMessages) history.remove(0)
        // Avoid starting a fresh context with an orphaned assistant response.
        if (history.length() > 0 && history.optJSONObject(0)?.optString("role") == "assistant") {
            history.remove(0)
        }
    }

    private fun saveHistory() {
        prefs.edit().putString("history", history.toString()).apply()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
