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
}

description = "Qalipsis Test Utils"

val micronautVersion: String by project
val testContainersVersion: String by project
val assertkVersion: String by project
val mockkVersion: String by project
val kotlinCoroutinesVersion: String by project
val catadioptreVersion: String by project

dependencies {
    compileOnly(kotlin("stdlib"))
    api(kotlin("reflect"))
    implementation(project(":api-common"))
    api(platform("io.micronaut:micronaut-bom:$micronautVersion"))
    api("org.jetbrains.kotlinx:kotlinx-coroutines-test:${kotlinCoroutinesVersion}")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-debug:${kotlinCoroutinesVersion}")
    api("org.junit.jupiter:junit-jupiter")
    api("io.mockk:mockk:$mockkVersion")
    api("org.skyscreamer:jsonassert:1.5.0")
    api("com.willowtreeapps.assertk:assertk:$assertkVersion")
    api("com.willowtreeapps.assertk:assertk-jvm:$assertkVersion")
    api("org.testcontainers:testcontainers:$testContainersVersion")
    api("io.aeris-consulting:catadioptre-kotlin:${catadioptreVersion}")
    api("org.testcontainers:junit-jupiter:$testContainersVersion") {
        exclude("junit", "junit")
    }
    api("ch.qos.logback:logback-classic")
}
