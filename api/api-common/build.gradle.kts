import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("plugin.allopen")
}

description = "Evolue API - Common module"

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_11.majorVersion
}

allOpen{
    annotations(
        "io.micronaut.aop.Around"
    )
}

dependencies {
    implementation(kotlin("stdlib"))
    api(project(":api:api-dsl"))
    api("org.slf4j:slf4j-api:1.7.30")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:${properties["kotlinCoroutinesVersion"]}")
    implementation(enforcedPlatform("io.micronaut:micronaut-bom:${properties["micronautVersion"]}"))
    implementation("io.micrometer:micrometer-core")

    implementation("com.google.guava:guava:28.2-jre")

    testImplementation(project(":test"))
}
