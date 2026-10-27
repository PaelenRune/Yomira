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
    }
}

rootProject.name = "Yomira"
include(
    ":app",
    ":core:model",
    ":core:database",
    ":core:ui",
    ":core:network",
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
