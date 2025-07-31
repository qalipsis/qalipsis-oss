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

import java.time.Duration

/**
 *
 * @property type type of the topic to create
 * @property bufferSize size of the buffer to keep data before the minions are ready to read them
 * @property idleTimeout time to idle of a subscription: once idle a subscription passed this duration, it is automatically cancelled, when set to `Duration.ZERO` (default) or less, there is no timeout.
 *
 * @author Eric Jessé
 */
class TopicConfiguration(
    var type: TopicType = TopicType.UNICAST,
    var bufferSize: Int = 0,
    var idleTimeout: Duration = Duration.ZERO
)

/**
 * Defines how the [io.qalipsis.api.steps.Step] feeding the topic will distribute the data to its descendants.
 *
 * @author Eric Jessé
 */
enum class TopicType {
    /**
     * Each record is provided only once to a unique minion. The topic acts as a FIFO-Provider.
     */
    UNICAST,

    /**
     * All the records are provided to all the minions. The topic acts as a Pub/Sub-Provider.
     */
    BROADCAST,

    /**
     * Like [BROADCAST] but looping to the beginning when the decorated step provided all the values already.
     */
    LOOP

}
