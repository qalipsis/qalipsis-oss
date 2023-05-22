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
    ":qalipsis-shared",
    ":qalipsis-cluster",
    ":qalipsis-head",
    ":qalipsis-factory",
    ":qalipsis-runtime",
    ":qalipsis-plugin-platform"
)

project(":qalipsis-shared").projectDir = File(rootDir, "shared")
project(":qalipsis-cluster").projectDir = File(rootDir, "cluster")
project(":qalipsis-head").projectDir = File(rootDir, "head")
project(":qalipsis-factory").projectDir = File(rootDir, "factory")
project(":qalipsis-runtime").projectDir = File(rootDir, "runtime")
project(":qalipsis-plugin-platform").projectDir = File(rootDir, "plugin-platform")