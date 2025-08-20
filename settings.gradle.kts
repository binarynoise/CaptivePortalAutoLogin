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
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
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
        mavenLocal()
//        maven("https://jitpack.io")
//        gradlePluginPortal()
    }
}

rootProject.name = "CaptivePortalAutoLogin"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
apply(from = "modules.gradle.kts")
