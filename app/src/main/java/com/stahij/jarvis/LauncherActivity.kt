package com.stahij.jarvis

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class LauncherActivity : Activity() {
    private lateinit var workspace: AtomWorkspaceView
    private val cyan = Color.rgb(99, 224, 255)
    private val bg = Color.rgb(3, 8, 20)
    private var installedApps: List<LauncherApp> = emptyList()
    private lateinit var pageLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.navigationBarDividerColor = Color.TRANSPARENT
        loadInstalledApps()
        buildWorkspace()
    }

    override fun onResume() {
        super.onResume()
        if (::workspace.isInitialized) workspace.resumeMotion()
    }

    override fun onPause() {
        if (::workspace.isInitialized) workspace.pauseMotion()
        super.onPause()
    }

    private fun loadInstalledApps() {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val resolved = packageManager.queryIntentActivities(launcher, 0)
        installedApps = resolved
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName + "/" + it.activityInfo.name }
            .map { info ->
                LauncherApp(
                    label = info.loadLabel(packageManager).toString(),
                    packageName = info.activityInfo.packageName,
                    className = info.activityInfo.name,
                    icon = info.loadIcon(packageManager)
                )
            }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    private fun buildWorkspace() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(10), dp(16), dp(4))
        }
        val brand = TextView(this).apply {
            text = "J A R V I S"
            textSize = 18f
            letterSpacing = .18f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(cyan)
        }
        header.addView(brand, LinearLayout.LayoutParams(0, dp(42), 1f))
        val assistant = action("◉", "Открыть Jarvis AI") {
            startActivity(Intent(this, ChatHubActivity::class.java))
        }
        header.addView(assistant, LinearLayout.LayoutParams(dp(46), dp(42)))
        val drawer = action("⌕", "Поиск приложений") { showAppDrawer() }
        header.addView(drawer, LinearLayout.LayoutParams(dp(46), dp(42)).apply { leftMargin = dp(8) })
        root.addView(header)

        pageLabel = TextView(this).apply {
            text = "ЖИВОЕ РАБОЧЕЕ ПРОСТРАНСТВО"
            textSize = 9f
            letterSpacing = .18f
            setTextColor(Color.rgb(104, 153, 184))
            setPadding(dp(22), 0, dp(22), dp(4))
        }
        root.addView(pageLabel)

        workspace = AtomWorkspaceView(this, installedApps) { page, count ->
            pageLabel.text = "ОРБИТА  %02d / %02d   •   ЯДРО JARVIS".format(page + 1, count)
        }
        root.addView(workspace, LinearLayout.LayoutParams(-1, 0, 1f))

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(4), dp(14), dp(10))
        }
        footer.addView(action("‹", "Предыдущая страница") { workspace.changePage(-1) },
            LinearLayout.LayoutParams(dp(48), dp(42)))
        footer.addView(action("●", "Зафиксировать орбиты") { workspace.restoreSavedPositions() },
            LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(8); rightMargin = dp(8) })
        footer.addView(action("›", "Следующая страница") { workspace.changePage(1) },
            LinearLayout.LayoutParams(dp(48), dp(42)))
        root.addView(footer)
        setContentView(root)
    }

    private fun action(symbol: String, description: String, click: () -> Unit) = TextView(this).apply {
        text = symbol
        contentDescription = description
        textSize = 21f
        gravity = Gravity.CENTER
        setTextColor(cyan)
        background = GradientDrawable().apply {
            setColor(Color.rgb(10, 25, 43))
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), Color.rgb(28, 83, 111))
        }
        isClickable = true
        isFocusable = true
        setOnClickListener { click() }
    }

    private fun showAppDrawer() {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(8))
        }
        val search = EditText(this).apply {
            hint = "Найти приложение"
            setSingleLine(true)
            textSize = 16f
        }
        column.addView(search)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = android.widget.ScrollView(this)
        scroll.addView(list)
        column.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val dialog = AlertDialog.Builder(this)
            .setTitle("Приложения • ${installedApps.size}")
            .setView(column)
            .setNegativeButton("Закрыть", null)
            .create()

        fun render(query: String) {
            list.removeAllViews()
            installedApps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }
                .forEach { app ->
                    val row = TextView(this).apply {
                        text = "  ${app.label}"
                        textSize = 16f
                        setTextColor(Color.WHITE)
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(8), dp(12), dp(8), dp(12))
                        setCompoundDrawablesWithIntrinsicBounds(app.icon, null, null, null)
                        compoundDrawablePadding = dp(12)
                        setOnClickListener { launch(app); dialog.dismiss() }
                    }
                    list.addView(row)
                }
        }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = render(s?.toString().orEmpty())
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        render("")
        dialog.show()
    }

    private fun launch(app: LauncherApp) {
        try {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(app.packageName, app.className)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Toast.makeText(this, "Не удалось открыть ${app.label}", Toast.LENGTH_SHORT).show()
            loadInstalledApps()
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
