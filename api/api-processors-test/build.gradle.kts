import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("kapt")
}

description = "Qalipsis - Tests for the compile time processors"

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_11.majorVersion
    kotlinOptions.javaParameters = true
}

val micronautVersion: String by project

dependencies {
    implementation(
        kotlin("stdlib")
    )

    kaptTest(platform("io.micronaut:micronaut-bom:${micronautVersion}"))
    kaptTest(project(":api:api-processors"))
    kaptTest("javax.annotation:javax.annotation-api")
    kaptTest("io.micronaut:micronaut-runtime")

    testImplementation(platform("io.micronaut:micronaut-bom:${micronautVersion}"))
    testImplementation(project(":api:api-processors"))
    testImplementation(project(":test"))
    testImplementation(project(":api:api-dsl"))
    testImplementation("javax.annotation:javax.annotation-api")
    testImplementation("io.micronaut:micronaut-runtime")
    testImplementation("io.micronaut:micronaut-inject")
}