import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR

plugins {
    java
    kotlin("jvm") version "1.4.0"
    kotlin("kapt") version "1.4.0"
    kotlin("plugin.allopen") version "1.4.0"
    id("net.ltgt.apt") version "0.21" apply false
}

configure<JavaPluginConvention> {
    description = "Evolue"

    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<Wrapper> {
    distributionType = Wrapper.DistributionType.ALL
}

allprojects {
    group = "io.evolue"
    version = File(rootDir, "project.version").readText().trim()

    apply(plugin = "java")
    apply(plugin = "net.ltgt.apt")

    repositories {
        mavenLocal()
        mavenCentral()
        jcenter()
        maven {
            name = "bintray"
            setUrl("https://jcenter.bintray.com")
        }
        maven {
            name = "rubygems"
            setUrl("http://rubygems-proxy.torquebox.org/releases")
        }
    }

    configure<JavaPluginConvention> {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    tasks.named("test", Test::class) {
        ignoreFailures = System.getProperty("ignoreUnitTestFailures", "false").toBoolean()
        this.exclude("**/*IntegrationTest.*", "**/*IntegrationTest$*")
    }

    tasks.register("integrationTest", Test::class) {
        this.group = "verification"
        ignoreFailures = System.getProperty("ignoreIntegrationTestFailures", "false").toBoolean()
        include("**/*IntegrationTest.*", "**/*IntegrationTest$*")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events(FAILED, STANDARD_ERROR)
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.SHORT

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
