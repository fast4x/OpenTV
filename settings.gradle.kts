pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "OpenTV"

// :app     -> applicazione Android (telefono + Android TV)
// :core    -> logica condivisa Kotlin Multiplatform (Android + JVM desktop)
// :desktop -> applicazione desktop Windows/Linux/macOS (Compose Multiplatform)
include(":app")
include(":core")
include(":desktop")
