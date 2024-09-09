@file:Suppress("UnstableApiUsage")

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
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        maven("https://jitpack.io")
//        maven("https://api.xposed.info/")
        maven("https://maven.mozilla.org/maven2/") {
            content {
                includeGroupByRegex("org\\.mozilla.*")
            }
        }
    }
}

rootProject.name = "CaptivePortalAutoLogin"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
include(":app")
include(":liberator")
include(":linux")
include(":logger")
include(":fileDB")
include(":api")
include(":api:client")
include(":api:server")
include(":util:okhttp-kts")
