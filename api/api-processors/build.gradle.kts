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
