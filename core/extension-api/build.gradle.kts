// API compatibility shims for third-party extension DEX linking (task V811-RT-A).
// The classes in this module live under eu.kanade.tachiyomi.* package names on
// purpose: in-process DexClassLoader runtimes resolve extension references
// against those exact FQCNs. Signatures are derived from the DEX inventory in
// src/test/resources/inventory/ (dexdump of the MangaFire, Weeb Central and
// Toonily extension APKs) and mirror keiyoushi/extensions-lib v1.6
// (commit 42255ee5fa) plus the Mihon runtime behaviors extensions expect.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.yomira.core.extension.api"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Release builds of the app minify; renaming or stripping these classes
    // would break DEX linking, so keep the shim surface untouched.
    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Public shim surface exposes OkHttp and kotlinx.serialization types.
    api(libs.okhttp)
    api(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    // ConfigurableSource.setupPreferenceScreen(PreferenceScreen)
    implementation("androidx.preference:preference-ktx:1.2.1")

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
