plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.dccleaner.musiccontract"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 26
    }
}

kotlin {
    jvmToolchain(21)
}
