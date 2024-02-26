// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.plugin.parcelize)
}

android {
    namespace = "org.equeim.spacer"
    compileSdk = libs.versions.sdk.platform.compile.get().toInt()
    defaultConfig {
        applicationId = "org.equeim.spacer"
        minSdk = libs.versions.sdk.platform.min.get().toInt()
        targetSdk = libs.versions.sdk.platform.target.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes.named("release") {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles.addAll(arrayOf(getDefaultProguardFile("proguard-android-optimize.txt"), file("proguard-rules.pro")))
        signingConfig = signingConfigs.getByName("debug")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }
    composeOptions.kotlinCompilerExtensionVersion = libs.versions.androidx.compose.compiler.get()
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
        freeCompilerArgs += buildList {
            add("-P")
            add("plugin:androidx.compose.compiler.plugins.kotlin:stabilityConfigurationPath=${layout.projectDirectory.file("compose_compiler_config.conf").asFile.absolutePath}")
            val composeReportsDir: String by lazy { layout.buildDirectory.dir("compose_compiler").get().asFile.absolutePath }
            if (project.findProperty("composeCompilerReports") == "true") {
                add("-P")
                add("plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=$composeReportsDir")
            }
            if (project.findProperty("composeCompilerMetrics") == "true") {
                add("-P")
                add("plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=$composeReportsDir")
            }
        }
    }
}

dependencies {
    implementation(project(":donki"))
    implementation(libs.coroutines.android)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.window)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.paging)
    implementation(libs.androidx.paging.compose)
    implementation(libs.navigation.reimagined)

    testImplementation(libs.junit)

    debugImplementation(libs.leakcanary)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
