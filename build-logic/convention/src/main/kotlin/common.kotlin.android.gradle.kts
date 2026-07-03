import buildlogic.commonKotlinDependencies
import gradle.kotlin.dsl.accessors._3dfc63a612bb7993dc38df28b51798c6.implementation

plugins {
    id("common.kotlin")
}

dependencies {
    for (dependency in commonKotlinDependencies) {
        implementation(dependency)
    }
}
