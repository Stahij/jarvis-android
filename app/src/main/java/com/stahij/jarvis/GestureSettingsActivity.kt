package com.stahij.jarvis

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class GestureSettingsActivity : Activity() {
    private val cyan = 0xff40e7ff.toInt()
    private val prefs by lazy { getSharedPreferences("launcher_gestures", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xff050a16.toInt()
        window.navigationBarColor = 0xff050a16.toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 28, 22, 20)
            setBackgroundColor(0xff050a16.toInt())
        }
        root.addView(TextView(this).apply {
            text = "ЖЕСТЫ JARVIS"
            textSize = 22f
            setTextColor(cyan)
            setPadding(0, 0, 0, 8)
        })
        root.addView(TextView(this).apply {
            text = "Системные жесты Xiaomi остаются под управлением Android: свайп снизу вверх — Домой, удержание — Недавние, свайп от края — Назад. Jarvis не перехватывает эти зоны."
            textSize = 14f
            setTextColor(0xffc5d5e3.toInt())
            setPadding(0, 8, 0, 18)
        })

        addChoice(root, "Свайп вверх по рабочему столу", "Открыть список приложений",
            "drawer_up", "drawer")
        addChoice(root, "Свайп вниз по рабочему столу", "Открыть поиск",
            "search_down", "search")
        addChoice(root, "Двойное касание пустого места", "Выключено",
            "double_tap", "off")

        val system = TextView(this).apply {
            text = "ОТКРЫТЬ НАСТРОЙКИ СИСТЕМНОЙ НАВИГАЦИИ"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(cyan)
            setPadding(12, 18, 12, 18)
            setOnClickListener {
                try { startActivity(Intent(Settings.ACTION_SETTINGS)) }
                catch (_: Exception) { Toast.makeText(this@GestureSettingsActivity, "Настройки недоступны", Toast.LENGTH_SHORT).show() }
            }
        }
        root.addView(system)
        setContentView(root)
    }

    private fun addChoice(root: LinearLayout, title: String, default: String, key: String, value: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 14, 14, 14)
            setBackgroundColor(0xff0c192a.toInt())
            setOnClickListener {
                prefs.edit().putString(key, value).apply()
                Toast.makeText(this@GestureSettingsActivity, "Сохранено", Toast.LENGTH_SHORT).show()
            }
        }
        row.addView(TextView(this).apply { text = title; textSize = 15f; setTextColor(-1) })
        row.addView(TextView(this).apply {
            text = prefs.getString(key, default) ?: default
            textSize = 12f
            setTextColor(0xff8ba9bd.toInt())
            setPadding(0, 5, 0, 0)
        })
        val params = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10 }
        root.addView(row, params)
    }
}
