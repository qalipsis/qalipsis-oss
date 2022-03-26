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
import java.time.Duration

@MicronautTest(environments = [ExecutionEnvironments.FACTORY], packages = ["io.qalipsis.core.factory"])
internal class FactoryConfigurationIntegrationTest {

    @Test
    @MicronautTest
    @Timeout(10)
    internal fun `should create the factory configuration with the default values`(factoryConfiguration: FactoryConfiguration) {
        assertThat(factoryConfiguration).all {
            prop(FactoryConfiguration::nodeId).isNotEmpty()
            prop(FactoryConfiguration::tags).isEmpty()
            prop(FactoryConfiguration::metadataPath).isEqualTo("./metadata")
            prop(FactoryConfiguration::tenant).isEmpty()
            prop(FactoryConfiguration::handshake).all {
                prop(FactoryConfiguration.HandshakeConfiguration::requestChannel).isEqualTo("handshake-request")
                prop(FactoryConfiguration.HandshakeConfiguration::responseChannel).isEqualTo("handshake-response")
                prop(FactoryConfiguration.HandshakeConfiguration::timeout).isEqualTo(Duration.ofSeconds(30))
            }
            prop(FactoryConfiguration::cache).all {
                prop(FactoryConfiguration.Cache::ttl).isEqualTo(Duration.ofMinutes(1))
                prop(FactoryConfiguration.Cache::keyPrefix).isEqualTo("shared-state-registry")
            }
            prop(FactoryConfiguration::assignment).all {
                prop(FactoryConfiguration.Assignment::evaluationBatchSize).isEqualTo(100)
                prop(FactoryConfiguration.Assignment::timeout).isEqualTo(Duration.ofSeconds(10))
            }
        }
    }

    @Test
    @PropertySource(
        Property(name = "factory.node-id", value = "The node ID"),
        Property(name = "factory.tags", value = "key1=value1,key2=value2"),
        Property(name = "factory.handshake.request-channel", value = "The handshake request channel"),
        Property(name = "factory.handshake.response-channel", value = "The handshake response channel"),
        Property(name = "factory.handshake.timeout", value = "20ms"),
        Property(name = "factory.metadata-path", value = "./another-metadata-path"),
        Property(name = "factory.tenant", value = "the tenant"),
        Property(name = "factory.metadata-path", value = "./another-metadata-path"),
        Property(name = "factory.cache.ttl", value = "PT2M"),
        Property(name = "factory.cache.keyPrefix", value = "some-other-registry"),
        Property(name = "factory.metadata-path", value = "./another-metadata-path"),
        Property(name = "factory.assignment.evaluation-batch-size", value = "67542"),
        Property(name = "factory.assignment.timeout", value = "143s")
    )
    @MicronautTest
    @Timeout(10)
    internal fun `should create the factory configuration with specified values`(factoryConfiguration: FactoryConfiguration) {
        assertThat(factoryConfiguration).all {
            prop(FactoryConfiguration::nodeId).isEqualTo("The node ID")
            prop(FactoryConfiguration::tags).all {
                hasSize(2)
                key("key1").isEqualTo("value1")
                key("key2").isEqualTo("value2")
            }
            prop(FactoryConfiguration::metadataPath).isEqualTo("./another-metadata-path")
            prop(FactoryConfiguration::tenant).isEqualTo("the tenant")
            prop(FactoryConfiguration::handshake).all {
                prop(FactoryConfiguration.HandshakeConfiguration::requestChannel).isEqualTo("The handshake request channel")
                prop(FactoryConfiguration.HandshakeConfiguration::responseChannel).isEqualTo("The handshake response channel")
                prop(FactoryConfiguration.HandshakeConfiguration::timeout).isEqualTo(Duration.ofMillis(20))
            }
            prop(FactoryConfiguration::cache).all {
                prop(FactoryConfiguration.Cache::ttl).isEqualTo(Duration.ofMinutes(2))
                prop(FactoryConfiguration.Cache::keyPrefix).isEqualTo("some-other-registry")
            }
            prop(FactoryConfiguration::assignment).all {
                prop(FactoryConfiguration.Assignment::evaluationBatchSize).isEqualTo(67542)
                prop(FactoryConfiguration.Assignment::timeout).isEqualTo(Duration.ofSeconds(143))
            }
        }
    }

    // TODO Validation tests.
}
