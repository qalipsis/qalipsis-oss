import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("kapt")
    kotlin("plugin.allopen")
    `java-test-fixtures`
}

description = "Qalipsis Runtime"

// Configure both compileKotlin and compileTestKotlin.
tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.majorVersion
        javaParameters = true
    }
}

allOpen {
    annotations(
        "io.micronaut.aop.Around",
        "jakarta.inject.Singleton",
        "io.qalipsis.api.annotations.StepConverter",
        "io.qalipsis.api.annotations.StepDecorator",
        "io.qalipsis.api.annotations.PluginComponent",
        "io.micronaut.validation.Validated"
    )
}

val testContainersVersion: String by project
val micronautVersion: String by project
val mockkVersion: String by project
val catadioptreVersion: String by project
val awaitilityVersion: String by project

dependencies {
    compileOnly(kotlin("stdlib"))

    compileOnly(platform("io.micronaut:micronaut-bom:$micronautVersion"))
    compileOnly("org.graalvm.nativeimage:svm")

    api("io.qalipsis:api-common:${project.version}")
    api("io.qalipsis:api-dsl:${project.version}")
    api("io.qalipsis:api-processors:${project.version}")
    api(project(":core"))
    compileOnly(project(":factory"))
    compileOnly(project(":head"))

    api(platform("io.micronaut:micronaut-bom:$micronautVersion"))
    api("ch.qos.logback:logback-classic")
    api("info.picocli:picocli")
    api("io.micronaut.picocli:micronaut-picocli")
    api("javax.annotation:javax.annotation-api")
    api("io.micronaut.micrometer:micronaut-micrometer-core")
    api("io.micronaut:micronaut-management")
    api("io.micronaut:micronaut-validation")
    api("io.micronaut:micronaut-runtime")
    api("io.micronaut.cache:micronaut-cache-core")

    kapt(platform("io.micronaut:micronaut-bom:$micronautVersion"))
    kapt("io.micronaut:micronaut-inject-java")
    kapt("io.micronaut:micronaut-validation")
    kapt("io.micronaut:micronaut-graal")

    testFixturesCompileOnly(kotlin("stdlib"))
    testFixturesImplementation("io.aeris-consulting:catadioptre-kotlin:$catadioptreVersion")

    testImplementation(project(":head"))
    testImplementation(project(":factory"))
    testImplementation(testFixtures(project(":core")))
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("io.qalipsis:test:${project.version}")
    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testImplementation("javax.annotation:javax.annotation-api")
    testImplementation("io.micronaut:micronaut-runtime")
    testImplementation("org.testcontainers:postgresql:$testContainersVersion")
    testImplementation("org.awaitility:awaitility-kotlin:$awaitilityVersion")

    kaptTest(platform("io.micronaut:micronaut-bom:$micronautVersion"))
    kaptTest("io.micronaut:micronaut-inject-java")
    kaptTest("io.qalipsis:api-processors:${project.version}")
}
