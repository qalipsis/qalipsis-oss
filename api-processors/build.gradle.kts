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
    kotlin("kapt")
    kotlin("plugin.serialization")
}

description = "QALIPSIS compile time processors"

kapt {
    includeCompileClasspath = true
}

dependencies {
    implementation(platform(project(":qalipsis-dev-platform")))
    implementation(kotlin("reflect"))
    implementation(project(":qalipsis-api-dsl"))

    implementation("com.squareup:javapoet:1.13.0")
    implementation("com.squareup:kotlinpoet:1.13.2")

    api("io.micronaut:micronaut-inject-java")
    api("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-protobuf")
    api("io.micronaut:micronaut-validation")
    api("io.micronaut:micronaut-inject")
    api("io.micronaut:micronaut-graal")

    kaptTest(platform(project(":qalipsis-dev-platform")))
    kaptTest("io.micronaut:micronaut-inject-java")
    kaptTest("io.micronaut:micronaut-inject")
    kaptTest("org.jetbrains.kotlinx:kotlinx-serialization-json")
    testAnnotationProcessor(platform(project(":qalipsis-dev-platform")))
    testAnnotationProcessor("io.micronaut:micronaut-inject-java")

    testImplementation(platform(project(":qalipsis-dev-platform")))
    testImplementation(project(":qalipsis-test"))
    testImplementation(project(":qalipsis-api-dsl"))
    testImplementation(project(":qalipsis-api-common"))
    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf")
    testImplementation("javax.annotation:javax.annotation-api")
    testImplementation("io.micronaut:micronaut-runtime")
    testImplementation("io.micronaut:micronaut-inject")
    testImplementation(testFixtures(project(":qalipsis-api-common")))
}
