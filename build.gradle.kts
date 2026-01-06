plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.shadow) apply false
}

buildscript {
    repositories {
        maven("https://storage.googleapis.com/r8-releases/raw") {
            content {
                includeGroup("com.android.tools")
            }
        }
    }
    dependencies {
        classpath(libs.r8)
    }
}
