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

rootProject.name = "qalipsis-oss"

include(
    ":qalipsis-dev-platform",
    ":qalipsis-api-dev",
    ":qalipsis-api-common",
    ":qalipsis-api-dsl",
    ":qalipsis-api-processors",
    ":qalipsis-test",

    ":qalipsis-shared",
    ":qalipsis-cluster",
    ":qalipsis-head",
    ":qalipsis-factory",
    ":qalipsis-runtime",
    ":qalipsis-plugin-platform",

    ":qalipsis-webapp"
)

val apiRootDir = rootDir.resolve("api")
project(":qalipsis-dev-platform").projectDir = apiRootDir.resolve("dev-platform")
project(":qalipsis-api-dev").projectDir = apiRootDir.resolve("api-dev")
project(":qalipsis-api-common").projectDir = apiRootDir.resolve("api-common")
project(":qalipsis-api-dsl").projectDir = apiRootDir.resolve("api-dsl")
project(":qalipsis-api-processors").projectDir = apiRootDir.resolve("api-processors")
project(":qalipsis-test").projectDir = apiRootDir.resolve("test")

project(":qalipsis-shared").projectDir = rootDir.resolve("shared")
project(":qalipsis-cluster").projectDir = rootDir.resolve("cluster")
project(":qalipsis-head").projectDir = rootDir.resolve("head")
project(":qalipsis-factory").projectDir = rootDir.resolve("factory")
project(":qalipsis-runtime").projectDir = rootDir.resolve("runtime")
project(":qalipsis-plugin-platform").projectDir = rootDir.resolve("plugin-platform")

project(":qalipsis-webapp").projectDir = rootDir.resolve("webapp")