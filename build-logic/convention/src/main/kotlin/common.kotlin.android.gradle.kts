import buildlogic.commonKotlinDependencies

plugins {
    id("common.kotlin")
}

dependencies {
    for (dependency in commonKotlinDependencies) {
        add("implementation", dependency)
    }
}
