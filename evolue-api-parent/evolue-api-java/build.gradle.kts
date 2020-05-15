plugins {
    java
}

description = "Evolue API for Java"

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    compile(project(":evolue-api-parent:evolue-api-common"))

    testCompile(
            "org.projectlombok:lombok:1.18.10"
    )
    testAnnotationProcessor(
            "org.projectlombok:lombok:1.18.10"
    )
}