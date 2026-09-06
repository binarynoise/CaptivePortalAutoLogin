import buildlogic.libs

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("common.jvm")
    id("common.kotlin")
}

dependencies {
    implementation(platform(libs.kotlin.bom))
    implementation(libs.jetbrains.annotations)
}
