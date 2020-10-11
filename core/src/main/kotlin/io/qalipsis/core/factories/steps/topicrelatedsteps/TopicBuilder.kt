package io.qalipsis.core.factories.steps.topicrelatedsteps

import io.qalipsis.api.messaging.Topic
import io.qalipsis.api.messaging.broadcastTopic
import io.qalipsis.api.messaging.loopTopic
import io.qalipsis.api.messaging.unicastTopic

/**
 *
 * @author Eric Jessé
 */
internal object TopicBuilder {

    fun <O> build(config: TopicConfiguration): Topic<O> {
        return when (config.type) {
            TopicType.UNICAST -> unicastTopic(config.bufferSize, config.idleTimeout)
            TopicType.BROADCAST -> broadcastTopic(config.bufferSize, config.idleTimeout)
            TopicType.LOOP -> loopTopic(config.idleTimeout)
        }
    }
}
