package com.stahij.jarvis

import android.app.Activity
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class HomeActivity : Activity() {
    private val background = Color.rgb(5, 10, 22)
    private val panel = Color.rgb(12, 25, 42)
    private val cyan = Color.rgb(64, 231, 255)
    private lateinit var appGrid: GridLayout
    private lateinit var search: EditText
    private lateinit var appCount: TextView
    private var apps: List<ResolveInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = background
        window.navigationBarColor = background
        buildHome()
        loadApps()
    }

    override fun onResume() {
        super.onResume()
        if (::appGrid.isInitialized) loadApps()
    }

    private fun buildHome() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(12))
            setBackgroundColor(background)
        }

        val brand = TextView(this).apply {
            text = "J A R V I S"
            textSize = 25f
            letterSpacing = 0.18f
            setTextColor(cyan)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        root.addView(brand)

        val subtitle = TextView(this).apply {
            text = "PERSONAL SYSTEM INTERFACE"
            textSize = 10f
            letterSpacing = 0.16f
            setTextColor(Color.rgb(105, 160, 184))
            setPadding(0, dp(3), 0, dp(18))
        }
        root.addView(subtitle)

        val assistant = TextView(this).apply {
            text = "◉   ОТКРЫТЬ JARVIS AI"
            textSize = 14f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(cyan)
            setPadding(dp(16), 0, dp(16), 0)
            background = rounded(panel, dp(14), Color.rgb(26, 94, 117))
            setOnClickListener { startActivity(Intent(this@HomeActivity, MainActivity::class.java)) }
        }
        root.addView(assistant, LinearLayout.LayoutParams(-1, dp(58)))

        val searchRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
            background = rounded(panel, dp(12))
        }
        val searchIcon = TextView(this).apply {
            text = "⌕"
            textSize = 25f
            setTextColor(cyan)
        }
        searchRow.addView(searchIcon)
        search = EditText(this).apply {
            hint = "Поиск приложений"
            setHintTextColor(Color.rgb(105, 130, 150))
            setTextColor(Color.WHITE)
            textSize = 14f
            singleLine = true
            background = null
            setPadding(dp(10), 0, 0, 0)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    renderApps(s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        searchRow.addView(search, LinearLayout.LayoutParams(0, dp(52), 1f))
        val clear = TextView(this).apply {
            text = "×"
            textSize = 23f
            setTextColor(Color.LTGRAY)
            setPadding(dp(8), 0, dp(4), 0)
            setOnClickListener { search.text.clear() }
        }
        searchRow.addView(clear)
        val searchParams = LinearLayout.LayoutParams(-1, dp(52)).apply {
            topMargin = dp(18)
            bottomMargin = dp(12)
        }
        root.addView(searchRow, searchParams)

        appCount = TextView(this).apply {
            text = "ПРИЛОЖЕНИЯ"
            textSize = 10f
            letterSpacing = 0.15f
            setTextColor(Color.rgb(105, 160, 184))
            setPadding(dp(2), dp(4), 0, dp(8))
        }
        root.addView(appCount)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        appGrid = GridLayout(this).apply {
            columnCount = if (resources.configuration.screenWidthDp >= 600) 6 else 4
            useDefaultMargins = false
            alignmentMode = GridLayout.ALIGN_BOUNDS
        }
        scroll.addView(appGrid)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val footer = TextView(this).apply {
            text = "JARVIS LAUNCHER  •  ПЕРВАЯ СБОРКА"
            textSize = 9f
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(66, 105, 128))
            setPadding(0, dp(10), 0, dp(4))
        }
        root.addView(footer)
        setContentView(root)
    }

    private fun loadApps() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        apps = packageManager.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase(Locale.getDefault()) }
        renderApps(search.text?.toString().orEmpty())
    }

    private fun renderApps(query: String) {
        if (!::appGrid.isInitialized) return
        appGrid.removeAllViews()
        val filtered = apps.filter {
            it.loadLabel(packageManager).toString().contains(query.trim(), ignoreCase = true)
        }
        appCount.text = if (query.isBlank()) {
            "ПРИЛОЖЕНИЯ  •  ${filtered.size}"
        } else {
            "РЕЗУЛЬТАТЫ ПОИСКА  •  ${filtered.size}"
        }

        val columns = appGrid.columnCount.coerceAtLeast(1)
        val cellWidth = (resources.displayMetrics.widthPixels - dp(36)) / columns
        filtered.forEachIndexed { index, info ->
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(3), dp(8), dp(3), dp(8))
                isClickable = true
                isFocusable = true
                background = rounded(Color.TRANSPARENT, dp(12))
                setOnClickListener { launchApp(info) }
            }
            val icon = ImageView(this).apply {
                setImageDrawable(info.loadIcon(packageManager))
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = info.loadLabel(packageManager).toString()
            }
            tile.addView(icon, LinearLayout.LayoutParams(dp(48), dp(48)))
            val label = TextView(this).apply {
                text = info.loadLabel(packageManager)
                textSize = 10.5f
                gravity = Gravity.CENTER
                maxLines = 2
                setTextColor(Color.rgb(220, 233, 243))
                setPadding(0, dp(6), 0, 0)
            }
            tile.addView(label, LinearLayout.LayoutParams(-1, dp(34)))
            val params = GridLayout.LayoutParams(
                GridLayout.spec(index / columns, 1f),
                GridLayout.spec(index % columns, 1f)
            ).apply {
                width = 0
                height = dp(94)
                setMargins(dp(2), dp(2), dp(2), dp(2))
            }
            appGrid.addView(tile, params)
        }

        if (filtered.isEmpty()) {
            val empty = TextView(this).apply {
                text = if (apps.isEmpty()) "Не удалось получить список приложений" else "Ничего не найдено"
                gravity = Gravity.CENTER
                setTextColor(Color.LTGRAY)
                setPadding(0, dp(28), 0, dp(28))
            }
            appGrid.addView(empty, GridLayout.LayoutParams().apply {
                width = cellWidth * columns
                height = dp(70)
            })
        }
    }

    private fun launchApp(info: ResolveInfo) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(info.activityInfo.packageName, info.activityInfo.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Не удалось открыть приложение", Toast.LENGTH_SHORT).show()
            loadApps()
        }
    }

    private fun rounded(color: Int, radius: Int, stroke: Int? = null) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
            stroke?.let { setStroke(dp(1), it) }
        }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
