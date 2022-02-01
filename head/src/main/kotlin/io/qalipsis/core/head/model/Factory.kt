package io.qalipsis.core.head.model

import io.micronaut.core.annotation.Introspected
import java.time.Instant

typealias NodeId = String

/**
 * Model of a factory able to execute scenarios.
 *
 * @author Eric Jessé
 */
@Introspected
internal open class Factory(
    val nodeId: NodeId,
    val registrationTimestamp: Instant,
    val unicastChannel: String,
    val broadcastChannel: String,
    open val version: Instant,
    val selectors: Map<String, String> = emptyMap(),
    open val activeScenarios: Collection<String> = emptySet()
) {

    constructor(nodeId: String, unicastChannel: String, broadcastChannel: String) : this(
        nodeId,
        Instant.now(),
        unicastChannel,
        broadcastChannel,
        Instant.now()
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Factory

        if (nodeId != other.nodeId) return false

        return true
    }

    override fun hashCode(): Int {
        return nodeId.hashCode()
    }

    override fun toString(): String {
        return "Factory(nodeId='$nodeId', registrationTimestamp=$registrationTimestamp, version=$version, selectors=$selectors, supportedScenarios=$activeScenarios)"
    }
}