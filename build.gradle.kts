// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

import com.deezer.caupain.model.StabilityLevelPolicy
import com.deezer.caupain.plugin.DependenciesUpdateTask
import java.util.Locale

plugins {
    alias(libs.plugins.android.application) apply (false)
    alias(libs.plugins.android.library) apply (false)
    alias(libs.plugins.kotlin.android) apply (false)
    alias(libs.plugins.kotlin.plugin.parcelize) apply (false)
    alias(libs.plugins.kotlin.plugin.serialization) apply (false)
    alias(libs.plugins.ksp) apply (false)
    alias(libs.plugins.deezer.caupain)
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

tasks.withType<DependenciesUpdateTask> {
    selectIf(StabilityLevelPolicy)
}
