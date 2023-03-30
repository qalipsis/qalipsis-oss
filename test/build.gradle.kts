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

dependencies {
    api(platform(project(":qalipsis-dev-platform")))

    implementation("org.jetbrains.kotlin:kotlin-reflect")

    implementation(project(":qalipsis-api-common"))
    api("io.micronaut.test:micronaut-test-junit5")
    api(platform("io.micronaut:micronaut-bom"))
    api("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-debug")
    api("org.junit.jupiter:junit-jupiter")
    api("io.mockk:mockk")
    api("org.skyscreamer:jsonassert:1.5.0")
    api("com.willowtreeapps.assertk:assertk")
    api("com.willowtreeapps.assertk:assertk-jvm")
    api("org.testcontainers:testcontainers")
    api("io.aeris-consulting:catadioptre-kotlin")
    api("org.testcontainers:junit-jupiter") {
        exclude("junit", "junit")
    }
    api("ch.qos.logback:logback-classic")
}
