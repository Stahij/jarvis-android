package com.stahij.jarvis

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.os.Debug
import android.os.SystemClock
import android.widget.TextView
import android.view.View
import android.text.method.ScrollingMovementMethod
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Local-only diagnostics. Never stores prompt text, answers, tokens, or credentials. */
object JarvisDiagnostics {
    private const val PREFS = "jarvis_diagnostics"
    private const val KEY = "events"
    private val lock = Any()

    fun event(context: Context, stage: String, details: String = "") {
        synchronized(lock) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val events = try { JSONArray(prefs.getString(KEY, "[]")) } catch (_: Exception) { JSONArray() }
            val item = JSONObject()
                .put("time", System.currentTimeMillis())
                .put("stage", stage.take(80))
                .put("details", details.take(500))
            events.put(item)
            while (events.length() > 120) events.remove(0)
            prefs.edit().putString(KEY, events.toString()).apply()
        }
    }

    fun report(context: Context): String {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memory)
        val runtime = Runtime.getRuntime()
        val events = try {
            JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"))
        } catch (_: Exception) { JSONArray() }
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val out = StringBuilder()
        out.append("JARVIS • ДИАГНОСТИКА\n")
        out.append("Версия: 1.2.0\n")
        out.append("Память устройства свободна: ${memory.availMem / 1048576L} МБ из ${memory.totalMem / 1048576L} МБ\n")
        out.append("Память процесса Java: ${(runtime.totalMemory()-runtime.freeMemory())/1048576L} МБ / ${runtime.maxMemory()/1048576L} МБ\n")
        out.append("Native heap: ${Debug.getNativeHeapAllocatedSize()/1048576L} МБ\n")
        out.append("CPU-потоки движка: 4 (текущая конфигурация)\n")
        out.append("GPU: отключён в текущей сборке llama.cpp\n")
        out.append("\nПОСЛЕДНИЕ СОБЫТИЯ (без текста запросов):\n")
        for (i in 0 until events.length()) {
            val item = events.optJSONObject(i) ?: continue
            out.append(date.format(Date(item.optLong("time"))))
                .append(" • ").append(item.optString("stage"))
            val details = item.optString("details")
            if (details.isNotBlank()) out.append(" — ").append(details)
            out.append('\n')
        }
        if (events.length() == 0) out.append("Пока нет записей. Отправь Jarvis несколько запросов.\n")
        return out.toString()
    }

    fun show(activity: Activity) {
        val view = TextView(activity).apply {
            text = report(activity)
            textSize = 12f
            setTextIsSelectable(true)
            movementMethod = ScrollingMovementMethod()
            setPadding(36, 24, 36, 24)
        }
        AlertDialog.Builder(activity)
            .setTitle("Диагностика Jarvis")
            .setView(view)
            .setPositiveButton("Обновить") { _, _ -> show(activity) }
            .setNeutralButton("Закрыть", null)
            .show()
    }
}
