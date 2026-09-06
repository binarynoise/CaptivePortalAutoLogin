import buildlogic.commonKotlinDependencies

plugins {
    id("common.kotlin")
}

dependencies {
    commonKotlinDependencies.forEach { add("implementation", it) }
}
