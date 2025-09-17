/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
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
    `java-library`
    id("com.github.node-gradle.node") version "7.0.2"
}

description = "QALIPSIS Web OSS"
version = File(rootDir, "project.version").readText().trim().lowercase()

node {
    version = "20.11.0"
    yarnVersion = "1.22.19"
    download = true
}

sourceSets {
    main {
        resources {
            //srcDir(".output/")
        }
    }
}

tasks {
    register("cleanNode", Delete::class.java) {
        group = "build"
        delete("node_modules", "package-lock.json", ".output", "dist")
    }

    register("cleanCache", Delete::class.java) {
        group = "build"
        delete(".nuxt")
    }

    named("clean") {
        dependsOn("cleanNode", "cleanCache")
    }

    register("website", com.github.gradle.node.yarn.task.YarnTask::class.java) {
        group = "build"
        args.set(listOf("generate-for-java"))
        dependsOn("npmInstall")
    }

    val copyWebSite = register<Copy>("copyWebSite") {
        dependsOn("website")
        from(".output")
        into(project.layout.buildDirectory.file("resources/main"))
        shouldRunAfter("processResources")
    }

    named<Jar>("jar") {
        dependsOn(copyWebSite)
    }

}