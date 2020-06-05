import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("kapt")
}

kapt {
    useBuildCache = false
}

description = "Evolue API Kotlin DSL"

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_11.majorVersion
}

dependencies {
    implementation(
        kotlin("stdlib")
    )
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:${properties["kotlinCoroutinesVersion"]}")
    implementation("cool.graph:cuid-java:0.1.1")

    testImplementation(project(":test"))
}