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
            "javax.inject.Singleton",
            "io.qalipsis.api.annotations.StepConverter",
            "io.qalipsis.api.annotations.StepDecorator",
            "io.qalipsis.api.annotations.PluginComponent",
            "io.micronaut.validation.Validated"
    )
}

val micronautVersion: String by project

dependencies {
    implementation(kotlin("stdlib"))

    compileOnly(platform("io.micronaut:micronaut-bom:${micronautVersion}"))
    compileOnly("org.graalvm.nativeimage:svm")

    api(project(":api:api-common"))
    api(project(":api:api-dsl"))
    api(project(":api:api-processors"))
    api(project(":core"))

    api(platform("io.micronaut:micronaut-bom:${micronautVersion}"))
    api("ch.qos.logback:logback-classic")
    api("info.picocli:picocli")
    api("io.micronaut.picocli:micronaut-picocli")
    api("javax.annotation:javax.annotation-api")
    api("io.micronaut.micrometer:micronaut-micrometer-core")
    api("io.micronaut:micronaut-management")
    api("io.micronaut:micronaut-validation")
    api("io.micronaut:micronaut-http-server-netty")
    api("io.micronaut:micronaut-runtime")
    api("io.micronaut.cache:micronaut-cache-core")

    kapt(platform("io.micronaut:micronaut-bom:${micronautVersion}"))
    kapt("io.micronaut:micronaut-inject-java")
    kapt("io.micronaut:micronaut-validation")
    kapt("io.micronaut:micronaut-graal")

    testFixturesImplementation(kotlin("stdlib"))

    testImplementation(project(":test"))
    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testImplementation("javax.annotation:javax.annotation-api")
    testImplementation("io.micronaut:micronaut-runtime")

    kaptTest(platform("io.micronaut:micronaut-bom:${micronautVersion}"))
    kaptTest("io.micronaut:micronaut-inject-java")
    kaptTest(project(":api:api-processors"))
}
