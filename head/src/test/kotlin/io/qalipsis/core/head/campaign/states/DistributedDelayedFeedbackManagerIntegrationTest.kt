package io.qalipsis.core.head.campaign.states

import assertk.assertThat
import assertk.assertions.isGreaterThanOrEqualTo
import com.hazelcast.core.HazelcastInstance
import io.micronaut.context.annotation.Property
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.feedbacks.CampaignManagementFeedback
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.head.communication.HeadChannel
import io.qalipsis.core.head.configuration.HeadConfiguration
import io.qalipsis.core.serialization.DistributionSerializer
import io.qalipsis.test.mockk.WithMockk
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch

@WithMockk
@MicronautTest(environments = [ExecutionEnvironments.HEAD], startApplication = false)
@Property(name = "hazelcast.discovery-strategy", value = "NONE")
internal class DistributedDelayedFeedbackManagerIntegrationTest {

    @MockK
    lateinit var headChannel: HeadChannel

    @MockK
    lateinit var configuration: HeadConfiguration

    @MockK
    lateinit var serializer: DistributionSerializer

    @Inject
    lateinit var hazelcastInstance: HazelcastInstance

    @Inject
    lateinit var distributedDelayedFeedbackManager: DistributedDelayedFeedbackManager

    @MockBean(HeadChannel::class)
    fun headChannel() = headChannel

    @MockBean(HeadConfiguration::class)
    fun configuration() = configuration

    @MockBean(DistributionSerializer::class)
    fun serializer() = serializer

    @Test
    fun `should publish the feedback after a delay`() {
        // given
        every { configuration.campaignCancellationStateGracePeriod } returns Duration.ofMillis(1200)
        val latch = CountDownLatch(1)
        coEvery { headChannel.publishFeedback(any(), any(), any()) } answers { latch.countDown() }
        val serializedFeedback = "My-feedback".encodeToByteArray()
        every { serializer.serialize(any<Feedback>(), any()) } returns serializedFeedback
        val feedback = mockk<Feedback>(moreInterfaces = arrayOf(CampaignManagementFeedback::class)) {
            every { (this@mockk as CampaignManagementFeedback).campaignKey } returns "my-campaign"
        }

        // when
        val beforeCall = Instant.now()
        distributedDelayedFeedbackManager.scheduleCancellation("the-channel", feedback)

        // then
        latch.await()
        assertThat(Duration.between(beforeCall, Instant.now())).isGreaterThanOrEqualTo(Duration.ofSeconds(1))
        coVerifyOrder {
            serializer.serialize(refEq(feedback), any())
            headChannel.publishFeedback("the-channel", "my-campaign", eq(serializedFeedback))
        }
    }
}