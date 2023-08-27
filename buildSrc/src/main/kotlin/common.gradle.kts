import java.io.FileInputStream
import java.util.*
import com.android.build.gradle.AppExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.gradle.kotlin.dsl.*

val isAndroid = plugins.hasPlugin("com.android.application")
val isAndroidLib = plugins.hasPlugin("com.android.library")
val isJavaLib = plugins.hasPlugin("java-library")
val isKotlinLib = plugins.hasPlugin("org.jetbrains.kotlin.jvm")
val isKotlinAndroid = plugins.hasPlugin("org.jetbrains.kotlin.android")

if (isAndroid) println("android")
if (isAndroidLib) println("android lib")
if (isJavaLib) println("java lib")
if (isKotlinLib) println("kotlin lib")
if (isKotlinAndroid) println("kotlin android")

val javaVersion = JavaVersion.VERSION_17
val javaVersionInt = javaVersion.majorVersion.toInt()

if (isAndroid || isAndroidLib) {
    val android = extensions.getByType<AppExtension>()
    with(android) {
        val propsFile = rootProject.file("keystore.properties")
        if (propsFile.exists()) {
            val props = Properties()
            props.load(FileInputStream(propsFile))
            
            signingConfigs {
                maybeCreate("release").apply {
                    storeFile = file(props["storeFile"].toString())
                    
                    check(storeFile != null && storeFile!!.exists()) { "keystore does not exist" }
                    
                    storePassword = props["storePassword"].toString()
                    keyAlias = props["keyAlias"].toString()
                    keyPassword = props["keyPassword"].toString()
                }
            }
            defaultConfig {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        
        buildTypes {
            getByName("release") {
                isMinifyEnabled = true
                multiDexEnabled = false
                
                if (isAndroid) {
                    isShrinkResources = true
                }
            }
            getByName("debug") {
                isMinifyEnabled = false
                isShrinkResources = false
                multiDexEnabled = false
                versionNameSuffix = "-dev"
            }
        }
        
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
}


if (isKotlinLib) {
    val kotlin = extensions.getByType<KotlinProjectExtension>()
    with(kotlin) {
        jvmToolchain(javaVersionInt)
    }
}

if (isAndroid || isAndroidLib) {
    dependencies {
        add("implementation", "androidx.annotation:annotation:1.6.0")
        add("compileOnly", "de.robv.android.xposed:api:82")
    }
}

System.out.println("applied common on " + project)
