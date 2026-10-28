pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // injekt-core (uy.kohesive.injekt) is only published via JitPack; the
        // extension DEXes link against it at runtime (HOST-PROVIDED table).
        maven("https://jitpack.io")
    }
}

rootProject.name = "Yomira"
include(
    ":app",
    ":core:model",
    ":core:database",
    ":core:ui",
    ":core:network",
    ":core:extension-api",
    ":domain:classifier",
    ":data:metadata",
)
include(":domain:extension", ":data:extension")
include(":domain:sync", ":data:sync")
include(":feature:home", ":feature:vault", ":feature:details")
include(":feature:reader")
include(":feature:settings")
// Windows/Desktop is postponed; Android Phone/Tablet is the only active app target.
// Keep the independent JVM desktop module on disk for a future phase.
// include(":desktop")
include(":domain:journey", ":feature:journey")
