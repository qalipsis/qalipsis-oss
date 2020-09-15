import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("kapt")
    kotlin("plugin.allopen")
    id("java-test-fixtures")
}

description = "Evolue Plugins - Jackson Serializer and Deserializer"

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.majorVersion
        javaParameters = true
    }
}

kapt {
    generateStubs = true
}

allOpen {
    annotations(
            "io.micronaut.aop.Around",
            "javax.inject.Singleton"
    )
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":api:api-common"))
    implementation(enforcedPlatform("io.micronaut:micronaut-bom:${properties["micronautVersion"]}"))
    implementation("io.micronaut:micronaut-runtime")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${properties["kotlinCoroutinesVersion"]}")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-csv:2.10.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.10.1")

    kapt(project(":api:api-processors"))

    testFixturesImplementation(kotlin("stdlib"))
    testFixturesImplementation(project(":api:api-common"))
    testFixturesImplementation(project(":test"))

    testImplementation(project(":test"))
    testImplementation(project(":api:api-dsl"))
    testImplementation(testFixtures(project(":api:api-dsl")))
    testImplementation(testFixtures(project(":api:api-common")))
    testImplementation(testFixtures(project(":runtime")))
    testImplementation("javax.annotation:javax.annotation-api")
    testImplementation("io.micronaut:micronaut-runtime")
    testRuntimeOnly(project(":runtime"))
    kaptTest("io.micronaut:micronaut-inject-java")
    kaptTest(project(":api:api-processors"))
}
