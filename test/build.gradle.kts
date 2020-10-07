import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("kapt")
}

description = "Qalipsis Test Utils"

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.majorVersion
        javaParameters = true
    }
}

val micronautVersion: String by project
val testContainersVersion: String by project

dependencies {
    implementation(kotlin("stdlib"))
    api(kotlin("reflect"))
    api("ch.qos.logback:logback-classic:1.2.3")
    implementation(project(":api:api-common"))
    api(platform("io.micronaut:micronaut-bom:${micronautVersion}"))
    api("org.junit.jupiter:junit-jupiter")
    api("io.mockk:mockk:1.9.3")
    api("org.skyscreamer:jsonassert:1.5.0")
    api("com.willowtreeapps.assertk:assertk:0.22")
    api("com.willowtreeapps.assertk:assertk-jvm:0.22")
    api("com.natpryce:hamkrest:1.7.0.3")
    api("org.testcontainers:testcontainers:${testContainersVersion}")
    api("org.testcontainers:junit-jupiter:${testContainersVersion}") {
        exclude("junit", "junit")
    }
}
