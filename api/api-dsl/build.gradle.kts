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
    kotlin("plugin.allopen")
    `java-test-fixtures`
}

description = "QALIPSIS API Kotlin DSL"

kapt {
    useBuildCache = false
}

allOpen {
    annotations(
        "io.micronaut.aop.Around",
        "io.qalipsis.api.annotations.Spec"
    )
}

kotlin.sourceSets["test"].kotlin.srcDir("build/generated/source/kaptKotlin/catadioptre")
kapt.useBuildCache = false

dependencies {
    implementation(platform(project(":qalipsis-dev-platform")))
    compileOnly("org.graalvm.nativeimage:svm")
    compileOnly("io.aeris-consulting:catadioptre-annotations")

    implementation(project(":qalipsis-api-dev"))
    implementation("cool.graph:cuid-java")
    implementation("javax.annotation:javax.annotation-api")
    implementation("io.micronaut:micronaut-inject-java")
    implementation("io.micronaut:micronaut-validation")

    // Required to make the scenario projects build.
    api("io.micronaut:micronaut-runtime")

    kapt(platform(project(":qalipsis-dev-platform")))
    kapt("io.micronaut:micronaut-inject-java")
    kapt("io.micronaut:micronaut-validation")
    kapt("io.micronaut:micronaut-graal")
    kapt("io.aeris-consulting:catadioptre-annotations")

    testImplementation(project(":qalipsis-test"))
    testImplementation(testFixtures(project(":qalipsis-api-common")))
    testImplementation("io.aeris-consulting:catadioptre-kotlin")

    testFixturesApi(platform(project(":qalipsis-dev-platform")))
    testFixturesImplementation(testFixtures(project(":qalipsis-api-common")))
}
