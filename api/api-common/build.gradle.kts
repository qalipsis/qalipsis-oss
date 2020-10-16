import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("kapt")
    kotlin("plugin.allopen")
    `java-test-fixtures`
}

description = "Qalipsis API - Common module"

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.majorVersion
        javaParameters = true
    }
}

allOpen {
    annotations(
        "io.micronaut.aop.Around"
    )
}

val micronautVersion: String by project
val kotlinCoroutinesVersion: String by project

dependencies {
    compileOnly(platform("io.micronaut:micronaut-bom:${micronautVersion}"))
    compileOnly("org.graalvm.nativeimage:svm")

    implementation(kotlin("stdlib"))
    api(project(":api:api-dev"))
    api(project(":api:api-dsl"))
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:${kotlinCoroutinesVersion}")
    // Libraries relevant for the development of plugins.
    api(platform("io.micronaut:micronaut-bom:${micronautVersion}"))
    api("io.micronaut:micronaut-inject-java")
    api("io.micronaut.micrometer:micronaut-micrometer-core")
    api("javax.annotation:javax.annotation-api")
    api("io.micronaut:micronaut-validation")
    api("cool.graph:cuid-java:0.1.1")

    kapt(platform("io.micronaut:micronaut-bom:${micronautVersion}"))
    kapt("io.micronaut:micronaut-inject-java")
    kapt("io.micronaut:micronaut-validation")
    kapt("io.micronaut:micronaut-graal")

    implementation(platform("io.micronaut:micronaut-bom:${micronautVersion}"))
    implementation("io.micrometer:micrometer-core")
    implementation("com.google.guava:guava:28.2-jre")

    testImplementation(project(":test"))

    testFixturesImplementation(project(":test"))
}
