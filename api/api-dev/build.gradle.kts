import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("kapt")
    `java-test-fixtures`
}

description = "Components to support Evolue API development"

kapt {
    useBuildCache = false
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.majorVersion
        javaParameters = true
        freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn,kotlinx.coroutines.ExperimentalCoroutinesApi"
    }
}

val micronautVersion: String by project
val kotlinCoroutinesVersion: String by project

dependencies {
    implementation(
            kotlin("stdlib")
    )
    implementation("org.slf4j:slf4j-api:1.7.30")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${kotlinCoroutinesVersion}")

    testImplementation(project(":test"))
}
