package io.qalipsis.core.redis

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.XReadArgs
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.orchestration.feedbacks.Feedback
import io.qalipsis.api.orchestration.feedbacks.FeedbackStatus
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.feedbacks.CampaignStartedForDagFeedback
import io.qalipsis.core.serialization.DistributionSerializer
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import jakarta.inject.Inject
import kotlinx.coroutines.flow.first
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.UUID

/**
 * @author Gabriel Moraes
 */
@ExperimentalLettuceCoroutinesApi 
@WithMockk
internal class RedisFeedbackChannelIntegrationTest: AbstractRedisIntegrationTest(){

    @Inject
    lateinit var redisCoroutinesCommands: RedisCoroutinesCommands<String, String>

    @Inject
    lateinit var distributionSerializer: DistributionSerializer

    @Inject
    lateinit var idGenerator: IdGenerator

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @Test
    @Timeout(10)
    fun `should send feedback data into Redis`() = testCoroutineDispatcher.run{
        val redisFeedbackChannel = RedisFeedbackChannel(this, distributionSerializer, redisCoroutinesCommands, idGenerator)
        redisFeedbackChannel.start("test")
        val feedback = CampaignStartedForDagFeedback("campaign-id", "scenario-id", DirectedAcyclicGraphId(), FeedbackStatus.IN_PROGRESS)
        redisFeedbackChannel.publish(feedback)

        val message = redisCoroutinesCommands.xread(XReadArgs.StreamOffset.from("test", "0")).first().body.values.first()
        val published = distributionSerializer.deserialize<Feedback>(message.toByteArray())
        Assertions.assertEquals(feedback.key, published.key)
    }

    @Test
    @Timeout(10)
    fun `should consume feedback data from Redis`() = testCoroutineDispatcher.run{
        val suspendedCountLatch = SuspendedCountLatch(1)
        val redisFeedbackChannel = RedisFeedbackChannel(this, distributionSerializer, redisCoroutinesCommands, idGenerator)
        redisFeedbackChannel.subscribe("test-consumer")
        val feedback = CampaignStartedForDagFeedback("campaign-id", "scenario-id", DirectedAcyclicGraphId(), FeedbackStatus.IN_PROGRESS)
        redisCoroutinesCommands.xadd("test-consumer", mapOf(feedback.key to distributionSerializer.serialize(feedback).decodeToString()))
        var feedbackReceived: Feedback? = null

        redisFeedbackChannel.onReceive(UUID.randomUUID().toString()){
            feedbackReceived = it
            suspendedCountLatch.decrement()
        }

        suspendedCountLatch.await()

        Assertions.assertEquals(feedback.key, feedbackReceived?.key)

    }
}