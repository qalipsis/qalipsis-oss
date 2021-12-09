package io.qalipsis.core.factory.orchestration.directives.consumers

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.orchestration.directives.Directive
import io.qalipsis.api.orchestration.directives.DirectiveProcessor
import io.qalipsis.api.serialization.Serializable
import io.qalipsis.core.directives.TestDescriptiveDirective
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.redis.AbstractRedisIntegrationTest
import io.qalipsis.core.serialization.DistributionSerializer
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import jakarta.inject.Inject
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * @author Gabriel Moraes
 */
@Suppress("UNCHECKED_CAST")
@WithMockk
@ExperimentalLettuceCoroutinesApi
@Serializable([TestDescriptiveDirective::class])
internal class RedisBasedDirectiveConsumerIntegrationTest : AbstractRedisIntegrationTest() {

    @RelaxedMockK
    lateinit var processor1: DirectiveProcessor<*>

    @RelaxedMockK
    lateinit var processor2: DirectiveProcessor<*>

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @Inject
    lateinit var redisCoroutinesCommands: RedisCoroutinesCommands<String, String>

    @Inject
    lateinit var distributionSerializer: DistributionSerializer

    @Inject
    lateinit var idGenerator: IdGenerator

    @Inject
    lateinit var factoryConfiguration: FactoryConfiguration

    @Test
    @Timeout(10)
    internal fun shouldConsumeAndProcessDirective() = testCoroutineDispatcher.run {
        // given
        val directive1: Directive = TestDescriptiveDirective()
        val directive2: Directive = TestDescriptiveDirective()
        every { processor1.accept(match{it.key == directive1.key}) } returns true
        every { processor2.accept(match{it.key == directive2.key}) } returns true

        val unicastChannel = "unicast"
        val broadcastChannel = "broadcastChannel"

        // when
        val consumer = RedisBasedDirectiveConsumer(redisCoroutinesCommands,
            listOf(processor1, processor2),
            distributionSerializer, idGenerator,
            this, factoryConfiguration
        )

        redisCoroutinesCommands.xadd(unicastChannel, mapOf(directive1.key to distributionSerializer.serialize(directive1).decodeToString()))
        redisCoroutinesCommands.xadd(broadcastChannel, mapOf(directive2.key to distributionSerializer.serialize(directive2).decodeToString()))

        consumer.start(unicastChannel, broadcastChannel)

        delay(500)

        coVerify(exactly = 2) {
            processor1.accept(any())
            processor2.accept(any())
        }

        coVerify(exactly = 1) {

            (processor1 as DirectiveProcessor<Directive>).process(match{it.key == directive1.key})
            (processor2 as DirectiveProcessor<Directive>).process(match{it.key == directive2.key})
        }
    }

    @Test
    @Timeout(10)
    internal fun shouldThrowErrorConsumerInvokedTwice() = testCoroutineDispatcher.run {
        // given
        val directive1: Directive = TestDescriptiveDirective()
        val directive2: Directive = TestDescriptiveDirective()
        every { processor1.accept(match{it.key == directive1.key}) } returns true
        every { processor2.accept(match{it.key == directive2.key}) } returns true

        val unicastChannel = "unicast"
        val broadcastChannel = "broadcastChannel"

        // when
        val consumer = RedisBasedDirectiveConsumer(redisCoroutinesCommands,
            listOf(processor1, processor2),
            distributionSerializer, idGenerator,
            this, factoryConfiguration
        )

        redisCoroutinesCommands.xadd(unicastChannel, mapOf(directive1.key to distributionSerializer.serialize(directive1).decodeToString()))
        redisCoroutinesCommands.xadd(broadcastChannel, mapOf(directive2.key to distributionSerializer.serialize(directive2).decodeToString()))


        assertThrows<IllegalStateException> {
            consumer.start(unicastChannel, broadcastChannel)

            consumer.start(unicastChannel, broadcastChannel)
        }
    }
}
