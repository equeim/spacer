// SPDX-FileCopyrightText: 2022-2023 Alexey Rochev
//
// SPDX-License-Identifier: MIT

import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import java.util.Locale

plugins {
    alias(libs.plugins.android.application) apply(false)
    alias(libs.plugins.android.library) apply(false)
    alias(libs.plugins.kotlin.android) apply(false)
    alias(libs.plugins.kotlin.plugin.parcelize) apply(false)
    alias(libs.plugins.kotlin.plugin.serialization) apply(false)
    alias(libs.plugins.ksp) apply(false)
    alias(libs.plugins.gradle.versions)
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

tasks.named<DependencyUpdatesTask>("dependencyUpdates") {
    val channels = VersionChannelProvider()
    val blacklist = listOf("org.jacoco" to "org.jacoco.ant")
    rejectVersionIf {
        if (blacklist.any { (group, module) -> candidate.group == group && candidate.module == module }) {
            return@rejectVersionIf true
        }
        val currentChannel = channels.getChannel(currentVersion)
        val candidateChannel = channels.getChannel(candidate.version)
        candidateChannel < currentChannel
    }
}

class VersionChannelProvider {
    enum class Channel(val keywords: List<String>) {
        Alpha(listOf("ALPHA")),
        Beta(listOf("BETA")),
        RC(listOf("RC")),
        Stable(listOf("RELEASE", "FINAL", "GA"))
    }
    private val channels = Channel.values()
    private val stableRegex = "^[0-9,.v-]+(-r)?$".toRegex()

    fun getChannel(version: String): Channel {
        val versionUppercase = version.uppercase(Locale.ROOT)
        return channels.find {
            it.keywords.any(versionUppercase::contains)
        } ?: if (stableRegex.matches(version)) {
            Channel.Stable
        } else {
            Channel.Alpha
        }
    }
}
