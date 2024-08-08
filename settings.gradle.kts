@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
//        maven("https://api.xposed.info/")
        maven("https://maven.mozilla.org/maven2/")
    }
}

rootProject.name = "CaptivePortalAutoLogin"

include(":app")
include(":liberator")
include(":linux")
include(":logger")
include(":fileDB")
include(":api")
include(":api:client")
include(":api:server")
include(":util:okhttp-kts")
