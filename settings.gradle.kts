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
