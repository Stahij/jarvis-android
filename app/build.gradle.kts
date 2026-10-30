plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.stahij.jarvis"
    compileSdk = 35

    defaultConfig {
        // Keep preview separate from the original 0.1.0 package.
        applicationId = "com.stahij.jarvis.preview"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.1.1-preview"
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
