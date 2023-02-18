// SPDX-FileCopyrightText: 2022 (c) Alexey Rochev
//
// SPDX-License-Identifier: MIT

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.plugin.parcelize)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "org.equeim.spacer.donki"
    compileSdk = libs.versions.sdk.platform.compile.get().toInt()
    defaultConfig {
        minSdk = libs.versions.sdk.platform.min.get().toInt()
        consumerProguardFiles.add(file("consumer-rules.pro"))
    }
    kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
}

ksp.arg("room.incremental", "true")

// Needed for ksp tasks
tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
}

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
    testImplementation(libs.mockk.agent.jvm)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.robolectric)
}
