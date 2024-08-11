@file:Suppress("UnstableApiUsage")

import java.io.FileInputStream
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.gradle.BaseExtension
import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.kohsuke.github.GHReleaseBuilder
import org.kohsuke.github.GitHub

val javaVersion = JavaVersion.VERSION_17

buildscript {
    dependencies {
        classpath("com.android.tools.build:gradle:8.2.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.0")
        classpath("org.kohsuke:github-api:1.321")
    }
    
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}


plugins {
    idea
}

idea {
    module {
        languageLevel = IdeaLanguageLevel(javaVersion)
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}
allprojects {
    apply<IdeaPlugin>()
    idea {
        module {
            languageLevel = IdeaLanguageLevel(javaVersion)
            isDownloadSources = true
            isDownloadJavadoc = true
        }
    }
}

allprojects {
    // apply in afterEvaluate because other plugins need to be loaded first
    afterEvaluate {
        
        val isAndroid = plugins.hasPlugin("com.android.application")
        val isAndroidLib = plugins.hasPlugin("com.android.library")
//        val isJavaLib = plugins.hasPlugin("java-library")
        val isKotlinLib = plugins.hasPlugin("org.jetbrains.kotlin.jvm")
        val isKotlinAndroid = plugins.hasPlugin("org.jetbrains.kotlin.android")

//        if (isAndroid) println("android")
//        if (isAndroidLib) println("android lib")
//        if (isJavaLib) println("java lib")
//        if (isKotlinLib) println("kotlin lib")
//        if (isKotlinAndroid) println("kotlin android")
        
        val date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val commitCount = getCommitCount()
        val commitHash = getCommitHash()
        val workingTreeClean = getWorkingTreeClean()
        val allCommitsPushed = getAllCommitsPushed()
        
        if (isAndroid || isAndroidLib) {
            val android = extensions.getByType<BaseExtension>()
            with(android) {
                val propsFile = rootProject.file("keystore.properties")
                if (propsFile.exists()) {
                    val props = Properties()
                    props.load(FileInputStream(propsFile))
                    
                    signingConfigs {
                        maybeCreate("release").apply {
                            storeFile = storeFile ?: rootProject.file(props["storeFile"].toString())
                            
                            check(storeFile != null && storeFile!!.exists()) { "keystore does not exist" }
                            
                            storePassword = storePassword ?: props["storePassword"].toString()
                            keyAlias = keyAlias ?: props["keyAlias"].toString()
                            keyPassword = keyPassword ?: props["keyPassword"].toString()
                            
                            maybeCreate("debug").let { debug ->
                                debug.storeFile = storeFile
                                debug.storePassword = storePassword
                                debug.keyAlias = keyAlias
                                debug.keyPassword = keyPassword
                            }
                        }
                    }
                    defaultConfig {
                        signingConfig = signingConfigs.getByName("release")
                    }
                } else {
                    defaultConfig {
                        signingConfig = signingConfigs.getByName("debug")
                    }
                }
                
                defaultConfig {
                    versionCode = commitCount
                    versionName = "$commitCount${if (workingTreeClean) "-" else "+"}$commitHash-$date"
                    
                    if (isAndroidLib) {
//                        proguardFiles("../proguard-rules.pro", "proguard-rules.pro")
                        consumerProguardFiles("consumer-rules.pro")
                    } else {
                        proguardFiles(getDefaultProguardFile("proguard-android.txt"), "../proguard-rules.pro")
                    }
                }
                
                buildTypes {
                    getByName("release") {
                        isMinifyEnabled = !isAndroidLib
                        if (isAndroid) {
                            isShrinkResources = true
                        }
                        
                        if (!isMinifyEnabled) isShrinkResources = false
                    }
                    getByName("debug") {
                        isMinifyEnabled = false
                        isShrinkResources = false
                        versionNameSuffix = "-dev"
                    }
                }
                
                compileOptions {
                    sourceCompatibility = javaVersion
                    targetCompatibility = javaVersion
                }
            }
        }
        
        if (isAndroid) {
            val common = extensions.getByType<ApplicationExtension>()
            with(common) {
                compileSdk = 34
                
                dependenciesInfo {
                    includeInApk = false
                    includeInBundle = false
                }
                
                buildFeatures {
                    buildConfig = true
                }
                
                lint {
                    disable += "DiscouragedApi"
                    disable += "ExpiredTargedSdkVersion"
                    disable += "OldTargetApi"
                    disable += "MissingApplicationIcon"
                    disable += "UnusedAttribute"
                }
                
                packaging {
                    resources.excludes += listOf(
                        "**/*.kotlin_builtins",
                        "**/*.kotlin_metadata",
                        "**/*.kotlin_module",
                        "kotlin-tooling-metadata.json",
                    )
                }
            }
        }
        
        if (isAndroidLib) {
            val library = extensions.getByType<LibraryExtension>()
            with(library) {
                compileSdk = 34
            }
        }
        
        if (isAndroid) {
            val android = extensions.getByType<BaseExtension>()
            
            tasks.register("createGithubRelease") {
                var logString: String? = null
                val properties = Properties()
                val file = rootProject.file("local.properties")
                if (file.exists()) {
                    properties.load(file.inputStream())
                }
                
                val repo = properties["github_repo"]
                val token = properties["github_api_key"]
                
                if (workingTreeClean && allCommitsPushed) {
                    dependsOn("assembleRelease")
                }
                
                doFirst {
                    check(repo != null && repo is String) { "github_repo not provided in local.properties" }
                    check(token != null && token is String) { "github_api_key not provided in local.properties" }
                    
                    check(workingTreeClean) { "Commit all changes before creating release" }
                    check(allCommitsPushed) { "Push to remote before creating release" }
                    
                    val packageRelease = project.tasks.getByName<DefaultTask>("packageRelease")
                    
                    val outputs = packageRelease.outputs.files
                    val apks = outputs.filter { it.isDirectory }.flatMap { it.listFiles { file -> file.extension == "apk" }!!.toList() }
                    
                    val github = GitHub.connectUsingOAuth(token)
                    val repository = github.getRepository(repo)
                    
                    val tagName = "${android.namespace}-v$commitCount"
                    val name = "${project.name}-v$commitCount"
                    
                    if (repository.getReleaseByTagName(tagName) != null) {
                        logString = "Release $name already exists"
                        return@doFirst
                    }
                    
                    val release = repository.createRelease(tagName).name(name).draft(true).makeLatest(GHReleaseBuilder.MakeLatest.FALSE).create()
                    
                    apks.forEach {
                        release.uploadAsset("${project.name}-v$commitCount.apk", it.inputStream(), "application/vnd.android.package-archive")
                    }
                    
                    logString = "Created release ${release.name}: ${release.htmlUrl}"
                }
                
                @Suppress("DEPRECATION") gradle.buildFinished {
                    if (logString != null) {
                        println(logString)
                    }
                }
            }
        }
        
        tasks.withType<KotlinCompile> {
            compilerOptions.jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
        }
        
        tasks.withType<JavaCompile> {
            sourceCompatibility = javaVersion.toString()
            targetCompatibility = javaVersion.toString()
        }
        
        if (isAndroid || isAndroidLib) {
            dependencies {
//                add("compileOnly", "de.robv.android.xposed:api:82")
                add("implementation", "androidx.annotation:annotation:1.8.1")
            }
        }
        
        if (isKotlinLib || isKotlinAndroid) {
            dependencies {
                add("implementation", "org.jetbrains:annotations:24.1.0")
                add("implementation", "org.jetbrains.kotlin:kotlin-bom")
            }
        }
    }
}

tasks.create("clearAllDraftReleases") {
    doFirst {
        val file = rootProject.file("local.properties")
        val properties = Properties()
        properties.load(file.inputStream())
        
        val repo = properties["github_repo"]
        val token = properties["github_api_key"]
        
        check(repo != null && repo is String) { "github_repo not provided in local.properties" }
        check(token != null && token is String) { "github_api_key not provided in local.properties" }
        
        val github = GitHub.connectUsingOAuth(token)
        
        github.getRepository(repo).listReleases().filter { it.isDraft }.forEach { release ->
            val name = release.name
            release.delete()
            println("Deleted release $name")
        }
    }
}

//<editor-fold desc="Git">
fun Project.getCommitCountExec() = providers.exec {
    executable("git")
    args("rev-list", "--count", "HEAD")
    args(projectDir.absolutePath)
    args(rootProject.file("gradle").absolutePath)
    
    rootProject.projectDir.listFiles { file -> file.isFile && file.extension != "md" }!!.forEach { args(it.absolutePath) }
    
    extensions.findByType<BaseExtension>()?.let {
        val metadata = rootProject.file("metadata").resolve(it.namespace!!)
        if (metadata.exists()) {
            args(metadata)
        }
    }
}

fun Project.getCommitHashExec() = providers.exec {
    executable("git")
    args("rev-parse", "--short", "HEAD")
}

fun Project.getWorkingTreeCleanExec() = providers.exec {
    executable("git")
    args("diff", "--quiet", "--exit-code", rootDir.absolutePath)
    isIgnoreExitValue = true
}

fun Project.getAllCommitsPushedExec(): ExecOutput {
    providers.exec {
        executable("git")
        args("fetch")
        isIgnoreExitValue = true
    }
    
    return providers.exec {
        executable("git")
        args("diff", "--quiet", "--exit-code", "origin/main..main")
        isIgnoreExitValue = true
    }
}
fun Project.getCommitCount() = try { getCommitCountExec().standardOutput.asText.get().trim().toInt() } catch (e: Exception) { 0 }

fun Project.getCommitHash() = try { getCommitHashExec().standardOutput.asText.get().trim() } catch (e: Exception) { "" }

fun Project.getWorkingTreeClean() = getWorkingTreeCleanExec().result.orNull?.exitValue == 0

fun Project.getAllCommitsPushed() = getAllCommitsPushedExec().result.orNull?.exitValue == 0
//</editor-fold>
