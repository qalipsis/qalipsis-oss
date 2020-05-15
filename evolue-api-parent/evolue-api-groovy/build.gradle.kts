plugins {
    groovy
    java
}

description = "Evolue API Groovy DSL"

repositories {
    mavenCentral()
}

dependencies {
    compile("org.codehaus.groovy:groovy-all:2.3.11")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_11
}