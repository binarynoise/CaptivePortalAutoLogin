import java.io.FileInputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Properties
import kotlin.collections.plusAssign
import buildlogic.bloat
import buildlogic.git.getCommitCount
import buildlogic.git.getCommitHash
import buildlogic.git.getWorkingTreeClean

plugins {
    id("com.android.application")
    id("common.android")
}

android {
    val propsFile = rootProject.projectDir.resolve("keystore.properties")
    if (!propsFile.exists()) {
        defaultConfig {
            signingConfig = signingConfigs.getByName("debug")
        }
    } else {
        val props = Properties()
        props.load(FileInputStream(propsFile))
        
        signingConfigs {
            maybeCreate("release").apply {
                storeFile = storeFile ?: rootProject.projectDir.resolve(props["storeFile"].toString())
                
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
    }
    
    val commitCount = project.getCommitCount()
    val commitHash = project.getCommitHash()
    val workingTreeClean = project.getWorkingTreeClean()
    val date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
    
    defaultConfig {
        versionCode = commitCount
        versionName = "$commitCount${if (workingTreeClean) "-" else "+"}$commitHash-$date"
        
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), rootProject.file("proguard-rules.pro"))
    }
    
    buildTypes {
        getByName("debug") {
            versionNameSuffix = "-dev"
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
        }
    }
    
    buildFeatures {
        buildConfig = true
    }
    
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    
    packaging {
        resources.excludes += bloat
    }
}
