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

plugins {
    kotlin("jvm")
    kotlin("kapt")
    kotlin("plugin.allopen")
    kotlin("plugin.serialization")
}

description = "Qalipsis Factory components"

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

val micronautVersion: String by project
val kotlinCoroutinesVersion: String by project
val testContainersVersion: String by project
val jacksonVersion: String by project
val catadioptreVersion: String by project
val kotlinSerialization: String by project

kotlin.sourceSets["test"].kotlin.srcDir("build/generated/source/kaptKotlin/catadioptre")
kapt.useBuildCache = false


dependencies {
    implementation(platform("io.qalipsis:dev-platform:${project.version}"))
    compileOnly("org.graalvm.nativeimage:svm")
    compileOnly("io.aeris-consulting:catadioptre-annotations:")

    implementation(project(":core"))
    implementation("io.qalipsis:api-common:${project.version}")
    implementation("io.qalipsis:api-dsl:${project.version}")
    implementation("io.qalipsis:api-processors:${project.version}")

    implementation("com.google.guava:guava")
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("cool.graph:cuid-java:0.1.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("io.micronaut.micrometer:micronaut-micrometer-core")
    implementation("javax.annotation:javax.annotation-api")
    implementation("io.micronaut:micronaut-validation")
    implementation("io.micronaut:micronaut-runtime")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf")

    kapt(platform("io.qalipsis:dev-platform:${project.version}"))
    kapt("io.micronaut:micronaut-inject-java")
    kapt("io.micronaut:micronaut-validation")
    kapt("io.micronaut:micronaut-graal")
    kapt("io.qalipsis:api-processors:${project.version}")
    kapt("io.aeris-consulting:catadioptre-annotations")
    kapt("org.jetbrains.kotlinx:kotlinx-serialization-protobuf")

    testImplementation(platform("io.qalipsis:dev-platform:${project.version}"))
    testImplementation(project(":core"))
    testImplementation("io.qalipsis:test:${project.version}")
    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testImplementation("javax.annotation:javax.annotation-api")
    testImplementation("io.micronaut:micronaut-runtime")
    testImplementation("io.aeris-consulting:catadioptre-kotlin")
    testImplementation(testFixtures("io.qalipsis:api-dsl:${project.version}"))
    testImplementation(testFixtures("io.qalipsis:api-common:${project.version}"))
    testImplementation(testFixtures(project(":runtime")))
    testImplementation(testFixtures(project(":core")))
    testImplementation(project(":head"))

    kaptTest(platform("io.qalipsis:dev-platform:${project.version}"))
    kaptTest("io.micronaut:micronaut-inject-java")
    kaptTest("io.qalipsis:api-processors:${project.version}")
}

