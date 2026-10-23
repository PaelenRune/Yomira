plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.yomira.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.yomira.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0-beta"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        // BuildConfig.VERSION_NAME feeds the in-app update checker.
        buildConfig = true
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":data:extension"))
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    implementation(libs.kotlinx.serialization.json)
    implementation(project(":domain:classifier"))
    implementation(project(":data:metadata"))
    implementation(project(":feature:home"))
    implementation(project(":feature:vault"))
    implementation(project(":feature:details"))
    implementation(project(":feature:reader"))
    implementation(project(":domain:extension"))
    implementation(project(":domain:sync"))
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:ui"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.okhttp)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
