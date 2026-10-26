import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val dccleanerVersionName = providers.gradleProperty("dccleaner.version").get()
val localAdJsonFile = rootProject.file("docs/ad.json")
val localAdJson = if (localAdJsonFile.isFile) localAdJsonFile.readText() else ""
val localAdJsonBuildConfigValue = localAdJson
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\r", "\\r")
    .replace("\n", "\\n")

android {
    namespace = "com.dccleaner.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.dccleaner.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 270
        versionName = dccleanerVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "LOCAL_AD_JSON", "\"$localAdJsonBuildConfigValue\"")
        }
        release {
            buildConfigField("String", "LOCAL_AD_JSON", "\"\"")
            vcsInfo {
                include = false
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    packaging {
        resources.excludes += setOf(
            "/audio-en.mp3", "/audio-ru.mp3",
            "/canvas.jpg", "/canvas_hint.jpg",
            "/grid.jpg", "/grid_2.jpg", "/grid_hint.jpg",
            "/normal.jpg", "/normal_2.jpg",
            "/rotate.jpg", "/rotate_2.jpg", "/rotate_3.jpg",
            "/vk.jpg", "/temu/**"
        )
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(project(":composeApp"))
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.gson)
    implementation(libs.androidx.security.crypto)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
