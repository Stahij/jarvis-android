package com.stahij.jarvis

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** Main multi-chat screen. MainActivity remains available for model, voice and GitHub tools. */
class ChatHubActivity : Activity() {
    private val bg = Color.rgb(5, 10, 22)
    private val cyan = Color.rgb(64, 231, 255)
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var store: ChatStore
    private lateinit var status: TextView
    private lateinit var input: EditText
    private lateinit var sendButton: Button
    private lateinit var messages: LinearLayout
    private lateinit var scroll: ScrollView
    private var currentChatId: String? = null
    private var archivedVisible = false
    private var modelReady = false
    private var sending = false
    private var loadingPath = ""

    private val modelFile by lazy { File(File(filesDir, "models"), "qwen2.5-3b-instruct-q4_k_m.gguf") }
    private val pocketModelFile by lazy { File(File(filesDir, "models"), "mnn-pocket-0.3b-q4_1.gguf") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        store = ChatStore(this)
        loadModelIfAvailable()
        showChatList()
    }

    override fun onResume() {
        super.onResume()
        if (::store.isInitialized) loadModelIfAvailable()
    }

    private fun loadModelIfAvailable() {
        val selected = getSharedPreferences("jarvis", MODE_PRIVATE).getString("selected_model", "qwen")
        val file = if (selected == "mnn-pocket" && pocketModelFile.exists()) pocketModelFile else modelFile
        if (!file.exists() || file.length() < 1024L * 1024L) {
            modelReady = false
            if (::status.isInitialized) status.text = "МОДЕЛЬ НЕ ПОДКЛЮЧЕНА"
            return
        }
        if (NativeLlm.loadedPath == file.absolutePath) {
            modelReady = true
            if (::status.isInitialized) status.text = "ЛОКАЛЬНАЯ МОДЕЛЬ ГОТОВА"
            return
        }
        if (loadingPath == file.absolutePath) return
        loadingPath = file.absolutePath
        modelReady = false
        if (::status.isInitialized) status.text = "ЗАПУСКАЮ МОДЕЛЬ..."
        executor.execute {
            val error = try { NativeLlm.ensureLoaded(file.absolutePath, 4, 4096) }
                catch (e: Throwable) { e.message ?: e.javaClass.simpleName }
            runOnUiThread {
                loadingPath = ""
                modelReady = error == null
                if (::status.isInitialized) {
                    status.text = if (modelReady) "ЛОКАЛЬНАЯ МОДЕЛЬ ГОТОВА"
                        else "ОШИБКА МОДЕЛИ: ${error ?: "неизвестно"}"
                }
                if (!modelReady && error != null && !isFinishing) {
                    AlertDialog.Builder(this)
                        .setTitle("Не удалось запустить модель")
                        .setMessage(error)
                        .setPositiveButton("Открыть управление моделями") { _, _ -> openTools() }
                        .setNegativeButton("Закрыть", null)
                        .show()
                }
            }
        }
    }

    private fun baseLayout(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(12))
        setBackgroundColor(bg)
    }

    private fun header(titleText: String, back: Boolean, menuAction: () -> Unit): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        if (back) row.addView(button("‹") { showChatList() }, LinearLayout.LayoutParams(dp(48), dp(44)))
        row.addView(TextView(this).apply {
            text = titleText
            textSize = 20f
            setTextColor(cyan)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
        }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(button("⋮", menuAction), LinearLayout.LayoutParams(dp(48), dp(44)))
        return row
    }

    private fun button(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 18f
        setTextColor(cyan)
        setBackgroundColor(Color.rgb(12, 36, 53))
        setOnClickListener { action() }
    }

    private fun showChatList() {
        currentChatId = null
        val root = baseLayout()
        root.addView(header("J A R V I S  •  ЧАТЫ", false) { showGlobalMenu(root) })
        status = TextView(this).apply {
            textSize = 11f
            setTextColor(Color.rgb(104, 160, 181))
            setPadding(0, dp(5), 0, dp(10))
        }
        root.addView(status)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(button("+ Новый чат") { createChat() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        actions.addView(button(if (archivedVisible) "Активные" else "Архив") {
            archivedVisible = !archivedVisible
            showChatList()
        }, LinearLayout.LayoutParams(dp(104), dp(48)))
        root.addView(actions)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroller = ScrollView(this)
        scroller.addView(list)
        root.addView(scroller, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(12) })
        val chats = store.all(includeArchived = true).filter { it.archived == archivedVisible }
        if (chats.isEmpty()) {
            list.addView(TextView(this).apply {
                text = if (archivedVisible) "Архив пуст" else "Пока нет чатов. Создай первый диалог."
                textSize = 15f
                setTextColor(Color.LTGRAY)
                setPadding(dp(12), dp(24), dp(12), dp(24))
            })
        }
        chats.forEach { chat ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                setBackgroundColor(Color.rgb(13, 27, 43))
                isClickable = true
                isFocusable = true
                setOnClickListener { openChat(chat.id) }
                setOnLongClickListener { showChatActions(this, chat); true }
            }
            card.addView(TextView(this).apply {
                text = (if (chat.pinned) "★  " else "") + chat.title
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                maxLines = 1
            })
            val last = chat.messages.optJSONObject(chat.messages.length() - 1)?.optString("content").orEmpty()
            card.addView(TextView(this).apply {
                text = if (last.isBlank()) "Пустой диалог  •  инструкции настроены" else last
                textSize = 12f
                setTextColor(Color.rgb(145, 169, 185))
                maxLines = 2
                setPadding(0, dp(5), 0, 0)
            })
            list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        root.addView(button("Инструменты, модели и GitHub") { openTools() },
            LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
        setContentView(root)
        loadModelIfAvailable()
    }

    private fun showGlobalMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add("Инструменты и модели").setOnMenuItemClickListener { openTools(); true }
            menu.add("Создать чат").setOnMenuItemClickListener { createChat(); true }
            show()
        }
    }

    private fun createChat() {
        val chat = store.create()
        openChat(chat.id)
    }

    private fun openChat(id: String) {
        val chat = store.find(id) ?: return showChatList()
        if (chat.archived) store.setArchived(id, false)
        currentChatId = id
        showConversation()
    }

    private fun showConversation() {
        val chat = currentChat() ?: return showChatList()
        val root = baseLayout()
        root.addView(header(chat.title) { showChatActions(root, chat) })
        status = TextView(this).apply {
            text = if (modelReady) "ЛОКАЛЬНАЯ МОДЕЛЬ ГОТОВА" else "МОДЕЛЬ ЗАГРУЖАЕТСЯ ИЛИ НЕ УСТАНОВЛЕНА"
            textSize = 10f
            setTextColor(Color.rgb(104, 160, 181))
            setPadding(0, dp(4), 0, dp(8))
        }
        root.addView(status)
        root.addView(button("Системный промпт • изменить") { editSystemPrompt(chat) },
            LinearLayout.LayoutParams(-1, dp(42)).apply { bottomMargin = dp(8) })
        scroll = ScrollView(this)
        messages = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(messages)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        renderMessages(chat)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        input = EditText(this).apply {
            hint = "Напиши сообщение..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            textSize = 16f
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEND
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.rgb(15, 27, 43))
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEND) { sendMessage(); true } else false
            }
        }
        row.addView(input, LinearLayout.LayoutParams(0, dp(52), 1f))
        sendButton = button("➤") { sendMessage() }
        sendButton.isEnabled = modelReady
        row.addView(sendButton, LinearLayout.LayoutParams(dp(56), dp(52)))
        root.addView(row)
        setContentView(root)
        loadModelIfAvailable()
    }

    private fun renderMessages(chat: JarvisChat) {
        messages.removeAllViews()
        for (i in 0 until chat.messages.length()) {
            val item = chat.messages.optJSONObject(i) ?: continue
            val role = item.optString("role")
            if (role == "user" || role == "assistant") {
                addBubble(if (role == "user") "ТЫ" else "JARVIS", item.optString("content"), role == "user")
            }
        }
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun addBubble(who: String, value: String, outgoing: Boolean) {
        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(9), dp(12), dp(9))
            setBackgroundColor(if (outgoing) Color.rgb(15, 47, 62) else Color.rgb(13, 23, 39))
        }
        bubble.addView(TextView(this).apply {
            text = who
            textSize = 10f
            setTextColor(if (outgoing) cyan else Color.rgb(104, 190, 211))
        })
        bubble.addView(TextView(this).apply {
            text = value
            textSize = 15f
            setTextColor(Color.rgb(229, 241, 248))
            setPadding(0, dp(3), 0, 0)
            setTextIsSelectable(true)
        })
        messages.addView(bubble, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(8)
            leftMargin = if (outgoing) dp(28) else 0
            rightMargin = if (outgoing) 0 else dp(28)
        })
    }

    private fun sendMessage() {
        if (sending) return
        val chat = currentChat() ?: return
        if (!modelReady) {
            status.text = "Сначала подключи модель через «Инструменты, модели и GitHub»."
            return
        }
        val prompt = input.text.toString().trim()
        if (prompt.isEmpty()) return
        input.setText("")
        chat.messages.put(JSONObject().put("role", "user").put("content", prompt))
        trimMessages(chat.messages)
        if (chat.title == "Новый чат") chat.title = prompt.take(34).ifBlank { "Новый чат" }
        store.update(chat)
        renderMessages(chat)
        sending = true
        input.isEnabled = false
        sendButton.isEnabled = false
        status.text = "ДУМАЮ..."
        executor.execute {
            try {
                val recentStart = (chat.messages.length() - 6).coerceAtLeast(0)
                val recent = (recentStart until chat.messages.length()).map { chat.messages.getJSONObject(it) }
                val roles = Array(recent.size + 1) { i -> if (i == 0) "system" else recent[i - 1].optString("role", "user") }
                val system = ChatStore.defaultPrompt() + "\n\nИНСТРУКЦИИ ЭТОГО ЧАТА:\n" +
                    chat.systemPrompt.take(5000) +
                    "\n\nНе смешивай историю других чатов с этим диалогом."
                val contents = Array(recent.size + 1) { i -> if (i == 0) system else recent[i - 1].optString("content") }
                val answer = NativeLlm.nativeGenerate(roles, contents, 384).trim()
                    .ifBlank { "Модель не вернула ответ. Попробуй ещё раз." }
                runOnUiThread {
                    chat.messages.put(JSONObject().put("role", "assistant").put("content", answer))
                    trimMessages(chat.messages)
                    store.update(chat)
                    if (currentChatId == chat.id) {
                        renderMessages(chat)
                        status.text = "ГОТОВ К ДИАЛОГУ"
                        input.isEnabled = true
                        sendButton.isEnabled = modelReady
                        input.requestFocus()
                    }
                    sending = false
                }
            } catch (e: Throwable) {
                runOnUiThread {
                    if (currentChatId == chat.id) {
                        status.text = "ОШИБКА: ${e.message ?: e.javaClass.simpleName}"
                        input.isEnabled = true
                        sendButton.isEnabled = modelReady
                    }
                    sending = false
                }
            }
        }
    }

    private fun trimMessages(array: JSONArray) {
        while (array.length() > 100) array.remove(0)
        if (array.length() > 0 && array.optJSONObject(0)?.optString("role") == "assistant") array.remove(0)
    }

    private fun currentChat(): JarvisChat? = currentChatId?.let { store.find(it) }

    private fun editSystemPrompt(chat: JarvisChat) {
        val editor = EditText(this).apply {
            setText(chat.systemPrompt)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            minLines = 5
            maxLines = 12
            gravity = Gravity.TOP
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.rgb(15, 27, 43))
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(editor)
            addView(button("Выбрать готовую роль") { chooseTemplate(chat, editor) })
        }
        AlertDialog.Builder(this)
            .setTitle("Инструкции для этого чата")
            .setView(panel)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сохранить") { _, _ ->
                chat.systemPrompt = editor.text.toString().trim()
                store.update(chat)
                status.text = "ПРОМПТ СОХРАНЁН ДЛЯ ЭТОГО ЧАТА"
            }
            .show()
    }

    private fun chooseTemplate(chat: JarvisChat, editor: EditText) {
        val templates = ChatStore.templates().toList()
        AlertDialog.Builder(this)
            .setTitle("Шаблоны ролей")
            .setItems(templates.map { it.first }.toTypedArray()) { _, which ->
                editor.setText(templates[which].second)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showChatActions(anchor: View, chat: JarvisChat) {
        PopupMenu(this, anchor).apply {
            menu.add("Изменить название").setOnMenuItemClickListener { renameChat(chat); true }
            menu.add("Изменить системный промпт").setOnMenuItemClickListener { editSystemPrompt(chat); true }
            menu.add(if (chat.pinned) "Открепить" else "Закрепить").setOnMenuItemClickListener {
                store.setPinned(chat.id, !chat.pinned); refreshCurrent(); true
            }
            menu.add("Сбросить историю, сохранив промпт").setOnMenuItemClickListener {
                AlertDialog.Builder(this@ChatHubActivity).setMessage("Очистить сообщения этого чата? Инструкции останутся.")
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Очистить") { _, _ -> store.reset(chat.id); refreshCurrent() }.show()
                true
            }
            menu.add(if (chat.archived) "Вернуть из архива" else "В архив").setOnMenuItemClickListener {
                store.setArchived(chat.id, !chat.archived); showChatList(); true
            }
            menu.add("Удалить чат").setOnMenuItemClickListener {
                AlertDialog.Builder(this@ChatHubActivity).setMessage("Удалить чат «${chat.title}» без возможности восстановления?")
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Удалить") { _, _ ->
                        store.delete(chat.id)
                        if (currentChatId == chat.id) currentChatId = null
                        if (store.all().isEmpty()) store.create()
                        showChatList()
                    }.show()
                true
            }
            show()
        }
    }

    private fun renameChat(chat: JarvisChat) {
        val field = EditText(this).apply { setText(chat.title); setTextColor(Color.WHITE); setSingleLine(true) }
        AlertDialog.Builder(this).setTitle("Название чата").setView(field)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сохранить") { _, _ ->
                chat.title = field.text.toString().trim().ifBlank { "Чат" }
                store.update(chat)
                refreshCurrent()
            }.show()
    }

    private fun showChatActions(anchor: View, chat: JarvisChat, unused: Unit = Unit) =
        showChatActions(anchor, chat)

    private fun refreshCurrent() {
        if (currentChatId == null) showChatList() else showConversation()
    }

    private fun openTools() {
        startActivity(Intent(this, MainActivity::class.java))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
