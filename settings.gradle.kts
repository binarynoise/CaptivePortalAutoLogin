@file:Suppress("UnstableApiUsage")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
                includeGroupAndSubgroups("androidx")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        google {
            content {
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
                includeGroupAndSubgroups("androidx")
            }
        }
        maven("https://api.xposed.info/") {
            content {
                includeGroup("de.robv.android.xposed")
            }
        }
        maven("https://maven.mozilla.org/maven2/") {
            content {
                includeGroupAndSubgroups("org.mozilla")
            }
        }
        maven("https://storage.googleapis.com/r8-releases/raw") {
            content {
                includeGroup("com.android.tools")
            }
        }
        mavenCentral()
//        maven("https://jitpack.io")
//        gradlePluginPortal()
    }
}

rootProject.name = "CaptivePortalAutoLogin"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
include(":api")
include(":api:client")
include(":api:server")
include(":app")
include(":fileDB")
include(":liberator")
include(":linux")
include(":logger")
include(":portalProxy")
include(":util:okhttp-kts")
