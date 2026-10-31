package com.stahij.jarvis

/**
 * Builds a compact, deterministic map from repository paths and fetched source text.
 *
 * This deliberately uses lightweight lexical extraction rather than pretending to be
 * a Kotlin compiler. Every entry is traceable to a path and blob SHA supplied by GitHub.
 */
object RepositoryCodeMap {
    data class SourceFile(
        val path: String,
        val content: String,
        val blobSha: String
    )

    data class FileEntry(
        val path: String,
        val blobSha: String,
        val characters: Int,
        val packageName: String?,
        val declarations: List<String>,
        val imports: List<String>
    )

    data class Index(
        val repository: String,
        val branch: String,
        val commitSha: String?,
        val totalTreePaths: Int,
        val files: List<FileEntry>
    ) {
        fun render(maxCharacters: Int = 12000): String = buildString {
            appendLine("CODE MAP: $repository")
            appendLine("Ветка: $branch")
            appendLine("Commit: ${commitSha ?: "не указан"}")
            appendLine("Файлов в дереве: $totalTreePaths; проиндексировано: ${files.size}")
            for (file in files) {
                if (length >= maxCharacters) break
                appendLine()
                appendLine(file.path + " • ${file.characters} символов • blob ${file.blobSha}")
                file.packageName?.let { appendLine("package $it") }
                file.imports.take(12).forEach { appendLine("  import $it") }
                file.declarations.take(30).forEach { appendLine("  $it") }
            }
        }.take(maxCharacters)
    }

    private val declarationPatterns = listOf(
        Regex("^\\s*(?:(?:public|private|internal|protected|open|override|suspend|inline|data|sealed|abstract|enum|annotation|expect|actual)\\s+)*(?:class|interface|object|enum\\s+class|data\\s+class|sealed\\s+class)\\s+[A-Za-z_][A-Za-z0-9_]*[^\\n]*"),
        Regex("^\\s*(?:(?:public|private|internal|protected|override|suspend|inline|operator|infix|tailrec|external)\\s+)*fun\\s+(?:<[^>]+>\\s*)?(?:[A-Za-z_][A-Za-z0-9_.<>?]*\\.)?[A-Za-z_][A-Za-z0-9_]*\\s*\\([^\\n]*"),
        Regex("^\\s*(?:(?:public|private|internal|protected|const|lateinit|override)\\s+)*(?:val|var)\\s+[A-Za-z_][A-Za-z0-9_]*[^\\n]*")
    )

    fun build(
        repository: String,
        branch: String,
        commitSha: String?,
        totalTreePaths: Int,
        sourceFiles: List<SourceFile>
    ): Index {
        val entries = sourceFiles.map { source ->
            val lines = source.content.lineSequence().toList()
            val packageName = lines.firstNotNullOfOrNull { line ->
                Regex("^\\s*package\\s+([A-Za-z0-9_.]+)").find(line)?.groupValues?.get(1)
            }
            val imports = lines.mapNotNull { line ->
                Regex("^\\s*import\\s+([A-Za-z0-9_.*]+)").find(line)?.groupValues?.get(1)
            }.distinct()
            val declarations = lines.filter { line ->
                declarationPatterns.any { it.containsMatchIn(line) }
            }.map { it.trim() }.distinct().take(80)
            FileEntry(
                path = source.path,
                blobSha = source.blobSha,
                characters = source.content.length,
                packageName = packageName,
                declarations = declarations,
                imports = imports
            )
        }
        return Index(repository, branch, commitSha, totalTreePaths, entries)
    }

    fun search(index: Index, query: String, limit: Int = 12): List<FileEntry> {
        val terms = query.lowercase().split(Regex("[^\\p{L}\\p{N}_]+"))
            .filter { it.length >= 2 }.distinct()
        if (terms.isEmpty()) return emptyList()
        return index.files.mapNotNull { file ->
            val haystack = (file.path + " " + file.packageName.orEmpty() + " " +
                file.declarations.joinToString(" ") + " " + file.imports.joinToString(" "))
                .lowercase()
            val score = terms.count { haystack.contains(it) }
            if (score == 0) null else score to file
        }.sortedByDescending { it.first }.take(limit).map { it.second }
    }
}
