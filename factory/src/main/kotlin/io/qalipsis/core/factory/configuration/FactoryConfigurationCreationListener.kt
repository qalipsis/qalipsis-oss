package io.qalipsis.core.factory.configuration

import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.logging.LoggerHelper.logger
import jakarta.inject.Singleton
import java.io.File

/**
 * Implementation of [BeanCreatedEventListener] in order to complete the configuration from additional files.
 */
@Singleton
internal class FactoryConfigurationCreationListener(
    private val idGenerator: IdGenerator
) : BeanCreatedEventListener<FactoryConfiguration> {

    override fun onCreated(event: BeanCreatedEvent<FactoryConfiguration>): FactoryConfiguration {
        val createdConfiguration = event.bean

        // When no node-id is set in the configuration, we first search in the ID from the local file where it might
        // have been persisted, and if the file cannot be read, we assigned a random temporary value.
        if (createdConfiguration.nodeId.isBlank()) {
            createdConfiguration.nodeId = try {
                val directory = File(createdConfiguration.metadataPath)
                File(directory, FactoryConfiguration.NODE_ID_FILE_NAME).readLines(Charsets.UTF_8).filterNot {
                    it.isBlank() || it.trim().startsWith("#")
                }.firstOrNull()?.trim()
            } catch (e: Exception) {
                log.error { e.message }
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