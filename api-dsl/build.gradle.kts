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
    kotlin("plugin.allopen")
    `java-test-fixtures`
}

description = "Qalipsis API Kotlin DSL"

kapt {
    useBuildCache = false
}

allOpen {
    annotations(
        "io.micronaut.aop.Around",
        "io.qalipsis.api.annotations.Spec"
    )
}

kotlin.sourceSets["test"].kotlin.srcDir("build/generated/source/kaptKotlin/catadioptre")
kapt.useBuildCache = false

dependencies {
    implementation(platform(project(":qalipsis-dev-platform")))
    compileOnly("org.graalvm.nativeimage:svm")
    compileOnly("io.aeris-consulting:catadioptre-annotations")

    implementation(project(":qalipsis-api-dev"))
    implementation("cool.graph:cuid-java")
    implementation("javax.annotation:javax.annotation-api")
    implementation("io.micronaut:micronaut-inject-java")
    implementation("io.micronaut:micronaut-validation")

    // Required to make the scenario projects build.
    api("io.micronaut:micronaut-runtime")

    kapt(platform(project(":qalipsis-dev-platform")))
    kapt("io.micronaut:micronaut-inject-java")
    kapt("io.micronaut:micronaut-validation")
    kapt("io.micronaut:micronaut-graal")
    kapt("io.aeris-consulting:catadioptre-annotations")

    testImplementation(project(":qalipsis-test"))
    testImplementation(testFixtures(project(":qalipsis-api-common")))
    testImplementation("io.aeris-consulting:catadioptre-kotlin")

    testFixturesApi(platform(project(":qalipsis-dev-platform")))
    testFixturesImplementation(testFixtures(project(":qalipsis-api-common")))
}
