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

package io.qalipsis.core.factory.configuration

import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.core.configuration.ExecutionEnvironments
import jakarta.inject.Singleton
import java.io.File
import java.io.FileNotFoundException

/**
 * Implementation of [BeanCreatedEventListener] in order to complete the configuration from additional files.
 */
@Singleton
@Requires(env = [ExecutionEnvironments.FACTORY, ExecutionEnvironments.STANDALONE])
class FactoryConfigurationCreationListener(
    private val idGenerator: IdGenerator
) : BeanCreatedEventListener<FactoryConfiguration> {

    override fun onCreated(event: BeanCreatedEvent<FactoryConfiguration>): FactoryConfiguration {
        val createdConfiguration = event.bean

        // When no node-id is set in the configuration, we first search in the ID from the local file where it might
        // have been persisted, and if the file cannot be read, we assigned a random temporary value.
        if (createdConfiguration.nodeId.isBlank()) {
            val directory = File(createdConfiguration.metadataPath)
            val metadataFile = File(directory, FactoryConfiguration.NODE_ID_FILE_NAME)
            createdConfiguration.nodeId = try {
                metadataFile.readLines(Charsets.UTF_8).filterNot {
                    it.isBlank() || it.trim().startsWith("#")
                }.firstOrNull()?.trim()
            } catch (e: FileNotFoundException) {
                log.info { "The file ${metadataFile.canonicalPath} does not exist, the starting factory is then considered as new" }
                null
            } ?: "_${idGenerator.short()}"
        }

        return createdConfiguration
    }

    private companion object {

        @JvmStatic
        val log = logger()
    }
}