plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.stahij.jarvis"
    compileSdk = 35
    defaultConfig {
        // Side-by-side preview keeps the user's installed 0.1.0 and its local history intact.
        applicationId = "com.stahij.jarvis.preview"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.1.1-preview"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
