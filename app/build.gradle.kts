plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.stahij.jarvis"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.stahij.jarvis"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.2.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
