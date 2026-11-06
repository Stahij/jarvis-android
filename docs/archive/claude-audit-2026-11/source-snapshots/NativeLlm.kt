package com.stahij.jarvis

import java.io.File

/** Receives decoded text from native code. Chunks always contain whole UTF-8 characters. */
fun interface TokenListener {
    fun onToken(piece: ByteArray)
}

/**
 * JNI bridge to the in-process llama.cpp runtime.
 * The GGUF model is stored in app-private files, separately from the APK.
 */
object NativeLlm {
    init {
        System.loadLibrary("jarvis-native")
    }

    @Volatile
    var loadedPath: String? = null
        private set

    @Volatile private var loadedLength: Long = -1L
    @Volatile private var loadedModified: Long = -1L
    @Volatile private var loadedThreads: Int = -1
    @Volatile private var loadedContextSize: Int = -1

    /** Avoid reloading the same unchanged GGUF model across Activity recreation. */
    @Synchronized
    fun ensureLoaded(path: String, threads: Int, contextSize: Int): String? {
        val file = File(path)
        val length = file.length()
        val modified = file.lastModified()
        if (loadedPath == path && loadedLength == length && loadedModified == modified &&
            loadedThreads == threads && loadedContextSize == contextSize
        ) return null

        clearLoadedState()
        val error = nativeLoadModel(path, threads, contextSize)
        if (error == null) {
            loadedPath = path
            loadedLength = length
            loadedModified = modified
            loadedThreads = threads
            loadedContextSize = contextSize
        }
        return error
    }

    /** Call after replacing the model file, including replacement at the same path. */
    @Synchronized
    fun invalidate() {
        clearLoadedState()
    }

    /**
     * Generates a reply. [onToken] is called on the calling thread for every decoded chunk
     * (move UI work to the main thread yourself). Native errors are thrown as
     * IllegalStateException, so they never get mistaken for an assistant answer.
     * Returns the full text, also after [abort].
     */
    fun generate(
        roles: List<String>,
        contents: List<String>,
        maxTokens: Int,
        onToken: ((String) -> Unit)? = null
    ): String {
        require(roles.size == contents.size) { "roles and contents differ in size" }
        val listener = onToken?.let { callback ->
            TokenListener { bytes -> callback(String(bytes, Charsets.UTF_8)) }
        }
        val bytes = nativeGenerate(
            roles.toTypedArray(),
            Array(contents.size) { contents[it].toByteArray(Charsets.UTF_8) },
            maxTokens,
            listener
        )
        return String(bytes, Charsets.UTF_8)
    }

    /** Asks a running generation to stop. Lock-free, safe from any thread, ends within one token. */
    fun abort() = nativeAbort()

    /** Unloads the native model and keeps Kotlin state synchronized. */
    @Synchronized
    fun unload() {
        nativeUnload()
        clearLoadedState()
    }

    private fun clearLoadedState() {
        loadedPath = null
        loadedLength = -1L
        loadedModified = -1L
        loadedThreads = -1
        loadedContextSize = -1
    }

    // Keep external names unchanged: JNI resolves these method names in C++.
    external fun nativeLoadModel(path: String, threads: Int, contextSize: Int): String?
    external fun nativeGenerate(roles: Array<String>, contents: Array<ByteArray>, maxTokens: Int, listener: TokenListener?): ByteArray
    external fun nativeAbort()
    external fun nativeUnload()
    external fun nativeGetLastPerf(): String
}
