import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
}

description = "Evolue API - Common module"

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_11.majorVersion
}

dependencies {
    implementation(kotlin("stdlib"))
    api(project(":api:api-specifications"))
    api("org.slf4j:slf4j-api:1.7.30")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:${properties["kotlinCoroutinesVersion"]}")
    api("io.micrometer:micrometer-core:1.4.1")

    implementation("com.google.guava:guava:28.2-jre")

    testImplementation(project(":test"))
}