import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.dccleaner.musicbridge"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.dccleaner.musicbridge"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = providers.gradleProperty("dccleaner.version").get()
    }

    buildTypes {
        release {
            vcsInfo {
                include = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(project(":musicContract"))
}
