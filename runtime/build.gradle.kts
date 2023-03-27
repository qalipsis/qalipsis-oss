/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
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
import org.apache.tools.ant.taskdefs.condition.Os

plugins {
    kotlin("jvm")
    kotlin("kapt")
    kotlin("plugin.allopen")
    `java-test-fixtures`
}

description = "Qalipsis Runtime"

allOpen {
    annotations(
        "io.micronaut.aop.Around",
        "jakarta.inject.Singleton",
        "io.qalipsis.api.annotations.StepConverter",
        "io.qalipsis.api.annotations.StepDecorator",
        "io.qalipsis.api.annotations.PluginComponent",
        "io.micronaut.validation.Validated"
    )
}

val apiVersion: String by project

dependencies {
    implementation(platform("io.qalipsis:qalipsis-dev-platform:$apiVersion"))
    compileOnly("org.graalvm.nativeimage:svm")

    api("io.qalipsis:qalipsis-api-common:$apiVersion")
    api("io.qalipsis:qalipsis-api-dsl:$apiVersion")
    api("io.qalipsis:qalipsis-api-processors:$apiVersion")
    api(project(":core"))
    compileOnly(project(":factory"))
    compileOnly(project(":head"))

    api("ch.qos.logback:logback-classic")
    api("info.picocli:picocli")
    api("io.micronaut.picocli:micronaut-picocli")
    api("javax.annotation:javax.annotation-api")
    api("io.micronaut.micrometer:micronaut-micrometer-core")
    api("io.micronaut:micronaut-management")
    api("io.micronaut:micronaut-validation")
    api("io.micronaut:micronaut-runtime")
    api("io.micronaut.cache:micronaut-cache-core")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf")
    implementation("net.logstash.logback:logstash-logback-encoder:7.2")

    kapt(platform("io.qalipsis:qalipsis-dev-platform:$apiVersion"))
    kapt("io.micronaut:micronaut-inject-java")
    kapt("io.micronaut:micronaut-validation")
    kapt("io.micronaut:micronaut-graal")

    testFixturesImplementation(platform("io.qalipsis:qalipsis-dev-platform:$apiVersion"))
    testFixturesImplementation("io.aeris-consulting:catadioptre-kotlin")

    testImplementation(platform("io.qalipsis:qalipsis-dev-platform:$apiVersion"))
    testImplementation(project(":head"))
    testImplementation(project(":factory"))
    testImplementation(testFixtures(project(":core")))
    testImplementation("io.mockk:mockk")
    testImplementation("io.qalipsis:qalipsis-test:$apiVersion")
    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testImplementation("javax.annotation:javax.annotation-api")
    testImplementation("io.micronaut:micronaut-runtime")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.awaitility:awaitility-kotlin")

    kaptTest(platform("io.qalipsis:qalipsis-dev-platform:$apiVersion"))
    kaptTest("io.micronaut:micronaut-inject-java")
    kaptTest("io.qalipsis:qalipsis-api-processors:$apiVersion")

    if (Os.isFamily(Os.FAMILY_MAC)) {
        testRuntimeOnly(group = "io.netty", name = "netty-resolver-dns-native-macos", classifier = "osx-aarch_64")
    }
}

task<JavaExec>("runQalipsis") {
    group = "application"
    description = "Starts QALIPSIS standalone, for a PostgreSQL"
    mainClass.set("io.qalipsis.runtime.Qalipsis")
    maxHeapSize = "256m"
    args(
        "--persistent",
        "-e", "api-documentation"
    )
    classpath =
        sourceSets["main"].runtimeClasspath +
                files("build/classes/kotlin/test", "build/classes/java/test", "build/tmp/kapt3/classes/test") +
                project(":head").sourceSets["main"].runtimeClasspath +
                project(":factory").sourceSets["main"].runtimeClasspath
    workingDir = File(projectDir, "workdir")

    dependsOn("testClasses")
}