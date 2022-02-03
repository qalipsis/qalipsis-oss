package io.qalipsis.core.handshake

import io.qalipsis.core.campaigns.DirectedAcyclicGraphSummary
import io.qalipsis.core.campaigns.ScenarioSummary
import java.time.Duration

/**
 * Notification sent from the factory to the head, when the factory has prepared its scenario and is ready for work.
 *
 * @property nodeId current identifier of the factory sending the feedback
 * @property selectors list of selectors configured in the factory
 * @property replyTo channel to use for the registration response
 * @property scenarios set of scenarios supported by the factory
 *
 * @author Eric Jessé
 */
data class HandshakeRequest(
    val nodeId: String,
    val selectors: Map<String, String>,
    val replyTo: String,
    val scenarios: List<RegistrationScenario>
)

typealias RegistrationScenario = ScenarioSummary
typealias RegistrationDirectedAcyclicGraph = DirectedAcyclicGraphSummary

/**
 * Response to the handshake initialized from a factory.
 *
 * @property handshakeNodeId ID of the node as received from the factory at the beginning of the handshake
 * @property nodeId final ID of the node, as assigned from the head
 * @property unicastDirectivesChannel name of the channel to listen for the unicast directives
 * @property broadcastDirectivesChannel name of the channel to listen for the broadcast directives
 * @property feedbackChannel name of the channel to use to send the feedbacks to directives
 * @property heartbeatChannel name of the channel to use to send the heartbeats
 * @property heartbeatPeriod period to emmit the heartbeats
 */
data class HandshakeResponse(
    val handshakeNodeId: String,
    val nodeId: String,
    val unicastDirectivesChannel: String,
    val broadcastDirectivesChannel: String,
    val feedbackChannel: String,
    val heartbeatChannel: String,
    val heartbeatPeriod: Duration
)