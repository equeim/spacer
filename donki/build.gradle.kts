// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
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
    namespace = "org.equeim.spacer.donki"
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
    testOptions.unitTests.all {
        it.systemProperties("robolectric.logging" to "stdout")
    }
}

ksp {
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(project(":retrofit-provider"))

    api(libs.okhttp)

    implementation(libs.coroutines.core)

    implementation(libs.androidx.core)
    implementation(libs.androidx.paging)

    implementation(platform(libs.androidx.compose.bom))
    compileOnly(libs.androidx.compose.runtime)

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
