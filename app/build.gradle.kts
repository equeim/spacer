plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
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
    }

    buildFeatures.compose = true
    composeOptions.kotlinCompilerExtensionVersion = libs.versions.androidx.compose.compiler.get()
    kotlinOptions.freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"
}

dependencies {
    implementation(project(":donki"))
    implementation(libs.coroutines.android)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    testImplementation(libs.junit)
    debugImplementation(libs.leakcanary)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
