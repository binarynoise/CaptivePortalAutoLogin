package buildlogic.git

import kotlin.text.trim
import com.android.build.api.dsl.CommonExtension
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.kotlin.dsl.*
import org.gradle.process.ExecResult

private fun Project.ensureInGitRepository() {
    if (rootDir.resolve(".git").exists().not()) {
        throw InvalidUserDataException("Not in a git repository")
    }
}

fun Project.getAllCommitHashes(): List<String> {
    ensureInGitRepository()
    return providers.exec {
        executable("git")
        args("rev-list", "--all")
    }.standardOutput.asText.get().lines().filter { line -> line.isNotBlank() }
}

fun Project.getCommitInfos(): Map<String, Long> {
    ensureInGitRepository()
    return providers.exec {
        executable("git")
        args("log", "--pretty=format:%H,%ct", "--all", "--reverse")
    }.standardOutput.asText.get()
        .lines()
        .filter { it.isNotBlank() }
        .map { it.split(",") }
        .associate { it[0] to it[1].toLong() }
}

fun Project.getPortalLiberatorCommitterDates(): Map<String, Long> {
    ensureInGitRepository()
    val portalFiles = rootDir.resolve("liberator/src/main/kotlin/de/binarynoise/liberator/portals/").listFiles()!!
    
    return portalFiles.associate { file ->
        val gitLogExec = providers.exec {
            executable("git")
            args("log", "-1", "--pretty=%ct", "--", file.absolutePath)
        }
        file.nameWithoutExtension to gitLogExec.standardOutput.asText.get().trim().toLong()
    }
}

fun Project.getCommitCount(): Int {
    ensureInGitRepository()
    return providers.exec {
        executable("git")
        args("rev-list", "--count", "HEAD")
        args("--")
        args(projectDir)
        args(rootProject.file("gradle"))
        args(rootProject.file("build-logic"))
        
        args(rootProject.file("build.gradle.kts"))
        args(rootProject.file("settings.gradle.kts"))
        args(rootProject.file("gradle.properties"))
        args(rootProject.file("proguard-rules.pro"))
        
        args(rootProject.file("gradlew"))
        args(rootProject.file("gradlew.bat"))
        
        extensions.getByType<CommonExtension>().namespace?.let {
            val metadata = rootProject.file("metadata").resolve(it)
            if (metadata.exists()) {
                args(metadata)
            }
        }
    }.standardOutput.asText.get().trim().toInt()
}

fun Project.getCommitHash(): String {
    ensureInGitRepository()
    return providers.exec {
        executable("git")
        args("rev-parse", "--short", "HEAD")
    }.standardOutput.asText.get().trim()
}

fun Project.getWorkingTreeClean(): Boolean {
    ensureInGitRepository()
    val gitStatus = providers.exec {
        executable("git")
        args("status", "--porcelain", rootDir.absolutePath)
        isIgnoreExitValue = true
    }
    return gitStatus.result.get().isSuccess() && gitStatus.standardOutput.asText.get().isBlank()
}

fun Project.getAllCommitsPushed(): Boolean {
    ensureInGitRepository()
    providers.exec {
        executable("git")
        args("fetch")
        isIgnoreExitValue = true
    }.result.get()
    val diffExec = providers.exec {
        executable("git")
        args("diff", "--quiet", "--exit-code", "origin/main..main")
        isIgnoreExitValue = true
    }
    return diffExec.result.get().isSuccess()
}

fun ExecResult.isSuccess() = exitValue == 0
