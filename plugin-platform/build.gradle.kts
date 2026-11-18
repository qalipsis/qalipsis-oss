/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
plugins {
    `java-platform`
}

javaPlatform {
    allowDependencies()
}

description = "QALIPSIS Plugin development platform"

dependencies {
    // Platform modules.
    api(platform(project(":qalipsis-dev-platform")))

    constraints {
        // API modules.
        api(project(":qalipsis-api-dsl"))
        api(project(":qalipsis-api-common"))
        api(project(":qalipsis-api-dev"))
        api(project(":qalipsis-api-processors"))
        api(project(":qalipsis-test"))

        // Core modules.
        api(project(":qalipsis-runtime"))
        api(project(":qalipsis-head"))
        api(project(":qalipsis-factory"))
    }
}

publishing {
    publications {
        create<MavenPublication>("qalipsisPluginPlatform") {
            pom {
                name.set(project.name)
                description.set(project.description)
                url.set("https://qalipsis.io")
                licenses {
                    license {
                        name.set("Apache License, Version 2.0 (Apache-2.0)")
                        url.set("https://opensource.org/license/apache-2-0")
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

            from(components["javaPlatform"])
        }
    }
}