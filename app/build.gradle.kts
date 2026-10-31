plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.stahij.jarvis"
    compileSdk = 35
    ndkVersion = "29.0.13113456"

    defaultConfig {
        applicationId = "com.stahij.jarvis.preview"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "0.7.0"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DCMAKE_BUILD_TYPE=Release",
                    "-DGGML_NATIVE=OFF",
                    "-DGGML_OPENMP=OFF",
                    "-DGGML_LLAMAFILE=OFF"
                )
            }
        }
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("JARVIS_KEYSTORE_PATH")
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
            }
            storePassword = System.getenv("JARVIS_STORE_PASSWORD")
            keyAlias = System.getenv("JARVIS_KEY_ALIAS")
            keyPassword = System.getenv("JARVIS_KEY_PASSWORD")
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
