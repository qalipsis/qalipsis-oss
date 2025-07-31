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

package io.qalipsis.runtime.bootstrap

import picocli.CommandLine
import java.util.Properties

/**
 * Implementation of Picocli [CommandLine.IVersionProvider] to provide QALIPSIS version to the help documentation.
 *
 * @author Eric Jessé
 */
class VersionProviderWithVariables : CommandLine.IVersionProvider {

    override fun getVersion(): Array<String> {
        return arrayOf("QALIPSIS version ${executionProperties.getProperty("version")}")
    }

    private companion object {
        val executionProperties = Properties().apply {
            load(VersionProviderWithVariables::class.java.getResourceAsStream("/build.properties"))
        }
    }
}