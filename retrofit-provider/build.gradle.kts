// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "org.equeim.spacer.retrofit"
    compileSdk = libs.versions.sdk.platform.compile.get().toInt()
    defaultConfig {
        minSdk = libs.versions.sdk.platform.min.get().toInt()
        consumerProguardFiles.add(file("consumer-rules.pro"))
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions.jvmTarget = JavaVersion.VERSION_11.toString()
}

dependencies {
    api(libs.okhttp)
    implementation(libs.okhttp.logging)
    api(libs.retrofit)
    api(libs.serialization.json)
    implementation(libs.serialization.json.okio)
}
