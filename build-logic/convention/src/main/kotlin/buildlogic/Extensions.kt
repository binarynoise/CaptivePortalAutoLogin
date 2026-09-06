package buildlogic

import com.android.build.api.dsl.CompileOptions
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.*

internal val Project.libs: LibrariesForLibs
    get() = extensions.getByType()

internal val Project.androidCompileSdk: Int
    get() = libs.versions.androidCompileSdk.get().toInt()

internal val Project.javaVersion: String
    get() = libs.versions.java.get()

context(project: Project)
internal fun JavaPluginExtension.applyJavaCompatibility() {
    sourceCompatibility = JavaVersion.toVersion(project.javaVersion)
    targetCompatibility = JavaVersion.toVersion(project.javaVersion)
}

context(project: Project)
internal fun CompileOptions.applyJavaCompatibility() {
    sourceCompatibility = JavaVersion.toVersion(project.javaVersion)
    targetCompatibility = JavaVersion.toVersion(project.javaVersion)
}
