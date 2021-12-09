package io.qalipsis.core.factory.configuration

import io.micronaut.context.annotation.ConfigurationProperties
import io.qalipsis.api.constraints.PositiveDuration
import java.time.Duration
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.NotNull

@ConfigurationProperties("factory")
class FactoryConfiguration {

    /**
     * ID of this factory, defaults to a random generated value which will be replaced after the registration
     * to the head.
     */
    var nodeId: String = ""

    /**
     * Set of selectors assigned to this factory, defaults to empty.
     */
    var selectors: Map<String, String> = emptyMap()

    /**
     * Channel to use to register the factory to the head, defaults to "registration".
     */
    @field:NotEmpty
    var handshakeRequestChannel: String = "handshake-request"

    /**
     * Channel to use to register the factory to the head, defaults to "registration".
     */
    @field:NotEmpty
    var handshakeResponseChannel: String = "handshake-response"

    /**
     * Folder where the metadata of the factory should be saved for later use.
     */
    @field:NotEmpty
    var metadataPath: String = "./metadata"

    @field:NotNull
    var directiveRegistry: DirectiveRegistry = DirectiveRegistry()

    /**
     * Directive registry configuration for a factory.
     */
    @ConfigurationProperties("directive-registry")
    class DirectiveRegistry {

        /**
         * Consumer group used by the factory to consumer from the unicast channel.
         */
        var unicastConsumerGroup: String = "consumer-group-directives-unicast"

        /**
         * Consumer group used by the factory to consumer from the broadcast channel.
         */
        var broadcastConsumerGroup: String = "consumer-group-directives-broadcast"

        /**
         * Unicast channel used by the factory to consume from.
         * Values are set by the response of the handshake with the head.
         */
        var unicastDirectivesChannel: String = ""

        /**
         * Broadcast channel used by the factory to consume from.
         * Values are set by the response of the handshake with the head.
         */
        var broadcastDirectivesChannel: String = ""
    }

    /**
     * This object contains the settings applied to each key and value stored in cache.
     */
    var cache: Cache = Cache()

    @ConfigurationProperties("cache")
    class Cache {

        /**
         * Time to live used for each value in cache.
         */
        @field:PositiveDuration
        var ttl: Duration = Duration.ofMinutes(1)

        /**
         * The prefix of the keys in cache.
         */
        var keyPrefix: String = "shared-state-registry"
    }

    companion object {

        /**
         * Name of the file containing the node ID when persisted locally.
         */
        const val NODE_ID_FILE_NAME = "id.meta"

    }
}
