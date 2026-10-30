package com.stahij.jarvis

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.PopupMenu
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
import java.io.File
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
    private var modelScreenDialog: AlertDialog? = null\n    private var isSending = false
    private var modelReady = false
    private var voiceReply = false
    private var voicePendingPermission = false
    private var tts: TextToSpeech? = null
    private val maxHistoryMessages = 24
    private val prefs by lazy { getSharedPreferences("jarvis", MODE_PRIVATE) }
    private val history = JSONArray()
    private val modelFile by lazy { File(File(filesDir, "models"), "qwen-coder.gguf") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildUi()
        tts = TextToSpeech(this) { }
        loadHistory()
        if (modelFile.exists() && modelFile.length() > 0L) {
            loadLocalModel(modelFile)
        } else {
            status.text = "ОЖИДАЕТ ПОДКЛЮЧЕНИЯ МОДЕЛИ"
        }
        maybeCheckForUpdates()
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
            textSize = 22f
            setSingleLine(true)
            setTextColor(cyan)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        val menuButton = Button(this).apply {
            text = "☰"
            textSize = 22f
            setTextColor(cyan)
            setBackgroundColor(Color.rgb(12, 36, 53))
            minWidth = dp(48)
            setOnClickListener { showMainMenu(this) }
        }
        header.addView(menuButton, LinearLayout.LayoutParams(dp(48), dp(44)))
        root.addView(header)

        status = TextView(this).apply {
            text = "ВСТРОЕННЫЙ ДВИЖОК • QWEN"
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
        row.addView(sendButton, LinearLayout.LayoutParams(dp(54), dp(52)))
        val voiceButton = Button(this).apply {
            text = "🎙"
            textSize = 19f
            setTextColor(cyan)
            setBackgroundColor(Color.rgb(15, 48, 67))
            minWidth = dp(50)
            setOnClickListener { startVoiceInput() }
        }
        row.addView(voiceButton, LinearLayout.LayoutParams(dp(50), dp(52)).apply {
            leftMargin = dp(6)
        })
        root.addView(row)
        setContentView(root)
    }

    private fun showMainMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add("Модель").setOnMenuItemClickListener {
                showModelScreen()
                true
            }
            menu.add("Проверка обновлений").setOnMenuItemClickListener {
                checkForUpdates(manual = true)
                true
            }
            show()
        }
    }

    // Отдельный экран управления локальной моделью, открываемый из бургер-меню.
    private fun showModelScreen() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(20))
            setBackgroundColor(bg)
        }
        panel.addView(TextView(this).apply {
            text = "МОДЕЛЬ JARVIS"
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(cyan)
        })
        panel.addView(TextView(this).apply {
            text = if (modelReady) "Локальная модель готова к работе"
                else if (modelFile.exists()) "Модель найдена • требуется подключение"
                else "Локальная модель не установлена"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(10), 0, dp(22))
        })
        fun actionButton(label: String, action: () -> Unit) {
            panel.addView(Button(this).apply {
                text = label
                isAllCaps = false
                setTextColor(cyan)
                setBackgroundColor(Color.rgb(12, 36, 53))
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(12) })
        }
        actionButton("Скачать Qwen • около 1,1 ГБ") { confirmQwenDownload() }
        actionButton("Импортировать GGUF-файл") { chooseModel() }
        actionButton("Назад") { modelScreenDialog?.dismiss() }
        modelScreenDialog = AlertDialog.Builder(this).setView(panel).create()
        modelScreenDialog?.setOnShowListener {
            modelScreenDialog?.window?.setLayout(-1, -1)
        }
        modelScreenDialog?.show()
        modelScreenDialog?.window?.setLayout(-1, -1)
    }

    private fun startVoiceInput() {
        if (!modelReady) {
            addBubble("Система", "Сначала подключи локальную модель через меню «Модель».", false)
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            voicePendingPermission = true
            startActivityForResult(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                REQUEST_OVERLAY
            )
            return
        }
        prefs.edit().putBoolean("orb_enabled", true).apply()
        startOrbService()
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Слушаю тебя")
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            voiceReply = true
            startActivityForResult(intent, REQUEST_VOICE)
        } catch (_: Exception) {
            voiceReply = false
            addBubble("Система", "На телефоне не найден сервис распознавания речи.", false)
        }
    }

    private fun maybeCheckForUpdates() {
        val lastCheck = prefs.getLong("last_update_check", 0L)
        if (System.currentTimeMillis() - lastCheck >= 24L * 60L * 60L * 1000L) {
            checkForUpdates(manual = false)
        }
    }

    private fun checkForUpdates(manual: Boolean) {
        if (manual) status.text = "ПРОВЕРЯЮ ОБНОВЛЕНИЯ..."
        executor.execute {
            try {
                val update = UpdateManager(this).checkForUpdate()
                prefs.edit().putLong("last_update_check", System.currentTimeMillis()).apply()
                runOnUiThread {
                    if (update == null) {
                        if (manual) {
                            AlertDialog.Builder(this)
                                .setTitle("Jarvis")
                                .setMessage("Установлена последняя доступная версия.")
                                .setPositiveButton("Хорошо", null)
                                .show()
                        }
                    } else {
                        AlertDialog.Builder(this)
                            .setTitle("Доступно обновление Jarvis")
                            .setMessage("Новая версия: ${update.version}. Скачать и установить?")
                            .setNegativeButton("Позже", null)
                            .setPositiveButton("Скачать") { _, _ -> downloadAndInstall(update) }
                            .show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (manual) {
                        AlertDialog.Builder(this)
                            .setTitle("Не удалось проверить обновления")
                            .setMessage(e.message ?: "Проверь подключение к интернету.")
                            .setPositiveButton("Закрыть", null)
                            .show()
                    }
                }
            }
        }
    }

    private fun downloadAndInstall(update: JarvisUpdate) {
        status.text = "СКАЧИВАЮ ОБНОВЛЕНИЕ ${update.version}..."
        executor.execute {
            try {
                val apk = UpdateManager(this).downloadAndVerify(update)
                runOnUiThread {
                    status.text = "ПОДПИСЬ И SHA-256 ПРОВЕРЕНЫ • ОТКРЫВАЮ УСТАНОВЩИК"
                    UpdateManager(this).install(apk)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "ОШИБКА ОБНОВЛЕНИЯ"
                    AlertDialog.Builder(this)
                        .setTitle("Обновление не установлено")
                        .setMessage(e.message ?: "Неизвестная ошибка")
                        .setPositiveButton("Закрыть", null)
                        .show()
                }
            }
        }
    }

    private fun confirmQwenDownload() {
        if (modelFile.exists() && modelFile.length() > 500L * 1024L * 1024L) {
            AlertDialog.Builder(this)
                .setTitle("Модель уже загружена")
                .setMessage("Заменить текущую модель Qwen? Это потребует повторной загрузки примерно 1,1 ГБ.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Заменить") { _, _ -> downloadQwenModel() }
                .show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Загрузить мозг Jarvis")
            .setMessage("Qwen2.5-Coder-1.5B-Instruct Q4_0. Размер около 1,1 ГБ. Модель будет храниться в памяти приложения и работать локально, без отправки запросов внешним ИИ.")
            .setNegativeButton("Позже", null)
            .setPositiveButton("Загрузить") { _, _ -> downloadQwenModel() }
            .show()
    }

    private fun downloadQwenModel() {
        sendButton.isEnabled = false
        status.text = "ГОТОВЛЮ ЗАГРУЗКУ..."
        executor.execute {
            try {
                val directory = modelFile.parentFile ?: throw IllegalStateException("Нет папки моделей")
                if (!directory.exists() && !directory.mkdirs()) {
                    throw IllegalStateException("Не удалось создать папку модели")
                }
                val part = File(directory, "qwen-coder.gguf.part")
                val url = java.net.URL(QWEN_MODEL_URL)
                var offset = if (part.exists()) part.length() else 0L
                val connection = (url.openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 20000
                    readTimeout = 90000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Jarvis-Android")
                    if (offset > 0L) setRequestProperty("Range", "bytes=$offset-")
                }
                try {
                    val code = connection.responseCode
                    if (code !in 200..299) throw IllegalStateException("Сервер модели вернул HTTP $code")
                    if (offset > 0L && code != 206) {
                        part.delete()
                        offset = 0L
                    }
                    val total = if (code == 206) {
                        val range = connection.getHeaderField("Content-Range")
                        range?.substringAfterLast('/')?.toLongOrNull()?.takeIf { it > 0L }
                    } else connection.contentLengthLong.takeIf { it > 0L }
                    connection.inputStream.use { source ->
                        java.io.FileOutputStream(part, offset > 0L).use { target ->
                            val buffer = ByteArray(256 * 1024)
                            var downloaded = offset
                            var lastUpdate = 0L
                            while (true) {
                                val count = source.read(buffer)
                                if (count < 0) break
                                target.write(buffer, 0, count)
                                downloaded += count
                                val now = System.currentTimeMillis()
                                if (now - lastUpdate > 700L) {
                                    lastUpdate = now
                                    val percent = total?.let { ((downloaded * 100L) / it).coerceIn(0L, 100L) }
                                    runOnUiThread {
                                        status.text = if (percent != null) "СКАЧИВАЮ QWEN • $percent%"
                                            else "СКАЧИВАЮ QWEN • ${downloaded / (1024L * 1024L)} МБ"
                                    }
                                }
                            }
                            target.fd.sync()
                        }
                    }
                } finally {
                    connection.disconnect()
                }
                if (part.length() < 500L * 1024L * 1024L) {
                    throw IllegalStateException("Загрузка неполная: файл меньше ожидаемого размера. Можно повторить — загрузка продолжится.")
                }
                val magic = ByteArray(4)
                part.inputStream().use { if (it.read(magic) != 4) throw IllegalStateException("Не удалось проверить GGUF-файл") }
                if (String(magic, Charsets.US_ASCII) != "GGUF") {
                    part.delete()
                    throw IllegalStateException("Сервер вернул не GGUF-модель. Файл удалён.")
                }
                if (modelFile.exists()) modelFile.delete()
                if (!part.renameTo(modelFile)) {
                    part.copyTo(modelFile, overwrite = true)
                    part.delete()
                }
                runOnUiThread {
                    status.text = "ПОДКЛЮЧАЮ МОДЕЛЬ..."
                    loadLocalModel(modelFile)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "ЗАГРУЗКА МОДЕЛИ ПРЕРВАНА"
                    addBubble("Система", e.message ?: "Не удалось скачать модель. Незавершённая загрузка сохранена для продолжения.", false)
                }
            }
        }
    }

    private fun chooseModel() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_TITLE, "Выбери GGUF-модель Qwen")
        }
        startActivityForResult(intent, REQUEST_MODEL)
    }

    private fun importModel(uri: Uri) {
        modelReady = false
        sendButton.isEnabled = false
        status.text = "КОПИРУЮ МОДЕЛЬ В ПАМЯТЬ JARVIS..."
        executor.execute {
            try {
                val directory = modelFile.parentFile ?: throw IllegalStateException("Нет папки моделей")
                if (!directory.exists() && !directory.mkdirs()) {
                    throw IllegalStateException("Не удалось создать папку модели")
                }
                val temporary = File(directory, "qwen-coder.gguf.part")
                contentResolver.openInputStream(uri)?.use { source ->
                    temporary.outputStream().use { target -> source.copyTo(target) }
                } ?: throw IllegalStateException("Не удалось открыть выбранный файл")

                if (temporary.length() < 1024L * 1024L) {
                    temporary.delete()
                    throw IllegalStateException("Файл слишком маленький для GGUF-модели")
                }
                if (modelFile.exists()) modelFile.delete()
                if (!temporary.renameTo(modelFile)) {
                    temporary.copyTo(modelFile, overwrite = true)
                    temporary.delete()
                }
                runOnUiThread { loadLocalModel(modelFile) }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "ОШИБКА ИМПОРТА МОДЕЛИ"
                    addBubble("Система", e.message ?: "Не удалось импортировать модель", false)
                }
            }
        }
    }

    private fun loadLocalModel(file: File) {
        modelReady = false
        sendButton.isEnabled = false
        status.text = "ЗАПУСКАЮ МОДЕЛЬ..."
        executor.execute {
            try {
                val error = NativeLlm.nativeLoadModel(file.absolutePath, 4, 4096)
                runOnUiThread {
                    if (error == null) {
                        modelReady = true
                        sendButton.isEnabled = true
                        status.text = "ГОТОВ К ДИАЛОГУ"
                    } else {
                        status.text = "НЕ УДАЛОСЬ ЗАГРУЗИТЬ МОДЕЛЬ"
                        addBubble("Система", error, false)
                    }
                }
            } catch (e: Throwable) {
                runOnUiThread {
                    status.text = "ОШИБКА ВСТРОЕННОГО ДВИЖКА"
                    addBubble("Система", e.message ?: e.javaClass.simpleName, false)
                }
            }
        }
    }

    private fun toggleOrb() {
        if (Settings.canDrawOverlays(this)) {
            prefs.edit().putBoolean("orb_enabled", true).apply()
            startOrbService()
            status.text = if (modelReady) "ЯДРО АКТИВНО"
                else "ЯДРО АКТИВНО • МОДЕЛЬ НЕ ВЫБРАНА"
        } else {
            startActivityForResult(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                REQUEST_OVERLAY
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
        if (requestCode == REQUEST_OVERLAY && Settings.canDrawOverlays(this)) {
            if (voicePendingPermission) {
                voicePendingPermission = false
                startVoiceInput()
            } else {
                toggleOrb()
            }
        }
        if (requestCode == REQUEST_MODEL && resultCode == RESULT_OK) {
            data?.data?.let { importModel(it) }
        }
        if (requestCode == REQUEST_VOICE) {
            val spoken = if (resultCode == RESULT_OK) {
                data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            } else null
            if (!spoken.isNullOrBlank()) {
                input.setText(spoken)
                sendMessage()
            } else {
                voiceReply = false
                status.text = "ГОЛОСОВОЙ ВВОД ОТМЕНЁН"
            }
        }
    }

    private fun sendMessage() {
        if (isSending) return
        if (!modelReady) {
            addBubble("Система", "Сначала подключи GGUF-модель через кнопку «МОЗГ».", false)
            return
        }
        val prompt = input.text.toString().trim()
        if (prompt.isEmpty()) return

        input.setText("")
        addBubble("Ты", prompt, true)
        history.put(JSONObject().put("role", "user").put("content", prompt))
        trimHistory()
        saveHistory()

        // The model runs locally through llama.cpp. Network access is optional and must
        // never be described as a requirement for ordinary chat or local generation.
        val systemInstruction = """
            Ты — Jarvis, персональный ИИ-помощник пользователя.
            Сейчас ты работаешь как локальная языковая модель внутри Android-приложения через llama.cpp.
            Обычный диалог, рассуждение и генерация ответов выполняются на устройстве и не требуют интернета.
            Не утверждай, что для обычного общения тебе обязательно нужен интернет.
            Интернет может использоваться только отдельными функциями поиска и получения актуальных сведений,
            когда такие функции доступны и пользователь явно попросил их использовать.
            Не выдумывай, будто ты уже выполнил поиск, открыл сайт или проверил свежие данные.
            Если актуальные сведения без сети недоступны, честно скажи об этом.
            Отвечай по-русски, ясно, доброжелательно и по существу.
        """.trimIndent()
        // В модель отправляем только последние 8 реплик: сохранённая история остаётся полной,
        // но каждый новый запрос не заставляет движок повторно обрабатывать весь диалог.
        val promptStart = (history.length() - 8).coerceAtLeast(0)
        val promptHistory = (promptStart until history.length()).map { history.getJSONObject(it) }
        val roles = Array(promptHistory.size + 1) { i ->
            if (i == 0) "system" else promptHistory[i - 1].optString("role", "user")
        }
        val contents = Array(promptHistory.size + 1) { i ->
            if (i == 0) systemInstruction else promptHistory[i - 1].optString("content")
        }

        // Частые короткие приветствия отвечаем мгновенно, не запуская LLM.
        val quickReply = quickGreetingReply(prompt)
        if (quickReply != null) {
            history.put(JSONObject().put("role", "assistant").put("content", quickReply))
            trimHistory()
            saveHistory()
            addBubble("Джарвис", quickReply, false)
            finishSending("ГОТОВ К ДИАЛОГУ")
            return
        }

        isSending = true
        input.isEnabled = false
        sendButton.isEnabled = false
        status.text = "ДУМАЮ..."

        executor.execute {
            try {
                val answer = NativeLlm.nativeGenerate(roles, contents, 512).trim()
                runOnUiThread {
                    history.put(JSONObject().put("role", "assistant").put("content", answer))
                    trimHistory()
                    saveHistory()
                    addBubble("Джарвис", answer.ifBlank { "Модель не вернула ответ. Попробуй ещё раз." }, false)
                    finishSending("ГОТОВ К ДИАЛОГУ")
                }
            } catch (ex: Throwable) {
                runOnUiThread {
                    addBubble("Система", ex.message ?: "Ошибка локальной генерации", false)
                    voiceReply = false
                    finishSending("ОШИБКА ЛОКАЛЬНОЙ МОДЕЛИ")
                }
            }
        }
    }

    private fun quickGreetingReply(message: String): String? {
        val normalized = message.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
        val greetings = setOf(
            "привет", "приветик", "здравствуй", "здравствуйте", "доброе утро",
            "добрый день", "добрый вечер", "хай", "салют", "ку"
        )
        return if (normalized in greetings) "Привет! Я на связи. Чем займёмся?"
            else null
    }

    private fun finishSending(statusText: String) {
        isSending = false
        input.isEnabled = true
        sendButton.isEnabled = modelReady
        status.text = statusText
        if (voiceReply) {
            voiceReply = false
            val reply = history.optJSONObject(history.length() - 1)?.optString("content").orEmpty()
            if (reply.isNotBlank()) {
                tts?.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "jarvis_voice_reply")
                moveTaskToBack(true)
            }
        } else {
            input.requestFocus()
        }
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
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val QWEN_MODEL_URL = "https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-1.5b-instruct-q4_0.gguf"
        private const val REQUEST_OVERLAY = 501
        private const val REQUEST_MODEL = 502
        private const val REQUEST_VOICE = 503
    }
}
