/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
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

package io.qalipsis.core.factory.steps.topicrelatedsteps

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
