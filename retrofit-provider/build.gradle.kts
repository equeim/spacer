// SPDX-FileCopyrightText: 2022 (c) Alexey Rochev
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
        targetSdk = libs.versions.sdk.platform.target.get().toInt()
        consumerProguardFiles.add(file("consumer-rules.pro"))
    }
    kotlinOptions.freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"
}

dependencies {
    api(libs.okhttp)
    implementation(libs.okhttp.logging)
    api(libs.retrofit)
    api(libs.serialization.json)
    implementation(libs.serialization.json.okio)
}
