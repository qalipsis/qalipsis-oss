/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    kotlin("kapt")
    kotlin("plugin.allopen")
    `java-test-fixtures`
}

description = "Qalipsis API - Common module"

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
    api("io.micronaut.micrometer:micronaut-micrometer-core")
    api("javax.annotation:javax.annotation-api")
    api("io.micronaut:micronaut-validation")

    kapt(platform(project(":qalipsis-dev-platform")))
    kapt("io.micronaut:micronaut-inject-java")
    kapt("io.micronaut:micronaut-validation")
    kapt("io.micronaut:micronaut-graal")
    kapt("io.aeris-consulting:catadioptre-annotations")

    implementation("io.micrometer:micrometer-core")
    implementation("com.google.guava:guava")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core")
    testImplementation("io.aeris-consulting:catadioptre-kotlin")

    testImplementation(project(":qalipsis-test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json")

    testFixturesImplementation(platform(project(":qalipsis-dev-platform")))
    testFixturesCompileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json")
    testFixturesImplementation("org.apache.commons:commons-lang3")
    testFixturesImplementation(project(":qalipsis-test"))
}
