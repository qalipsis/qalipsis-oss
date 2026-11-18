/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is dual-licensed: you may use, modify and distribute it under the terms of either
 *
 *   1. the GNU Affero General Public License as published by the Free Software Foundation,
 *      either version 3 of the License, or (at your option) any later version; or
 *   2. a commercial license agreement concluded with AERIS IT Solutions GmbH.
 *
 * Under the GNU Affero General Public License, this program is distributed in the hope that it
 * will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * For the commercial license, see LICENSE-COMMERCIAL.md at the root of the repository.
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
        freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn,kotlinx.coroutines.ObsoleteCoroutinesApi"
    }
}

kotlin.sourceSets["test"].kotlin.srcDir("build/generated/source/kaptKotlin/catadioptre")
kapt {
    correctErrorTypes = true
    useBuildCache = false
}

dependencies {
    api(platform(project(":qalipsis-dev-platform")))

    implementation(project(":qalipsis-api-dev"))

    compileOnly("io.swagger.core.v3:swagger-annotations")
    compileOnly("io.micronaut:micronaut-core")
    compileOnly("io.micronaut:micronaut-http-server-netty")
    compileOnly("io.micronaut:micronaut-inject-java")
    compileOnly("io.micronaut:micronaut-validation")

    kapt(platform(project(":qalipsis-dev-platform")))
    kapt("io.micronaut:micronaut-inject-java")
    kapt("io.micronaut:micronaut-validation")
    kapt("io.micronaut:micronaut-graal")

}

