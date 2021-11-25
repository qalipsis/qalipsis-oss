import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("kapt")
    kotlin("plugin.serialization")
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
    implementation("com.squareup:javapoet:1.+")
    implementation("com.squareup:kotlinpoet:1.+")
    api(platform("io.micronaut:micronaut-bom:${micronautVersion}"))
    api("io.micronaut:micronaut-inject-java")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.+")
    api("io.micronaut:micronaut-validation")
    api("io.micronaut:micronaut-inject")
    api("io.micronaut:micronaut-graal")

    kaptTest(platform("io.micronaut:micronaut-bom:${micronautVersion}"))
    kaptTest("io.micronaut:micronaut-inject-java")
    kaptTest("io.micronaut:micronaut-inject")
    testAnnotationProcessor(platform("io.micronaut:micronaut-bom:${micronautVersion}"))
    testAnnotationProcessor("io.micronaut:micronaut-inject-java")

    testImplementation(platform("io.micronaut:micronaut-bom:${micronautVersion}"))
    testImplementation(project(":test"))
    testImplementation(project(":api-dsl"))
    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testImplementation("javax.annotation:javax.annotation-api")
    testImplementation("io.micronaut:micronaut-runtime")
    testImplementation("io.micronaut:micronaut-inject")
    testImplementation(project(":api-common"))
    testImplementation(testFixtures(project(":api-common")))
}
