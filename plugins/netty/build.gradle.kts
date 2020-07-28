import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("kapt")
    kotlin("plugin.allopen")
    id("java-test-fixtures")
}

description = "Evolue Plugins - Netty clients"

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

val nettyVersion = "4.1.51.Final"

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":api:api-common"))
    implementation(enforcedPlatform("io.micronaut:micronaut-bom:${properties["micronautVersion"]}"))
    implementation("io.micronaut:micronaut-runtime")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${properties["kotlinCoroutinesVersion"]}")
    implementation(enforcedPlatform("io.netty:netty-bom:$nettyVersion"))
    implementation("io.netty:netty-handler")
    implementation("io.netty:netty-handler-proxy")
    implementation("io.netty:netty-transport")
    implementation("io.netty:netty-buffer")
    implementation("io.netty:netty-codec")
    implementation("com.github.ben-manes.caffeine:caffeine:2.8.1")

    kapt(project(":api:api-processors"))

    testFixturesImplementation(kotlin("stdlib"))
    testFixturesImplementation(project(":api:api-common"))
    testFixturesImplementation(project(":test"))
    testFixturesImplementation(enforcedPlatform("io.netty:netty-bom:$nettyVersion"))
    testFixturesImplementation("io.netty:netty-handler")
    testFixturesImplementation("io.netty:netty-transport")
    testFixturesImplementation("io.netty:netty-handler-proxy")
    testFixturesImplementation("io.netty:netty-buffer")
    testFixturesImplementation("io.netty:netty-example") {
        exclude("io.netty", "netty-tcnative")
    }

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
