import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("kapt")
    kotlin("plugin.allopen")
    `java-test-fixtures`
}

description = "Evolue API Kotlin DSL"

kapt {
    useBuildCache = false
}

allOpen {
    annotations(
        "io.micronaut.aop.Around"
    )
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.majorVersion
        javaParameters = true
    }
}

val micronautVersion: String by project
val kotlinCoroutinesVersion: String by project

dependencies {
    implementation(
        kotlin("stdlib")
    )
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${kotlinCoroutinesVersion}")
    implementation("cool.graph:cuid-java:0.1.1")
    implementation("javax.annotation:javax.annotation-api")
    implementation(enforcedPlatform("io.micronaut:micronaut-bom:${micronautVersion}"))
    implementation("io.micronaut:micronaut-inject-java")
    implementation("io.micronaut.micrometer:micronaut-micrometer-core")

    // Required to make the scenario projects build.
    api(enforcedPlatform("io.micronaut:micronaut-bom:${micronautVersion}"))
    api("io.micronaut:micronaut-runtime")

    kapt(enforcedPlatform("io.micronaut:micronaut-bom:${micronautVersion}"))
    kapt("io.micronaut:micronaut-inject-java")

    testImplementation(project(":test"))
}
