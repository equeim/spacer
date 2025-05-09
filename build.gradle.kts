// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import java.util.Locale

plugins {
    alias(libs.plugins.android.application) apply (false)
    alias(libs.plugins.android.library) apply (false)
    alias(libs.plugins.kotlin.android) apply (false)
    alias(libs.plugins.kotlin.plugin.parcelize) apply (false)
    alias(libs.plugins.kotlin.plugin.serialization) apply (false)
    alias(libs.plugins.ksp) apply (false)
    alias(libs.plugins.gradle.versions)
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

tasks.named<DependencyUpdatesTask>("dependencyUpdates") {
    gradleReleaseChannel = "current"
    val channelProvider = VersionChannelProvider()
    rejectVersionIf {
        val currentChannel = channelProvider.getChannel(candidate.moduleIdentifier, currentVersion)
            ?: return@rejectVersionIf false
        val candidateChannel =
            channelProvider.getChannel(candidate.moduleIdentifier, candidate.version)
                ?: return@rejectVersionIf false
        candidateChannel < currentChannel
    }
}

class VersionChannelProvider {
    enum class Channel(private val keywords: List<String>) {
        Alpha("alpha"),
        Beta("beta"),
        RC("rc"),
        Stable("release", "final", "ga");

        constructor(vararg keywords: String) : this(keywords.asList())

        fun matches(versionLowercase: String): Boolean =
            keywords.any { versionLowercase.contains(it) }
    }

    private val channels = Channel.values()
    private val stableVersionRegex = "^[0-9.-]+$".toRegex()

    fun getChannel(dependency: ModuleIdentifier, version: String): Channel? {
        val versionLowercase = version.lowercase(Locale.ROOT)
        if (versionLowercase.matches(stableVersionRegex)) {
            return Channel.Stable
        }
        val channelFromKeyword = channels.find { it.matches(versionLowercase) }
        if (channelFromKeyword != null) return channelFromKeyword
        logger.error("Failed to determine channel for dependency $dependency with version '$version'")
        return null
    }
}
