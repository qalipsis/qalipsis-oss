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

package io.qalipsis.core.head.model

import io.micronaut.core.annotation.Introspected
import io.qalipsis.api.context.NodeId
import java.time.Instant

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
    open val version: Instant,
    val tags: Map<String, String> = emptyMap(),
    open val activeScenarios: Collection<String> = emptySet(),
    val zone: String? = null
) {

    constructor(nodeId: String, unicastChannel: String) : this(
        nodeId,
        Instant.now(),
        unicastChannel,
        Instant.now()
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Factory

        return nodeId == other.nodeId
    }

    override fun hashCode(): Int {
        return nodeId.hashCode()
    }

    override fun toString(): String {
        return "Factory(nodeId='$nodeId', registrationTimestamp=$registrationTimestamp, version=$version, tags=$tags, supportedScenarios=$activeScenarios)"
    }
}