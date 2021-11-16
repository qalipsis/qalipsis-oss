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
val kotlinCoroutinesVersion: String by project
val catadioptreVersion: String by project

dependencies {
    compileOnly(kotlin("stdlib"))
    api(kotlin("reflect"))
    implementation(project(":api-common"))
    api(platform("io.micronaut:micronaut-bom:$micronautVersion"))
    api("org.jetbrains.kotlinx:kotlinx-coroutines-test:${kotlinCoroutinesVersion}")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-debug:${kotlinCoroutinesVersion}")
    api("org.junit.jupiter:junit-jupiter")
    api("io.mockk:mockk:$mockkVersion")
    api("org.skyscreamer:jsonassert:1.5.0")
    api("com.willowtreeapps.assertk:assertk:$assertkVersion")
    api("com.willowtreeapps.assertk:assertk-jvm:$assertkVersion")
    api("org.testcontainers:testcontainers:$testContainersVersion")
    api("io.aeris-consulting:catadioptre-kotlin:${catadioptreVersion}")
    api("org.testcontainers:junit-jupiter:$testContainersVersion") {
        exclude("junit", "junit")
    }
    api("ch.qos.logback:logback-classic")
}
