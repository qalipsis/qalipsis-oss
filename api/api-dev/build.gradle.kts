/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("kapt")
    kotlin("plugin.allopen")
    `java-test-fixtures`
    id("me.champeau.jmh") version "0.7.2"
}

description = "Components to support QALIPSIS API development"

allOpen {
    annotations(
        "org.openjdk.jmh.annotations.State"
    )
}

kapt {
    useBuildCache = false
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.majorVersion
        javaParameters = true
        freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn,kotlinx.coroutines.ExperimentalCoroutinesApi"
    }
}

kotlin.sourceSets["test"].kotlin.srcDir(layout.buildDirectory.dir("generated/source/kaptKotlin/catadioptre"))
kapt.useBuildCache = false

jmh {
    includeTests.set(false)
    jvmArgsAppend.set(listOf("-Duser.language=en"))

    operationsPerInvocation.set(4)
    timeOnIteration.set("1s")
    threads.set(2)

    benchmarkMode.set(listOf("thrpt", "avgt"))
    timeUnit.set("us")
}

dependencies {
    api(platform(project(":qalipsis-dev-platform")))
    compileOnly("io.aeris-consulting:catadioptre-annotations")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8")
    compileOnly("io.micronaut:micronaut-runtime")

    testImplementation(project(":qalipsis-test"))
    testImplementation("io.aeris-consulting:catadioptre-kotlin")
    kapt(platform(project(":qalipsis-dev-platform")))
    kapt("io.aeris-consulting:catadioptre-annotations")
}
