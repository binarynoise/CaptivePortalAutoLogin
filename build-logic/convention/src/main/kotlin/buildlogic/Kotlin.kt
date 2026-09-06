package buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.provider.Provider

context(project: Project)
val DependencyHandler.commonKotlinDependencies: List<Provider<MinimalExternalModuleDependency>>
    get() = listOf(
        platform(project.libs.kotlin.bom),
        project.libs.jetbrains.annotations,
    )
