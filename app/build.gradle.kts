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
    compileSdk = 36

    defaultConfig {
        applicationId = "com.yomira.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 29
        versionName = "8.2.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "QA_CAPTURE", "false")
    }

    // Fixed, repo-committed keystore so every debug and release APK (local or CI)
    // carries the exact same cryptographic signature. This lets devices upgrade
    // in place instead of failing with "signatures do not match" and forcing an uninstall.
    signingConfigs {
        create("release") {
            storeFile = file("signing/yomira_release.keystore")
            storePassword = "yomirakeystorepass"
            keyAlias = "yomira"
            keyPassword = "yomirakeystorepass"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("qa") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            buildConfigField("boolean", "QA_CAPTURE", "true")
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

    sourceSets {
        // The debug screenshot harness composes its screens with the artwork
        // that ships in art/showcase, mounted as assets instead of being copied
        // into the module. Only that folder is bundled, and release builds never
        // see it.
        getByName("debug").assets.srcDir("../art/showcase")
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation("com.google.android.gms:play-services-auth:21.4.0")
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
    implementation(project(":feature:settings"))
    implementation(project(":feature:journey"))
    implementation(project(":domain:extension"))
    implementation(project(":domain:journey"))
    implementation(project(":domain:sync"))
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    // Extension API compatibility shims (in-process DexClassLoader linking);
    // unused at runtime until the extension runtime host lands.
    implementation(project(":core:extension-api"))
    // RoomBackupImportTarget runs its merge inside RoomDatabase.withTransaction.
    implementation(libs.androidx.room.ktx)
    implementation(project(":core:ui"))
    implementation(libs.androidx.core.ktx)
    // AppCompat 1.6+ backs the in-app language switcher below API 33 and persists the
    // chosen locale across restarts (AppLocalesMetadataHolderService autoStoreLocales).
    implementation(libs.androidx.appcompat)
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
    implementation(libs.conscrypt.android)
    // HOST-PROVIDED runtime classpath for third-party extension DEXes
    // (runtime-link-inventory.md): the in-process DexClassLoader resolves
    // these through its parent (the app classloader). Never prune them.
    implementation(libs.okhttp.brotli)
    implementation(libs.okhttp.zstd)
    implementation(libs.zstd.kmp.okio)
    implementation(libs.injekt.core)
    implementation(libs.kotlinx.serialization.json.okio)
    implementation(libs.coil.compose)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // Debug-only screenshot harness composes WebtoonReader with its own loader.
    debugImplementation(libs.coil.compose)
}
