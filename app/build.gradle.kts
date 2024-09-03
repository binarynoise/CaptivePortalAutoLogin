plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.serialization") version "2.0.0"
}

android {
    namespace = "de.binarynoise.captiveportalautologin"
    
    defaultConfig {
        minSdk = 23
        //noinspection EditedTargetSdkVersion
        targetSdk = 34
        multiDexEnabled = true
    }
    
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
    
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    
    viewBinding {
        enable = true
    }
    
    buildTypes {
        create("quickRelease") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            
            isMinifyEnabled = false
            isShrinkResources = false
            versionNameSuffix = "-qr"
        }
        create("r8debug") {
            initWith(getByName("debug"))
            matchingFallbacks += "debug"
            
            isMinifyEnabled = true
            isShrinkResources = true
            versionNameSuffix = "-r8d"
        }
    }
    
    flavorDimensions += "abi"
    productFlavors {
        create("arm") {
            dimension = "abi"
            ndk.abiFilters.add("armeabi-v7a")
        }
        create("arm64") {
            dimension = "abi"
            ndk.abiFilters.add("arm64-v8a")
        }
        create("x86") {
            dimension = "abi"
            ndk.abiFilters.add("x86")
        }
        create("x86_64") {
            dimension = "abi"
            ndk.abiFilters.add("x86_64")
        }
        create("universal") {
            dimension = "abi"
            // no filters
            isDefault = true
        }
    }
    
    
    defaultConfig {
        missingDimensionStrategy("abi", "universal")
    }
    
    packaging {
        resources {
            excludes += listOf(
                "**/.idea",
                "**/play-services-*",
                "**/*.pro",
                "**/*.version",
                "META-INF/README.md", // jsoup
                "META-INF/CHANGES", // jsoup
            )
        }
        jniLibs {
            useLegacyPackaging = true // compress .so files even if they need to be extracted on-device then
        }
    }
}

dependencies {
//    implementation(project(":portalMetadata"))
    implementation(project(":logger"))
    implementation(project(":liberator"))
    implementation(project(":api:client"))
    
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("com.github.kirich1409:viewbindingpropertydelegate-noreflection:1.5.9")
    
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.0")
//    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.0")
    
    // source: https://maven.mozilla.org/maven2/org/mozilla/geckoview/geckoview/125.0.20240419144423/geckoview-125.0.20240419144423-sources.jar
    implementation("org.mozilla.geckoview:geckoview:125.0.20240419144423") {
        exclude("com.google.android.gms","play-services-fido")
    }
    
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-urlconnection:4.12.0")
    
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
}
