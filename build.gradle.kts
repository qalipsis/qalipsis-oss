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
import org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR
import org.jetbrains.kotlin.gradle.plugin.extraProperties
import org.jreleaser.model.Active
import org.jreleaser.model.Signing
import org.jreleaser.model.api.deploy.maven.MavenCentralMavenDeployer


plugins {
    idea
    kotlin("jvm") version "1.9.25"
    kotlin("kapt") version "1.9.25"
    kotlin("plugin.allopen") version "1.9.25"
    kotlin("plugin.serialization") version "1.9.25"
    kotlin("plugin.noarg") version "1.9.25"
    `maven-publish`
    id("org.jreleaser") version "1.18.0"
    id("com.github.jk1.dependency-license-report") version "2.9"
    id("com.palantir.git-version") version "3.0.0"
}

description = "QALIPSIS OSS"

repositories {
    mavenLocal()
    mavenCentral()
}

licenseReport {
    renderers = arrayOf<com.github.jk1.license.render.ReportRenderer>(
        com.github.jk1.license.render.InventoryHtmlReportRenderer(
            "report.html",
            "Qalipsis OSS"
        )
    )
    allowedLicensesFile = File("$projectDir/build-config/allowed-licenses.json")
    filters =
        arrayOf<com.github.jk1.license.filter.DependencyFilter>(com.github.jk1.license.filter.LicenseBundleNormalizer())
}

tasks.withType<Wrapper> {
    distributionType = Wrapper.DistributionType.BIN
    gradleVersion = "8.14.1"
}

val testNumCpuCore: String? by project

jreleaser {
    gitRootSearch.set(true)

    release {
        // One least one enabled release provider is mandatory, so let's use Github and disable
        // all the options.
        github {
            skipRelease.set(true)
            skipTag.set(true)
            uploadAssets.set(Active.NEVER)
            token.set("dummy")
        }
    }

    val enableSign = !extraProperties.has("qalipsis.sign") || extraProperties.get("qalipsis.sign") != "false"
    if (enableSign) {
        signing {
            active.set(Active.ALWAYS)
            mode.set(Signing.Mode.MEMORY)
            armored = true
        }
    }

    deploy {
        maven {
            mavenCentral {
                register("qalipsis-releases") {
                    active.set(Active.RELEASE_PRERELEASE)
                    namespace.set("io.qalipsis")
                    applyMavenCentralRules.set(true)
                    stage.set(MavenCentralMavenDeployer.Stage.UPLOAD)
                    stagingRepository(layout.buildDirectory.dir("staging-deploy").get().asFile.path)
                }
            }
            nexus2 {
                register("qalipsis-snapshots") {
                    active.set(Active.SNAPSHOT)
                    url.set("https://central.sonatype.com/repository/maven-snapshots/")
                    snapshotUrl.set("https://central.sonatype.com/repository/maven-snapshots/")
                    applyMavenCentralRules.set(true)
                    verifyPom.set(false)
                    snapshotSupported.set(true)
                    closeRepository.set(true)
                    releaseRepository.set(true)
                    stagingRepository(layout.buildDirectory.dir("staging-deploy").get().asFile.path)
                }
            }
        }
    }
}

allprojects {
    group = "io.qalipsis"
    version = File(rootDir, "project.version").readText().trim()

    apply(plugin = "maven-publish")
    apply(plugin = "com.palantir.git-version")

    repositories {
        mavenLocal()
        if (version.toString().endsWith("-SNAPSHOT")) {
            maven {
                name = "Central Portal Snapshots"
                url = uri("https://central.sonatype.com/repository/maven-snapshots")
                content {
                    includeGroup("io.qalipsis")
                }
            }
        }
        mavenCentral()
    }

    // https://jreleaser.org/guide/latest/examples/maven/maven-central.html#_gradle
    publishing {
        repositories {
            maven {
                // Local repository to store the artifacts before they are released by JReleaser.
                name = "PreRelease"
                setUrl(rootProject.layout.buildDirectory.dir("staging-deploy"))
            }
        }
    }

    if (isNotPlatform()) {
        configureNotPlatform()
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
    apply(plugin = "org.jetbrains.kotlin.jvm")

    kotlin {
        javaToolchains {
            jvmToolchain(11)
        }
    }

    java {
        withJavadocJar()
        withSourcesJar()
    }
    val project = this
    tasks {
        withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            kotlinOptions {
                javaParameters = true
                freeCompilerArgs += listOf(
                    "-opt-in=kotlin.RequiresOptIn",
                    "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                    "-opt-in=kotlinx.coroutines.ObsoleteCoroutinesApi",
                    "-Xemit-jvm-type-annotations"
                )
            }
        }

        val versionDetails: groovy.lang.Closure<com.palantir.gradle.gitversion.VersionDetails> by extra
        val gitDetails = versionDetails()
        val replacedPropertiesInResources = mapOf(
            "project.version" to project.version,
            "git.commit" to gitDetails.gitHash,
            "git.branch" to (gitDetails.branchName ?: "<Unknown>")
        )
        withType<ProcessResources> {
            filter<org.apache.tools.ant.filters.ReplaceTokens>("tokens" to replacedPropertiesInResources)
        }

        named<Test>("test") {
            ignoreFailures = System.getProperty("ignoreUnitTestFailures", "false").toBoolean()
            exclude("**/*IntegrationTest*", "**/*IntegrationTest$*")
        }

        register<Test>("integrationTest") {
            this.group = "verification"
            ignoreFailures = System.getProperty("ignoreIntegrationTestFailures", "false").toBoolean()
            include("**/*IntegrationTest*", "**/*IntegrationTest$*", "**/*IntegrationTest.**")
        }

        // Enable the plugin after the creation of the integration test to auto-configure it.
        apply(plugin = "jacoco")

        named<JacocoReport>("jacocoTestReport") {
            reports {
                html.required.set(true)
                html.outputLocation.set(layout.buildDirectory.dir("reports/coverage"))
            }
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
                events(FAILED, STANDARD_ERROR)
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL

                debug {
                    events(*org.gradle.api.tasks.testing.logging.TestLogEvent.values())
                    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                }

                info {
                    events(FAILED, SKIPPED, PASSED, STANDARD_ERROR)
                    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                }
            }
        }

        if (project.plugins.hasPlugin("java-test-fixtures")) {
            artifacts {
                archives(findByName("testFixturesSources") as Jar)
                archives(findByName("testFixturesJavadoc") as Jar)
                archives(findByName("testFixturesJar") as Jar)
            }
        }
    }

    project.afterEvaluate {
        publishing {
            publications {
                create<MavenPublication>("maven") {
                    from(components["java"])
                    pom {

                        name.set(this@configureNotPlatform.name)
                        description.set(this@configureNotPlatform.description)

                        if (version.toString().endsWith("-SNAPSHOT")) {
                            this.withXml {
                                this.asNode().appendNode("distributionManagement").appendNode("repository").apply {
                                    this.appendNode("id", "central-snapshots")
                                    this.appendNode("name", "Central Portal Snapshots")
                                    this.appendNode("url", "https://central.sonatype.com/repository/maven-snapshots")
                                }
                            }
                        }
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
                            connection.set("scm:git:https://github.com/qalipsis/qalipsis-oss.git")
                            url.set("https://github.com/qalipsis/qalipsis-oss.git/")
                        }
                    }
                }
            }
        }
    }
}
tasks.register("displayVersion") {
    group = "help"
    description = "Displays the current version of QALIPSIS"
    doLast {
        logger.lifecycle("Project version: ${project.version}")
    }
}


val allTestTasks = subprojects.flatMap { p ->
    val testTasks = mutableListOf<Test>()
    (p.tasks.findByName("test") as Test?)?.apply {
        testTasks.add(this)
    }
    (p.tasks.findByName("integrationTest") as Test?)?.apply {
        testTasks.add(this)
    }
    testTasks
}

tasks.register("aggregatedTestReport", TestReport::class) {
    group = "documentation"
    description = "Create an aggregated test report"

    destinationDirectory.set(project.layout.buildDirectory.dir("reports/tests"))
    testResults.from(*(allTestTasks.toTypedArray()))
    dependsOn.clear()
}

val allCoverageTasks = subprojects.flatMap { p ->
    p.tasks.withType(JacocoReport::class.java)
}

tasks.register("aggregatedCoverageReport", JacocoReport::class) {
    group = "documentation"
    description = "Create an aggregated coverage report"

    sourceDirectories.from.addAll(allCoverageTasks.map { it.sourceDirectories })
    classDirectories.from.addAll(allCoverageTasks.map { it.classDirectories })
    executionData.from.addAll(allCoverageTasks.map { it.executionData })
    reports {
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/coverage"))
    }
}

