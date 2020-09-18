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
    annotation("io.micronaut.aop.Around")
}
kapt {
    generateStubs = true
}

val micronautVersion: String by project

dependencies {
    implementation(kotlin("stdlib"))

    implementation(project(":api:api-common"))
    implementation(project(":api:api-dsl"))
    implementation(project(":api:api-processors"))
    implementation(project(":core"))

    implementation(enforcedPlatform("io.micronaut:micronaut-bom:${micronautVersion}"))
    implementation("org.slf4j:slf4j-api")
    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("info.picocli:picocli")
    implementation("io.micronaut.picocli:micronaut-picocli")
    implementation("javax.annotation:javax.annotation-api")
    implementation("io.micronaut.micrometer:micronaut-micrometer-core")
    implementation("io.micronaut.micrometer:micronaut-micrometer-registry-elastic")
    implementation("io.micronaut:micronaut-management")
    implementation("io.micronaut:micronaut-validation")
    implementation("io.micronaut:micronaut-http-server-netty")
    implementation("io.micronaut:micronaut-runtime")
    implementation("io.micronaut.cache:micronaut-cache-core")

    kapt(enforcedPlatform("io.micronaut:micronaut-bom:${micronautVersion}"))
    kapt("io.micronaut:micronaut-inject-java")
    kapt("io.micronaut:micronaut-validation")

    testFixturesImplementation(kotlin("stdlib"))

    testImplementation(project(":test"))
    testImplementation("javax.annotation:javax.annotation-api")
    testImplementation("io.micronaut:micronaut-runtime")

    kaptTest(enforcedPlatform("io.micronaut:micronaut-bom:${micronautVersion}"))
    kaptTest("io.micronaut:micronaut-inject-java")
    kaptTest(project(":api:api-processors"))
}
