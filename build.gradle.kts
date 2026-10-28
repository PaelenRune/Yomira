plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
}

// Hilt 2.58 is the last release whose Gradle plugin supports AGP 8, but its
// processors ship kotlin-metadata-jvm 2.2.20, which cannot read Kotlin 2.4
// class metadata (the reader only accepts metadata up to its own version).
// Force the 2.4.10 reader onto every processor classpath (KSP and the Hilt
// javac aggregation task) so Hilt can process Kotlin 2.4.10-compiled modules.
subprojects {
    configurations.configureEach {
        resolutionStrategy.force("org.jetbrains.kotlin:kotlin-metadata-jvm:2.4.10")
    }
}
