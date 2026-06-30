/*
 * QALIPSIS
 * Copyright (C) 2026 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.core.head.communication

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.feedbacks.CampaignMetersFeedback
import io.qalipsis.core.feedbacks.Feedback
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.core.head.configuration.HeadConfiguration
import io.qalipsis.core.heartbeat.Heartbeat
import io.qalipsis.core.meters.CampaignMeterSnapshot
import io.qalipsis.core.serialization.DistributionSerializer
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyNever
import kotlinx.coroutines.CoroutineScope
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant

@WithMockk
@Timeout(10)
internal class ChannelSubscriberTest {

    @field:RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @MockK
    private lateinit var serializer: DistributionSerializer

    @RelaxedMockK
    private lateinit var headConfiguration: HeadConfiguration

    @MockK
    private lateinit var meterFeedbackListener1: CampaignMeterFeedbackListener

    @MockK
    private lateinit var meterFeedbackListener2: CampaignMeterFeedbackListener

    @RelaxedMockK
    private lateinit var feedbackListener: FeedbackListener<Feedback>

    @RelaxedMockK
    private lateinit var heartbeatListener1: HeartbeatListener

    @RelaxedMockK
    private lateinit var heartbeatListener2: HeartbeatListener

    @RelaxedMockK
    private lateinit var handshakeRequestListener1: HandshakeRequestListener

    @RelaxedMockK
    private lateinit var handshakeRequestListener2: HandshakeRequestListener

    private lateinit var subscriber: TestChannelSubscriber

    @BeforeEach
    fun setUp() {
        every { headConfiguration.heartbeatChannel } returns "heartbeat"
    }

    private fun buildSubscriber(
        scope: CoroutineScope,
        meterListeners: Collection<CampaignMeterFeedbackListener> = listOf(
            meterFeedbackListener1,
            meterFeedbackListener2
        ),
        heartbeatListeners: Collection<HeartbeatListener> = emptyList(),
        handshakeRequestListeners: Collection<HandshakeRequestListener> = emptyList()
    ): TestChannelSubscriber = TestChannelSubscriber(
        serializer = serializer,
        headConfiguration = headConfiguration,
        heartbeatListeners = heartbeatListeners,
        feedbackListeners = listOf(feedbackListener),
        handshakeRequestListeners = handshakeRequestListeners,
        meterFeedbackListeners = meterListeners,
        orchestrationCoroutineScope = scope
    )

    @Test
    internal fun `given message on subscribed meter channel when deserializeAndDispatch then dispatches to all meter listeners`() =
        testDispatcherProvider.run {
            // given
            subscriber = buildSubscriber(this)
            subscriber.subscribedMeterFeedbackChannels.add("meters")
            val bytes = byteArrayOf(1, 2, 3)
            val snapshot = CampaignMeterSnapshot(name = "m1", timestampEpochMs = 1000L, type = "counter")
            val feedback = CampaignMetersFeedback(meters = listOf(snapshot))
            val countLatch = SuspendedCountLatch(2)
            every { serializer.deserialize<CampaignMetersFeedback>(bytes) } returns feedback
            coEvery { meterFeedbackListener1.notify(feedback) } coAnswers { countLatch.decrement() }
            coEvery { meterFeedbackListener2.notify(feedback) } coAnswers { countLatch.decrement() }

            // when
            subscriber.deserializeAndDispatch("meters", bytes)
            countLatch.await()

            // then
            coVerify(exactly = 1) { meterFeedbackListener1.notify(eq(feedback)) }
            coVerify(exactly = 1) { meterFeedbackListener2.notify(eq(feedback)) }
        }

    @Test
    internal fun `given message on meter channel when deserialize returns null when deserializeAndDispatch then no listener notified`() =
        testDispatcherProvider.run {
            // given
            subscriber = buildSubscriber(this)
            subscriber.subscribedMeterFeedbackChannels.add("meters")
            val bytes = byteArrayOf(4, 5, 6)
            every { serializer.deserialize<CampaignMetersFeedback>(bytes) } returns null

            // when
            subscriber.deserializeAndDispatch("meters", bytes)

            // then
            coVerifyNever { meterFeedbackListener1.notify(any()) }
            coVerifyNever { meterFeedbackListener2.notify(any()) }
        }

    @Test
    internal fun `given message on feedback channel when deserializeAndDispatch then only feedback listeners dispatched not meter listeners`() =
        testDispatcherProvider.run {
            // given
            subscriber = buildSubscriber(this)
            subscriber.subscribedFeedbackChannels.add("feedback")
            val bytes = byteArrayOf(7, 8, 9)
            val feedbackMessage = mockk<Feedback>(relaxed = true)
            val countLatch = SuspendedCountLatch(1)
            every { serializer.deserialize<Feedback>(bytes) } returns feedbackMessage
            every { feedbackListener.accept(feedbackMessage) } returns true
            coEvery { feedbackListener.notify(feedbackMessage) } coAnswers { countLatch.decrement() }

            // when
            subscriber.deserializeAndDispatch("feedback", bytes)
            countLatch.await()

            // then
            coVerifyNever { meterFeedbackListener1.notify(any()) }
            coVerifyNever { meterFeedbackListener2.notify(any()) }
        }

    @Test
    internal fun `given message on unregistered channel when deserializeAndDispatch then no listener called`() =
        testDispatcherProvider.run {
            // given
            subscriber = buildSubscriber(this)
            val bytes = byteArrayOf(10, 11, 12)

            // when
            subscriber.deserializeAndDispatch("unknown-channel", bytes)

            // then
            coVerifyNever { meterFeedbackListener1.notify(any()) }
            coVerifyNever { meterFeedbackListener2.notify(any()) }
            coVerifyNever { feedbackListener.notify(any()) }
        }

    @Test
    internal fun `given message on meter channel when no meter listeners registered when deserializeAndDispatch then nothing crashes`() =
        testDispatcherProvider.run {
            // given
            subscriber = buildSubscriber(this, meterListeners = emptyList())
            subscriber.subscribedMeterFeedbackChannels.add("meters")
            val bytes = byteArrayOf(13, 14, 15)
            val snapshot = CampaignMeterSnapshot(name = "m2", timestampEpochMs = 2000L, type = "gauge")
            val feedback = CampaignMetersFeedback(meters = listOf(snapshot))
            every { serializer.deserialize<CampaignMetersFeedback>(bytes) } returns feedback

            // when — should complete without error
            subscriber.deserializeAndDispatch("meters", bytes)

            // then — no exception and no listeners to verify
        }

    @Test
    internal fun `given heartbeat message on heartbeat channel when deserializeAndDispatch then dispatches to all heartbeat listeners`() =
        testDispatcherProvider.run {
            // given
            subscriber = buildSubscriber(
                this,
                heartbeatListeners = listOf(heartbeatListener1, heartbeatListener2)
            )
            val bytes = byteArrayOf(20, 21, 22)
            val heartbeat = Heartbeat("node-1", "tenant-1", Instant.now())
            val countLatch = SuspendedCountLatch(2)
            every { serializer.deserialize<Heartbeat>(bytes) } returns heartbeat
            coEvery { heartbeatListener1.notify(heartbeat) } coAnswers { countLatch.decrement() }
            coEvery { heartbeatListener2.notify(heartbeat) } coAnswers { countLatch.decrement() }

            // when
            subscriber.deserializeAndDispatch("heartbeat", bytes)
            countLatch.await()

            // then
            coVerify(exactly = 1) { heartbeatListener1.notify(eq(heartbeat)) }
            coVerify(exactly = 1) { heartbeatListener2.notify(eq(heartbeat)) }
        }

    @Test
    internal fun `given handshake request on subscribed channel when deserializeAndDispatch then dispatches to all handshake listeners`() =
        testDispatcherProvider.run {
            // given
            subscriber = buildSubscriber(
                this,
                handshakeRequestListeners = listOf(handshakeRequestListener1, handshakeRequestListener2)
            )
            subscriber.subscribedHandshakeRequestsChannels.add("handshake-requests")
            val bytes = byteArrayOf(30, 31, 32)
            val request = HandshakeRequest(
                nodeId = "node-1",
                tags = mapOf("key" to "value"),
                replyTo = "reply-channel",
                scenarios = emptyList()
            )
            val countLatch = SuspendedCountLatch(2)
            every { serializer.deserialize<HandshakeRequest>(bytes) } returns request
            coEvery { handshakeRequestListener1.notify(request) } coAnswers { countLatch.decrement() }
            coEvery { handshakeRequestListener2.notify(request) } coAnswers { countLatch.decrement() }

            // when
            subscriber.deserializeAndDispatch("handshake-requests", bytes)
            countLatch.await()

            // then
            coVerify(exactly = 1) { handshakeRequestListener1.notify(eq(request)) }
            coVerify(exactly = 1) { handshakeRequestListener2.notify(eq(request)) }
        }

    /**
     * Minimal concrete subclass exposing the abstract [ChannelSubscriber] for testing purposes.
     */
    private class TestChannelSubscriber(
        serializer: DistributionSerializer,
        headConfiguration: HeadConfiguration,
        heartbeatListeners: Collection<HeartbeatListener>,
        feedbackListeners: Collection<FeedbackListener<*>>,
        handshakeRequestListeners: Collection<HandshakeRequestListener>,
        meterFeedbackListeners: Collection<CampaignMeterFeedbackListener>,
        orchestrationCoroutineScope: CoroutineScope
    ) : ChannelSubscriber(
        serializer,
        headConfiguration,
        heartbeatListeners,
        feedbackListeners,
        handshakeRequestListeners,
        meterFeedbackListeners,
        orchestrationCoroutineScope
    )
}
