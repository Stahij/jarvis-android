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

/** Read-only GitHub browser. Repository content is always treated as untrusted data. */
class GitHubRepositoryActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val background = Color.rgb(5, 10, 22)
    private val accent = Color.rgb(64, 231, 255)
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var repoInput: EditText
    private var currentRepo = DEFAULT_REPOSITORY
    private var currentRef = DEFAULT_BRANCH
    private var currentPath = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        loadDirectory()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(background)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "GITHUB • READ ONLY"
            textSize = 18f
            setTextColor(accent)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(Button(this).apply {
            text = "Назад"
            setOnClickListener { finish() }
        })
        root.addView(header)

        repoInput = EditText(this).apply {
            setText(DEFAULT_REPOSITORY)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            hint = "owner/repository"
            singleLine = true
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setBackgroundColor(Color.rgb(15, 27, 43))
        }
        root.addView(repoInput)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Открыть", "Файлы", "Ветки", "Коммиты", "Сборки").forEach { label ->
            actions.addView(Button(this).apply {
                text = label
                textSize = 10f
                isAllCaps = false
                setOnClickListener {
                    when (label) {
                        "Открыть" -> {
                            val value = repoInput.text.toString().trim()
                            if (value.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) {
                                currentRepo = value
                                currentRef = if (value == DEFAULT_REPOSITORY) DEFAULT_BRANCH else "main"
                                currentPath = ""
                                loadDirectory()
                            } else showMessage("Укажи репозиторий в формате owner/name")
                        }
                        "Файлы" -> loadDirectory()
                        "Ветки" -> loadBranches()
                        "Коммиты" -> loadCommits()
                        "Сборки" -> loadWorkflowRuns()
                    }
                }
            }, LinearLayout.LayoutParams(0, dp(44), 1f))
        }
        root.addView(actions)

        status = TextView(this).apply {
            text = "Подключаюсь к GitHub API..."
            textSize = 11f
            setTextColor(Color.rgb(104, 160, 181))
            setPadding(0, dp(8), 0, dp(8))
        }
        root.addView(status)
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun loadDirectory() {
        val repo = currentRepo
        val ref = currentRef
        val path = currentPath
        requestJson("https://api.github.com/repos/" + repo + "/contents/" +
            path.split("/").joinToString("/") { enc(it) } + "?ref=" + enc(ref)) { result ->
            content.removeAllViews()
            if (result is JSONArray) {
                addHeading(repo + " • " + ref + " • /" + path)
                if (path.isNotEmpty()) addItem("⬆  ..") {
                    currentPath = path.substringBeforeLast("/", "")
                    loadDirectory()
                }
                val entries = (0 until result.length()).map { result.getJSONObject(it) }
                    .sortedWith(compareBy<JSONObject> { if (it.optString("type") == "dir") 0 else 1 }.thenBy { it.optString("name") })
                entries.forEach { entry ->
                    val name = entry.optString("name")
                    val isDir = entry.optString("type") == "dir"
                    addItem((if (isDir) "📁  " else "📄  ") + name) {
                        if (isDir) {
                            currentPath = listOf(path, name).filter { it.isNotBlank() }.joinToString("/")
                            loadDirectory()
                        } else loadFile(entry)
                    }
                }
                status.text = "Режим чтения • файлы не изменяются"
            } else showMessage("Не удалось прочитать список файлов")
        }
    }

    private fun loadFile(entry: JSONObject) {
        val url = entry.optString("url")
        if (url.isBlank()) return showMessage("GitHub не вернул адрес файла")
        requestJson(url) { result ->
            content.removeAllViews()
            addItem("← К списку файлов") { loadDirectory() }
            if (result is JSONObject) {
                val name = result.optString("name", entry.optString("name"))
                val encoded = result.optString("content").replace("\n", "")
                val bytes = try { android.util.Base64.decode(encoded, android.util.Base64.DEFAULT) }
                    catch (_: Exception) { ByteArray(0) }
                val fileText = if (bytes.isNotEmpty()) String(bytes, Charsets.UTF_8) else
                    "Файл пуст, слишком большой или GitHub не предоставил его содержимое."
                addHeading(name)
                addText(fileText.take(MAX_FILE_CHARS))
                if (fileText.length > MAX_FILE_CHARS) addText("… файл обрезан в целях безопасности и производительности.")
                status.text = "Просмотр файла • только чтение"
            } else showMessage("Не удалось загрузить файл")
        }
    }

    private fun loadBranches() {
        requestJson("https://api.github.com/repos/" + currentRepo + "/branches?per_page=100") { result ->
            content.removeAllViews()
            addHeading("Ветки • " + currentRepo)
            if (result is JSONArray) for (i in 0 until result.length()) {
                val name = result.getJSONObject(i).optString("name")
                addItem("⑂  " + name) {
                    currentRef = name
                    currentPath = ""
                    loadDirectory()
                }
            } else showMessage("Не удалось загрузить ветки")
        }
    }

    private fun loadCommits() {
        requestJson("https://api.github.com/repos/" + currentRepo + "/commits?per_page=30") { result ->
            content.removeAllViews()
            addHeading("Последние коммиты")
            if (result is JSONArray) for (i in 0 until result.length()) {
                val item = result.getJSONObject(i)
                val sha = item.optString("sha")
                val message = item.optJSONObject("commit")?.optJSONObject("message")?.optString("message").orEmpty()
                addItem(sha.take(7) + "  " + message.lineSequence().firstOrNull().orEmpty()) {
                    showMessage("Коммит " + sha + "\n" + message)
                }
            } else showMessage("Не удалось загрузить коммиты")
        }
    }

    private fun loadWorkflowRuns() {
        requestJson("https://api.github.com/repos/" + currentRepo + "/actions/runs?per_page=20") { result ->
            content.removeAllViews()
            addHeading("GitHub Actions • последние сборки")
            val runs = (result as? JSONObject)?.optJSONObject("workflow_runs")?.optJSONArray("workflow_runs")
            if (runs != null) for (i in 0 until runs.length()) {
                val item = runs.getJSONObject(i)
                val title = item.optString("name", "Workflow")
                val branch = item.optString("head_branch")
                val state = item.optString("status") + " / " + item.optString("conclusion", "выполняется")
                addItem(title + " • " + branch + "\n" + state) {
                    showMessage("Запуск #" + item.optLong("run_number") + "\n" + state + "\n" + item.optString("html_url"))
                }
            } else showMessage("Не удалось загрузить сборки. Для некоторых данных нужна авторизация.")
        }
    }

    private fun requestJson(address: String, callback: (Any?) -> Unit) {
        status.text = "ЗАПРАШИВАЮ GITHUB..."
        executor.execute {
            val result: Any? = try {
                val connection = URL(address).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 12000
                connection.readTimeout = 15000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                connection.setRequestProperty("User-Agent", "Jarvis-Android")
                try {
                    val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
                    val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (connection.responseCode !in 200..299) JSONObject().put("error", "HTTP " + connection.responseCode + ": " + body.take(300))
                    else if (body.trimStart().startsWith("[")) JSONArray(body) else JSONObject(body)
                } finally { connection.disconnect() }
            } catch (e: Exception) {
                JSONObject().put("error", e.message ?: "Network error")
            }
            runOnUiThread {
                val error = (result as? JSONObject)?.optString("error")
                if (!error.isNullOrBlank()) status.text = "GitHub: " + error
                callback(result)
            }
        }
    }

    private fun addHeading(value: String) {
        content.addView(TextView(this).apply {
            text = value
            textSize = 14f
            setTextColor(accent)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, dp(8), 0, dp(10))
        })
    }

    private fun addItem(value: String, action: () -> Unit) {
        content.addView(Button(this).apply {
            text = value
            isAllCaps = false
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(13, 27, 43))
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(5) })
    }

    private fun addText(value: String) {
        content.addView(TextView(this).apply {
            text = value
            textSize = 12f
            setTextColor(Color.rgb(220, 232, 240))
            setPadding(dp(8), dp(8), dp(8), dp(8))
        })
    }

    private fun showMessage(value: String) {
        content.removeAllViews()
        addText(value)
        status.text = "Информация GitHub"
    }

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val DEFAULT_REPOSITORY = "Stahij/jarvis-android"
        private const val DEFAULT_BRANCH = "feature/local-brain-and-updater"
        private const val MAX_FILE_CHARS = 18000
    }
}
