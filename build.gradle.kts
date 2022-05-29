import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import java.util.*

plugins {
    alias(libs.plugins.android.application) apply(false)
    alias(libs.plugins.android.library) apply(false)
    alias(libs.plugins.kotlin.android) apply(false)
    alias(libs.plugins.kotlin.plugin.parcelize) apply(false)
    alias(libs.plugins.kotlin.plugin.serialization) apply(false)
    alias(libs.plugins.gradle.versions)
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}

tasks.named<DependencyUpdatesTask>("dependencyUpdates") {
    val checker = DependencyVersionChecker()
    rejectVersionIf {
        checker.isNonStable(candidate.version)
    }
}

class DependencyVersionChecker {
    private val stableKeywords = listOf("RELEASE", "FINAL", "GA")
    private val regex = "^[0-9,.v-]+(-r)?$".toRegex()

    fun isNonStable(version: String): Boolean {
        val versionUppercase = version.toUpperCase(Locale.ROOT)
        val hasStableKeyword = stableKeywords.any(versionUppercase::contains)
        val isStable = hasStableKeyword || regex.matches(version)
        return isStable.not()
    }
}
