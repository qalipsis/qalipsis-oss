import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("kapt")
    `java-test-fixtures`
}

description = "Components to support Qalipsis API development"

kapt {
    useBuildCache = false
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.majorVersion
        javaParameters = true
        freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn,kotlinx.coroutines.ExperimentalCoroutinesApi"
    }
}

val micronautVersion: String by project
val kotlinCoroutinesVersion: String by project
val jacksonVersion: String by project
val catadioptreVersion: String by project

kotlin.sourceSets["test"].kotlin.srcDir(layout.buildDirectory.dir("generated/source/kaptKotlin/catadioptre"))
kapt.useBuildCache = false

dependencies {
    compileOnly(kotlin("stdlib"))
    compileOnly("io.aeris-consulting:catadioptre-annotations:${catadioptreVersion}")
    api("io.github.microutils:kotlin-logging-jvm:2.0.6")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:${kotlinCoroutinesVersion}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${kotlinCoroutinesVersion}")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:$jacksonVersion")
    implementation(platform("io.micronaut:micronaut-bom:${micronautVersion}"))
    compileOnly("io.micronaut:micronaut-runtime")
    implementation("io.micronaut.micrometer:micronaut-micrometer-core")

    compileOnly("org.slf4j:slf4j-api")

    testImplementation(project(":test"))
    testImplementation("io.aeris-consulting:catadioptre-kotlin:${catadioptreVersion}")
    kapt("io.aeris-consulting:catadioptre-annotations:${catadioptreVersion}")
}
