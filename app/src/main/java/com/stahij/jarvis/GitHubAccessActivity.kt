package com.stahij.jarvis

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

/** GitHub account connection, repository inspection and explicitly confirmed file writes. */
class GitHubAccessActivity : Activity() {
    private val bg = Color.rgb(5, 10, 22)
    private val cyan = Color.rgb(64, 231, 255)
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var tokenInput: EditText
    private lateinit var repoInput: EditText
    private lateinit var branchInput: EditText
    private lateinit var pathInput: EditText
    private lateinit var contentInput: EditText
    private var loadedFile: GitHubApi.RemoteFile? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(18))
            setBackgroundColor(bg)
        }
        root.addView(TextView(this).apply {
            text = "GITHUB WORKSPACE"
            textSize = 21f
            setTextColor(cyan)
        })
        status = TextView(this).apply {
            text = if (GitHubTokenStore.hasToken(this@GitHubAccessActivity))
                "ТОКЕН СОХРАНЁН В ЗАЩИЩЁННОМ ХРАНИЛИЩЕ" else "GITHUB НЕ ПОДКЛЮЧЁН"
            textSize = 11f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(8), 0, dp(12))
        }
        root.addView(status)
        val scroll = ScrollView(this)
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tokenInput = field("Fine-grained GitHub token", true)
        tokenInput.hint = "github_pat_… (вводится только здесь)"
        tokenInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        form.addView(tokenInput)
        button(form, "Сохранить и проверить подключение") { saveAndVerify() }
        button(form, "Отключить GitHub") {
            AlertDialog.Builder(this).setTitle("Отключить GitHub?")
                .setMessage("Зашифрованный токен будет удалён с устройства.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Отключить") { _, _ ->
                    GitHubTokenStore.clear(this)
                    tokenInput.setText("")
                    status.text = "GITHUB ОТКЛЮЧЁН"
                }.show()
        }
        form.addView(label("Репозиторий"))
        repoInput = field("owner/repository").apply { setText("Stahij/jarvis-android") }
        form.addView(repoInput)
        form.addView(label("Ветка"))
        branchInput = field("branch").apply { setText("feature/local-brain-and-updater") }
        form.addView(branchInput)
        button(form, "Проверить репозиторий и список файлов") { inspectRepository() }
        form.addView(label("Путь к файлу"))
        pathInput = field("например app/src/main/…/MainActivity.kt")
        form.addView(pathInput)
        button(form, "Прочитать файл из GitHub") { readFile() }
        form.addView(label("Содержимое файла"))
        contentInput = EditText(this).apply {
            hint = "После чтения здесь появится содержимое. Можно подготовить правки."
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            textSize = 13f
            gravity = Gravity.TOP
            minLines = 8
            maxLines = 24
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setBackgroundColor(Color.rgb(15, 27, 43))
        }
        form.addView(contentInput, LinearLayout.LayoutParams(-1, dp(260)))
        button(form, "Показать изменения и подтвердить запись") { previewWrite() }
        button(form, "Назад") { finish() }
        scroll.addView(form)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun field(hintText: String, password: Boolean = false) = EditText(this).apply {
        hint = hintText
        setTextColor(Color.WHITE)
        setHintTextColor(Color.GRAY)
        textSize = 14f
        singleLine = !password
        setPadding(dp(10), dp(8), dp(10), dp(8))
        setBackgroundColor(Color.rgb(15, 27, 43))
    }

    private fun label(value: String) = TextView(this).apply {
        text = value
        setTextColor(cyan)
        textSize = 12f
        setPadding(0, dp(12), 0, dp(5))
    }

    private fun button(parent: LinearLayout, title: String, action: () -> Unit) {
        parent.addView(Button(this).apply {
            text = title
            isAllCaps = false
            setTextColor(cyan)
            setBackgroundColor(Color.rgb(12, 36, 53))
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(7) })
    }

    private fun saveAndVerify() {
        val token = tokenInput.text.toString().trim()
        if (token.isBlank()) {
            showMessage("Введи fine-grained token. Он не отправляется в чат и не сохраняется открытым текстом.")
            return
        }
        runTask("ПРОВЕРЯЮ GITHUB…") {
            GitHubTokenStore.save(this, token)
            val login = GitHubApi(token).verify()
            "Подключён GitHub: @$login. Токен зашифрован Android Keystore."
        }
        tokenInput.setText("")
    }

    private fun inspectRepository() {
        runTask("ЧИТАЮ РЕПОЗИТОРИЙ…") {
            val api = api()
            val repo = repoInput.text.toString().trim()
            val branch = branchInput.text.toString().trim()
            val meta = api.repository(repo)
            val files = api.tree(repo, branch)
            "Репозиторий: $repo\nВетка: $branch\nВидимость: ${meta.optString("visibility")}\nФайлов: ${files.size}\n\n" +
                files.take(250).joinToString("\n") + if (files.size > 250) "\n… список сокращён" else ""
        }
    }

    private fun readFile() {
        runTask("ЧИТАЮ ФАЙЛ…") {
            val remote = api().readFile(repoInput.text.toString().trim(),
                branchInput.text.toString().trim(), pathInput.text.toString().trim())
            loadedFile = remote
            runOnUiThread { contentInput.setText(remote.content) }
            "Прочитан ${remote.path}\nBlob SHA: ${remote.sha}\nРазмер: ${remote.content.length} символов."
        }
    }

    private fun previewWrite() {
        val original = loadedFile ?: run {
            showMessage("Сначала прочитай существующий файл. Это нужно для проверки актуального SHA и защиты от перезаписи чужих изменений.")
            return
        }
        val replacement = contentInput.text.toString()
        if (replacement == original.content) {
            showMessage("Изменений нет.")
            return
        }
        val oldLines = original.content.lines()
        val newLines = replacement.lines()
        val diff = buildString {
            appendLine("--- a/${original.path}")
            appendLine("+++ b/${original.path}")
            oldLines.zip(newLines).forEachIndexed { index, pair ->
                if (pair.first != pair.second) {
                    appendLine("@@ строка ${index + 1} @@")
                    appendLine("- ${pair.first}")
                    appendLine("+ ${pair.second}")
                }
            }
            if (oldLines.size > newLines.size) oldLines.drop(newLines.size).forEach { appendLine("- $it") }
            if (newLines.size > oldLines.size) newLines.drop(oldLines.size).forEach { appendLine("+ $it") }
            appendLine("\nПолный текст будет записан целиком. Проверь diff перед подтверждением.")
        }
        AlertDialog.Builder(this)
            .setTitle("Проверка изменений")
            .setMessage(diff.take(12000))
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Подтвердить запись") { _, _ ->
                val message = "Jarvis: update ${original.path}"
                runTask("ОТПРАВЛЯЮ ИЗМЕНЕНИЕ…") {
                    val commit = api().writeFile(repoInput.text.toString().trim(),
                        branchInput.text.toString().trim(), original.path, replacement, original.sha, message)
                    loadedFile = api().readFile(repoInput.text.toString().trim(),
                        branchInput.text.toString().trim(), original.path)
                    "Файл записан в GitHub.\nCommit: $commit\nПуть: ${original.path}"
                }
            }.show()
    }

    private fun api(): GitHubApi {
        val token = GitHubTokenStore.load(this) ?: throw IllegalStateException("Сначала подключи GitHub.")
        return GitHubApi(token)
    }

    private fun runTask(progress: String, action: () -> String) {
        status.text = progress
        worker.execute {
            try {
                val result = action()
                runOnUiThread { status.text = "ГОТОВО"; showMessage(result) }
            } catch (e: Exception) {
                runOnUiThread { status.text = "ОШИБКА GITHUB"; showMessage(e.message ?: "Неизвестная ошибка") }
            }
        }
    }

    private fun showMessage(message: String) {
        AlertDialog.Builder(this).setTitle("Jarvis • GitHub").setMessage(message)
            .setPositiveButton("Закрыть", null).show()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }
}
