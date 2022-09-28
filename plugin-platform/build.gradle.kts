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

dependencies {
    // Platform modules.
    api(platform("io.qalipsis:dev-platform:${project.version}"))

    // API modules.
    api("io.qalipsis:api-dsl:${project.version}")
    api("io.qalipsis:api-common:${project.version}")

    constraints {
        // API modules.
        api("io.qalipsis:api-dev:${project.version}")
        api("io.qalipsis:api-processors:${project.version}")
        api("io.qalipsis:test:${project.version}")

        // Core modules.
        api(project(":runtime"))
        api(project(":head"))
        api(project(":factory"))
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