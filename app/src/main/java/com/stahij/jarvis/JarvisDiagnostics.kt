package com.stahij.jarvis

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.StatFs
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Local-only diagnostics. Does not store prompt text, answers, or credentials. */
object JarvisDiagnostics {
    private const val PREFS = "jarvis_diagnostics"
    private const val KEY = "events"
    private val handler = Handler(Looper.getMainLooper())
    private var activeDialog: AlertDialog? = null
    private var activeText: TextView? = null
    private var previousCpu: LongArray? = null

    fun event(context: Context, stage: String, details: String = "") {
        synchronized(this) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val list = try { JSONArray(prefs.getString(KEY, "[]")) } catch (_: Exception) { JSONArray() }
            list.put(JSONObject().put("time", System.currentTimeMillis()).put("stage", stage.take(80)).put("details", details.take(300)))
            while (list.length() > 120) list.remove(0)
            prefs.edit().putString(KEY, list.toString()).apply()
        }
    }

    private fun eventList(context: Context) = try {
        JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"))
    } catch (_: Exception) { JSONArray() }

    private fun cpuPercent(): Int? = try {
        val values = File("/proc/stat").readLines().first().trim().split(Regex("\\s+")).drop(1).take(8).map { it.toLong() }
        val idle = values[3] + values.getOrElse(4) { 0L }
        val total = values.sum()
        val old = previousCpu
        previousCpu = longArrayOf(total, idle)
        if (old == null || total <= old[0]) null else (((total - old[0] - (idle - old[1])).toDouble() / (total - old[0])) * 100).toInt().coerceIn(0, 100)
    } catch (_: Exception) { null }

    private fun snapshot(context: Context): JSONObject {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mem)
        val runtime = Runtime.getRuntime()
        val battery = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temp = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val voltage = battery?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) power.currentThermalStatus else -1
        val pss = am.getProcessMemoryInfo(intArrayOf(android.os.Process.myPid())).firstOrNull()?.totalPss
        val stat = StatFs(context.filesDir.absolutePath)
        val model = File(File(context.filesDir, "models"), "qwen2.5-3b-instruct-q4_k_m.gguf")
        val modelState = when { model.length() > 1_700L * 1024L * 1024L -> "ready"; model.exists() -> "incomplete"; else -> "missing" }
        return JSONObject()
            .put("timestamp", System.currentTimeMillis())
            .put("cpu_percent", cpuPercent() ?: JSONObject.NULL)
            .put("process_cpu_ms", android.os.Process.getElapsedCpuTime())
            .put("process_pss_kb", pss ?: JSONObject.NULL)
            .put("ram_available_bytes", mem.availMem).put("ram_total_bytes", mem.totalMem)
            .put("ram_low", mem.lowMemory).put("ram_threshold_bytes", mem.threshold)
            .put("java_used_bytes", runtime.totalMemory() - runtime.freeMemory()).put("java_max_bytes", runtime.maxMemory())
            .put("native_heap_bytes", Debug.getNativeHeapAllocatedSize())
            .put("battery_temperature_c", if (temp >= 0) temp / 10.0 else JSONObject.NULL)
            .put("battery_percent", if (level >= 0 && scale > 0) level * 100 / scale else JSONObject.NULL)
            .put("battery_voltage_mv", if (voltage > 0) voltage else JSONObject.NULL)
            .put("battery_plugged", plugged != 0).put("thermal_status", thermal)
            .put("storage_free_bytes", stat.availableBytes).put("storage_total_bytes", stat.totalBytes)
            .put("model_state", modelState).put("model_size_bytes", if (model.exists()) model.length() else 0L)
            .put("gpu_enabled", false).put("engine_cpu_threads", 4).put("engine_batch_threads", 4)
    }

    private fun liveText(context: Context): String {
        val s = snapshot(context)
        fun mb(key: String) = s.optLong(key) / 1048576L
        return buildString {
            append("JARVIS • LIVE DASHBOARD\n")
            append("Обновлено: ").append(SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(s.optLong("timestamp")))).append("\n\n")
            append("CPU: ").append(if (s.isNull("cpu_percent")) "нет данных" else s.optInt("cpu_percent").toString() + "%").append("\n")
            append("RAM свободно: ").append(mb("ram_available_bytes")).append(" / ").append(mb("ram_total_bytes")).append(" МБ\n")
            append("Java heap: ").append(mb("java_used_bytes")).append(" / ").append(mb("java_max_bytes")).append(" МБ\n")
            append("PSS процесса: ").append(if (s.isNull("process_pss_kb")) "нет данных" else s.optLong("process_pss_kb") / 1024L).append(" МБ\n")
            append("Native heap: ").append(mb("native_heap_bytes")).append(" МБ\n")
            append("CPU процесса: ").append(s.optLong("process_cpu_ms")).append(" мс накопленно\n")
            append("Температура батареи: ").append(if (s.isNull("battery_temperature_c")) "нет данных" else s.optDouble("battery_temperature_c").toString() + " °C").append("\n")
            append("Батарея: ").append(if (s.isNull("battery_percent")) "нет данных" else s.optInt("battery_percent").toString() + "%")
                .append(if (s.isNull("battery_voltage_mv")) "" else " • " + s.optInt("battery_voltage_mv") + " мВ")
                .append(if (s.optBoolean("battery_plugged")) " • зарядка" else " • без зарядки").append("\n")
            append("Тепловой статус Android: ").append(thermalLabel(s.optInt("thermal_status", -1))).append("\n")
            append("Хранилище свободно: ").append(mb("storage_free_bytes")).append(" / ").append(mb("storage_total_bytes")).append(" МБ\n")
            append("Модель: ").append(s.optString("model_state")).append("\n")
            append("GPU llama.cpp: отключён\nПотоки движка: 4 • batch: 4\n")
            append("\nПримечание: температура батареи не является температурой CPU/SoC. CPU — оценка по /proc/stat, если доступна.")
        }
    }

    private fun thermalLabel(value: Int): String = when (value) {
        0 -> "норма"; 1 -> "лёгкий нагрев"; 2 -> "умеренный нагрев"
        3 -> "сильный нагрев"; 4 -> "критический нагрев"; 5 -> "аварийное состояние"
        else -> "нет данных / API ниже 29"
    }

    fun report(context: Context): String {
        val out = StringBuilder("JARVIS • ДИАГНОСТИКА 1.5.5\n")
        out.append("Устройство: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n")
        out.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n\n")
        out.append(liveText(context)).append("\n\nПОСЛЕДНИЕ СОБЫТИЯ\n")
        val list = eventList(context)
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        for (i in 0 until list.length()) {
            val item = list.optJSONObject(i) ?: continue
            out.append(fmt.format(Date(item.optLong("time"))).toString()).append(" • ").append(item.optString("stage"))
            if (item.optString("details").isNotBlank()) out.append(" — ").append(item.optString("details"))
            out.append('\n')
        }
        return out.toString()
    }

    fun reportJson(context: Context): String {
        val s = snapshot(context)
        s.put("app", "Jarvis").put("version", "1.5.5")
        s.put("device", JSONObject().put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL).put("android", Build.VERSION.RELEASE).put("api", Build.VERSION.SDK_INT))
        s.put("events", eventList(context))
        return s.toString(2)
    }

    private fun export(activity: Activity, ext: String, mime: String, data: String) {
        try {
            val name = "Jarvis-diagnostics-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".$ext"
            val values = ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME, name); put(MediaStore.Downloads.MIME_TYPE, mime); if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Downloads.IS_PENDING, 1) }
            val uri: Uri = activity.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Не удалось создать файл")
            activity.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(data) } ?: error("Не удалось записать файл")
            if (Build.VERSION.SDK_INT >= 29) activity.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            Toast.makeText(activity, "Отчёт сохранён в Загрузки", Toast.LENGTH_LONG).show()
        } catch (e: Exception) { Toast.makeText(activity, "Экспорт не удался: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    fun show(activity: Activity) {
        val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 18, 28, 12) }
        val text = TextView(activity).apply { textSize = 13f; setTextIsSelectable(true) }
        activeText = text
        root.addView(ScrollView(activity).apply { addView(text) }, LinearLayout.LayoutParams(-1, 0, 1f))
        val buttons = LinearLayout(activity).apply { gravity = android.view.Gravity.CENTER }
        buttons.addView(Button(activity).apply { this.text = "TXT"; isAllCaps = false; setOnClickListener { export(activity, "txt", "text/plain", report(activity)) } }, LinearLayout.LayoutParams(0, -2, 1f))
        buttons.addView(Button(activity).apply { this.text = "JSON"; isAllCaps = false; setOnClickListener { export(activity, "json", "application/json", reportJson(activity)) } }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(buttons)
        val current = AlertDialog.Builder(activity).setTitle("Jarvis • LIVE диагностика").setView(root).setNegativeButton("Закрыть", null).create()
        activeDialog = current
        current.setOnDismissListener { handler.removeCallbacksAndMessages(null); activeText = null; activeDialog = null }
        current.setOnShowListener {
            val tick = object : Runnable { override fun run() { if (activeDialog !== current || !current.isShowing) return; activeText?.text = liveText(activity); handler.postDelayed(this, 1500L) } }
            tick.run()
        }
        current.show()
    }
}