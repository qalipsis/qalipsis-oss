package io.qalipsis.core.factory.configuration

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.key
import assertk.assertions.prop
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.PropertySource
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.qalipsis.core.configuration.ExecutionEnvironments
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.time.Duration

@MicronautTest
internal class FactoryConfigurationIntegrationTest {

    @Test
    @MicronautTest(environments = [ExecutionEnvironments.FACTORY])
    @Timeout(10)
    internal fun `should create the factory configuration with the default values`(factoryConfiguration: FactoryConfiguration) {
        assertThat(factoryConfiguration).all {
            prop(FactoryConfiguration::nodeId).isEmpty()
            prop(FactoryConfiguration::selectors).isEmpty()
            prop(FactoryConfiguration::handshakeRequestChannel).isEqualTo("handshake-request")
            prop(FactoryConfiguration::handshakeResponseChannel).isEqualTo("handshake-response")
            prop(FactoryConfiguration::metadataPath).isEqualTo("./metadata")
            prop(FactoryConfiguration::tenant).isEmpty()
            prop(FactoryConfiguration::cache).all {
                prop(FactoryConfiguration.Cache::ttl).isEqualTo(Duration.ofMinutes(1))
                prop(FactoryConfiguration.Cache::keyPrefix).isEqualTo("shared-state-registry")
            }
            prop(FactoryConfiguration::directiveRegistry).all {
                prop(FactoryConfiguration.DirectiveRegistry::unicastConsumerGroup).isEqualTo("consumer-group-directives-unicast")
                prop(FactoryConfiguration.DirectiveRegistry::broadcastConsumerGroup).isEqualTo("consumer-group-directives-broadcast")
                prop(FactoryConfiguration.DirectiveRegistry::unicastDirectivesChannel).isEmpty()
                prop(FactoryConfiguration.DirectiveRegistry::broadcastDirectivesChannel).isEmpty()
            }
            prop(FactoryConfiguration::assignment).all {
                prop(FactoryConfiguration.Assignment::evaluationBatchSize).isEqualTo(10)
                prop(FactoryConfiguration.Assignment::timeout).isEqualTo(Duration.ofSeconds(10))
            }
        }
    }

    @Test
    @PropertySource(
        Property(name = "factory.node-id", value = "The node ID"),
        Property(name = "factory.selectors", value = "key1=value1,key2=value2"),
        Property(name = "factory.handshake-request-channel", value = "The handshake request channel"),
        Property(name = "factory.handshake-response-channel", value = "The handshake response channel"),
        Property(name = "factory.metadata-path", value = "./another-metadata-path"),
        Property(name = "factory.tenant", value = "the tenant"),
        Property(name = "factory.directive-registry.unicast-consumer-group", value = "unicast-consumer-group"),
        Property(name = "factory.directive-registry.broadcast-consumer-group", value = "broadcast-consumer-group"),
        Property(name = "factory.directive-registry.unicast-directives-channel", value = "unicast-directives-channels"),
        Property(
            name = "factory.directive-registry.broadcast-directives-channel",
            value = "broadcast-directives-channels"
        ),
        Property(name = "factory.metadata-path", value = "./another-metadata-path"),
        Property(name = "factory.cache.ttl", value = "PT2M"),
        Property(name = "factory.cache.keyPrefix", value = "some-other-registry"),
        Property(name = "factory.metadata-path", value = "./another-metadata-path"),
        Property(name = "factory.assignment.evaluation-batch-size", value = "67542"),
        Property(name = "factory.assignment.timeout", value = "143s")
    )
    @MicronautTest(environments = [ExecutionEnvironments.FACTORY], packages = ["io.qalipsis.core.factory"])
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
            prop(FactoryConfiguration::tenant).isEqualTo("the tenant")
            prop(FactoryConfiguration::directiveRegistry).all {
                prop(FactoryConfiguration.DirectiveRegistry::unicastConsumerGroup).isEqualTo("unicast-consumer-group")
                prop(FactoryConfiguration.DirectiveRegistry::broadcastConsumerGroup).isEqualTo("broadcast-consumer-group")
                prop(FactoryConfiguration.DirectiveRegistry::unicastDirectivesChannel).isEqualTo("unicast-directives-channels")
                prop(FactoryConfiguration.DirectiveRegistry::broadcastDirectivesChannel).isEqualTo("broadcast-directives-channels")
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
