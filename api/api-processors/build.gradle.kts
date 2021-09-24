import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("kapt")
}

description = "Qalipsis compile time processors"

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.majorVersion
        javaParameters = true
    }
}

val micronautVersion: String by project

dependencies {
    compileOnly(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    implementation(project(":api:api-dsl"))
    implementation("com.squareup:javapoet:1.13.0")
    api(platform("io.micronaut:micronaut-bom:${micronautVersion}"))
    api("io.micronaut:micronaut-inject-java")
    api("io.micronaut:micronaut-validation")
    api("io.micronaut:micronaut-inject")
    api("io.micronaut:micronaut-graal")
}
