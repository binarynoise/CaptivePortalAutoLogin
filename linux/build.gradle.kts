import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    alias(libs.plugins.application)
    alias(libs.plugins.buildlogic.kotlin.jvm)
    alias(libs.plugins.shadow)
}

val r8: Configuration by configurations.creating
dependencies {
    implementation(projects.logger)
    implementation(projects.liberator)
    implementation(projects.api.client)
    compileOnly(libs.okhttp)
    //noinspection GradleDynamicVersion
    r8("com.android.tools:r8:+")
}

val mainClass = "de.binarynoise.captiveportalautologin.MainKt"
application.mainClass = mainClass
tasks.withType<Jar> {
    manifest {
        attributes(mapOf("Main-Class" to mainClass))
    }
}

tasks.withType<ShadowJar> {
    archiveClassifier.set("shadow")
    mergeServiceFiles()
    minimize()
    exclude("**/*.kotlin_*")
}

tasks {
    assemble {
        dependsOn(shadowJarMinified)
    }
}

val shadowJarMinified = tasks.register<JavaExec>("shadowJarMinified") {
    dependsOn(configurations.runtimeClasspath)
    
    val proguardRules = rootProject.file("proguard-rules.pro")
    inputs.files(tasks.shadowJar.get().outputs.files, proguardRules)
    
    val r8File = layout.buildDirectory.file("libs/${base.archivesName.get()}-shadow-minified.jar").get().asFile
    outputs.file(r8File)
    
    classpath(r8)
    
    mainClass.set("com.android.tools.r8.R8")
    val javaHome = File(ProcessHandle.current().info().command().get()).parentFile.parentFile.canonicalPath
    val args = mutableListOf(
        //"--debug",
        "--classfile",
        "--output",
        r8File.toString(),
        "--pg-conf",
        proguardRules.toString(),
        "--lib",
        javaHome,
    )
    args.add(tasks.shadowJar.get().outputs.files.joinToString(" "))
    
    this.args = args
    
    doFirst {
        val javaHomeVersion = JavaVersion.current()
        check(javaHomeVersion.isCompatibleWith(javaVersion)) {
            "Incompatible Java Versions: compile-target $javaVersion, r8 runtime $javaHomeVersion (needs to be as new or newer)"
        }
        
        check(proguardRules.exists()) { "$proguardRules doesn't exist" }
    }
}
