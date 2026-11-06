package com.stahij.jarvis

import java.io.File

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
    external fun nativeGenerate(roles: Array<String>, contents: Array<String>, maxTokens: Int): String
    external fun nativeUnload()
    external fun nativeGetLastPerf(): String
}
