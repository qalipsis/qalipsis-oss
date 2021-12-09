import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR

plugins {
    idea
    java
    kotlin("jvm") version "1.5.31"
    kotlin("kapt") version "1.5.31"
    kotlin("plugin.allopen") version "1.5.31"
    kotlin("plugin.serialization") version "1.5.31"
    id("net.ltgt.apt") version "0.21" apply false

    id("nebula.contacts") version "5.1.0"
    id("nebula.info") version "9.1.1"
    id("nebula.maven-publish") version "17.0.0"
    id("nebula.maven-scm") version "17.0.0"
    id("nebula.maven-manifest") version "17.0.0"
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

    apply(plugin = "java")
    apply(plugin = "net.ltgt.apt")
    apply(plugin = "nebula.contacts")
    apply(plugin = "nebula.info")
    apply(plugin = "nebula.maven-publish")
    apply(plugin = "nebula.maven-scm")
    apply(plugin = "nebula.maven-manifest")
    apply(plugin = "nebula.maven-developer")
    apply(plugin = "signing")
    apply(plugin = "nebula.source-jar")

    infoBroker {
        excludedManifestProperties = listOf(
            "Manifest-Version", "Module-Owner", "Module-Email", "Module-Source",
            "Built-OS", "Build-Host", "Build-Job", "Build-Host", "Build-Job", "Build-Number", "Build-Id", "Build-Url",
            "Built-Status"
        )
    }

    contacts {
        addPerson("eric.jesse@aeris-consulting.com", delegateClosureOf<nebula.plugin.contacts.Contact> {
            moniker = "Eric Jessé"
            github = "ericjesse"
            role("Owner")
        })
    }

    repositories {
        mavenLocal()
        mavenCentral()
        jcenter()
        maven {
            name = "maven-central-snapshots"
            setUrl("https://oss.sonatype.org/content/repositories/snapshots")
        }
    }

    configure<JavaPluginConvention> {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
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

    tasks {
        withType<Jar> {
            archiveBaseName.set(project.name)
        }

        withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            kotlinOptions {
                jvmTarget = target.majorVersion
                freeCompilerArgs += listOf(
                    "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                    "-Xopt-in=kotlinx.coroutines.ObsoleteCoroutinesApi"
                )
            }
        }

        val replacedPropertiesInResources = mapOf("project.version" to project.version)
        withType<ProcessResources> {
            filter<org.apache.tools.ant.filters.ReplaceTokens>("tokens" to replacedPropertiesInResources)
        }

        named<Test>("test") {
            ignoreFailures = System.getProperty("ignoreUnitTestFailures", "false").toBoolean()
            this.exclude("**/*IntegrationTest.*", "**/*IntegrationTest$*")
        }

        val integrationTest = register<Test>("integrationTest") {
            this.group = "verification"
            ignoreFailures = System.getProperty("ignoreIntegrationTestFailures", "false").toBoolean()
            include("**/*IntegrationTest.*", "**/*IntegrationTest$*")
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

tasks.register("testReport", TestReport::class) {
    this.group = "verification"
    destinationDir = file("${buildDir}/reports/tests")
    reportOn(*(testTasks.toTypedArray()))
}
