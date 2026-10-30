package com.stahij.jarvis

/**
 * JNI bridge to the in-process llama.cpp runtime.
 * The GGUF model is stored in app-private files, separately from the APK.
 */
object NativeLlm {
    init {
        System.loadLibrary("jarvis-native")
    }

    external fun nativeLoadModel(path: String, threads: Int, contextSize: Int): String?
    external fun nativeGenerate(roles: Array<String>, contents: Array<String>, maxTokens: Int): String
    external fun nativeUnload()
}
