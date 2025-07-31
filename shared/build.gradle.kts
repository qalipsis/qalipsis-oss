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

description = "QALIPSIS shared library for head and factories"

kapt {
    correctErrorTypes = true
    useBuildCache = false
}

// Configure both compileKotlin and compileTestKotlin.
tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.majorVersion
        javaParameters = true
        freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn,kotlinx.coroutines.ObsoleteCoroutinesApi"
    }
}

allOpen {
    annotations(
        "io.micronaut.aop.Around",
        "jakarta.inject.Singleton",
        "io.micronaut.context.annotation.Factory",
        "io.qalipsis.api.annotations.StepConverter",
        "io.qalipsis.api.annotations.StepDecorator",
        "io.qalipsis.api.annotations.PluginComponent",
        "io.micronaut.context.annotation.Bean",
        "io.micronaut.validation.Validated",
        "org.openjdk.jmh.annotations.State"
    )
}

kotlin.sourceSets["test"].kotlin.srcDir("build/generated/source/kaptKotlin/catadioptre")
kapt {
    correctErrorTypes = true
    useBuildCache = false
}

dependencies {
    api("io.micronaut.redis:micronaut-redis-lettuce")
    api("org.apache.commons:commons-lang3:3.+")
    compileOnly("io.aeris-consulting:catadioptre-annotations")
    compileOnly("io.swagger.core.v3:swagger-annotations")
    compileOnly("org.graalvm.nativeimage:svm")

    implementation(platform(project(":qalipsis-dev-platform")))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive")
    implementation(project(":qalipsis-api-common"))
    implementation(project(":qalipsis-api-dsl"))
    implementation(project(":qalipsis-api-processors"))

    implementation("com.google.guava:guava")
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("cool.graph:cuid-java")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("io.micronaut.micrometer:micronaut-micrometer-core")
    implementation("jakarta.annotation:jakarta.annotation-api")
    implementation("io.micronaut:micronaut-validation")
    implementation("io.micronaut:micronaut-runtime")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")

    kapt(platform(project(":qalipsis-dev-platform")))
    kapt("io.micronaut:micronaut-inject-java")
    kapt("io.micronaut:micronaut-validation")
    kapt("io.micronaut:micronaut-graal")
    kapt(project(":qalipsis-api-processors"))
    kapt("io.aeris-consulting:catadioptre-annotations")
    kapt("org.jetbrains.kotlinx:kotlinx-serialization-protobuf")

    testImplementation(platform(project(":qalipsis-dev-platform")))
    testImplementation(project(":qalipsis-test"))
    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testImplementation("jakarta.annotation:jakarta.annotation-api")
    testImplementation("io.micronaut:micronaut-runtime")
    testImplementation("io.aeris-consulting:catadioptre-kotlin")
    testImplementation(testFixtures(project(":qalipsis-api-dsl")))
    testImplementation(testFixtures(project(":qalipsis-api-common")))
    testImplementation(testFixtures(project(":qalipsis-runtime")))
    testImplementation("org.testcontainers:testcontainers")

    kaptTest(platform(project(":qalipsis-dev-platform")))
    kaptTest("io.micronaut:micronaut-inject-java")
    kaptTest(project(":qalipsis-api-processors"))

    testFixturesApi(platform(project(":qalipsis-dev-platform")))
    testFixturesApi("org.testcontainers:testcontainers")
    testFixturesApi("org.testcontainers:postgresql")
    testFixturesApi(project(":qalipsis-api-common"))
    testFixturesImplementation("io.micronaut.test:micronaut-test-junit5")
    testFixturesImplementation("io.micronaut:micronaut-runtime")
    testFixturesImplementation("org.testcontainers:junit-jupiter")
    testFixturesImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json")
    testFixturesImplementation("org.postgresql:postgresql")

}

