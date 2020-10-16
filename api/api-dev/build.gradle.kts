import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("kapt")
    `java-test-fixtures`
}

description = "Components to support Qalipsis API development"

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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${kotlinCoroutinesVersion}")
    compileOnly(platform("io.micronaut:micronaut-bom:${micronautVersion}"))
    compileOnly("org.slf4j:slf4j-api")

    testImplementation(project(":test"))
}
