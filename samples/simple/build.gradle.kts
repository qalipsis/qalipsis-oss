import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm")
    kotlin("kapt")
    id("com.github.johnrengelman.shadow") version "5.2.0"
}

description = "Evolue Simple Demo"

// Configure both compileKotlin and compileTestKotlin.
tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.majorVersion
        javaParameters = true
    }
}

kapt {
    generateStubs = true
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":api:api-dsl"))
    runtimeOnly(project(":runtime"))
    kapt(project(":api:api-processors"))

    implementation("org.slf4j:slf4j-api:1.7.30")
}

application {
    mainClassName = "io.evolue.runtime.Evolue"
    applicationDefaultJvmArgs = listOf("-noverify", "-XX:TieredStopAtLevel=1", "-Dcom.sun.management.jmxremote",
        "-Dmicronaut.env.deduction=false")
    this.ext["workingDir"] = projectDir
}

tasks {
    named<ShadowJar>("shadowJar") {
        mergeServiceFiles()
        archiveClassifier.set("evolue")
    }
}

tasks {
    build {
        dependsOn(shadowJar)
    }
}
