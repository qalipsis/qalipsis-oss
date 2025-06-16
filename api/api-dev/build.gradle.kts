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

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("kapt")
    `java-test-fixtures`
}

description = "Components to support QALIPSIS API development"

kapt {
    useBuildCache = false
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.majorVersion
        javaParameters = true
        freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn,kotlinx.coroutines.ExperimentalCoroutinesApi"
    }
}

kotlin.sourceSets["test"].kotlin.srcDir(layout.buildDirectory.dir("generated/source/kaptKotlin/catadioptre"))
kapt.useBuildCache = false

dependencies {
    implementation(platform(project(":qalipsis-dev-platform")))
    compileOnly("io.aeris-consulting:catadioptre-annotations")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8")
    compileOnly("io.micronaut:micronaut-runtime")

    testImplementation(project(":qalipsis-test"))
    testImplementation("io.aeris-consulting:catadioptre-kotlin")
    kapt(platform(project(":qalipsis-dev-platform")))
    kapt("io.aeris-consulting:catadioptre-annotations")
}
