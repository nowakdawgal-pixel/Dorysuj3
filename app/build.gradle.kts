plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "pl.dorysuj.game"
    compileSdk = 35

    defaultConfig {
        applicationId = "pl.dorysuj.game"
        minSdk = 23
        targetSdk = 35
        versionCode = 3
        versionName = "3.0"
    }
}
