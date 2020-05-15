plugins {
    java
}

configure<JavaPluginConvention> {
    description = "Evolue"

    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11

}

allprojects {
    group = "io.evolue"
    version = File(rootDir, "project.version").readText().trim()

    repositories {
        mavenLocal()
        mavenCentral()
        jcenter()
        maven {
            setUrl("http://rubygems-proxy.torquebox.org/releases")
        }
    }
}

tasks {
    "wrapper"(Wrapper::class) {
        distributionType = Wrapper.DistributionType.ALL
        gradleVersion = "6.1.1"
    }
}
