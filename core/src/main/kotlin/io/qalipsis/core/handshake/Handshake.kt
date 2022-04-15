package io.qalipsis.core.handshake

import io.qalipsis.core.campaigns.DirectedAcyclicGraphSummary
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.serialization.DurationKotlinSerializer
import kotlinx.serialization.Serializable
import java.time.Duration

/**
 * Notification sent from the factory to the head, when the factory has prepared its scenario and is ready for work.
 *
 * @property nodeId current identifier of the factory sending the feedback
 * @property tags list of selectors configured in the factory
 * @property replyTo channel to use for the registration response
 * @property scenarios set of scenarios supported by the factory
 * @property tenant tenant identifier of the factory sending the feedback
 *
 * @author Eric Jessé
 */
@Serializable
data class HandshakeRequest(
    val nodeId: String,
    val tags: Map<String, String>,
    val replyTo: String,
    val scenarios: List<RegistrationScenario>,
    val tenant: String = ""
)

typealias RegistrationScenario = ScenarioSummary
typealias RegistrationDirectedAcyclicGraph = DirectedAcyclicGraphSummary

/**
 * Response to the handshake initialized from a factory.
 *
 * @property handshakeNodeId ID of the node as received from the factory at the beginning of the handshake
 * @property nodeId final ID of the node, as assigned from the head
 * @property unicastChannel name of the channel to listen for the unicast directives dedicated for this node
 * @property heartbeatChannel name of the channel to use to send the heartbeats
 * @property heartbeatPeriod period to emmit the heartbeats
 */
@Serializable
data class HandshakeResponse(
    val handshakeNodeId: String,
    val nodeId: String,
    val unicastChannel: String,
    val heartbeatChannel: String,
    @Serializable(with = DurationKotlinSerializer::class) val heartbeatPeriod: Duration
)