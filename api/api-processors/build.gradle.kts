import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("kapt")
}

description = "Evolue compile time processors"

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_11.majorVersion
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    implementation(project(":api:api-dsl"))
    api(enforcedPlatform("io.micronaut:micronaut-bom:${properties["micronautVersion"]}"))
    api("io.micronaut:micronaut-inject-java")
    api("io.micronaut:micronaut-validation")
}
