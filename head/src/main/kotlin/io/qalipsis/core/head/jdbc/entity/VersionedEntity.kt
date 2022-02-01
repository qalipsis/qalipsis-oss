package io.qalipsis.core.head.jdbc.entity

import java.time.Instant

/**
 * Entity which encapsulates all of the common properties for persistent storage
 *
 * @author rklymenko
 */
internal interface VersionedEntity : Entity {
    val version: Instant
}