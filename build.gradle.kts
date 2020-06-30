plugins {
    java
    kotlin("jvm") version "1.3.72"
    kotlin("kapt") version "1.3.72"
    kotlin("plugin.allopen") version "1.3.72"
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

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    configure<JavaPluginConvention> {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    tasks.named("test", Test::class) {
        this.exclude("**/*IntegrationTest.*")
    }

    tasks.register("integrationTest", Test::class) {
        this.group = "verification"
        include("**/*IntegrationTest.*")
    }
}
