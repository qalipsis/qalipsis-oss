package io.qalipsis.core.factory.configuration

import io.micronaut.context.annotation.ConfigurationProperties
import javax.validation.constraints.NotEmpty

@ConfigurationProperties("factory")
internal class FactoryConfiguration {

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

    companion object {

        /**
         * Name of the file containing the node ID when persisted locally.
         */
        const val NODE_ID_FILE_NAME = "id.meta"

    }
}