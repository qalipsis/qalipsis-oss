import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("kapt")
}

description = "Qalipsis compile time processors"

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.majorVersion
        javaParameters = true
    }
}

kapt {
    includeCompileClasspath = true
}

val micronautVersion: String by project

dependencies {
    compileOnly(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    implementation(project(":api-dsl"))
    implementation("com.squareup:javapoet:1.13.0")
    api(platform("io.micronaut:micronaut-bom:${micronautVersion}"))
    api("io.micronaut:micronaut-inject-java")
    api("io.micronaut:micronaut-validation")
    api("io.micronaut:micronaut-inject")
    api("io.micronaut:micronaut-graal")

    kaptTest(platform("io.micronaut:micronaut-bom:${micronautVersion}"))
    kaptTest("javax.annotation:javax.annotation-api")
    kaptTest("io.micronaut:micronaut-runtime")

    testImplementation(platform("io.micronaut:micronaut-bom:${micronautVersion}"))
    testImplementation(project(":test"))
    testImplementation(project(":api-dsl"))
    testImplementation("javax.annotation:javax.annotation-api")
    testImplementation("io.micronaut:micronaut-runtime")
    testImplementation("io.micronaut:micronaut-inject")
}
