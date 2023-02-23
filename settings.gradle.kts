// SPDX-FileCopyrightText: 2022 (c) Alexey Rochev
//
// SPDX-License-Identifier: MIT

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://s01.oss.sonatype.org/content/repositories/snapshots/")
    }
}
rootProject.name = "Spacer"
include(":app", ":donki", ":retrofit-provider")
