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

val micronautVersion: String by project
val kotlinCoroutinesVersion: String by project
val catadioptreVersion: String by project

kotlin.sourceSets["test"].kotlin.srcDir("build/generated/source/kaptKotlin/catadioptre")
kapt.useBuildCache = false

dependencies {
    compileOnly(platform("io.micronaut:micronaut-bom:${micronautVersion}"))
    compileOnly("org.graalvm.nativeimage:svm")
    compileOnly("io.aeris-consulting:catadioptre-annotations:${catadioptreVersion}")

    compileOnly(kotlin("stdlib"))
    implementation(project(":api-dev"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${kotlinCoroutinesVersion}")
    implementation("cool.graph:cuid-java:0.1.1")
    implementation("javax.annotation:javax.annotation-api")
    implementation(platform("io.micronaut:micronaut-bom:${micronautVersion}"))
    implementation("io.micronaut:micronaut-inject-java")
    implementation("io.micronaut:micronaut-validation")
    implementation("io.micronaut.micrometer:micronaut-micrometer-core")

    // Required to make the scenario projects build.
    api(platform("io.micronaut:micronaut-bom:${micronautVersion}"))
    api("io.micronaut:micronaut-runtime")

    kapt(platform("io.micronaut:micronaut-bom:${micronautVersion}"))
    kapt("io.micronaut:micronaut-inject-java")
    kapt("io.micronaut:micronaut-validation")
    kapt("io.micronaut:micronaut-graal")
    kapt("io.aeris-consulting:catadioptre-annotations:${catadioptreVersion}")

    testImplementation(project(":test"))
    testImplementation(testFixtures(project(":api-common")))
    testImplementation("io.aeris-consulting:catadioptre-kotlin:${catadioptreVersion}")
}
