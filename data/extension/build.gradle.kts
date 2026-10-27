plugins {
    alias(libs.plugins.kotlin.jvm)
    // @Serializable JSON-first manifests (JsonSourceManifest).
    alias(libs.plugins.kotlin.serialization)
}

kotlin { jvmToolchain(17) }

// The clean official catalog is shared by Android and desktop; no network bootstrap required.
tasks.processResources {
    from(rootProject.file("assets/keiyoushi")) {
        into("assets/keiyoushi")
    }
}

dependencies {
    api(project(":domain:extension"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
