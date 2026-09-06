import buildlogic.commonKotlinDependencies

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("common.jvm")
    id("common.kotlin")
}

dependencies {
    commonKotlinDependencies.forEach { implementation(it) }
}
