/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
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
    kotlin("plugin.serialization")
    kotlin("kapt")
    kotlin("plugin.allopen")
    `java-test-fixtures`
}

description = "QALIPSIS API - Common module"

allOpen {
    annotations(
        "io.micronaut.aop.Around"
    )
}

kotlin.sourceSets["test"].kotlin.srcDir("build/generated/source/kaptKotlin/catadioptre")
kapt.useBuildCache = false

dependencies {
    implementation(platform(project(":qalipsis-dev-platform")))
    compileOnly("org.graalvm.nativeimage:svm")
    compileOnly("io.swagger.core.v3:swagger-annotations")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-protobuf")
    compileOnly("io.aeris-consulting:catadioptre-annotations")
    compileOnly("io.aeris-consulting:catadioptre-kotlin")

    implementation("cool.graph:cuid-java")
    api(project(":qalipsis-api-dev"))
    api(project(":qalipsis-api-dsl"))
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    api("io.micronaut:micronaut-inject-java")
    api("javax.annotation:javax.annotation-api")
    api("io.micronaut:micronaut-validation")

    kapt(platform(project(":qalipsis-dev-platform")))
    kapt("io.micronaut:micronaut-inject-java")
    kapt("io.micronaut:micronaut-validation")
    kapt("io.micronaut:micronaut-graal")
    kapt("io.aeris-consulting:catadioptre-annotations")

    implementation("com.google.guava:guava")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm")
    implementation("org.apache.commons:commons-text:1.11.0")
    testImplementation("io.aeris-consulting:catadioptre-kotlin")

    testImplementation(project(":qalipsis-test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json")

    testFixturesImplementation(platform(project(":qalipsis-dev-platform")))
    testFixturesCompileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json")
    testFixturesImplementation("org.apache.commons:commons-lang3")
    testFixturesImplementation(project(":qalipsis-test"))
}
