// SPDX-FileCopyrightText: 2022 (c) Alexey Rochev
//
// SPDX-License-Identifier: MIT

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.plugin.parcelize)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.ksp)
}

android {
    compileSdk = libs.versions.sdk.platform.compile.get().toInt()
    defaultConfig {
        minSdk = libs.versions.sdk.platform.min.get().toInt()
        targetSdk = libs.versions.sdk.platform.target.get().toInt()
        consumerProguardFiles.add(file("consumer-rules.pro"))
    }
    kotlinOptions.freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"
}

ksp.arg("room.incremental", "true")

dependencies {
    implementation(project(":retrofit-provider"))

    implementation(libs.coroutines.core)

    implementation(libs.androidx.core)
    implementation(libs.androidx.paging)

    implementation(libs.androidx.room)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.androidx.test.core)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.robolectric)
}
