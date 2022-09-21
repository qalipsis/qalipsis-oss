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

dependencies {
    implementation(platform("io.qalipsis:dev-platform:${project.version}"))
    compileOnly("org.graalvm.nativeimage:svm")

    api("io.qalipsis:api-common:${project.version}")
    api("io.qalipsis:api-dsl:${project.version}")
    api("io.qalipsis:api-processors:${project.version}")
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

    kapt(platform("io.qalipsis:dev-platform:${project.version}"))
    kapt("io.micronaut:micronaut-inject-java")
    kapt("io.micronaut:micronaut-validation")
    kapt("io.micronaut:micronaut-graal")

    testFixturesImplementation(platform("io.qalipsis:dev-platform:${project.version}"))
    testFixturesImplementation("io.aeris-consulting:catadioptre-kotlin")

    testImplementation(platform("io.qalipsis:dev-platform:${project.version}"))
    testImplementation(project(":head"))
    testImplementation(project(":factory"))
    testImplementation(testFixtures(project(":core")))
    testImplementation("io.mockk:mockk")
    testImplementation("io.qalipsis:test:${project.version}")
    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testImplementation("javax.annotation:javax.annotation-api")
    testImplementation("io.micronaut:micronaut-runtime")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.awaitility:awaitility-kotlin")

    kaptTest(platform("io.qalipsis:dev-platform:${project.version}"))
    kaptTest("io.micronaut:micronaut-inject-java")
    kaptTest("io.qalipsis:api-processors:${project.version}")
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