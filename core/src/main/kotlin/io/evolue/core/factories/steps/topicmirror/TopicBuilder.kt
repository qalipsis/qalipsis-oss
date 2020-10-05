package io.evolue.core.factories.steps.topicmirror

import io.evolue.api.messaging.Topic
import io.evolue.api.messaging.broadcastTopic
import io.evolue.api.messaging.loopTopic
import io.evolue.api.messaging.unicastTopic

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
