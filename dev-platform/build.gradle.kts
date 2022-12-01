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
    `java-platform`
}

val kotlin = "1.7.10"
val kotlinCoroutines = "1.6.4"
val kotlinSerialization = "1.4.0"

val micronaut = "3.7.+"
val jackson = "2.13.2"
val klogging = "2.1.23"
val logback = "1.4.+"
val slf4j = "2.0.1"
val guava = "29.0-jre"
val caffeineCache = "2.8.+"
val cuid = "0.1.1"
val commonsLang = "3.+"

val netty = "4.1.82.Final"
val bouncycastle = "1.64"
val postgresqlDriver = "42.3.1"

val testContainers = "1.+"
val mockk = "1.10.+"
val catadioptre = "0.4.+"
val awaitility = "4.2.+"
val assertk = "0.25"

javaPlatform {
    allowDependencies()
}

dependencies {
    // Platform modules.
    api(platform("io.micronaut:micronaut-bom:${micronaut}"))
    api(platform("com.fasterxml.jackson:jackson-bom:$jackson"))
    api(platform("org.testcontainers:testcontainers-bom:$testContainers"))
    api(platform("io.netty:netty-bom:$netty"))
    api("org.jetbrains.kotlin:kotlin-stdlib:$kotlin")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinCoroutines")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:$kotlinCoroutines")

    // Libraries that could be brought by other dependencies and break the compilation or execution.
    api("org.slf4j:slf4j-api:$slf4j")
    api("ch.qos.logback:logback-classic:$logback")
    api("ch.qos.logback:logback-core:$logback")
    api("io.github.microutils:kotlin-logging-jvm:$klogging")

    constraints {
        // Platform modules.
        api("org.jetbrains.kotlin:kotlin-reflect:$kotlin")
        api("org.jetbrains.kotlinx:kotlinx-serialization-core:$kotlinSerialization")
        api("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinSerialization")
        api("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:$kotlinSerialization")
        api("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:$kotlinCoroutines")
        api("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:$kotlinCoroutines")
        api("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$kotlinCoroutines")
        api("org.jetbrains.kotlinx:kotlinx-coroutines-jdk9:$kotlinCoroutines")

        // Misc libraries.
        api("com.google.guava:guava:$guava")
        api("com.github.ben-manes.caffeine:caffeine:$caffeineCache")
        api("org.postgresql:postgresql:$postgresqlDriver")
        api("cool.graph:cuid-java:$cuid")
        api("org.apache.commons:commons-lang3:$commonsLang")
        api("org.bouncycastle:bcprov-jdk15on:$bouncycastle")
        api("org.bouncycastle:bcprov-ext-jdk15on:$bouncycastle")
        api("org.bouncycastle:bcpkix-jdk15on:$bouncycastle")

        // Testing libraries
        api("org.jetbrains.kotlinx:kotlinx-coroutines-debug:$kotlinCoroutines")
        api("org.jetbrains.kotlinx:kotlinx-coroutines-test:$kotlinCoroutines")
        api("io.mockk:mockk:$mockk")
        api("com.willowtreeapps.assertk:assertk:$assertk")
        api("com.willowtreeapps.assertk:assertk-jvm:$assertk")
        api("io.aeris-consulting:catadioptre-kotlin:$catadioptre")
        api("io.aeris-consulting:catadioptre-annotations:$catadioptre")
        api("org.awaitility:awaitility-kotlin:$awaitility")
    }
}

publishing {
    publications {
        create<MavenPublication>("qalipsisDevPlatform") {
            from(components["javaPlatform"])
        }
    }
}