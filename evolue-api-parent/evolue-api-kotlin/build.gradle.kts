import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    kotlin("jvm") version "1.3.50"
}

description = "Evolue API Kotlin DSL"

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

dependencies {
    implementation(
            kotlin("stdlib")
    )
    implementation("com.willowtreeapps.assertk:assertk-jvm:0.20")
    implementation("io.mockk:mockk:1.9.3")
}