package io.qalipsis.core.directives.redis

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.XReadArgs
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.micronaut.context.annotation.Property
import io.micronaut.test.annotation.MockBean
import io.mockk.mockk
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.DirectiveReference
import io.qalipsis.core.directives.DirectiveRegistry
import io.qalipsis.core.directives.TestDescriptiveDirective
import io.qalipsis.core.directives.TestListDirective
import io.qalipsis.core.directives.TestListDirectiveReference
import io.qalipsis.core.directives.TestQueueDirective
import io.qalipsis.core.directives.TestQueueDirectiveReference
import io.qalipsis.core.directives.TestSingleUseDirective
import io.qalipsis.core.directives.TestSingleUseDirectiveReference
import io.qalipsis.core.redis.AbstractRedisIntegrationTest
import io.qalipsis.core.redis.RedisDirectiveProducer
import io.qalipsis.core.serialization.RecordDistributionSerializer
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import jakarta.inject.Inject
import kotlinx.coroutines.flow.first
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * @author Gabriel Moraes
 */
@ExperimentalLettuceCoroutinesApi 
@WithMockk
@Property(name = ExecutionEnvironments.DISTRIBUTED_STREAMING_PLATFORM_PROPERTY, value = ExecutionEnvironments.REDIS)
@Serializable([TestQueueDirectiveReference::class, TestListDirectiveReference::class, TestSingleUseDirectiveReference::class, TestDescriptiveDirective::class])
internal class RedisDirectiveProducerIntegrationTest : AbstractRedisIntegrationTest() {

    @RegisterExtension
    @JvmField
    val testDispatcherProvider = TestDispatcherProvider()

    private val registry = mockk<DirectiveRegistry>(relaxed = true)

    @MockBean(DirectiveRegistry::class)
    fun directiveRegistry() = registry

    @Inject
    private lateinit var producer: RedisDirectiveProducer

    @Inject
    lateinit var redisCoroutinesCommands: RedisCoroutinesCommands<String, String>

    @Inject
    lateinit var distributionSerializer: RecordDistributionSerializer

    @BeforeEach
    fun setUp() {
        testDispatcherProvider.run { redisCoroutinesCommands.del("broadcast-channel") }
    }

    @Test
    internal fun shouldSaveAndProduceQueueDirective() = testDispatcherProvider.run {
        // given
        val directive = TestQueueDirective((0 until 20).toList())

        // when
        producer.publish(directive)

        // then
        coVerifyOnce {
            registry.save("my-queue-directive", directive)
        }
        val message = redisCoroutinesCommands.xread(XReadArgs.StreamOffset.from("broadcast-channel", "0")).first().body.values.first()
        val published = distributionSerializer.deserialize<DirectiveReference>(message.toByteArray())
        Assertions.assertEquals(directive.toReference().key, published.key)
    }

    @Test
    internal fun shouldSaveAndProduceListDirective() = testDispatcherProvider.run {
        // given
        val directive = TestListDirective((0 until 20).toList())

        // when
        producer.publish(directive)

        // then
        coVerifyOnce {
            registry.save("my-list-directive", directive)
        }
        val message = redisCoroutinesCommands.xread(XReadArgs.StreamOffset.from("broadcast-channel", "0")).first().body.values.first()
        val published = distributionSerializer.deserialize<DirectiveReference>(message.toByteArray())
        Assertions.assertEquals(directive.toReference().key, published.key)
    }

    @Test
    internal fun shouldSaveAndSingleUseListDirective() = testDispatcherProvider.run {
        // given
        val directive = TestSingleUseDirective(100)

        // when
        producer.publish(directive)

        // then
        coVerifyOnce {
            registry.save("my-single-use-directive", directive)
        }
        val message = redisCoroutinesCommands.xread(XReadArgs.StreamOffset.from("broadcast-channel", "0")).first().body.values.first()
        val published = distributionSerializer.deserialize<DirectiveReference>(message.toByteArray())
        Assertions.assertEquals(directive.toReference().key, published.key)
    }

    @Test
    internal fun shouldSaveAndProduceOtherDirective() = testDispatcherProvider.run {
        // given
        val directive = TestDescriptiveDirective(channel = "broadcast-channel")

        // when
        producer.publish(directive)

        // then
        val message = redisCoroutinesCommands.xread(XReadArgs.StreamOffset.from("broadcast-channel", "0")).first().body.values.first()
        val published = distributionSerializer.deserialize<Directive>(message.toByteArray())
        Assertions.assertEquals(directive.key, published.key)
    }
}