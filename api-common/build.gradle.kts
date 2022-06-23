plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    kotlin("kapt")
    kotlin("plugin.allopen")
    `java-test-fixtures`
}

description = "Qalipsis API - Common module"

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

    compileOnly(kotlin("stdlib"))
    implementation("cool.graph:cuid-java:0.1.1")
    api(project(":api-dev"))
    api(project(":api-dsl"))
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:${kotlinCoroutinesVersion}")
    // Libraries relevant for the development of plugins.
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.+")
    api(platform("io.micronaut:micronaut-bom:${micronautVersion}"))
    api("io.micronaut:micronaut-inject-java")
    api("io.micronaut.micrometer:micronaut-micrometer-core")
    api("javax.annotation:javax.annotation-api")
    api("io.micronaut:micronaut-validation")

    kapt(platform("io.micronaut:micronaut-bom:${micronautVersion}"))
    kapt("io.micronaut:micronaut-inject-java")
    kapt("io.micronaut:micronaut-validation")
    kapt("io.micronaut:micronaut-graal")

    implementation(platform("io.micronaut:micronaut-bom:${micronautVersion}"))
    implementation("io.micrometer:micrometer-core")
    implementation("com.google.guava:guava:28.2-jre")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.3.1")

    testImplementation(project(":test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.1")

    testFixturesImplementation(project(":test"))
}
