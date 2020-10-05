import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("kapt")
    kotlin("plugin.allopen")
}

description = "Evolue Core"

// Configure both compileKotlin and compileTestKotlin.
tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.majorVersion
        javaParameters = true
        freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn,kotlinx.coroutines.ObsoleteCoroutinesApi"
    }
}

allOpen {
    annotations(
            "io.micronaut.aop.Around",
            "javax.inject.Singleton",
            "io.evolue.api.annotations.StepConverter",
            "io.evolue.api.annotations.StepDecorator",
            "io.evolue.api.annotations.PluginComponent",
            "io.micronaut.validation.Validated"
    )
}

val micronautVersion: String by project
val kotlinCoroutinesVersion: String by project
val testContainersVersion: String by project

dependencies {
    compileOnly(kotlin("reflect"))
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${kotlinCoroutinesVersion}")
    implementation(platform("io.micronaut:micronaut-bom:${micronautVersion}"))

    implementation(project(":api:api-common"))
    implementation(project(":api:api-dsl"))
    implementation(project(":api:api-processors"))

    implementation("com.google.guava:guava:29.0-jre")
    implementation("com.github.ben-manes.caffeine:caffeine:2.8.1")
    implementation("cool.graph:cuid-java:0.1.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.10.1")
    implementation("io.ktor:ktor-client-apache:1.3.1")
    implementation("io.ktor:ktor-client-auth:1.3.1")
    implementation("io.ktor:ktor-client-auth-jvm:1.3.1")
    implementation("io.micronaut.micrometer:micronaut-micrometer-core")
    implementation("javax.annotation:javax.annotation-api")
    implementation("io.micronaut:micronaut-validation")
    implementation("io.micronaut:micronaut-runtime")

    kapt(platform("io.micronaut:micronaut-bom:${micronautVersion}"))
    kapt("io.micronaut:micronaut-inject-java")
    kapt("io.micronaut:micronaut-validation")
    kapt(project(":api:api-processors"))
    kapt(kotlin("stdlib"))
    kapt(kotlin("reflect"))

    testImplementation(project(":test"))
    testImplementation("io.ktor:ktor-client-mock:1.3.1")
    testImplementation("io.ktor:ktor-client-mock-jvm:1.3.1")
    testImplementation("javax.annotation:javax.annotation-api")
    testImplementation("io.micronaut:micronaut-runtime")
    testImplementation("org.testcontainers:elasticsearch:$testContainersVersion")
    testImplementation(testFixtures(project(":api:api-dsl")))
    testImplementation(testFixtures(project(":api:api-common")))

    kaptTest(platform("io.micronaut:micronaut-bom:${micronautVersion}"))
    kaptTest("io.micronaut:micronaut-inject-java")
    kaptTest(project(":api:api-processors"))
}
