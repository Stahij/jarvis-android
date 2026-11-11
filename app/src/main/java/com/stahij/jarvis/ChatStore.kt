package com.stahij.jarvis

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Versioned, app-private chat storage.
 * The legacy single history is copied once; it is deliberately left untouched for rollback.
 */
data class JarvisChat(
    val id: String,
    var title: String,
    var systemPrompt: String,
    val messages: JSONArray = JSONArray(),
    var pinned: Boolean = false,
    var archived: Boolean = false,
    var updatedAt: Long = System.currentTimeMillis()
)

class ChatStore(context: Context) {
    private val prefs = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
    private val chats = mutableListOf<JarvisChat>()

    init {
        load()
    }

    @Synchronized
    fun all(includeArchived: Boolean = true): List<JarvisChat> =
        chats.filter { includeArchived || !it.archived }
            .sortedWith(compareByDescending<JarvisChat> { it.pinned }
                .thenByDescending { it.updatedAt })

    @Synchronized
    fun find(id: String): JarvisChat? = chats.firstOrNull { it.id == id }

    @Synchronized
    fun create(title: String = "Новый чат", prompt: String = defaultPrompt()): JarvisChat {
        val chat = JarvisChat(UUID.randomUUID().toString(), title, prompt)
        chats.add(chat)
        persist()
        return chat
    }

    @Synchronized
    fun update(chat: JarvisChat) {
        if (chats.none { it.id == chat.id }) return
        chat.updatedAt = System.currentTimeMillis()
        persist()
    }

    @Synchronized
    fun delete(id: String) {
        chats.removeAll { it.id == id }
        persist()
    }

    @Synchronized
    fun setPinned(id: String, value: Boolean) {
        find(id)?.let { it.pinned = value; it.updatedAt = System.currentTimeMillis(); persist() }
    }

    @Synchronized
    fun setArchived(id: String, value: Boolean) {
        find(id)?.let { it.archived = value; it.updatedAt = System.currentTimeMillis(); persist() }
    }

    @Synchronized
    fun reset(id: String) {
        find(id)?.let { it.messages.clear(); it.updatedAt = System.currentTimeMillis(); persist() }
    }

    private fun load() {
        val raw = prefs.getString(KEY_CHATS, null)
        if (!raw.isNullOrBlank()) {
            try {
                val array = JSONArray(raw)
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val messages = item.optJSONArray("messages") ?: JSONArray()
                    chats.add(
                        JarvisChat(
                            id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                            title = item.optString("title", "Чат"),
                            systemPrompt = item.optString("systemPrompt", defaultPrompt()),
                            messages = messages,
                            pinned = item.optBoolean("pinned", false),
                            archived = item.optBoolean("archived", false),
                            updatedAt = item.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                }
            } catch (_: Exception) {
                chats.clear()
            }
        }

        if (chats.isEmpty() && !prefs.getBoolean(KEY_MIGRATED, false)) {
            val legacy = try { JSONArray(prefs.getString("history", "[]")) } catch (_: Exception) { JSONArray() }
            val persona = prefs.getString("active_persona", "Общий помощник") ?: "Общий помощник"
            val migrated = JarvisChat(
                id = UUID.randomUUID().toString(),
                title = if (legacy.length() > 0) "Предыдущий диалог" else "Новый чат",
                systemPrompt = defaultPrompt() + "\n\nТекущая роль: $persona. " + personaPrompt(persona),
                messages = legacy
            )
            chats.add(migrated)
            prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
            persist()
        }
        if (chats.isEmpty()) create()
    }

    private fun persist() {
        val array = JSONArray()
        chats.forEach { chat ->
            array.put(JSONObject()
                .put("id", chat.id)
                .put("title", chat.title)
                .put("systemPrompt", chat.systemPrompt)
                .put("messages", chat.messages)
                .put("pinned", chat.pinned)
                .put("archived", chat.archived)
                .put("updatedAt", chat.updatedAt))
        }
        prefs.edit().putString(KEY_CHATS, array.toString()).apply()
    }

    companion object {
        private const val KEY_CHATS = "chats_v1"
        private const val KEY_MIGRATED = "chats_v1_migrated"

        fun defaultPrompt(): String =
            "Ты — Jarvis, персональный ИИ-помощник. Отвечай по-русски, ясно и по существу. " +
            "Работай локально на устройстве. Не утверждай, что выполнял действия или проверки, которых не было."

        fun templates(): LinkedHashMap<String, String> = linkedMapOf(
            "Общий помощник" to "Будь универсальным помощником: отвечай прямо, затем при необходимости давай практические шаги.",
            "Программист" to "Работай как опытный разработчик. Предлагай проверяемые решения, учитывай тесты, ошибки и безопасность. Не утверждай, что запускал код, если запуска не было.",
            "Переводчик" to "Переводи точно и естественно, сохраняя смысл, тон и структуру. Уточняй целевой язык, если он неясен.",
            "Собеседник" to "Будь живым, внимательным и доброжелательным собеседником. Не превращай каждый ответ в инструкцию.",
            "Автор" to "Помогай писать и редактировать тексты. Сохраняй авторский замысел и стиль.",
            "Поддержка" to "Помогай бережно осмыслить ситуацию, не ставь диагнозов и уважай границы собеседника."
        )

        private fun personaPrompt(name: String): String =
            templates()[name] ?: templates().getValue("Общий помощник")
    }
}
