plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
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
            keepDebugSymbols += "**/*.so"
        }
    }
}

dependencies {
    implementation(projects.logger)
    implementation(projects.liberator)
    implementation(projects.api.client)
    
    implementation(libs.activity.ktx)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.preference.ktx)
    implementation(libs.viewbindingpropertydelegate.noreflection)
    
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    
    // source: https://maven.mozilla.org/maven2/org/mozilla/geckoview/geckoview/125.0.20240419144423/geckoview-125.0.20240419144423-sources.jar
    implementation(libs.geckoview) {
        exclude("com.google.android.gms", "play-services-fido")
    }
    
    implementation(libs.okhttp)
    implementation(libs.okhttp.urlconnection)
    
    compileOnly(libs.xposed.api)
    implementation(libs.hiddenapibypass)
    
    debugImplementation(libs.leakcanary.android)
}
