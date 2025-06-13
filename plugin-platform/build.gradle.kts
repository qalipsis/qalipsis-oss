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
plugins {
    `java-platform`
}

javaPlatform {
    allowDependencies()
}

description = "QALIPSIS Plugin development platform"

val apiVersion: String by project

dependencies {
    // Platform modules.
    api(platform("io.qalipsis:qalipsis-dev-platform:$apiVersion"))

    constraints {
        // API modules.
        api("io.qalipsis:qalipsis-api-dsl:$apiVersion")
        api("io.qalipsis:qalipsis-api-common:$apiVersion")
        api("io.qalipsis:qalipsis-api-dev:$apiVersion")
        api("io.qalipsis:qalipsis-api-processors:$apiVersion")
        api("io.qalipsis:qalipsis-test:$apiVersion")

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

            from(components["javaPlatform"])
        }
    }
}