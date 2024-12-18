package io.qalipsis.core.head.campaign.states

import assertk.assertThat
import assertk.assertions.isGreaterThanOrEqualTo
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.feedbacks.CampaignManagementFeedback
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.head.communication.HeadChannel
import io.qalipsis.core.head.configuration.HeadConfiguration
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch

@WithMockk
@MicronautTest(environments = [ExecutionEnvironments.HEAD, ExecutionEnvironments.SINGLE_HEAD], startApplication = false)
internal class InMemoryDelayedFeedbackManagerIntegrationTest {

    @MockK
    lateinit var headChannel: HeadChannel

    @MockK
    lateinit var configuration: HeadConfiguration

    @Inject
    lateinit var inMemoryDelayedFeedbackManager: InMemoryDelayedFeedbackManager

    @MockBean(HeadChannel::class)
    fun headChannel() = headChannel

    @MockBean(HeadConfiguration::class)
    fun configuration() = configuration

    @Test
    fun `should publish the feedback after a delay`() {
        // given
        every { configuration.campaignCancellationStateGracePeriod } returns Duration.ofMillis(1200)
        val latch = CountDownLatch(1)
        coEvery { headChannel.publishFeedback(any(), any(), any()) } answers { latch.countDown() }
        val feedback = mockk<Feedback>(moreInterfaces = arrayOf(CampaignManagementFeedback::class)) {
            every { (this@mockk as CampaignManagementFeedback).campaignKey } returns "my-campaign"
        }

        // when
        val beforeCall = Instant.now()
        inMemoryDelayedFeedbackManager.scheduleCancellation("the-channel", feedback)

        // then
        latch.await()
        assertThat(Duration.between(beforeCall, Instant.now())).isGreaterThanOrEqualTo(Duration.ofSeconds(1))
        coVerifyOnce {
            headChannel.publishFeedback("the-channel", "my-campaign", refEq(feedback))
        }
    }
}