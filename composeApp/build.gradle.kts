plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
}

val desktopPackageName = "DCCleaner Mobile"
val desktopPackageVersion = providers.gradleProperty("dccleaner.version").get()
val generatedDesktopBuildConfigDir = layout.buildDirectory.dir("generated/source/desktopBuildConfig/kotlin")
val desktopIconsDir = project.layout.projectDirectory.dir("src/desktopMain/resources/icons")
val localAdJsonFile = rootProject.file("docs/ad.json")
val localAdJson = if (localAdJsonFile.isFile) localAdJsonFile.readText() else ""
val localAdJsonBuildConfigValue = localAdJson
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\r", "\\r")
    .replace("\n", "\\n")
val windowsIconFile = desktopIconsDir.file("app.ico").asFile
val macIconFile = desktopIconsDir.file("app.icns").asFile

kotlin {
    jvmToolchain(21)

    androidTarget()

    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.material3)
                implementation(libs.compose.ui)
                implementation(libs.compose.material.icons.extended)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        val jvmMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(libs.okhttp)
                implementation(libs.okhttp.logging)
                implementation(libs.okhttp.urlconnection)
                implementation(libs.jsoup)
                implementation(libs.kotlin.serialization.json)
                implementation(libs.twocaptcha)
                implementation(libs.gson)
            }
        }
        val androidMain by getting {
            dependsOn(jvmMain)
            dependencies {
                implementation(libs.androidx.activity.compose)
                implementation(project(":musicContract"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val desktopMain by getting {
            dependsOn(jvmMain)
            kotlin.srcDir(generatedDesktopBuildConfigDir)
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

val generateDesktopBuildConfig by tasks.registering {
    inputs.property("versionName", desktopPackageVersion)
    outputs.dir(generatedDesktopBuildConfigDir)

    doLast {
        val outputFile = generatedDesktopBuildConfigDir.get()
            .file("com/dccleaner/app/desktop/DesktopBuildConfig.kt")
            .asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            """
            package com.dccleaner.app.desktop

            internal object DesktopBuildConfig {
                const val VERSION_NAME = "$desktopPackageVersion"
            }
            """.trimIndent()
        )
    }
}

tasks.named("compileKotlinDesktop") {
    dependsOn(generateDesktopBuildConfig)
}

android {
    namespace = "com.dccleaner.composeapp"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 26
        buildConfigField("String", "LOCAL_AD_JSON", "\"\"")
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("String", "LOCAL_AD_JSON", "\"$localAdJsonBuildConfigValue\"")
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

compose.desktop {
    application {
        mainClass = "com.dccleaner.app.desktop.DesktopMainKt"

        nativeDistributions {
            modules("jdk.unsupported")
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Pkg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi
            )
            packageName = desktopPackageName
            packageVersion = desktopPackageVersion
            description = "DCCleaner Mobile desktop application"
            copyright = "AGPL-3.0"

            windows {
                menuGroup = desktopPackageName
                shortcut = true
                dirChooser = true
                perUserInstall = true
                if (windowsIconFile.exists()) {
                    iconFile.set(windowsIconFile)
                }
            }

            macOS {
                bundleID = "com.dccleaner.app"
                if (macIconFile.exists()) {
                    iconFile.set(macIconFile)
                }
            }
        }
    }
}

tasks.register("verifyDesktopRuntimeRestore", Exec::class) {
    dependsOn("createDistributable")
    doFirst {
        val appDirectory = layout.buildDirectory.dir("compose/binaries/main/app").get().asFile
        val osName = System.getProperty("os.name").lowercase()
        val launcher = when {
            osName.contains("mac") ->
                appDirectory.resolve("$desktopPackageName.app/Contents/MacOS/$desktopPackageName")

            osName.contains("win") ->
                appDirectory.resolve("$desktopPackageName/$desktopPackageName.exe")

            else ->
                appDirectory.resolve("$desktopPackageName/bin/$desktopPackageName")
        }
        check(launcher.isFile) { "Packaged desktop launcher was not found: $launcher" }
        commandLine(launcher.absolutePath, "--verify-storage-runtime")
    }
}
