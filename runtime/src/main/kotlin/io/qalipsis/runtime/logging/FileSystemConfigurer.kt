/*
 * QALIPSIS
 * Copyright (C) 2024 AERIS IT Solutions GmbH
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

package io.qalipsis.runtime.logging

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.Configurator
import ch.qos.logback.classic.spi.ConfiguratorRank
import ch.qos.logback.classic.util.DefaultJoranConfigurator
import ch.qos.logback.core.status.ErrorStatus
import ch.qos.logback.core.status.InfoStatus
import java.io.File

/**
 * Implementation of [Configurator] that loads configuration from logback.xml or config/logback.xml if they exist.
 *
 * See [Logback Documentation](https://logback.qos.ch/manual/configuration.html).
 */
@ConfiguratorRank(ConfiguratorRank.CUSTOM_TOP_PRIORITY)
class FileSystemConfigurer : DefaultJoranConfigurator(), Configurator {

    override fun configure(loggerContext: LoggerContext): Configurator.ExecutionStatus {
        var executionStatus = loadConfigFile(File("logback.xml"))
        if (executionStatus != Configurator.ExecutionStatus.DO_NOT_INVOKE_NEXT_IF_ANY) {
            executionStatus = loadConfigFile(File("config", "logback.xml"))
        }
        return executionStatus
    }

    private fun loadConfigFile(configFile: File): Configurator.ExecutionStatus {
        val sm = context.statusManager
        var executionStatus = Configurator.ExecutionStatus.INVOKE_NEXT_IF_ANY
        if (configFile.exists() && configFile.isFile && configFile.canRead()) {
            try {
                sm.add(InfoStatus("Trying to load configuration from file [${configFile.canonicalPath}]", this.context))
                configureByResource(configFile.toURI().toURL())
                sm.add(InfoStatus("Configuration loaded from file [${configFile.canonicalPath}]", this.context))
                executionStatus = Configurator.ExecutionStatus.DO_NOT_INVOKE_NEXT_IF_ANY
            } catch (e: Exception) {
                sm.add(
                    ErrorStatus(
                        "Configuration could NOT be loaded from file [${configFile.canonicalPath}]",
                        this.context,
                        e
                    )
                )
            }
        } else {
            sm.add(
                InfoStatus(
                    "Could NOT find readable file [${configFile.canonicalPath}]",
                    this.context
                )
            )
        }
        return executionStatus
    }
}