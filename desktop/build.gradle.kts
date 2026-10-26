import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
    // The desktop target keeps Compose Multiplatform's `jvmMain`/`jvmTest` convention.
    sourceSets {
        main {
            kotlin.srcDir("src/jvmMain/kotlin")
        }
        test {
            kotlin.srcDir("src/jvmTest/kotlin")
        }
    }
}

dependencies {
    // JetBrains Compose Multiplatform for the current OS (Windows x64 in CI).
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    implementation(project(":core:model"))
    implementation(project(":core:network"))
    implementation(project(":data:metadata"))
    implementation(project(":data:sync"))

    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.jsoup)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}

tasks.withType<Test>().configureEach {
    useJUnit()
    // Shortcut/layout tests load Compose UI value classes; no display is needed.
    systemProperty("java.awt.headless", "true")
}

compose.desktop {
    application {
        mainClass = "com.yomira.desktop.MainKt"

        nativeDistributions {
            // Windows-first independent desktop app: an EXE launcher and an MSI installer.
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "Yomira"
            packageVersion = "0.4.1"
            description = "Yomira — OLED manga, manhwa and western comics reader"
            copyright = "© 2026 Yomira"
            vendor = "Yomira"

            windows {
                menuGroup = "Yomira"
                shortcut = true
                perUserInstall = true
                dirChooser = true
                // Stable identity so an upgrade replaces the previous install instead
                // of creating a second side-by-side copy.
                upgradeUuid = "D6A9E3A0-9F7B-4C2E-8D51-6B0C1E2F7A44"
                iconFile.set(project.file("icons/yomira.ico"))
            }

            modules(
                "java.naming",
                "java.sql",
                "jdk.unsupported",
            )
        }
    }
}
