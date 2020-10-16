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
val assertkVersion: String by project
val mockkVersion: String by project

dependencies {
    implementation(kotlin("stdlib"))
    api(kotlin("reflect"))
    implementation(project(":api:api-common"))
    api(platform("io.micronaut:micronaut-bom:$micronautVersion"))
    api("org.junit.jupiter:junit-jupiter")
    api("io.mockk:mockk:$mockkVersion")
    api("org.skyscreamer:jsonassert:1.5.0")
    api("com.willowtreeapps.assertk:assertk:$assertkVersion")
    api("com.willowtreeapps.assertk:assertk-jvm:$assertkVersion")
    api("org.testcontainers:testcontainers:$testContainersVersion")
    api("org.testcontainers:junit-jupiter:$testContainersVersion") {
        exclude("junit", "junit")
    }
    api("ch.qos.logback:logback-classic")
}
