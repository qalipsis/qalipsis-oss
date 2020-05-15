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
    }
}

kapt {
    generateStubs = true
}

dependencies {
    implementation(platform("io.micronaut:micronaut-bom:${properties["micronautVersion"]}"))
    implementation(kotlin("stdlib"))

    implementation(project(":api:api-common"))
    implementation(project(":api:api-specifications"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${properties["kotlinCoroutinesVersion"]}")
    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("com.google.guava:guava:28.2-jre")
    implementation("com.github.ben-manes.caffeine:caffeine:2.8.1")
    implementation("com.google.dagger:dagger:2.27")
    implementation("cool.graph:cuid-java:0.1.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.10.1")
    implementation("io.ktor:ktor-client-apache:1.3.1")
    implementation("io.ktor:ktor-client-auth:1.3.1")
    implementation("io.ktor:ktor-client-auth-jvm:1.3.1")
    implementation("io.micrometer:micrometer-registry-elastic:1.4.1")
    implementation("io.micronaut:micronaut-validation")

    testImplementation(project(":test"))
    testImplementation("io.ktor:ktor-client-mock:1.3.1")
    testImplementation("io.ktor:ktor-client-mock-jvm:1.3.1")
    testImplementation("javax.annotation:javax.annotation-api")
    testImplementation("io.micronaut:micronaut-runtime")

    compileOnly("javax.annotation:javax.annotation-api")
    compileOnly("io.micronaut:micronaut-runtime")

    annotationProcessor(platform("io.micronaut:micronaut-bom:${properties["micronautVersion"]}"))
    annotationProcessor("io.micronaut:micronaut-inject-java")
    testAnnotationProcessor(platform("io.micronaut:micronaut-bom:${properties["micronautVersion"]}"))
    testAnnotationProcessor("io.micronaut:micronaut-inject-java")

    kapt("io.micronaut:micronaut-inject-java")
    kapt(platform("io.micronaut:micronaut-bom:${properties["micronautVersion"]}"))
    kaptTest("io.micronaut:micronaut-inject-java")
    kaptTest(platform("io.micronaut:micronaut-bom:${properties["micronautVersion"]}"))
}