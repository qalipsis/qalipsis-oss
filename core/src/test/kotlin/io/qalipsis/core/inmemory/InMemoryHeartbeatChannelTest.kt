package io.qalipsis.core.inmemory

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isNull
import assertk.assertions.prop
import io.mockk.slot
import io.qalipsis.api.sync.Latch
import io.qalipsis.core.heartbeat.Heartbeat
import io.qalipsis.test.coroutines.TestDispatcherProvider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration
import java.time.Instant

internal class InMemoryHeartbeatChannelTest {

    @RegisterExtension
    private val testDispatcherProvider = TestDispatcherProvider()

    @Test
    @Timeout(3)
    internal fun `should consumer the later produced heartbeat`() = testDispatcherProvider.run {
        // given
        val heartbeatChannel = InMemoryHeartbeatChannel()
        val latch = Latch(true)
        val heartbeatSlot = slot<Heartbeat>()
        heartbeatChannel.start("") {
            heartbeatSlot.captured = it
            latch.release()
        }

        // when
        val beforeHeartbeat = Instant.now()
        heartbeatChannel.start("my-factory", "", Duration.ofSeconds(123))
        latch.await()

        // then
        assertThat(heartbeatSlot.captured).all {
            prop(Heartbeat::nodeId).isEqualTo("my-factory")
            prop(Heartbeat::campaignId).isNull()
            prop(Heartbeat::state).isEqualTo(Heartbeat.STATE.HEALTHY)
            prop(Heartbeat::timestamp).isGreaterThanOrEqualTo(beforeHeartbeat)
        }
    }
}