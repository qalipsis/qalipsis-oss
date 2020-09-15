package io.evolue.core.factory.steps.decorators

import java.time.Duration

/**
 *
 * @author Eric Jessé
 */
internal class TopicConfiguration(
        /**
         * Nature of the topic to create.
         */
        var type: TopicType = TopicType.UNICAST,

        /**
         * Size of the buffer to keep data before the minions are ready to read them.
         */
        var bufferSize: Int = 0,

        /**
         * Time to idle of a subscription. Once idle a subscription passed this duration, it is automatically cancelled.
         * When set to `Duration.ZERO` (default) or less, there is no timeout.
         */
        var idleTimeout: Duration = Duration.ZERO
)

/**
 * Defines how a [OutputTopicStepDecorator] will distribute the data to its siblings.
 *
 * @author Eric Jessé
 */
internal enum class TopicType() {
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