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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "eSalesSFA"

include(":app")

// Pure Kotlin JVM — business logic, KHÔNG phụ thuộc Android
include(":domain")

// Data layer — RepositoryImpl, mapper, datasource
include(":data")

// Core
include(":core:common")
include(":core:database")
include(":core:network")
include(":core:datastore")
include(":core:ui")
include(":core:sync")

// Features
include(":feature:auth")
