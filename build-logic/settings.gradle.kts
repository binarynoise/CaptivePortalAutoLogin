@file:Suppress("UnstableApiUsage")

dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        google {
            content {
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
                includeGroupAndSubgroups("androidx")
            }
        }
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
            
            // override the Kotlin version to match Gradle's embedded version
            // so `kotlin-dsl` / `embedded-kotlin` doesn't warn about version mismatch.
            version("kotlin", embeddedKotlinVersion)
            println("Using Kotlin version $embeddedKotlinVersion")
        }
    }
}
rootProject.name = "build-logic"
include(":convention")
