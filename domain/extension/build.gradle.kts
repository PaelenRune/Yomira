plugins { alias(libs.plugins.kotlin.jvm) }

kotlin { jvmToolchain(17) }

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Benchmark harness parses the 100-series testset from test resources.
    testImplementation(libs.kotlinx.serialization.json)
}
