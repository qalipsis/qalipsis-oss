package io.qalipsis.core.head.communication

import io.qalipsis.core.directives.DispatcherChannel
import io.qalipsis.core.factory.communication.HeadChannel

abstract class AbstractHeadChannel : HeadChannel {

    override val subscribedHandshakeRequestsChannels = mutableSetOf<DispatcherChannel>()

    override val subscribedFeedbackChannels = mutableSetOf<DispatcherChannel>()

}