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
}

description = "QALIPSIS Test Utils"

dependencies {
    implementation(platform(project(":qalipsis-dev-platform")))
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation(project(":qalipsis-api-common"))

    api("io.micronaut.test:micronaut-test-junit5")
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
