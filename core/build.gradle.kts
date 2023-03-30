/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("kapt")
    kotlin("plugin.allopen")
    kotlin("plugin.serialization")
    `java-test-fixtures`
}

description = "Qalipsis Core"

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
        "jakarta.inject.Singleton",
        "io.qalipsis.api.annotations.StepConverter",
        "io.qalipsis.api.annotations.StepDecorator",
        "io.qalipsis.api.annotations.PluginComponent",
        "io.micronaut.validation.Validated"
    )
}

kotlin.sourceSets["test"].kotlin.srcDir("build/generated/source/kaptKotlin/catadioptre")
kapt.useBuildCache = false

val apiVersion: String by project

dependencies {
    implementation(platform("io.qalipsis:qalipsis-dev-platform:$apiVersion"))
    api("io.micronaut.redis:micronaut-redis-lettuce")
    compileOnly("org.graalvm.nativeimage:svm")
    compileOnly("io.aeris-consulting:catadioptre-annotations")
    compileOnly("io.swagger.core.v3:swagger-annotations")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive")

    implementation("io.qalipsis:qalipsis-api-common:$apiVersion")
    implementation("io.qalipsis:qalipsis-api-dsl:$apiVersion")
    implementation("io.qalipsis:qalipsis-api-processors:$apiVersion")

    implementation("com.google.guava:guava")
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("cool.graph:cuid-java")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("io.micronaut.micrometer:micronaut-micrometer-core")
    implementation("javax.annotation:javax.annotation-api")
    implementation("io.micronaut:micronaut-validation")
    implementation("io.micronaut:micronaut-runtime")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")

    kapt(platform("io.qalipsis:qalipsis-dev-platform:$apiVersion"))
    kapt("io.micronaut:micronaut-inject-java")
    kapt("io.micronaut:micronaut-validation")
    kapt("io.micronaut:micronaut-graal")
    kapt("io.qalipsis:qalipsis-api-processors:$apiVersion")
    kapt("io.aeris-consulting:catadioptre-annotations")
    kapt("org.jetbrains.kotlinx:kotlinx-serialization-protobuf")

    testImplementation(platform("io.qalipsis:qalipsis-dev-platform:$apiVersion"))
    testImplementation("io.qalipsis:qalipsis-test:$apiVersion")
    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testImplementation("javax.annotation:javax.annotation-api")
    testImplementation("io.micronaut:micronaut-runtime")
    testImplementation("io.aeris-consulting:catadioptre-kotlin")
    testImplementation(testFixtures("io.qalipsis:qalipsis-api-dsl:$apiVersion"))
    testImplementation(testFixtures("io.qalipsis:qalipsis-api-common:$apiVersion"))
    testImplementation(testFixtures(project(":qalipsis-runtime")))
    testImplementation("org.testcontainers:testcontainers")

    kaptTest(platform("io.qalipsis:qalipsis-dev-platform:$apiVersion"))
    kaptTest("io.micronaut:micronaut-inject-java")
    kaptTest("io.qalipsis:qalipsis-api-processors:$apiVersion")

    testFixturesApi(platform("io.qalipsis:qalipsis-dev-platform:$apiVersion"))
    testFixturesApi("org.testcontainers:testcontainers")
    testFixturesApi("io.qalipsis:qalipsis-api-common:$apiVersion")
    testFixturesImplementation("io.micronaut.test:micronaut-test-junit5")
    testFixturesImplementation("io.micronaut:micronaut-runtime")
    testFixturesImplementation("org.testcontainers:junit-jupiter")
    testFixturesImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json")

}

