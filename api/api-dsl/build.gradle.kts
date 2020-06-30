import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("kapt")
    kotlin("plugin.allopen")
}

description = "Evolue API Kotlin DSL"

kapt {
    useBuildCache = false
}

allOpen{
    annotations(
        "io.micronaut.aop.Around"
    )
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_11.majorVersion
}

dependencies {
    implementation(
        kotlin("stdlib")
    )
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:${properties["kotlinCoroutinesVersion"]}")
    implementation("cool.graph:cuid-java:0.1.1")
    implementation("javax.annotation:javax.annotation-api")
    implementation(enforcedPlatform("io.micronaut:micronaut-bom:${properties["micronautVersion"]}"))
    implementation("io.micronaut:micronaut-inject-java")

    kapt(enforcedPlatform("io.micronaut:micronaut-bom:${properties["micronautVersion"]}"))
    kapt("io.micronaut:micronaut-inject-java")

    testImplementation(project(":test"))
}
