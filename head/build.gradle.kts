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
    kotlin("plugin.serialization")
    kotlin("plugin.noarg")
}

description = "QALIPSIS Head microservice"

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

kotlin.sourceSets["test"].kotlin.srcDir("build/generated/source/kaptKotlin/catadioptre")
kapt.useBuildCache = false

val apiVersion: String by project

dependencies {
    implementation(platform("io.qalipsis:qalipsis-dev-platform:$apiVersion"))
    compileOnly("org.graalvm.nativeimage:svm")
    compileOnly("io.aeris-consulting:catadioptre-annotations")
    compileOnly("io.swagger.core.v3:swagger-annotations")

    implementation(project(":qalipsis-shared"))
    implementation(project(":qalipsis-cluster"))
    implementation("io.qalipsis:qalipsis-api-common:$apiVersion")
    implementation("io.qalipsis:qalipsis-api-dsl:$apiVersion")
    implementation("io.qalipsis:qalipsis-api-processors:$apiVersion")
    implementation("com.varabyte.kotter:kotter-jvm:1.+")

    implementation("io.micronaut.security:micronaut-security")
    implementation("com.google.guava:guava")
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("cool.graph:cuid-java")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("io.micronaut.micrometer:micronaut-micrometer-core")
    compileOnly("io.micronaut.data:micronaut-data-jdbc")
    implementation("io.micronaut.data:micronaut-data-r2dbc")
    runtimeOnly("io.r2dbc:r2dbc-pool")
    runtimeOnly("org.postgresql:r2dbc-postgresql")
    runtimeOnly("io.micronaut.liquibase:micronaut-liquibase")
    runtimeOnly("io.micronaut.sql:micronaut-jdbc-hikari")
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf")
    implementation("javax.annotation:javax.annotation-api")
    implementation("io.micronaut.cache:micronaut-cache-caffeine")
    implementation("io.micronaut:micronaut-validation")
    implementation("io.micronaut:micronaut-runtime")
    implementation("io.micronaut:micronaut-http-server-netty")
    implementation("io.micronaut:micronaut-http-client")
    implementation("io.micronaut.reactor:micronaut-reactor")
    implementation("io.micronaut.kotlin:micronaut-kotlin-runtime")
    implementation("io.micronaut.kotlin:micronaut-kotlin-extension-functions")
    implementation("io.micronaut.rxjava3:micronaut-rxjava3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.module:jackson-module-paranamer")
    implementation("org.apache.commons:commons-lang3")
    implementation("org.thymeleaf:thymeleaf:3.1.1.RELEASE")
    implementation("io.micronaut.views:micronaut-views-thymeleaf")
    implementation("org.xhtmlrenderer:flying-saucer-pdf:9.1.22")
    implementation("org.xhtmlrenderer:flying-saucer-core")
    implementation("org.jsoup:jsoup")
    implementation("org.apache.xmlgraphics:batik-transcoder:1.17")
    implementation("org.thymeleaf.extras:thymeleaf-extras-java8time")
    implementation("org.jfree:jfreechart:1.0.19")
    implementation("org.jfree:org.jfree.svg:5.0.5")
    kapt(platform("io.qalipsis:qalipsis-dev-platform:$apiVersion"))
    kapt("io.micronaut:micronaut-inject-java")
    kapt("io.micronaut:micronaut-validation")
    kapt("io.micronaut:micronaut-graal")
    kapt("io.qalipsis:qalipsis-api-processors:$apiVersion")
    kapt("io.aeris-consulting:catadioptre-annotations")
    kapt("io.micronaut.data:micronaut-data-processor")
    kapt("io.micronaut.openapi:micronaut-openapi")
    kapt("org.jetbrains.kotlinx:kotlinx-serialization-protobuf")

    testImplementation(platform("io.qalipsis:qalipsis-dev-platform:$apiVersion"))
    testImplementation("io.qalipsis:qalipsis-test:$apiVersion")
    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testImplementation("javax.annotation:javax.annotation-api")
    testImplementation("io.micronaut:micronaut-runtime")
    testImplementation("io.aeris-consulting:catadioptre-kotlin")
    testImplementation(testFixtures("io.qalipsis:qalipsis-api-dsl:$apiVersion"))
    testImplementation(testFixtures("io.qalipsis:qalipsis-api-common:$apiVersion"))
    testImplementation(testFixtures(project(":qalipsis-runtime")))
    testImplementation(testFixtures(project(":qalipsis-shared")))
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:postgresql")

    kaptTest(platform("io.qalipsis:qalipsis-dev-platform:$apiVersion"))
    kaptTest("io.micronaut:micronaut-inject-java")
    kaptTest("io.qalipsis:qalipsis-api-processors:$apiVersion")
}

