package io.qalipsis.core.factories.steps.topicrelatedsteps

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import io.qalipsis.api.messaging.Record
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import kotlinx.coroutines.channels.Channel
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * @author Eric Jessé
 */
internal class TopicBuilderTest {


    @Test
    internal fun `should build a unicast topic`() {
        // given
        val config = TopicConfiguration(TopicType.UNICAST, 1434, Duration.ofSeconds(23))

        // when
        val topic = TopicBuilder.build<String>(config)

        // then
        assertThat(topic).all {
            transform { topic -> topic::class.simpleName }.isEqualTo("UnicastTopic")
            typedProp<Channel<Record<String>>>("channel").prop("capacity").isEqualTo(1434)
            prop("idleTimeout").isEqualTo(Duration.ofSeconds(23))
        }
    }

    @Test
    internal fun `should build a broadcast topic`() {
        // given
        val config = TopicConfiguration(TopicType.BROADCAST, 1434, Duration.ofSeconds(23))

        // when
        val topic = TopicBuilder.build<String>(config)

        // then
        assertThat(topic).all {
            transform { topic -> topic::class.simpleName }.isEqualTo("BroadcastTopic")
            prop("idleTimeout").isEqualTo(Duration.ofSeconds(23))
        }
    }

    @Test
    internal fun `should build a loop topic`() {
        // given
        val config = TopicConfiguration(TopicType.LOOP, 1434, Duration.ofSeconds(23))

        // when
        val topic = TopicBuilder.build<String>(config)

        // then
        assertThat(topic).all {
            transform { topic -> topic::class.simpleName }.isEqualTo("LoopTopic")
            prop("idleTimeout").isEqualTo(Duration.ofSeconds(23))
        }
    }

}
