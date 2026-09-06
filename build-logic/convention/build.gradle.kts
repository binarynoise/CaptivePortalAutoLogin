import org.jetbrains.kotlin.gradle.dsl.JvmTarget

group = "buildlogic"

plugins {
    `kotlin-dsl`
}

val javaVersion = libs.versions.java.get()

java {
    sourceCompatibility = JavaVersion.toVersion(javaVersion)
    targetCompatibility = JavaVersion.toVersion(javaVersion)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(javaVersion)
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

tasks {
    validatePlugins {
        enableStricterValidation = true
        failOnWarning = true
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.android.tools.common)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.shadow.gradlePlugin)
//    compileOnly(libs.ksp.gradlePlugin)
    implementation(libs.github.api) {
        constraints {
            implementation("com.fasterxml.jackson.core:jackson-core:2.22.0") {
                because("Fix WS-2026-0003 vulnerability")
            }
        }
    }
    
    // Hack to make the libs accessor work
    // https://github.com/gradle/gradle/issues/15383
    compileOnly(files(libs::class.java.superclass.protectionDomain.codeSource.location))
}
