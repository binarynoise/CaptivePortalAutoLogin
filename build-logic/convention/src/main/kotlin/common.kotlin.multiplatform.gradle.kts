import buildlogic.commonKotlinDependencies

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("common.kotlin")
}

dependencies {
    commonKotlinDependencies.forEach { commonMainImplementation(it) }
}
