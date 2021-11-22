package io.qalipsis.core.factory.configuration

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.key
import assertk.assertions.prop
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.PropertySource
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.qalipsis.core.configuration.ExecutionEnvironments
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

@MicronautTest
internal class FactoryConfigurationIntegrationTest {

    @Test
    @MicronautTest(environments = [ExecutionEnvironments.FACTORY])
    @Timeout(10)
    internal fun `should create the factory configuration with the default values`(factoryConfiguration: FactoryConfiguration) {
        assertThat(factoryConfiguration).all {
            prop(FactoryConfiguration::nodeId).isNotEmpty()
            prop(FactoryConfiguration::selectors).isEmpty()
            prop(FactoryConfiguration::handshakeRequestChannel).isEqualTo("handshake-request")
            prop(FactoryConfiguration::handshakeResponseChannel).isEqualTo("handshake-response")
            prop(FactoryConfiguration::metadataPath).isEqualTo("./metadata")
        }
    }

    @Test
    @PropertySource(
        Property(name = "factory.node-id", value = "The node ID"),
        Property(name = "factory.selectors", value = "key1=value1,key2=value2"),
        Property(name = "factory.handshake-request-channel", value = "The handshake request channel"),
        Property(name = "factory.handshake-response-channel", value = "The handshake response channel"),
        Property(name = "factory.metadata-path", value = "./another-metadata-path")
    )
    @MicronautTest(environments = [ExecutionEnvironments.FACTORY])
    @Timeout(10)
    internal fun `should create the factory configuration with specified values`(factoryConfiguration: FactoryConfiguration) {
        assertThat(factoryConfiguration).all {
            prop(FactoryConfiguration::nodeId).isEqualTo("The node ID")
            prop(FactoryConfiguration::selectors).all {
                hasSize(2)
                key("key1").isEqualTo("value1")
                key("key2").isEqualTo("value2")
            }
            prop(FactoryConfiguration::handshakeRequestChannel).isEqualTo("The handshake request channel")
            prop(FactoryConfiguration::handshakeResponseChannel).isEqualTo("The handshake response channel")
            prop(FactoryConfiguration::metadataPath).isEqualTo("./another-metadata-path")
        }
    }

}