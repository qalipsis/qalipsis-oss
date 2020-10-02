import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("kapt")
    kotlin("plugin.allopen")
    `java-test-fixtures`
}

description = "Evolue Runtime"

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
            "io.evolue.api.annotations.StepConverter",
            "io.evolue.api.annotations.StepDecorator",
            "io.evolue.api.annotations.PluginComponent",
            "io.micronaut.validation.Validated"
    )
}

val micronautVersion: String by project

dependencies {
    implementation(kotlin("stdlib"))

    api(project(":api:api-common"))
    api(project(":api:api-dsl"))
    api(project(":api:api-processors"))
    api(project(":core"))

    api(platform("io.micronaut:micronaut-bom:${micronautVersion}"))
    api("org.slf4j:slf4j-api")
    api("ch.qos.logback:logback-classic:1.2.3")
    api("info.picocli:picocli")
    api("io.micronaut.picocli:micronaut-picocli")
    api("javax.annotation:javax.annotation-api")
    api("io.micronaut.micrometer:micronaut-micrometer-core")
    api("io.micronaut.micrometer:micronaut-micrometer-registry-elastic")
    api("io.micronaut:micronaut-management")
    api("io.micronaut:micronaut-validation")
    api("io.micronaut:micronaut-http-server-netty")
    api("io.micronaut:micronaut-runtime")
    api("io.micronaut.cache:micronaut-cache-core")

    kapt(platform("io.micronaut:micronaut-bom:${micronautVersion}"))
    kapt("io.micronaut:micronaut-inject-java")
    kapt("io.micronaut:micronaut-validation")

    testFixturesImplementation(kotlin("stdlib"))

    testImplementation(project(":test"))
    testImplementation("javax.annotation:javax.annotation-api")
    testImplementation("io.micronaut:micronaut-runtime")

    kaptTest(platform("io.micronaut:micronaut-bom:${micronautVersion}"))
    kaptTest("io.micronaut:micronaut-inject-java")
    kaptTest(project(":api:api-processors"))
}
