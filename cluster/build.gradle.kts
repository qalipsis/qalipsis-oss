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

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("kapt")
}

description = "QALIPSIS shared library for the services implied in an advanced cluster"

kapt {
    correctErrorTypes = true
    useBuildCache = false
}

// Configure both compileKotlin and compileTestKotlin.
tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.majorVersion
        javaParameters = true
        freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn,kotlinx.coroutines.ObsoleteCoroutinesApi"
    }
}

kotlin.sourceSets["test"].kotlin.srcDir("build/generated/source/kaptKotlin/catadioptre")
kapt {
    correctErrorTypes = true
    useBuildCache = false
}

val apiVersion: String by project

dependencies {
    implementation(platform("io.qalipsis:qalipsis-dev-platform:$apiVersion"))
    implementation("io.qalipsis:qalipsis-api-dev:$apiVersion")

    compileOnly("io.swagger.core.v3:swagger-annotations")
    compileOnly("io.micronaut:micronaut-core")
    compileOnly("io.micronaut:micronaut-inject-java")
    compileOnly("io.micronaut:micronaut-validation")

    kapt(platform("io.qalipsis:qalipsis-dev-platform:$apiVersion"))
    kapt("io.micronaut:micronaut-inject-java")
    kapt("io.micronaut:micronaut-validation")
    kapt("io.micronaut:micronaut-graal")

}

