import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("kapt")
}

description = "Evolue - Tests for the compile time processors"

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_11.majorVersion
}

dependencies {
    implementation(
        kotlin("stdlib")
    )

    testImplementation(project(":api:api-dsl"))
    kaptTest(project(":api:api-processors"))

    testImplementation(project(":api:api-processors"))
    testImplementation(project(":test"))
}