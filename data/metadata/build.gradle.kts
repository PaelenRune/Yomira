plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin { jvmToolchain(17) }

// Pure JVM on purpose: the metadata waterfall is shared by Android and the
// independent Compose Desktop target. Android-only persistence lives in
// :core:database (RoomMetadataCache), so this module never pulls in Android.
dependencies {
    api(project(":core:network"))
    api(project(":domain:classifier"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
