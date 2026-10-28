plugins {
    alias(libs.plugins.kotlin.jvm)
    // @Serializable JSON-first manifests (JsonSourceManifest).
    alias(libs.plugins.kotlin.serialization)
}

kotlin { jvmToolchain(17) }

// The curated repository snapshots are shared by Android and desktop; a fresh install
// is usable offline because these ship inside the APK/JAR as JVM resources.
tasks.processResources {
    from(rootProject.file("assets/keiyoushi")) {
        into("assets/keiyoushi")
    }
    from(rootProject.file("assets/repositories")) {
        into("assets/repositories")
    }
}

dependencies {
    api(project(":domain:extension"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
