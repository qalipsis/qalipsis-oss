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

import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR
import org.gradle.api.tasks.testing.logging.TestLogEvent.STARTED

plugins {
    idea
    java
    kotlin("jvm") version "1.6.21"
    kotlin("kapt") version "1.6.21"
    kotlin("plugin.allopen") version "1.6.21"
    kotlin("plugin.serialization") version "1.6.21"
    kotlin("plugin.noarg") version "1.6.21"
    `maven-publish`
    signing
}

/**
 * Target version of the generated JVM bytecode.
 */
val target = JavaVersion.VERSION_11

configure<JavaPluginConvention> {
    description = "Qalipsis Engine"

    sourceCompatibility = target
    targetCompatibility = target
}

tasks.withType<Wrapper> {
    distributionType = Wrapper.DistributionType.BIN
    gradleVersion = "6.8.1"
}

val testNumCpuCore: String? by project

allprojects {
    group = "io.qalipsis"
    version = File(rootDir, "project.version").readText().trim()
    apply(plugin = "signing")
    apply(plugin = "maven-publish")

    repositories {
        mavenLocal()
        mavenCentral()
        maven {
            name = "maven-central-snapshots"
            setUrl("https://oss.sonatype.org/content/repositories/snapshots")
        }
    }

    signing {
        publishing.publications.forEach { sign(it) }
    }

    val ossrhUsername: String? by project
    val ossrhPassword: String? by project
    publishing {
        repositories {
            maven {
                val releasesRepoUrl = "https://oss.sonatype.org/service/local/staging/deploy/maven2/"
                val snapshotsRepoUrl = "https://oss.sonatype.org/content/repositories/snapshots/"
                name = "sonatype"
                // See https://docs.gradle.org/current/userguide/single_versions.html#version_ordering.
                url = uri(if (project.version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl)
                credentials {
                    username = ossrhUsername
                    password = ossrhPassword
                }
            }
        }
    }

    if (isNotPlatform()) {
        logger.lifecycle("Applying the Java configuration on $name")
        configureNotPlatform()
    } else {
        logger.lifecycle("Ignoring the Java configuration on $name")
    }
}


/**
 * Verifies whether a Gradle project is a Java platform module.
 */
fun Project.isNotPlatform() = !name.contains("platform")

/**
 * Applies the configuration for non-platform modules.
 */
fun Project.configureNotPlatform() {
    apply(plugin = "java")

    configure<JavaPluginConvention> {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    publishing {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
                pom {

                    name.set(this@configureNotPlatform.name)
                    description.set(this@configureNotPlatform.description)
                    url.set("https://qalipsis.io")
                    licenses {
                        license {
                            name.set("GNU AFFERO GENERAL PUBLIC LICENSE, Version 3 (AGPL-3.0)")
                            url.set("http://opensource.org/licenses/AGPL-3.0")
                        }
                    }
                    developers {
                        developer {
                            id.set("ericjesse")
                            name.set("Eric Jessé")
                        }
                    }
                    scm {
                        url.set("https://github.com/qalipsis/qalipsis-oss.git/")
                    }
                }
            }
        }
    }

    tasks {
        withType<Jar> {
            archiveBaseName.set(project.name)
        }

        withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            kotlinOptions {
                jvmTarget = target.majorVersion
                freeCompilerArgs += listOf(
                    "-Xopt-in=kotlin.RequiresOptIn",
                    "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                    "-Xopt-in=kotlinx.coroutines.ObsoleteCoroutinesApi",
                    "-Xemit-jvm-type-annotations"
                )
            }
        }

        val replacedPropertiesInResources = mapOf("project.version" to project.version)
        withType<ProcessResources> {
            filter<org.apache.tools.ant.filters.ReplaceTokens>("tokens" to replacedPropertiesInResources)
        }

        named<Test>("test") {
            ignoreFailures = System.getProperty("ignoreUnitTestFailures", "false").toBoolean()
            exclude("**/*IntegrationTest*", "**/*IntegrationTest$*")
        }

        val integrationTest = register<Test>("integrationTest") {
            this.group = "verification"
            ignoreFailures = System.getProperty("ignoreIntegrationTestFailures", "false").toBoolean()
            include("**/*IntegrationTest*", "**/*IntegrationTest$*", "**/*IntegrationTest.**")
        }

        named<Task>("check") {
            dependsOn(integrationTest.get())
        }

        if (!project.file("src/main/kotlin").isDirectory) {
            project.logger.lifecycle("Disabling publish for ${project.name}")
            withType<AbstractPublishToMaven> {
                enabled = false
            }
        }

        withType<Test> {
            // Simulates the execution of the tests with a given number of CPUs.
            if (!testNumCpuCore.isNullOrBlank()) {
                project.logger.lifecycle("Running tests of ${project.name} with $testNumCpuCore cores")
                jvmArgs("-XX:ActiveProcessorCount=$testNumCpuCore")
            }
            useJUnitPlatform()
            testLogging {
                events(STARTED, FAILED, STANDARD_ERROR)
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL

                debug {
                    events(*org.gradle.api.tasks.testing.logging.TestLogEvent.values())
                    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                }

                info {
                    events(*org.gradle.api.tasks.testing.logging.TestLogEvent.values())
                    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                }
            }
        }

        artifacts {
            if (project.plugins.hasPlugin("java-test-fixtures")) {
                archives(findByName("testFixturesSources") as Jar)
                archives(findByName("testFixturesJavadoc") as Jar)
                archives(findByName("testFixturesJar") as Jar)
            }
        }
    }

}

val testTasks = subprojects.flatMap {
    val testTasks = mutableListOf<Test>()
    (it.tasks.findByName("test") as Test?)?.apply {
        testTasks.add(this)
    }
    (it.tasks.findByName("integrationTest") as Test?)?.apply {
        testTasks.add(this)
    }
    testTasks
}

tasks.register("displayVersion") {
    this.group = "help"
    doLast {
        logger.lifecycle("Project version: ${project.version}")
    }
}

tasks.register("testReport", TestReport::class) {
    this.group = "verification"
    destinationDir = file("${buildDir}/reports/tests")
    reportOn(*(testTasks.toTypedArray()))
}
