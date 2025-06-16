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
    `java-platform`
}

description = "Platform for QALIPSIS Core modules"

val kotlin = "1.9.25"
val kotlinCoroutines = "1.8.1"
val kotlinSerialization = "1.6.3"

val micronaut = "3.9.7"
val jackson = "2.18.4"
val klogging = "3.0.5"
val logback = "1.4.14"
val guava = "33.4.8-jre"
val caffeineCache = "3.2.0"
val cuid = "0.1.1"
val commonsLang = "3.17.0"

val netty = "4.2.1.Final"
val bouncycastle = "1.70"
val postgresqlDriver = "42.3.1"

val testContainers = "1.21.1"
val mockk = "1.14.2"
val catadioptre = "0.6.3"
val awaitility = "4.2.2"
val assertk = "0.28.1"

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
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$kotlinCoroutines")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:$kotlinCoroutines")

    // Libraries that could be brought by other dependencies and break the compilation or execution.
    api("org.slf4j:slf4j-api")
    api("ch.qos.logback:logback-classic:$logback")
    api("ch.qos.logback:logback-core:$logback")
    api("io.github.microutils:kotlin-logging-jvm:$klogging")

    constraints {
        // Platform modules.
        api("org.jetbrains.kotlin:kotlin-reflect:$kotlin")
        api("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:$kotlinSerialization")
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
            pom {
                name.set(project.name)
                description.set(project.description)
                url.set("https://qalipsis.io")
                licenses {
                    license {
                        name.set("Apache License, Version 2.0 (Apache-2.0)")
                        url.set("http://https://opensource.org/license/apache-2-0")
                    }
                }
                developers {
                    developer {
                        id.set("ericjesse")
                        name.set("Eric Jessé")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/qalipsis/qalipsis-api.git")
                    url.set("https://github.com/qalipsis/qalipsis-api.git/")
                }
            }
        }
    }
}