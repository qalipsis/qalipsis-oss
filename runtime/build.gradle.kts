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

description = "QALIPSIS Runtime"

kapt {
    correctErrorTypes = true
    useBuildCache = false
}

allOpen {
    annotations(
        "io.micronaut.aop.Around",
        "jakarta.inject.Singleton",
        "io.micronaut.context.annotation.Factory",
        "io.qalipsis.api.annotations.StepConverter",
        "io.qalipsis.api.annotations.StepDecorator",
        "io.qalipsis.api.annotations.PluginComponent",
        "io.micronaut.context.annotation.Bean",
        "io.micronaut.validation.Validated",
        "org.openjdk.jmh.annotations.State"
    )
}

sourceSets.create("localRun")

repositories {
    maven {
        name = "qalipsis-oss-snapshots"
        setUrl("https://maven.qalipsis.com/repository/oss-snapshots/")
    }
}

dependencies {
    compileOnly("org.graalvm.nativeimage:svm")
    compileOnly(project(":qalipsis-factory"))
    compileOnly(project(":qalipsis-head"))

    api(project(":qalipsis-api-common"))
    api(project(":qalipsis-api-dsl"))
    api(project(":qalipsis-api-processors"))
    api(project(":qalipsis-shared"))

    implementation(platform(project(":qalipsis-dev-platform")))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf")

    api("ch.qos.logback:logback-classic")
    api("info.picocli:picocli")
    api("io.micronaut.picocli:micronaut-picocli")
    api("jakarta.annotation:jakarta.annotation-api")
    api("io.micronaut.micrometer:micronaut-micrometer-core")
    api("io.micronaut:micronaut-management")
    api("io.micronaut:micronaut-validation")
    api("io.micronaut:micronaut-runtime")
    api("io.micronaut.cache:micronaut-cache-core")

    kapt(platform(project(":qalipsis-dev-platform")))
    kapt("io.micronaut:micronaut-inject-java")
    kapt("io.micronaut:micronaut-validation")
    kapt("io.micronaut:micronaut-graal")

    testFixturesImplementation(platform(project(":qalipsis-dev-platform")))
    testFixturesImplementation("io.aeris-consulting:catadioptre-kotlin")

    testImplementation(platform(project(":qalipsis-dev-platform")))
    testImplementation(project(":qalipsis-head"))
    testImplementation(project(":qalipsis-factory"))
    testImplementation(testFixtures(project(":qalipsis-shared")))
    testImplementation("io.mockk:mockk")
    testImplementation(project(":qalipsis-test"))
    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testImplementation("jakarta.annotation:jakarta.annotation-api")
    testImplementation("io.micronaut:micronaut-runtime")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.awaitility:awaitility-kotlin") {
        exclude(group = "org.jetbrains.kotlin")
    }

    kaptTest(platform(project(":qalipsis-dev-platform")))
    kaptTest("io.micronaut:micronaut-inject-java")
    kaptTest(project(":qalipsis-api-processors"))

    if (Os.isFamily(Os.FAMILY_MAC)) {
        testRuntimeOnly(group = "io.netty", name = "netty-resolver-dns-native-macos", classifier = "osx-aarch_64")
    }

    "localRunRuntimeOnly"("io.qalipsis.plugin:qalipsis-plugin-timescaledb:0.14.+")
}

tasks {
    register<JavaExec>("runQalipsis") {
        group = "application"
        description = "Starts QALIPSIS standalone, for PostgreSQL"
        mainClass.set("io.qalipsis.runtime.Qalipsis")
        maxHeapSize = "256m"
        args(
            "--persistent",
            "-e", "api-documentation"
        )
        classpath =
            sourceSets["main"].runtimeClasspath + sourceSets["localRun"].runtimeClasspath +
                    files("build/classes/kotlin/test", "build/classes/java/test", "build/tmp/kapt3/classes/test") +
                    project(":qalipsis-head").sourceSets["main"].runtimeClasspath +
                    project(":qalipsis-factory").sourceSets["main"].runtimeClasspath
        workingDir = File(projectDir, "workdir")

        dependsOn("testClasses")
    }

    register<JavaExec>("runQalipsisWithGui") {
        group = "application"
        description = "Starts QALIPSIS standalone with the GUI, for PostgreSQL"
        mainClass.set("io.qalipsis.runtime.Qalipsis")
        maxHeapSize = "256m"
        args(
            "--persistent",
            "--gui",
            "-e", "api-documentation"
        )
        classpath =
            sourceSets["main"].runtimeClasspath + sourceSets["localRun"].runtimeClasspath +
                    files("build/classes/kotlin/test", "build/classes/java/test", "build/tmp/kapt3/classes/test") +
                    project(":qalipsis-head").sourceSets["main"].runtimeClasspath +
                    project(":qalipsis-factory").sourceSets["main"].runtimeClasspath
        workingDir = File(projectDir, "workdir")

        dependsOn("testClasses")
    }
}